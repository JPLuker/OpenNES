package com.swordfish.lemuroid.app.shared.game

/**
 * Counts only time the game Activity is in the foreground. A screen rotation
 * recreates that Activity, so the accumulated duration can be passed through
 * savedInstanceState without counting the intervening background interval.
 *
 * Times must come from the monotonic Android elapsedRealtime clock.
 */
internal class ForegroundPlaySession(initialElapsedMillis: Long = 0L) {
    private var accumulatedMillis = initialElapsedMillis.coerceAtLeast(0L)
    private var startedAtMillis: Long? = null

    fun resume(nowMillis: Long) {
        if (startedAtMillis == null) startedAtMillis = nowMillis
    }

    fun pause(nowMillis: Long) {
        val startedAt = startedAtMillis ?: return
        accumulatedMillis += (nowMillis - startedAt).coerceAtLeast(0L)
        startedAtMillis = null
    }

    fun elapsed(nowMillis: Long): Long {
        val activeMillis =
            startedAtMillis?.let { (nowMillis - it).coerceAtLeast(0L) } ?: 0L
        return accumulatedMillis + activeMillis
    }
}
