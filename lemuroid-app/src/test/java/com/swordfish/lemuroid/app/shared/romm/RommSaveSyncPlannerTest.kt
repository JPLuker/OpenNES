package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RommSaveSyncPlannerTest {
    private val a = RommSaveSyncPlanner.Snapshot("a".repeat(64), 1024)
    private val b = RommSaveSyncPlanner.Snapshot("b".repeat(64), 1024)
    private val c = RommSaveSyncPlanner.Snapshot("c".repeat(64), 4096)

    private fun request() =
        RommSaveSyncPlanner.Request(
            optedIn = true,
            online = true,
            romId = 31,
            confirmedRomId = 31,
            previousLocal = a,
            previousRemote = a,
            local = b,
            remote = a,
        )

    @Test
    fun syncIsDisabledByDefault() {
        assertEquals(
            RommSaveSyncPlanner.Decision.Disabled,
            RommSaveSyncPlanner.plan(request().copy(optedIn = false)),
        )
    }

    @Test
    fun offlineChangesCannotBeBlindlyReplayed() {
        assertEquals(
            RommSaveSyncPlanner.Decision.RevalidateWhenOnline,
            RommSaveSyncPlanner.plan(
                request().copy(
                    online = false,
                    direction = RommSaveSyncPlanner.Direction.UPLOAD,
                    approvedThisOperation = true,
                    versionedBackupVerified = true,
                ),
            ),
        )
    }

    @Test
    fun unverifiedRomOrUnpairedSaveCannotSync() {
        assertEquals(
            RommSaveSyncPlanner.Decision.UnverifiedIdentity,
            RommSaveSyncPlanner.plan(request().copy(confirmedRomId = 99)),
        )
        assertEquals(
            RommSaveSyncPlanner.Decision.InitialPairingRequired,
            RommSaveSyncPlanner.plan(request().copy(previousRemote = null)),
        )
        assertEquals(
            RommSaveSyncPlanner.Decision.InitialPairingRequired,
            RommSaveSyncPlanner.plan(request().copy(remote = null)),
        )
    }

    @Test
    fun simultaneousLocalAndRemoteChangesAreConflicts() {
        assertEquals(
            RommSaveSyncPlanner.Decision.Conflict,
            RommSaveSyncPlanner.plan(
                request().copy(
                    local = b,
                    remote = c,
                    direction = RommSaveSyncPlanner.Direction.UPLOAD,
                    approvedThisOperation = true,
                    versionedBackupVerified = true,
                ),
            ),
        )
    }

    @Test
    fun uploadRequiresDirectionConsentAndBackup() {
        assertEquals(
            RommSaveSyncPlanner.Decision.ExplicitApprovalRequired,
            RommSaveSyncPlanner.plan(request()),
        )
        assertEquals(
            RommSaveSyncPlanner.Decision.VerifiedBackupRequired,
            RommSaveSyncPlanner.plan(
                request().copy(
                    direction = RommSaveSyncPlanner.Direction.UPLOAD,
                    approvedThisOperation = true,
                ),
            ),
        )
        val decision =
            RommSaveSyncPlanner.plan(
                request().copy(
                    direction = RommSaveSyncPlanner.Direction.UPLOAD,
                    approvedThisOperation = true,
                    versionedBackupVerified = true,
                ),
            )
        assertEquals(RommSaveSyncPlanner.Decision.UploadCopy(b, a), decision)
    }

    @Test
    fun remoteChangesCanOnlyProposeAnExplicitDownload() {
        val remoteChanged = request().copy(local = a, remote = c)
        assertEquals(
            RommSaveSyncPlanner.Decision.ExplicitApprovalRequired,
            RommSaveSyncPlanner.plan(
                remoteChanged.copy(
                    direction = RommSaveSyncPlanner.Direction.UPLOAD,
                    approvedThisOperation = true,
                    versionedBackupVerified = true,
                ),
            ),
        )
        assertEquals(
            RommSaveSyncPlanner.Decision.DownloadCopy(a, c),
            RommSaveSyncPlanner.plan(
                remoteChanged.copy(
                    direction = RommSaveSyncPlanner.Direction.DOWNLOAD,
                    approvedThisOperation = true,
                    versionedBackupVerified = true,
                ),
            ),
        )
    }

    @Test
    fun identicalSnapshotsRequireNoWrite() {
        assertEquals(
            RommSaveSyncPlanner.Decision.InSync,
            RommSaveSyncPlanner.plan(request().copy(local = a, remote = a)),
        )
    }

    @Test
    fun invalidFingerprintsAreRejectedBeforePlanning() {
        var rejected = false
        try {
            RommSaveSyncPlanner.Snapshot("unknown", 20)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
