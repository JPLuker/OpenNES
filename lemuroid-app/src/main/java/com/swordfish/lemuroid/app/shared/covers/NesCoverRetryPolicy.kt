package com.swordfish.lemuroid.app.shared.covers

/** Retry transient cover failures after a cooldown instead of blacklisting them for the whole process. */
internal object NesCoverRetryPolicy {
    private const val MAX_ATTEMPTS = 3
    private const val COOLDOWN_MS = 120_000L

    fun shouldAttempt(
        failures: Int,
        lastFailureAt: Long?,
        now: Long,
    ): Boolean {
        if (failures < MAX_ATTEMPTS) return true
        if (lastFailureAt == null || now < lastFailureAt) return false
        return now - lastFailureAt >= COOLDOWN_MS
    }
}
