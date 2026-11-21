package com.app.core.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

data class DriveSyncState(
    val email: String? = null,
    val automatic: Boolean = false,
    val lastSynced: Long? = null,
    val syncing: Boolean = false,
    val message: String? = null
)

class DriveSyncCoordinator<T>(
    context: Context,
    private val repository: BackupRepository<T>,
    private val subscription: SubscriptionChecker,
    val authorization: GoogleDriveAuthorization
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("private_drive_backup", Context.MODE_PRIVATE)
    private val workManager get() = WorkManager.getInstance(app)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(
        DriveSyncState(prefs.getString("email", null), prefs.getBoolean("automatic", false),
            prefs.getLong("lastSynced", 0).takeIf { it > 0 }))
    val state = mutableState.asStateFlow()

    suspend fun link(email: String) = mutex.withLock {
        require(subscription.refresh()) { "A Pro subscription is required." }
        // The background flow must be able to re-authorize for this exact account.
        authorization.token(email)
        val previous = mutableState.value
        if (previous.email != email) {
            prefs.edit().clear().putString("email", email).apply()
            mutableState.value = DriveSyncState(email = email)
            workManager.cancelUniqueWork(PERIODIC_WORK)
        } else {
            mutableState.value = previous.copy(message = null)
        }
    }

    suspend fun disconnect() = mutex.withLock {
        prefs.edit().clear().apply()
        mutableState.value = DriveSyncState()
        workManager.cancelUniqueWork(PERIODIC_WORK)
        workManager.cancelUniqueWork(IMMEDIATE_WORK)
    }

    suspend fun setAutomatic(enabled: Boolean) = mutex.withLock {
        if (enabled) {
            require(mutableState.value.email != null && subscription.refresh()) { "Connect Google Drive with Pro first." }
            val request = PeriodicWorkRequestBuilder<DriveAutoSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(network).setInputData(androidx.work.workDataOf("periodic" to true)).build()
            workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            workManager.cancelUniqueWork(PERIODIC_WORK)
        }
        prefs.edit().putBoolean("automatic", enabled).apply()
        mutableState.value = mutableState.value.copy(automatic = enabled)
    }

    fun triggerManualDriveSync() {
        workManager.enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DriveAutoSyncWorker>().setConstraints(network).build())
        mutableState.value = mutableState.value.copy(message = "Drive sync queued.")
    }

    internal suspend fun sync(periodic: Boolean = false): Result<Unit> = operate(periodic) { drive ->
        val local = repository.snapshot()
        val localHash = repository.fingerprint(local)
        val previousHash = prefs.getString("hash", null)
        val previous = savedRevision()
        val remote = drive.revision()
        val localChanged = localHash != previousHash
        when {
            remote == null && previous != null -> throw DriveBackupConflict()
            remote == null -> record(drive.upload(repository.encode(local), null), localHash)
            remote.sameRevision(previous) && localChanged ->
                record(drive.upload(repository.encode(local), remote), localHash)
            remote.sameRevision(previous) -> record(remote, localHash)
            else -> {
                val cloud = repository.decode(drive.download(remote))
                val cloudHash = repository.fingerprint(cloud)
                when {
                    cloudHash == localHash -> record(remote, localHash)
                    previous != null && !localChanged -> {
                        repository.replace(cloud, local)
                        record(remote, cloudHash)
                    }
                    else -> throw DriveBackupConflict()
                }
            }
        }
    }

    suspend fun restoreFromCloud(): Result<Unit> = operate { drive ->
        val local = repository.snapshot()
        val remote = drive.revision() ?: error("No Drive backup found.")
        val cloud = repository.decode(drive.download(remote))
        repository.replace(cloud, local)
        record(remote, repository.fingerprint(cloud))
    }

    private suspend fun operate(
        periodic: Boolean = false,
        action: suspend (GoogleDriveSyncManager) -> Unit
    ): Result<Unit> = mutex.withLock {
        val current = mutableState.value
        val email = current.email ?: return@withLock Result.success(Unit)
        if (periodic && !current.automatic) return@withLock Result.success(Unit)
        mutableState.value = current.copy(syncing = true, message = null)
        try {
            val result = backupResult {
                if (subscription.refresh()) {
                    val drive = GoogleDriveSyncManager(authorization.token(email))
                    withContext(Dispatchers.IO) { action(drive) }
                } else if (!periodic) {
                    error("A Pro subscription is required.")
                }
            }
            mutableState.value = mutableState.value.copy(message = result.exceptionOrNull()?.message)
            result
        } finally {
            mutableState.value = mutableState.value.copy(syncing = false)
        }
    }

    private fun record(remote: DriveBackupRevision, hash: String) {
        val now = System.currentTimeMillis()
        prefs.edit().putString("id", remote.id).putString("version", remote.version)
            .putLong("modified", remote.modifiedAt).putString("hash", hash).putLong("lastSynced", now).apply()
        mutableState.value = mutableState.value.copy(lastSynced = now)
    }

    private fun savedRevision(): DriveBackupRevision? {
        val id = prefs.getString("id", null) ?: return null
        val version = prefs.getString("version", null) ?: return null
        return DriveBackupRevision(id, prefs.getLong("modified", 0), version, null)
    }

    private companion object {
        const val PERIODIC_WORK = "private-drive-periodic-backup"
        const val IMMEDIATE_WORK = "private-drive-manual-backup"
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    }
}

interface DriveSyncProvider {
    val driveSync: DriveSyncCoordinator<*>
}

class DriveAutoSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val provider = applicationContext as? DriveSyncProvider ?: return Result.failure()
        val error = provider.driveSync.sync(inputData.getBoolean("periodic", false)).exceptionOrNull()
        return when {
            error == null -> Result.success()
            runAttemptCount >= 4 -> Result.failure()
            error is BillingUnavailable -> Result.retry()
            error is DriveRequestFailed ->
                if (error.code == 429 || error.code >= 500) Result.retry() else Result.failure()
            error is IOException -> Result.retry()
            else -> Result.failure()
        }
    }
}
