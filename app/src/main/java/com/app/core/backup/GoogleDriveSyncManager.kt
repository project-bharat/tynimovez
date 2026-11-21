package com.app.core.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit
import java.time.Instant
import java.util.UUID

internal data class DriveBackupRevision(
    val id: String,
    val modifiedAt: Long,
    val version: String,
    val etag: String?
) {
    fun sameRevision(other: DriveBackupRevision?) =
        other != null && id == other.id && modifiedAt == other.modifiedAt && version == other.version
}

internal class DriveBackupConflict : Exception("Local and Drive backups differ. Export a local copy before restoring from Drive.")
internal class DriveRequestFailed(val code: Int) : IOException("Google Drive request failed ($code).")

class GoogleDriveSyncManager internal constructor(private val token: String) {
    internal suspend fun revision(): DriveBackupRevision? = withContext(Dispatchers.IO) {
        val listing = request("$FILES?spaces=appDataFolder&q=name%3D%27$NAME%27&pageSize=2&fields=nextPageToken,files(id)") {
            JSONObject(it.readText())
        }
        val files = listing.getJSONArray("files")
        if (files.length() > 1 || listing.has("nextPageToken")) throw DriveBackupConflict()
        if (files.length() == 0) null else {
            val id = checkedId(files.getJSONObject(0).getString("id"))
            request("$FILES/$id?fields=$FIELDS") {
                metadata(JSONObject(it.readText()), it.header("ETag"))
            }
        }
    }

    suspend fun uploadBackup(json: String): Result<Long> = backupResult {
        upload(json, expected = revision()).modifiedAt
    }

    suspend fun downloadLatestBackup(): Result<String?> = backupResult {
        revision()?.let { download(it) }
    }

    internal suspend fun download(expected: DriveBackupRevision): String = withContext(Dispatchers.IO) {
        val body = request("$FILES/${expected.id}?alt=media") { connection ->
            connection.readText()
        }
        if (!expected.sameRevision(revision())) throw DriveBackupConflict()
        body
    }

    internal suspend fun upload(json: String, expected: DriveBackupRevision?): DriveBackupRevision =
        withContext(Dispatchers.IO) {
            val content = json.toByteArray(Charsets.UTF_8)
            require(content.size <= MAX_BACKUP_BYTES) { "Backup exceeds the supported size." }
            val current = revision()
            if (expected == null && current != null || expected != null && !expected.sameRevision(current)) {
                throw DriveBackupConflict()
            }
            if (current == null) {
                val boundary = UUID.randomUUID().toString()
                val metadata = """{"name":"$NAME","parents":["appDataFolder"],"mimeType":"application/json"}"""
                val start = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
                    "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"
                val end = "\r\n--$boundary--\r\n"
                request("$UPLOAD?uploadType=multipart&fields=$FIELDS", "POST",
                    "multipart/related; boundary=$boundary", start.toByteArray() + content + end.toByteArray()) {
                    metadata(JSONObject(it.readText()), it.header("ETag"))
                }
            } else {
                request("$UPLOAD/${current.id}?uploadType=media&fields=$FIELDS", "PATCH",
                    "application/json; charset=UTF-8", content, current.etag) {
                    metadata(JSONObject(it.readText()), it.header("ETag"))
                }
            }
        }

    private fun metadata(json: JSONObject, etag: String?) = DriveBackupRevision(
        checkedId(json.getString("id")), Instant.parse(json.getString("modifiedTime")).toEpochMilli(),
        json.getString("version"), etag)

    private fun checkedId(id: String): String {
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid Drive backup ID." }
        return id
    }

    private fun Response.readText() = requireNotNull(body).byteStream().use { it.readBackup() }

    private fun <T> request(
        url: String,
        method: String = "GET",
        contentType: String? = null,
        body: ByteArray? = null,
        etag: String? = null,
        read: (Response) -> T
    ): T {
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .method(method, body?.toRequestBody(contentType?.toMediaType()))
        if (etag != null) request.header("If-Match", etag)
        return http.newCall(request.build()).execute().use { response ->
            when (val status = response.code) {
                in 200..299 -> read(response)
                412 -> throw DriveBackupConflict()
                else -> throw DriveRequestFailed(status)
            }
        }
    }

    private companion object {
        val http = OkHttpClient.Builder().followRedirects(false).retryOnConnectionFailure(false)
            .callTimeout(60, TimeUnit.SECONDS).build()

        const val NAME = "app_cloud_backup.json"
        const val FILES = "https://www.googleapis.com/drive/v3/files"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
        const val FIELDS = "id,modifiedTime,version"
    }
}
