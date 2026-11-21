package com.projectbharat.counterapp.data

import androidx.room.withTransaction
import com.app.core.backup.BackupStore
import org.json.JSONArray
import org.json.JSONObject

internal data class CounterBackupData(
    val categories: List<CounterCategory>,
    val logs: List<CounterLog>,
    val babySessions: List<BabySession>,
    val babyDetections: List<BabyDetection>,
    val walkingSessions: List<WalkingSession>
)

internal class CounterBackupStore(private val db: CounterDatabase) : BackupStore<CounterBackupData> {
    private val dao = db.dao()

    override suspend fun read(): CounterBackupData = db.withTransaction { readTables() }

    private suspend fun readTables() = CounterBackupData(
        dao.allCategories().sortedBy { it.id }, dao.allLogs().sortedBy { it.id },
        dao.allBabySessions().sortedBy { it.id }, dao.allBabyDetections().sortedBy { it.id },
        dao.allWalkingSessions().sortedBy { it.id })

    override suspend fun replace(data: CounterBackupData, expectedCurrent: CounterBackupData?) {
        db.withTransaction {
            val current = readTables()
            check(expectedCurrent == null || current == expectedCurrent) { "Local data changed. Try again." }
            check(current.babySessions.none {
                it.endedAt == null && System.currentTimeMillis() < it.startedAt + CounterRepository.TWO_HOURS_MS
            }) { "End the active movement session before restoring." }
            dao.restoreSnapshot(data.categories, data.logs, data.babySessions, data.babyDetections, data.walkingSessions)
        }
    }

    override fun decode(json: String): CounterBackupData {
        val envelope = JSONObject(json)
        val legacy = !envelope.has("schemaVersion")
        require(if (legacy) envelope.optInt("formatVersion", -1) == 1 else envelope.optInt("schemaVersion", -1) == 1) {
            "Unsupported backup format."
        }
        if (!legacy) {
            require(envelope.getString("appVersion").isNotBlank() && envelope.getLong("exportedAtTimestamp") > 0)
        }
        val root = if (legacy) envelope else envelope.getJSONObject("data")
        val categoryArray = root.getJSONArray("categories")
        val logArray = root.getJSONArray("logs")
        val sessionArray = root.getJSONArray("babySessions")
        val detectionArray = if (legacy && !root.has("babyDetections")) JSONArray() else root.getJSONArray("babyDetections")
        val walkingArray = if (legacy && !root.has("walkingSessions")) JSONArray() else root.getJSONArray("walkingSessions")
        require(categoryArray.length() > 0) { "Backup contains no categories" }

        val categories = (0 until categoryArray.length()).map { i ->
            val o = categoryArray.getJSONObject(i)
            CounterCategory(o.getLong("id"), o.getString("titleMr"), o.getString("titleEn"),
                o.optString("kind", "GENERAL"), o.optInt("goal", 0), o.optBoolean("enabled", true))
        }
        val logs = (0 until logArray.length()).map { i ->
            val o = logArray.getJSONObject(i)
            CounterLog(o.getLong("id"), o.getString("date"), o.getLong("timestamp"),
                o.getLong("categoryId"), o.optInt("count", 1), o.longOrNull("sessionDurationMs"),
                o.stringOrNull("strength"), o.stringOrNull("note"), o.longOrNull("sessionId"))
        }
        val sessions = (0 until sessionArray.length()).map { i ->
            val o = sessionArray.getJSONObject(i)
            BabySession(o.getLong("id"), o.getLong("categoryId"), o.getString("date"),
                o.getLong("startedAt"), o.longOrNull("endedAt"), o.getString("dayPeriod"),
                o.optInt("movementCount", 0), o.longOrNull("timeToTenMs"),
                o.stringOrNull("strength"), o.stringOrNull("note"), o.optString("trackingMode", "TWO_HOURS"), o.optInt("firstHourCount", 0))
        }

        val detections = (0 until detectionArray.length()).map { i ->
            val o = detectionArray.getJSONObject(i)
            BabyDetection(o.getLong("id"), o.getLong("sessionId"), o.getLong("timestamp"), o.getDouble("magnitude").toFloat(), o.optString("status", "PENDING"))
        }
        val walkingSessions = (0 until walkingArray.length()).map { i ->
            val o = walkingArray.getJSONObject(i)
            WalkingSession(o.getLong("id"), o.getLong("categoryId"), o.getString("date"), o.getLong("startedAt"),
                o.longOrNull("endedAt"), o.optInt("steps", 0), o.optDouble("distanceMeters", 0.0), o.optBoolean("gpsReliable", false))
        }
        require(detections.all { d -> sessions.any { it.id == d.sessionId } && d.status in setOf("PENDING", "CONFIRMED", "DISMISSED") }) { "Invalid baby detection records" }
        require(walkingSessions.all { it.steps >= 0 && it.distanceMeters >= 0.0 && categories.any { c -> c.id == it.categoryId } }) { "Invalid walking session records" }
        BackupSnapshotValidator.validate(categories, logs, sessions)
        require(detections.map { it.id }.toSet().size == detections.size &&
            detections.all { it.id > 0 && it.timestamp >= 0 && it.magnitude.isFinite() })
        require(walkingSessions.map { it.id }.toSet().size == walkingSessions.size &&
            walkingSessions.all { it.id > 0 && it.distanceMeters.isFinite() && it.startedAt >= 0 &&
                (it.endedAt == null || it.endedAt >= it.startedAt) && runCatching { java.time.LocalDate.parse(it.date) }.isSuccess })
        return CounterBackupData(categories.sortedBy { it.id }, logs.sortedBy { it.id },
            sessions.sortedBy { it.id }, detections.sortedBy { it.id }, walkingSessions.sortedBy { it.id })
    }


    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
}
