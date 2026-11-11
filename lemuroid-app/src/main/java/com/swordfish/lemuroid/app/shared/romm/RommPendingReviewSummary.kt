package com.swordfish.lemuroid.app.shared.romm

/** Read-only description of hashes observed when a sync intent was created. */
internal object RommPendingReviewSummary {
    enum class Status {
        SERVER_MISMATCH,
        JOURNAL_UNAVAILABLE,
        REQUEST_CHANGED,
        LOCAL_CHANGED,
        RECORDED_MATCH,
        RECORDED_DIFFERENCE,
    }

    data class Summary(
        val status: Status,
        val localHash: String,
        val archivedHash: String,
        val localBytes: Long,
        val archivedBytes: Long,
        val executable: Boolean = false,
    )

    fun summarize(
        pending: RommPendingSyncJournal.Pending,
        activeServerFingerprint: String?,
        localReview: RommPendingSyncJournal.LocalReview?,
    ): Summary {
        val status =
            when {
                activeServerFingerprint == null || pending.serverFingerprint != activeServerFingerprint ->
                    Status.SERVER_MISMATCH
                localReview == RommPendingSyncJournal.LocalReview.JOURNAL_CORRUPT ->
                    Status.JOURNAL_UNAVAILABLE
                localReview == RommPendingSyncJournal.LocalReview.REQUEST_CHANGED ->
                    Status.REQUEST_CHANGED
                localReview == RommPendingSyncJournal.LocalReview.CHANGED_SINCE_REQUEST ->
                    Status.LOCAL_CHANGED
                pending.observedLocal == pending.observedRemote -> Status.RECORDED_MATCH
                else -> Status.RECORDED_DIFFERENCE
            }
        return Summary(
            status = status,
            localHash = pending.observedLocal.sha256.take(12),
            archivedHash = pending.observedRemote.sha256.take(12),
            localBytes = pending.observedLocal.bytes,
            archivedBytes = pending.observedRemote.bytes,
        )
    }
}
