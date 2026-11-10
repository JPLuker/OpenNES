package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RommPendingSyncJournalTest {
    private fun snapshot(letter: String) =
        RommSaveSyncPlanner.Snapshot(letter.repeat(64), 128L)

    @Test
    fun journalSurvivesProcessRecreationWithoutStoringApproval() {
        val folder = Files.createTempDirectory("romm-pending-").toFile()
        try {
            val original = RommPendingSyncJournal(folder)
            assertTrue(
                original.enqueue(
                    42,
                    RommSaveSyncPlanner.Direction.UPLOAD,
                    snapshot("b"),
                    snapshot("a"),
                ),
            )
            assertFalse(
                original.enqueue(
                    42,
                    RommSaveSyncPlanner.Direction.DOWNLOAD,
                    snapshot("b"),
                    snapshot("a"),
                ),
            )
            val afterRestart = RommPendingSyncJournal(folder)
            val entry = (afterRestart.list() as RommPendingSyncJournal.ReadResult.Entries).pending.single()
            assertEquals(42, entry.romId)
            assertEquals(RommSaveSyncPlanner.Direction.UPLOAD, entry.direction)
            assertTrue(afterRestart.discard(entry.id))
            assertTrue((afterRestart.list() as RommPendingSyncJournal.ReadResult.Entries).pending.isEmpty())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun corruptedJournalBlocksQueueMutationInsteadOfAutoReplaying() {
        val folder = Files.createTempDirectory("romm-corrupt-").toFile()
        try {
            val journal = RommPendingSyncJournal(folder)
            assertTrue(journal.enqueue(7, RommSaveSyncPlanner.Direction.DOWNLOAD, snapshot("a"), snapshot("b")))
            val file = File(folder, "romm-pending-sync-v1.json")
            file.writeText(file.readText().replace("UPLOAD", "DOWNLOAD").replace("sha256", "broken"))
            assertEquals(RommPendingSyncJournal.ReadResult.Corrupt, journal.list())
            assertFalse(journal.enqueue(8, RommSaveSyncPlanner.Direction.UPLOAD, snapshot("b"), snapshot("a")))
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun pendingOfflineIntentNeverBypassesFreshHashesOrApproval() {
        val folder = Files.createTempDirectory("romm-recheck-").toFile()
        try {
            val journal = RommPendingSyncJournal(folder)
            assertTrue(journal.enqueue(5, RommSaveSyncPlanner.Direction.UPLOAD, snapshot("b"), snapshot("a")))
            val entry = (journal.list() as RommPendingSyncJournal.ReadResult.Entries).pending.single()
            val valid =
                RommSaveSyncPlanner.Request(
                    optedIn = true,
                    online = true,
                    romId = 5,
                    confirmedRomId = 5,
                    previousLocal = snapshot("a"),
                    previousRemote = snapshot("a"),
                    local = snapshot("b"),
                    remote = snapshot("a"),
                    direction = RommSaveSyncPlanner.Direction.UPLOAD,
                    approvedThisOperation = true,
                    versionedBackupVerified = true,
                )
            assertEquals(
                RommSaveSyncPlanner.Decision.Conflict,
                journal.evaluate(entry, valid.copy(local = snapshot("c"))),
            )
            assertEquals(
                RommSaveSyncPlanner.Decision.RevalidateWhenOnline,
                journal.evaluate(entry, valid.copy(online = false)),
            )
            assertEquals(
                RommSaveSyncPlanner.Decision.ExplicitApprovalRequired,
                journal.evaluate(entry, valid.copy(approvedThisOperation = false)),
            )
            assertTrue(journal.evaluate(entry, valid) is RommSaveSyncPlanner.Decision.UploadCopy)
        } finally {
            folder.deleteRecursively()
        }
    }
}
