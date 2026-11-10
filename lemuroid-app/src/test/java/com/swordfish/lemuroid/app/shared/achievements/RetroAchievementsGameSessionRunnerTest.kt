package com.swordfish.lemuroid.app.shared.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroAchievementsGameSessionRunnerTest {
    private val official = "https://retroachievements.org/dorequest.php"

    @Test
    fun permitsOnlyOfficialNativePostRequests() {
        assertTrue(
            RetroAchievementsGameSessionRunner.isOfficialGameRequest(
                official,
                "r=startsession&u=Tester&t=1234567890abcdef&g=1454",
            ),
        )
        assertTrue(
            RetroAchievementsGameSessionRunner.isOfficialGameRequest(
                official,
                "r=awardachievement&u=Tester&t=1234567890abcdef&a=123",
            ),
        )
        assertFalse(
            RetroAchievementsGameSessionRunner.isOfficialGameRequest(
                "https://example.org/dorequest.php",
                "r=awardachievement&u=Tester&t=abc",
            ),
        )
        assertFalse(
            RetroAchievementsGameSessionRunner.isOfficialGameRequest(
                "http://retroachievements.org/dorequest.php",
                "r=login2&u=Tester&t=abc",
            ),
        )
        assertFalse(
            RetroAchievementsGameSessionRunner.isOfficialGameRequest(
                "$official?r=awardachievement",
                "r=awardachievement&t=abc",
            ),
        )
        assertFalse(
            RetroAchievementsGameSessionRunner.isOfficialGameRequest(
                official,
                "r=login2&u=Tester&p=plaintext",
            ),
        )
    }

    @Test
    fun decodedAchievementRowsAreStrictlyNativeAndBounded() {
        val row =
            RetroAchievementsGameSessionRunner.decodeAchievementRow(
                "125\t10\t1\tA Real Achievement\tDefeat an enemy",
            )
        assertNotNull(row)
        assertEquals(125, row?.id)
        assertEquals(10, row?.points)
        assertTrue(row?.unlocked == true)
        assertEquals("A Real Achievement", row?.title)
        assertEquals("Defeat an enemy", row?.description)
        assertEquals(
            null,
            RetroAchievementsGameSessionRunner.decodeAchievementRow(
                "0\t10\t1\tInvalid\tMissing achievement ID",
            ),
        )
        assertEquals(
            null,
            RetroAchievementsGameSessionRunner.decodeAchievementRow(
                "125\t10000\t1\tToo many points\tInvalid",
            ),
        )
        assertEquals(
            null,
            RetroAchievementsGameSessionRunner.decodeAchievementRow(
                "125\t10\twrong\tInvalid unlock state\tInvalid",
            ),
        )
        assertEquals(null, RetroAchievementsGameSessionRunner.decodeAchievementRow("bad"))
    }

    @Test
    fun lateGameSessionCannotOverwriteNewRomAchievements() {
        val gameA = "a".repeat(32)
        val gameB = "b".repeat(32)
        val oldTicket = RetroAchievementsGameStateStore.begin(gameA)
        val newTicket = RetroAchievementsGameStateStore.begin(gameB)

        val oldActive =
            RetroAchievementsDisplayState.Active(
                gameA,
                "Tester",
                listOf(VerifiedAchievement(1, "Old", "Wrong game", 5, true)),
            )
        RetroAchievementsGameStateStore.publish(oldTicket, gameA, oldActive)
        assertEquals(gameB, RetroAchievementsGameStateStore.snapshots.value.romMd5)
        assertEquals(
            RetroAchievementsDisplayState.Connecting,
            RetroAchievementsGameStateStore.snapshots.value.state,
        )
        RetroAchievementsGameStateStore.clear(oldTicket)
        assertEquals(gameB, RetroAchievementsGameStateStore.snapshots.value.romMd5)

        val currentActive =
            RetroAchievementsDisplayState.Active(
                gameB,
                "Tester",
                listOf(VerifiedAchievement(2, "New", "Correct game", 5, false)),
            )
        RetroAchievementsGameStateStore.publish(newTicket, gameB, currentActive)
        assertEquals(currentActive, RetroAchievementsGameStateStore.snapshots.value.state)
        RetroAchievementsGameStateStore.clear(newTicket)
        assertEquals(null, RetroAchievementsGameStateStore.snapshots.value.romMd5)
    }
}
