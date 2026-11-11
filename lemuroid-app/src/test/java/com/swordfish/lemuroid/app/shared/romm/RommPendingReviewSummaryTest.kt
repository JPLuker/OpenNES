package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RommPendingReviewSummaryTest {
    private val server = "a".repeat(64)

    private fun snap(letter: String) = RommSaveSyncPlanner.Snapshot(letter.repeat(64), 1024)

    private val request =
        RommPendingSyncJournal.Pending(
            id = "550e8400-e29b-41d4-a716-446655440000",
            serverFingerprint = server,
            romId = 42,
            direction = RommSaveSyncPlanner.Direction.UPLOAD,
            observedLocal = snap("b"),
            observedRemote = snap("a"),
            requestedAtMillis = 1L,
        )

    @Test
    fun differingRecordedSavesAreNotExecutableConflictResolutions() {
        val result = RommPendingReviewSummary.summarize(request, server, null)
        assertEquals(RommPendingReviewSummary.Status.RECORDED_DIFFERENCE, result.status)
        assertEquals("bbbbbbbbbbbb", result.localHash)
        assertEquals("aaaaaaaaaaaa", result.archivedHash)
        assertFalse(result.executable)
    }

    @Test
    fun sameHistoricalHashesStillDoNotImplyFreshnessOrApproval() {
        val result =
            RommPendingReviewSummary.summarize(
                request.copy(observedRemote = snap("b")),
                server,
                RommPendingSyncJournal.LocalReview.MATCHES_OBSERVED,
            )
        assertEquals(RommPendingReviewSummary.Status.RECORDED_MATCH, result.status)
        assertFalse(result.executable)
    }

    @Test
    fun mismatchedServerAndChangedLocalTakePriorityOverHistoricalHashes() {
        val foreign = RommPendingReviewSummary.summarize(request, "c".repeat(64), null)
        assertEquals(RommPendingReviewSummary.Status.SERVER_MISMATCH, foreign.status)
        val changed =
            RommPendingReviewSummary.summarize(
                request,
                server,
                RommPendingSyncJournal.LocalReview.CHANGED_SINCE_REQUEST,
            )
        assertEquals(RommPendingReviewSummary.Status.LOCAL_CHANGED, changed.status)
        assertFalse(changed.executable)
    }
}
