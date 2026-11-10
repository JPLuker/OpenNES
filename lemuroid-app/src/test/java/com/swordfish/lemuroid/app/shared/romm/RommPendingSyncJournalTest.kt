package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RommPendingSyncJournalTest {
    private val serverA = "a".repeat(64)
    private val serverB = "b".repeat(64)

    private fun snapshot(letter: String) = RommSaveSyncPlanner.Snapshot(letter.repeat(64), 128L)

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
                    serverA,
                ),
            )
            assertFalse(
                original.enqueue(
                    42,
                    RommSaveSyncPlanner.Direction.DOWNLOAD,
                    snapshot("b"),
                    snapshot("a"),
                    serverA,
                ),
            )
            val reloaded = RommPendingSyncJournal(folder)
            val entry = (reloaded.list() as RommPendingSyncJournal.ReadResult.Entries).pending.single()
            assertEquals(42, entry.romId)
            assertEquals(serverA, entry.serverFingerprint)
            assertEquals(RommSaveSyncPlanner.Direction.UPLOAD, entry.direction)
            assertTrue(reloaded.discard(entry.id))
            assertTrue((reloaded.list() as RommPendingSyncJournal.ReadResult.Entries).pending.isEmpty())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun separateServersCanQueueSameGameIdWithoutCrossServerCollision() {
        val folder = Files.createTempDirectory("romm-multi-server-").toFile()
        try {
            val journal = RommPendingSyncJournal(folder)
            assertTrue(
                journal.enqueue(
                    51,
                    RommSaveSyncPlanner.Direction.UPLOAD,
                    snapshot("a"),
                    snapshot("b"),
                    serverA,
                ),
            )
            assertTrue(
                journal.enqueue(
                    51,
                    RommSaveSyncPlanner.Direction.DOWNLOAD,
                    snapshot("b"),
                    snapshot("a"),
                    serverB,
                ),
            )
            val restored = (journal.list() as RommPendingSyncJournal.ReadResult.Entries).pending
            assertEquals(2, restored.size)
            assertFalse(
                journal.enqueue(
                    51,
                    RommSaveSyncPlanner.Direction.DOWNLOAD,
                    snapshot("b"),
                    snapshot("a"),
                    serverA,
                ),
            )
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun localReviewNeverConvertsAnObservedHashIntoTransferApproval() {
        val folder = Files.createTempDirectory("romm-local-review-").toFile()
        try {
            val journal = RommPendingSyncJournal(folder)
            assertTrue(
                journal.enqueue(
                    72,
                    RommSaveSyncPlanner.Direction.UPLOAD,
                    snapshot("b"),
                    snapshot("a"),
                    serverA,
                ),
            )
            val pending = (journal.list() as RommPendingSyncJournal.ReadResult.Entries).pending.single()
            assertEquals(
                RommPendingSyncJournal.LocalReview.MATCHES_OBSERVED,
                journal.reviewLocal(pending, serverA, snapshot("b")),
            )
            assertEquals(
                RommPendingSyncJournal.LocalReview.CHANGED_SINCE_REQUEST,
                journal.reviewLocal(pending, serverA, snapshot("c")),
            )
            assertEquals(
                RommPendingSyncJournal.LocalReview.DIFFERENT_SERVER,
                journal.reviewLocal(pending, serverB, snapshot("b")),
            )
            assertTrue(journal.discard(pending.id))
            assertEquals(
                RommPendingSyncJournal.LocalReview.REQUEST_CHANGED,
                journal.reviewLocal(pending, serverA, snapshot("b")),
            )
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun corruptOrLegacyUnscopedJournalsCannotBeEnqueuedOrReplayed() {
        val folder = Files.createTempDirectory("romm-corrupt-").toFile()
        try {
            val journal = RommPendingSyncJournal(folder)
            assertTrue(
                journal.enqueue(7, RommSaveSyncPlanner.Direction.DOWNLOAD, snapshot("a"), snapshot("b"), serverA),
            )
            val file = File(folder, "romm-pending-sync-v2.json")
            file.writeText(file.readText().replace(serverA, serverB))
            assertEquals(RommPendingSyncJournal.ReadResult.Corrupt, journal.list())
            assertFalse(journal.enqueue(8, RommSaveSyncPlanner.Direction.UPLOAD, snapshot("b"), snapshot("a"), serverA))
        } finally {
            folder.deleteRecursively()
        }

        val legacy = Files.createTempDirectory("romm-legacy-").toFile()
        try {
            File(legacy, "romm-pending-sync-v1.json").writeText("{}")
            assertEquals(RommPendingSyncJournal.ReadResult.Corrupt, RommPendingSyncJournal(legacy).list())
        } finally {
            legacy.deleteRecursively()
        }
    }

    @Test
    fun pendingIntentRequiresSameServerAndFreshHashesAndApproval() {
        val folder = Files.createTempDirectory("romm-recheck-").toFile()
        try {
            val journal = RommPendingSyncJournal(folder)
            assertFalse(journal.enqueue(5, RommSaveSyncPlanner.Direction.UPLOAD, snapshot("b"), snapshot("a"), "bad"))
            assertTrue(journal.enqueue(5, RommSaveSyncPlanner.Direction.UPLOAD, snapshot("b"), snapshot("a"), serverA))
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
                RommSaveSyncPlanner.Decision.UnverifiedIdentity,
                journal.evaluate(entry, valid, serverB),
            )
            assertEquals(
                RommSaveSyncPlanner.Decision.Conflict,
                journal.evaluate(entry, valid.copy(local = snapshot("c")), serverA),
            )
            assertEquals(
                RommSaveSyncPlanner.Decision.RevalidateWhenOnline,
                journal.evaluate(entry, valid.copy(online = false), serverA),
            )
            assertEquals(
                RommSaveSyncPlanner.Decision.ExplicitApprovalRequired,
                journal.evaluate(entry, valid.copy(approvedThisOperation = false), serverA),
            )
            assertTrue(journal.evaluate(entry, valid, serverA) is RommSaveSyncPlanner.Decision.UploadCopy)
        } finally {
            folder.deleteRecursively()
        }
    }
}
