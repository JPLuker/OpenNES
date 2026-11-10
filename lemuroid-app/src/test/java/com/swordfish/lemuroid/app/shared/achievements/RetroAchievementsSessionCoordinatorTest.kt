package com.swordfish.lemuroid.app.shared.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroAchievementsSessionCoordinatorTest {
    private val firstHash = "0123456789abcdef0123456789abcdef"
    private val secondHash = "fedcba9876543210fedcba9876543210"

    private fun achievement(id: Int = 100) =
        VerifiedAchievement(id, "Verified title", "Verified description", 5, unlocked = false)

    private fun activate(
        coordinator: RetroAchievementsSessionCoordinator,
        ticket: RetroAchievementsSessionCoordinator.Ticket,
    ) {
        assertTrue(coordinator.beginAuthentication(ticket))
        assertTrue(coordinator.confirmAuthenticated(ticket, "tester"))
        assertTrue(coordinator.confirmGameActivated(ticket, ticket.romMd5, 42, listOf(achievement())))
    }

    @Test
    fun localFingerprintNeverCreatesAuthenticatedProgress() {
        val coordinator = RetroAchievementsSessionCoordinator()
        val ticket = coordinator.beginGame(firstHash)
        assertEquals(RetroAchievementsDisplayState.NotConnected, coordinator.displayState())
        assertFalse(coordinator.confirmGameActivated(ticket, firstHash, 42, listOf(achievement())))
        assertFalse(coordinator.confirmUnlocked(ticket, 42, 100))
    }

    @Test
    fun incorrectHashAndDuplicateAchievementsCannotActivateGame() {
        val coordinator = RetroAchievementsSessionCoordinator()
        val ticket = coordinator.beginGame(firstHash)
        assertTrue(coordinator.beginAuthentication(ticket))
        assertTrue(coordinator.confirmAuthenticated(ticket, "tester"))
        assertFalse(coordinator.confirmGameActivated(ticket, secondHash, 42, listOf(achievement())))
        assertFalse(coordinator.confirmGameActivated(ticket, firstHash, 42, listOf(achievement(), achievement())))
        assertFalse(coordinator.confirmGameActivated(ticket, firstHash, 0, listOf(achievement())))
        assertEquals(RetroAchievementsDisplayState.Connecting, coordinator.displayState())
    }

    @Test
    fun staleCallbacksCannotUnlockOrStopNewGame() {
        val coordinator = RetroAchievementsSessionCoordinator()
        val old = coordinator.beginGame(firstHash)
        activate(coordinator, old)
        val current = coordinator.beginGame(secondHash)
        assertFalse(coordinator.confirmUnlocked(old, 42, 100))
        coordinator.stop(old)
        assertFalse(coordinator.confirmAuthenticated(old, "tester"))
        activate(coordinator, current)
        assertEquals(secondHash, (coordinator.displayState() as RetroAchievementsDisplayState.Active).romMd5)
        assertFalse(coordinator.confirmUnlocked(current, 999, 100))
        assertTrue(coordinator.confirmUnlocked(current, 42, 100))
        assertTrue((coordinator.displayState() as RetroAchievementsDisplayState.Active).achievements.single().unlocked)
    }

    @Test
    fun anOutageHidesActiveAwardsAndRequiresReauthentication() {
        val coordinator = RetroAchievementsSessionCoordinator()
        val ticket = coordinator.beginGame(firstHash)
        activate(coordinator, ticket)
        assertTrue(coordinator.transportLost(ticket))
        assertEquals(RetroAchievementsDisplayState.NotConnected, coordinator.displayState())
        assertFalse(coordinator.confirmUnlocked(ticket, 42, 100))
        assertTrue(coordinator.restartAuthentication(ticket))
        assertTrue(coordinator.confirmAuthenticated(ticket, "tester"))
        assertTrue(coordinator.confirmGameActivated(ticket, firstHash, 42, listOf(achievement())))
        coordinator.stop(ticket)
        assertEquals(RetroAchievementsDisplayState.NotConnected, coordinator.displayState())
    }

    @Test
    fun invalidFingerprintCannotStartSession() {
        val coordinator = RetroAchievementsSessionCoordinator()
        assertThrows(IllegalArgumentException::class.java) {
            coordinator.beginGame("RoboCop")
        }
    }

    @Test
    fun serverNoMatchCannotBePresentedAsAnActiveGame() {
        val coordinator = RetroAchievementsSessionCoordinator()
        val ticket = coordinator.beginGame(firstHash)
        assertTrue(coordinator.beginAuthentication(ticket))
        assertTrue(coordinator.confirmAuthenticated(ticket, "tester"))
        assertTrue(coordinator.noMatchingGame(ticket))
        assertEquals(RetroAchievementsDisplayState.NoMatchingGame, coordinator.displayState())
        assertFalse(coordinator.confirmGameActivated(ticket, firstHash, 42, listOf(achievement())))
    }
}
