package com.swordfish.lemuroid.app.shared.game

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundPlaySessionTest {
    @Test
    fun backgroundTimeIsNeverCounted() {
        val session = ForegroundPlaySession()
        session.resume(1_000)
        assertEquals(2_000L, session.elapsed(3_000))
        session.pause(3_000)
        assertEquals(2_000L, session.elapsed(50_000))
        session.resume(51_000)
        session.pause(54_000)
        assertEquals(5_000L, session.elapsed(90_000))
    }

    @Test
    fun rotationRestoresThePreviousActivityDuration() {
        val oldActivity = ForegroundPlaySession()
        oldActivity.resume(1_000)
        oldActivity.pause(7_000)
        val savedDuration = oldActivity.elapsed(7_000)

        val recreatedActivity = ForegroundPlaySession(savedDuration)
        recreatedActivity.resume(10_000)
        recreatedActivity.pause(13_000)

        assertEquals(9_000L, recreatedActivity.elapsed(100_000))
    }

    @Test
    fun repeatedResumeAndPauseDoNotDoubleCount() {
        val session = ForegroundPlaySession()
        session.resume(100)
        session.resume(500)
        session.pause(1_100)
        session.pause(2_100)
        assertEquals(1_000L, session.elapsed(3_000))
    }

    @Test
    fun invalidRestoredTimeAndClockRegressionAreClamped() {
        val session = ForegroundPlaySession(-5)
        session.resume(1_000)
        assertEquals(0L, session.elapsed(500))
        session.pause(500)
        assertEquals(0L, session.elapsed(2_000))
    }
}
