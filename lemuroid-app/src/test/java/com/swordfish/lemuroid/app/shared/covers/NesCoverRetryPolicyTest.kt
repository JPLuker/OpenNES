package com.swordfish.lemuroid.app.shared.covers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NesCoverRetryPolicyTest {
    @Test
    fun earlyFailuresCanRetryImmediately() {
        assertTrue(NesCoverRetryPolicy.shouldAttempt(0, null, 1000L))
        assertTrue(NesCoverRetryPolicy.shouldAttempt(2, 900L, 1000L))
    }

    @Test
    fun exhaustedAttemptsRecoverAfterCooldown() {
        assertFalse(NesCoverRetryPolicy.shouldAttempt(3, 1000L, 1001L))
        assertFalse(NesCoverRetryPolicy.shouldAttempt(3, 1000L, 120999L))
        assertTrue(NesCoverRetryPolicy.shouldAttempt(3, 1000L, 121000L))
    }

    @Test
    fun missingOrFutureFailureTimestampCannotBypassCooldown() {
        assertFalse(NesCoverRetryPolicy.shouldAttempt(3, null, 1000L))
        assertFalse(NesCoverRetryPolicy.shouldAttempt(3, 2000L, 1000L))
    }
}
