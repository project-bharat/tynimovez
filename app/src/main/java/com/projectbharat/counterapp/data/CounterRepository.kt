package com.projectbharat.counterapp.data

import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

class CounterRepository(private val dao: CounterDao) {
    val categories = dao.categories()
    fun today(categoryId: Long): Flow<Int> = dao.todayCount(categoryId, LocalDate.now().toString())
    fun logsForDay(categoryId: Long, date: LocalDate): Flow<List<CounterLog>> = dao.logsForDay(categoryId, date.toString())
    suspend fun updateLog(log: CounterLog) {
        require(log.count > 0) { "Count must be positive" }
        dao.updateLog(log.copy(note = log.note?.trim()?.takeIf { it.isNotEmpty() }))
    }
    suspend fun deleteLog(id: Long) = dao.deleteLog(id)

    fun week(categoryId: Long): Flow<List<DayTotal>> {
        val today = LocalDate.now()
        return dao.weeklyTotals(categoryId, today.minusDays(6).toString(), today.toString())
    }
    fun babySessionsForDay(categoryId: Long, date: String = LocalDate.now().toString()) =
        dao.babySessionsForDay(categoryId, date)

    fun babySessionsForMonth(categoryId: Long, month: LocalDate): Flow<List<BabySession>> {
        val first = month.withDayOfMonth(1)
        return dao.babySessionsForMonth(categoryId, first.toString(), first.withDayOfMonth(first.lengthOfMonth()).toString())
    }

    fun averageBabyTimeToTen(categoryId: Long): Flow<Double?> {
        val today = LocalDate.now()
        return dao.averageBabyTimeToTen(categoryId, today.minusDays(6).toString(), today.toString())
    }

    fun latestBabySession(categoryId: Long): Flow<BabySession?> =
        dao.latestSessionFlow(categoryId, 0L)

    fun babyDetections(sessionId: Long): Flow<List<BabyDetection>> = dao.detectionsForSession(sessionId)
    fun babyDetectionsForDay(categoryId: Long, date: String): Flow<List<BabyDetection>> = dao.detectionsForDay(categoryId, date)

    private fun sessionLimit(session: BabySession): Long = TWO_HOURS_MS

    suspend fun recordBabyDetection(sessionId: Long, timestamp: Long, magnitude: Float) {
        val session = dao.babySession(sessionId) ?: return
        if (session.endedAt == null && timestamp >= session.startedAt && timestamp < session.startedAt + sessionLimit(session)) {
            dao.addBabyDetection(BabyDetection(sessionId = sessionId, timestamp = timestamp, magnitude = magnitude))
        }
    }

    suspend fun dismissBabyDetection(id: Long) = dao.dismissDetection(id)

    suspend fun confirmBabyDetection(id: Long) {
        val detection = dao.pendingDetection(id) ?: return
        val session = dao.babySession(detection.sessionId) ?: return
        val sessionEnd = minOf(session.endedAt ?: (session.startedAt + sessionLimit(session)), session.startedAt + sessionLimit(session))
        if (detection.timestamp < session.startedAt || detection.timestamp > sessionEnd) return
        dao.confirmDetection(id, CounterLog(date = session.date, timestamp = detection.timestamp, categoryId = session.categoryId), detection.timestamp - session.startedAt)
        // Reaching ten movements records a milestone; the two-hour session continues.
    }

    suspend fun startBabySession(categoryId: Long): Long? {
        val now = System.currentTimeMillis()
        val previous = dao.latestBabySession(categoryId, now - TWO_HOURS_MS)
        if (previous != null && now < previous.startedAt + sessionLimit(previous)) return null
        val time = LocalTime.now()
        val period = when (time.hour) {
            in 5..10 -> "MORNING"
            in 11..15 -> "AFTERNOON"
            in 16..18 -> "EVENING"
            else -> "NIGHT"
        }
        return dao.insertBabySession(
            BabySession(
                categoryId = categoryId,
                date = LocalDate.now().toString(),
                startedAt = now,
                dayPeriod = period,
                trackingMode = "TWO_HOURS"
            )
        )
    }

    suspend fun addBabyMovement(categoryId: Long, sessionId: Long, strength: String?) {
        val now = System.currentTimeMillis()
        val session = dao.babySession(sessionId) ?: return
        if (session.endedAt != null || now >= session.startedAt + sessionLimit(session)) return
        dao.addBabyMovementAtomically(
            CounterLog(
                date = session.date,
                timestamp = now,
                categoryId = categoryId,
                strength = strength,
                sessionId = sessionId
            ),
            sessionId,
            now - session.startedAt
        )
        // Do not end the session at ten movements; keep recording until two hours.
    }

    suspend fun undoBabyMovement(sessionId: Long) {
        dao.undoLatestBabyMovementAtomically(sessionId)
    }

    suspend fun updateBabySessionRecord(session: BabySession) = dao.editBabySessionAndLog(session)

    suspend fun finishBabySession(sessionId: Long, strength: String?) {
        val now = System.currentTimeMillis()
        val session = dao.babySession(sessionId) ?: return
        if (session.endedAt == null) dao.finishBabySession(sessionId, minOf(now, session.startedAt + sessionLimit(session)), strength)
    }

    suspend fun resumeBabySession(sessionId: Long) {
        val now = System.currentTimeMillis()
        val session = dao.babySession(sessionId) ?: return
        if (session.endedAt != null && now < session.startedAt + sessionLimit(session)) dao.resumeBabySession(sessionId)
    }

    suspend fun completeExpiredBabySession(sessionId: Long) {
        val session = dao.babySession(sessionId) ?: return
        val completedAt = session.startedAt + sessionLimit(session)
        if (session.endedAt == null && System.currentTimeMillis() >= completedAt) {
            dao.finishBabySession(sessionId, completedAt, session.strength)
        }
    }

    companion object {
        const val TWO_HOURS_MS = 2 * 60 * 60 * 1000L
    }
}
