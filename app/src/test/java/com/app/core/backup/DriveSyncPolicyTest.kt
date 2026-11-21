package com.app.core.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveSyncPolicyTest {
    private val synced = DriveBackupRevision("backup", 100, "1", null)
    private val newer = synced.copy(modifiedAt = 200, version = "2")

    @Test
    fun onlyUploadsOverThePreviouslySyncedRevision() {
        assertEquals(DriveSyncAction.CREATE, decideDriveSync(true, null, null))
        assertEquals(DriveSyncAction.UPDATE, decideDriveSync(true, synced, synced))
        assertEquals(DriveSyncAction.UNCHANGED, decideDriveSync(false, synced, synced))
        assertEquals(DriveSyncAction.COMPARE, decideDriveSync(true, synced, newer))
        assertEquals(DriveSyncAction.COMPARE, decideDriveSync(true, null, newer))
    }

    @Test
    fun onlyAutoRestoresAnUpdatedKnownBackupOverUnchangedLocalData() {
        assertEquals(DriveSyncAction.RESTORE_IF_CLEAN, decideDriveSync(false, synced, newer))
        assertEquals(DriveSyncAction.COMPARE, decideDriveSync(false, synced, newer.copy(id = "replacement")))
        assertEquals(DriveSyncAction.COMPARE, decideDriveSync(false, synced, newer.copy(modifiedAt = 90)))
        assertEquals(DriveSyncAction.CONFLICT, decideDriveSync(false, synced, null))
    }
}
