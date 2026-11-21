package com.projectbharat.counterapp

import android.app.Application
import com.app.core.backup.BackupRepository
import com.app.core.backup.DriveSyncCoordinator
import com.app.core.backup.DriveSyncProvider
import com.app.core.backup.GoogleDriveAuthorization
import com.app.core.backup.LocalFileBackupManager
import com.app.core.backup.PlaySubscriptionChecker
import com.projectbharat.counterapp.data.CounterBackupStore
import com.projectbharat.counterapp.data.CounterDatabase

class CounterApplication : Application(), DriveSyncProvider {
    internal val subscription by lazy { PlaySubscriptionChecker(this, getString(R.string.pro_subscription_product_id)) }
    private val repository by lazy {
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        BackupRepository(CounterBackupStore(CounterDatabase.get(this)), version)
    }
    internal val files by lazy { LocalFileBackupManager(this, repository) }
    override val driveSync: DriveSyncCoordinator<*> by lazy {
        DriveSyncCoordinator(this, repository, subscription, GoogleDriveAuthorization(this))
    }
}
