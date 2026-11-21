package com.app.core.backup

import android.content.Context
import android.net.Uri
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

interface BackupStore<T> {
    suspend fun read(): T
    fun decode(json: String): T
    suspend fun replace(data: T, expectedCurrent: T? = null)
}

data class BackupPayload<T>(
    val schemaVersion: Int = 1,
    val appVersion: String,
    val exportedAtTimestamp: Long,
    val data: T
)

class BackupRepository<T>(private val store: BackupStore<T>, private val appVersion: String) {
    private val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()

    internal suspend fun snapshot(): T = store.read()
    internal fun decode(json: String): T = store.decode(json)
    internal fun fingerprint(snapshot: T): String = MessageDigest.getInstance("SHA-256")
        .digest(gson.toJson(snapshot).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    internal fun encode(snapshot: T): String = gson.toJson(
        BackupPayload(appVersion = appVersion, exportedAtTimestamp = System.currentTimeMillis(), data = snapshot))

    suspend fun exportDatabaseToJson(): String = withContext(Dispatchers.IO) { encode(snapshot()) }

    suspend fun importDatabaseFromJson(jsonString: String): Result<Unit> = backupResult {
        withContext(Dispatchers.IO) { store.replace(decode(jsonString)) }
    }

    internal suspend fun replace(snapshot: T, expectedCurrent: T) = store.replace(snapshot, expectedCurrent)
}

class LocalFileBackupManager<T>(context: Context, private val repository: BackupRepository<T>) {
    private val resolver = context.applicationContext.contentResolver

    fun defaultFileName(): String =
        "AppBackup_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", Locale.ROOT))}.json"

    suspend fun export(uri: Uri): Result<Unit> = backupResult {
        withContext(Dispatchers.IO) {
            val data = repository.exportDatabaseToJson().toByteArray(Charsets.UTF_8)
            require(data.size <= MAX_BACKUP_BYTES) { "Backup exceeds the supported size." }
            requireNotNull(resolver.openOutputStream(uri, "wt")) { "Unable to create the backup file." }
                .use { it.write(data) }
        }
    }

    suspend fun restore(uri: Uri): Result<Unit> = backupResult {
        withContext(Dispatchers.IO) {
            val json = requireNotNull(resolver.openInputStream(uri)) { "Unable to open the backup file." }
                .use { it.readBackup() }
            repository.importDatabaseFromJson(json).getOrThrow()
        }
    }
}

internal const val MAX_BACKUP_BYTES = 32 * 1024 * 1024

internal fun InputStream.readBackup(): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= MAX_BACKUP_BYTES) { "Backup exceeds the supported size." }
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}

internal inline fun <T> backupResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}
