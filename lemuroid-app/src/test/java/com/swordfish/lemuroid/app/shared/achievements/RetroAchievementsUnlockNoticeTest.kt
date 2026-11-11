package com.swordfish.lemuroid.app.shared.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroAchievementsUnlockNoticeTest {
    private val mario = "a".repeat(32)
    private val zelda = "b".repeat(32)

    @Test
    fun nativeNoticeIsOneShotAndSurvivesProgressRefresh() {
        val ticket = RetroAchievementsGameStateStore.begin(mario)
        assertTrue(RetroAchievementsGameStateStore.notifyUnlock(ticket, mario, 125, "Found a secret"))
        val notice = RetroAchievementsGameStateStore.snapshots.value.notice
        assertEquals(125, notice?.id)
        assertFalse(RetroAchievementsGameStateStore.notifyUnlock(ticket, mario, 125, "Duplicate"))
        RetroAchievementsGameStateStore.publish(ticket, mario, RetroAchievementsDisplayState.Connecting)
        assertEquals(notice, RetroAchievementsGameStateStore.snapshots.value.notice)
        RetroAchievementsGameStateStore.dismissUnlock(notice!!.sequence)
        assertNull(RetroAchievementsGameStateStore.snapshots.value.notice)
        RetroAchievementsGameStateStore.clear(ticket)
    }

    @Test
    fun lateOldRomEventOrDismissalCannotChangeNewRomBanner() {
        val oldTicket = RetroAchievementsGameStateStore.begin(mario)
        assertTrue(RetroAchievementsGameStateStore.notifyUnlock(oldTicket, mario, 11, "Mario"))
        val oldNotice = RetroAchievementsGameStateStore.snapshots.value.notice!!
        val newTicket = RetroAchievementsGameStateStore.begin(zelda)
        assertFalse(RetroAchievementsGameStateStore.notifyUnlock(oldTicket, mario, 12, "Stale"))
        assertTrue(RetroAchievementsGameStateStore.notifyUnlock(newTicket, zelda, 19, "Zelda"))
        RetroAchievementsGameStateStore.dismissUnlock(oldNotice.sequence)
        assertEquals(19, RetroAchievementsGameStateStore.snapshots.value.notice?.id)
        RetroAchievementsGameStateStore.clear(newTicket)
        assertNull(RetroAchievementsGameStateStore.snapshots.value.notice)
    }

    @Test
    fun malformedOrUnrelatedUnlocksAreIgnored() {
        val ticket = RetroAchievementsGameStateStore.begin(mario)
        assertFalse(RetroAchievementsGameStateStore.notifyUnlock(ticket, mario, 0, "Unknown"))
        assertFalse(RetroAchievementsGameStateStore.notifyUnlock(ticket, zelda, 20, "Wrong ROM"))
        assertFalse(RetroAchievementsGameStateStore.notifyUnlock(ticket, mario, 20, "Line\nbreak"))
        assertFalse(RetroAchievementsGameStateStore.notifyUnlock(ticket, mario, 20, " "))
        assertNull(RetroAchievementsGameStateStore.snapshots.value.notice)
        RetroAchievementsGameStateStore.clear(ticket)
    }
}
