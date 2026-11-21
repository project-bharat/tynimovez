package com.projectbharat.counterapp.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class CounterCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titleMr: String,
    val titleEn: String,
    val kind: String = "GENERAL",
    val goal: Int = 0,
    val enabled: Boolean = true
)

@Entity(tableName = "baby_sessions", indices = [Index("categoryId"), Index("date"), Index("startedAt")])
data class BabySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val date: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val dayPeriod: String,
    val movementCount: Int = 0,
    val timeToTenMs: Long? = null,
    val strength: String? = null,
    val note: String? = null,
    @ColumnInfo(defaultValue = "'TEN_MOVEMENTS'") val trackingMode: String = "TWO_HOURS",
    @ColumnInfo(defaultValue = "0") val firstHourCount: Int = 0
)

@Entity(tableName = "baby_detections", indices = [Index("sessionId"), Index("timestamp")])
data class BabyDetection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val magnitude: Float,
    val status: String = "PENDING"
)

@Entity(tableName = "counter_logs", indices = [Index("categoryId"), Index("timestamp"), Index("sessionId")])
data class CounterLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val timestamp: Long,
    val categoryId: Long,
    val count: Int = 1,
    val sessionDurationMs: Long? = null,
    val strength: String? = null,
    val note: String? = null,
    val sessionId: Long? = null
)

@Entity(tableName = "walking_sessions", indices = [Index("categoryId"), Index("date"), Index("startedAt")])
data class WalkingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val date: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val steps: Int = 0,
    @ColumnInfo(defaultValue = "0.0") val distanceMeters: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val gpsReliable: Boolean = false
)

@Dao
interface CounterDao {
    @Query("SELECT * FROM categories WHERE enabled = 1 AND kind = 'BABY' ORDER BY id")
    fun categories(): Flow<List<CounterCategory>>

    @Insert
    suspend fun addCategory(category: CounterCategory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(items: List<CounterCategory>)

    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun allCategories(): List<CounterCategory>

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Update
    suspend fun updateCategory(category: CounterCategory)

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun category(id: Long): CounterCategory?

    @Query("UPDATE categories SET enabled = 0 WHERE id = :id")
    suspend fun disableCategory(id: Long)

    @Query("SELECT * FROM categories WHERE enabled = 0 ORDER BY id")
    fun archivedCategories(): Flow<List<CounterCategory>>

    @Query("UPDATE categories SET enabled = 1 WHERE id = :id")
    suspend fun enableCategory(id: Long)

    @Query("SELECT COALESCE(SUM(count),0) FROM counter_logs WHERE categoryId=:categoryId AND date=:date")
    fun todayCount(categoryId: Long, date: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(count),0) FROM counter_logs WHERE categoryId=:categoryId AND date=:date")
    suspend fun countNow(categoryId: Long, date: String): Int

    @Insert
    suspend fun addLog(log: CounterLog): Long

    @Insert
    suspend fun insertWalkingSession(session: WalkingSession): Long

    @Query("UPDATE walking_sessions SET steps=steps+:steps, distanceMeters=distanceMeters+:distanceMeters, gpsReliable=CASE WHEN gpsReliable=1 OR :distanceMeters>0 THEN 1 ELSE 0 END WHERE id=:id AND endedAt IS NULL")
    suspend fun addWalkingMetrics(id: Long, steps: Int, distanceMeters: Double)

    @Query("UPDATE walking_sessions SET endedAt=:endedAt WHERE id=:id AND endedAt IS NULL")
    suspend fun finishWalkingSession(id: Long, endedAt: Long)

    @Query("UPDATE walking_sessions SET endedAt=:endedAt WHERE categoryId=:categoryId AND endedAt IS NULL")
    suspend fun finishOpenWalkingSessions(categoryId: Long, endedAt: Long)

    @Query("SELECT * FROM walking_sessions WHERE categoryId=:categoryId AND date BETWEEN :fromDate AND :toDate ORDER BY startedAt DESC")
    fun walkingSessionsForRange(categoryId: Long, fromDate: String, toDate: String): Flow<List<WalkingSession>>

    @Query("SELECT * FROM walking_sessions ORDER BY id")
    suspend fun allWalkingSessions(): List<WalkingSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWalkingSessions(items: List<WalkingSession>)

    @Query("DELETE FROM walking_sessions")
    suspend fun deleteAllWalkingSessions()

    @Insert
    suspend fun addBabyDetection(detection: BabyDetection): Long

    @Query("SELECT * FROM baby_detections WHERE sessionId=:sessionId ORDER BY timestamp DESC")
    fun detectionsForSession(sessionId: Long): Flow<List<BabyDetection>>

    @Query("SELECT d.* FROM baby_detections d INNER JOIN baby_sessions s ON s.id=d.sessionId WHERE s.categoryId=:categoryId AND s.date=:date ORDER BY d.timestamp DESC")
    fun detectionsForDay(categoryId: Long, date: String): Flow<List<BabyDetection>>

    @Query("SELECT * FROM baby_detections ORDER BY id")
    suspend fun allBabyDetections(): List<BabyDetection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBabyDetections(items: List<BabyDetection>)

    @Query("DELETE FROM baby_detections")
    suspend fun deleteAllBabyDetections()

    @Query("UPDATE baby_detections SET status='DISMISSED' WHERE id=:id AND status='PENDING'")
    suspend fun dismissDetection(id: Long)

    @Transaction
    suspend fun confirmDetection(id: Long, log: CounterLog, durationMs: Long): Boolean {
        val candidate = pendingDetection(id) ?: return false
        addLog(log.copy(timestamp = candidate.timestamp, sessionId = candidate.sessionId))
        incrementBabySession(candidate.sessionId, durationMs)
        markDetectionConfirmed(id)
        return true
    }

    @Query("SELECT * FROM baby_detections WHERE id=:id AND status='PENDING' LIMIT 1")
    suspend fun pendingDetection(id: Long): BabyDetection?

    @Query("UPDATE baby_detections SET status='CONFIRMED' WHERE id=:id AND status='PENDING'")
    suspend fun markDetectionConfirmed(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(items: List<CounterLog>)

    @Query("SELECT * FROM counter_logs ORDER BY id")
    suspend fun allLogs(): List<CounterLog>

    @Query("DELETE FROM counter_logs")
    suspend fun deleteAllLogs()

    @Update
    suspend fun updateLog(log: CounterLog)

    @Query("SELECT * FROM counter_logs WHERE categoryId=:categoryId AND date=:date ORDER BY timestamp DESC, id DESC")
    fun logsForDay(categoryId: Long, date: String): Flow<List<CounterLog>>

    @Query("SELECT * FROM counter_logs WHERE categoryId=:categoryId AND date BETWEEN :fromDate AND :toDate ORDER BY timestamp DESC, id DESC")
    fun logsForRange(categoryId: Long, fromDate: String, toDate: String): Flow<List<CounterLog>>

    @Query("SELECT * FROM counter_logs WHERE categoryId=:categoryId AND date=:date ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestLog(categoryId: Long, date: String): CounterLog?

    @Query("DELETE FROM counter_logs WHERE id=:id")
    suspend fun deleteLog(id: Long)

    @Transaction
    suspend fun addBabyMovementsAtomically(
        sessionId: Long, now: Long, sessionLimitMs: Long, amount: Int, strength: String?,
        expectedStart: Long? = null
    ): Boolean {
        val session = babySession(sessionId) ?: return false
        if (expectedStart != null && session.startedAt != expectedStart) return false
        val duration = now - session.startedAt
        if (session.endedAt != null || duration >= sessionLimitMs) return false
        repeat(amount) {
            addLog(CounterLog(date = session.date, timestamp = now, categoryId = session.categoryId,
                strength = strength, sessionId = sessionId))
            incrementBabySession(sessionId, duration)
        }
        return true
    }

    @Transaction
    suspend fun undoLatestBabyMovementAtomically(sessionId: Long): Boolean {
        val latest = latestSessionLog(sessionId) ?: return false
        deleteSessionLog(latest.id)
        decrementBabySession(sessionId, latest.timestamp)
        return true
    }

    @Query("DELETE FROM counter_logs WHERE categoryId=:categoryId AND date=:date")
    suspend fun resetDay(categoryId: Long, date: String)

    @Query("SELECT date, SUM(count) AS total FROM counter_logs WHERE categoryId=:categoryId AND date BETWEEN :fromDate AND :toDate GROUP BY date ORDER BY date")
    fun weeklyTotals(categoryId: Long, fromDate: String, toDate: String): Flow<List<DayTotal>>

    @Query("SELECT AVG(sessionDurationMs) FROM counter_logs WHERE categoryId=:categoryId AND sessionDurationMs IS NOT NULL AND date BETWEEN :fromDate AND :toDate")
    fun averageSession(categoryId: Long, fromDate: String, toDate: String): Flow<Double?>

    @Insert
    suspend fun insertBabySession(session: BabySession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBabySessions(items: List<BabySession>)

    @Query("SELECT * FROM baby_sessions ORDER BY id")
    suspend fun allBabySessions(): List<BabySession>

    @Query("DELETE FROM baby_sessions")
    suspend fun deleteAllBabySessions()

    @Transaction
    suspend fun restoreSnapshot(categories: List<CounterCategory>, logs: List<CounterLog>, sessions: List<BabySession>, detections: List<BabyDetection>, walkingSessions: List<WalkingSession>) {
        deleteAllLogs()
        deleteAllBabySessions()
        deleteAllBabyDetections()
        deleteAllWalkingSessions()
        deleteAllCategories()
        insertCategories(categories)
        if (sessions.isNotEmpty()) insertBabySessions(sessions)
        if (detections.isNotEmpty()) insertBabyDetections(detections)
        if (walkingSessions.isNotEmpty()) insertWalkingSessions(walkingSessions)
        if (logs.isNotEmpty()) insertLogs(logs)
    }

    @Query("SELECT * FROM baby_sessions WHERE categoryId=:categoryId AND endedAt IS NULL AND startedAt > :lockStart ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeBabySession(categoryId: Long, lockStart: Long): BabySession?

    @Query("SELECT * FROM baby_sessions WHERE categoryId=:categoryId AND startedAt > :lockStart ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestBabySession(categoryId: Long, lockStart: Long): BabySession?

    @Query("SELECT * FROM baby_sessions WHERE categoryId=:categoryId AND date=:date ORDER BY startedAt DESC")
    fun babySessionsForDay(categoryId: Long, date: String): Flow<List<BabySession>>

    @Query("SELECT * FROM baby_sessions WHERE categoryId=:categoryId AND date BETWEEN :fromDate AND :toDate ORDER BY startedAt DESC")
    fun babySessionsForMonth(categoryId: Long, fromDate: String, toDate: String): Flow<List<BabySession>>

    @Query("SELECT * FROM baby_sessions WHERE categoryId=:categoryId AND startedAt > :lockStart ORDER BY startedAt DESC LIMIT 1")
    fun latestSessionFlow(categoryId: Long, lockStart: Long): Flow<BabySession?>

    @Query("UPDATE baby_sessions SET movementCount=movementCount+1, timeToTenMs=CASE WHEN movementCount=9 AND timeToTenMs IS NULL THEN :durationMs ELSE timeToTenMs END, firstHourCount=CASE WHEN :durationMs<=3600000 THEN firstHourCount+1 ELSE firstHourCount END WHERE id=:sessionId")
    suspend fun incrementBabySession(sessionId: Long, durationMs: Long)

    @Query("UPDATE baby_sessions SET endedAt=:endedAt, strength=:strength WHERE id=:sessionId")
    suspend fun finishBabySession(sessionId: Long, endedAt: Long, strength: String?)

    @Query("UPDATE baby_sessions SET date=:date, startedAt=:startedAt, endedAt=:endedAt, movementCount=:movementCount, timeToTenMs=:timeToTenMs WHERE id=:id")
    suspend fun updateBabySessionFields(id: Long, date: String, startedAt: Long, endedAt: Long?, movementCount: Int, timeToTenMs: Long?)

    @Query("DELETE FROM counter_logs WHERE sessionId=:sessionId")
    suspend fun deleteBabySessionLogs(sessionId: Long)

    @Transaction
    suspend fun editBabySessionAndLog(session: BabySession) {
        updateBabySessionFields(session.id, session.date, session.startedAt, session.endedAt, session.movementCount, session.timeToTenMs)
        deleteBabySessionLogs(session.id)
        if (session.movementCount > 0) addLog(CounterLog(date=session.date, timestamp=session.startedAt, categoryId=session.categoryId, count=session.movementCount, strength=session.strength, sessionId=session.id))
    }

    @Query("UPDATE baby_sessions SET endedAt=NULL, strength=NULL WHERE id=:sessionId AND endedAt IS NOT NULL")
    suspend fun resumeBabySession(sessionId: Long)

    @Query("SELECT * FROM baby_sessions WHERE id=:id LIMIT 1")
    suspend fun babySession(id: Long): BabySession?

    @Query("SELECT * FROM baby_sessions WHERE id=:id LIMIT 1")
    fun observeBabySession(id: Long): Flow<BabySession?>

    @Query("SELECT AVG(timeToTenMs) FROM baby_sessions WHERE categoryId=:categoryId AND timeToTenMs IS NOT NULL AND date BETWEEN :fromDate AND :toDate")
    fun averageBabyTimeToTen(categoryId: Long, fromDate: String, toDate: String): Flow<Double?>

    @Query("SELECT * FROM counter_logs WHERE sessionId=:sessionId ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestSessionLog(sessionId: Long): CounterLog?

    @Query("DELETE FROM counter_logs WHERE id=:id")
    suspend fun deleteSessionLog(id: Long)

    @Query("UPDATE baby_sessions SET timeToTenMs=CASE WHEN movementCount=10 THEN NULL ELSE timeToTenMs END, movementCount=CASE WHEN movementCount>0 THEN movementCount-1 ELSE 0 END, firstHourCount=CASE WHEN :timestamp<startedAt+3600000 AND firstHourCount>0 THEN firstHourCount-1 ELSE firstHourCount END WHERE id=:sessionId")
    suspend fun decrementBabySession(sessionId: Long, timestamp: Long)
}

data class DayTotal(val date: String, val total: Int)

@Database(entities = [CounterCategory::class, CounterLog::class, BabySession::class, BabyDetection::class, WalkingSession::class], version = 8, exportSchema = false)
abstract class CounterDatabase : RoomDatabase() {
    abstract fun dao(): CounterDao
    companion object {
        @Volatile private var INSTANCE: CounterDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS baby_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, categoryId INTEGER NOT NULL, date TEXT NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER, dayPeriod TEXT NOT NULL, movementCount INTEGER NOT NULL DEFAULT 0, strength TEXT, note TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_baby_sessions_categoryId ON baby_sessions(categoryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_baby_sessions_date ON baby_sessions(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_baby_sessions_startedAt ON baby_sessions(startedAt)")
                db.execSQL("ALTER TABLE baby_sessions ADD COLUMN timeToTenMs INTEGER")
                db.execSQL("ALTER TABLE counter_logs ADD COLUMN sessionId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_counter_logs_sessionId ON counter_logs(sessionId)")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 2 already contains timeToTenMs.
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS baby_detections (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId INTEGER NOT NULL, timestamp INTEGER NOT NULL, magnitude REAL NOT NULL, status TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_baby_detections_sessionId ON baby_detections(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_baby_detections_timestamp ON baby_detections(timestamp)")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE baby_sessions ADD COLUMN trackingMode TEXT NOT NULL DEFAULT 'TEN_MOVEMENTS'")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Preserve historical per-counter totals in the legacy database.
                db.execSQL("CREATE INDEX IF NOT EXISTS index_counter_logs_categoryId_date_note ON counter_logs(categoryId, date, note)")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE baby_sessions ADD COLUMN firstHourCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE baby_sessions SET firstHourCount=(SELECT COALESCE(SUM(count),0) FROM counter_logs WHERE counter_logs.sessionId=baby_sessions.id AND counter_logs.timestamp < baby_sessions.startedAt+3600000)")
                db.execSQL("CREATE TABLE IF NOT EXISTS walking_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, categoryId INTEGER NOT NULL, date TEXT NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER, steps INTEGER NOT NULL DEFAULT 0, distanceMeters REAL NOT NULL DEFAULT 0.0, gpsReliable INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_walking_sessions_categoryId ON walking_sessions(categoryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_walking_sessions_date ON walking_sessions(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_walking_sessions_startedAt ON walking_sessions(startedAt)")
            }
        }
                private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Hide legacy step-counter category; retain its records for existing backups.
                db.execSQL("UPDATE categories SET enabled = 0 WHERE kind = 'STEPS'")
            }
        }
        fun get(context: Context): CounterDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, CounterDatabase::class.java, "counterapp.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build().also { INSTANCE = it }
        }
    }
}
