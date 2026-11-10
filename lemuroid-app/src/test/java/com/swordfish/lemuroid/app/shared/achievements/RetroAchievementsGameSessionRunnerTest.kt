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
    fun clientEmulatorWarningsNeverCountTowardRealAwards() {
        val warning = "notice\tWarning: Unknown Emulator\tHardcore unlocks cannot be earned using this emulator."
        assertEquals(
            "Warning: Unknown Emulator: Hardcore unlocks cannot be earned using this emulator.",
            RetroAchievementsGameSessionRunner.decodeCompatibilityNotice(warning),
        )
        assertEquals(null, RetroAchievementsGameSessionRunner.decodeAchievementRow(warning))
        assertEquals(
            null,
            RetroAchievementsGameSessionRunner.decodeAchievementRow(
                "101000001\t0\t1\tWarning: Unknown Emulator\tNot a real achievement",
            ),
        )
        assertEquals(null, RetroAchievementsGameSessionRunner.decodeCompatibilityNotice("notice\t\tMissing title"))
        assertEquals(null, RetroAchievementsGameSessionRunner.decodeCompatibilityNotice("notice\tUnsafe\tNew\nline"))
        assertNotNull(
            RetroAchievementsGameSessionRunner.decodeAchievementRow(
                "19245\t5\t1\tGenuine NES award\tEarned through gameplay",
            ),
        )
    }

    @Test
    fun nativeAwardsAreRestrictedToVerifiedNesMemoryMaps() {
        val nes =
            NesRetroAchievementsHash.Identity(
                "a".repeat(32),
                NesRetroAchievementsHash.Console.NES,
            )
        val fds =
            NesRetroAchievementsHash.Identity(
                "b".repeat(32),
                NesRetroAchievementsHash.Console.FDS,
            )
        assertTrue(RetroAchievementsGameSessionRunner.supportsNativeGame(nes))
        assertFalse(RetroAchievementsGameSessionRunner.supportsNativeGame(fds))
    }

    @Test
    fun nativeEventsRejectMalformedAwardsAndReportConnectionChanges() {
        val decode: (String) -> RetroAchievementsGameSessionRunner.NativeGameEvent? =
            { RetroAchievementsGameSessionRunner.decodeNativeEvent(it) }
        assertEquals(
            RetroAchievementsGameSessionRunner.NativeGameEvent.Unlock(125, "Defeat an enemy"),
            decode("unlock\t125\t5\tDefeat an enemy"),
        )
        assertEquals(
            RetroAchievementsGameSessionRunner.NativeGameEvent.Disconnected,
            decode("disconnected"),
        )
        assertEquals(
            RetroAchievementsGameSessionRunner.NativeGameEvent.Reconnected,
            decode("reconnected"),
        )
        assertEquals(null, decode("unlock\t0\t5\tInvalid ID"))
        assertEquals(null, decode("unlock\t125\t501\tInvalid points"))
        assertEquals(null, decode("unlock\t125\t5\t"))
        assertEquals(null, decode("unlock\t125\t5\tUnexpected\nnewline"))
        assertEquals(null, decode("disconnected\twrong"))
    }

    @Test
    fun connectionGateDoesNotResumeAwardsUntilNativeReconnect() {
        val gate = RetroAchievementsGameSessionRunner.NativeConnectionGate()
        assertTrue(gate.isConnected)
        assertFalse(gate.observe(RetroAchievementsGameSessionRunner.NativeGameEvent.Disconnected))
        assertFalse(gate.observe(RetroAchievementsGameSessionRunner.NativeGameEvent.Unlock(125, "Offline trigger")))
        assertFalse(gate.isConnected)
        assertTrue(gate.observe(RetroAchievementsGameSessionRunner.NativeGameEvent.Reconnected))
        assertTrue(gate.observe(RetroAchievementsGameSessionRunner.NativeGameEvent.Unlock(125, "Connected trigger")))
    }

    @Test
    fun failedSessionCanBeRetriedOnlyForItsCurrentRom() {
        val gameA = "a".repeat(32)
        val gameB = "b".repeat(32)
        val oldTicket = RetroAchievementsGameStateStore.begin(gameA)
        assertFalse(RetroAchievementsGameStateStore.requestRetry(gameA))
        RetroAchievementsGameStateStore.finishFailed(oldTicket, gameA)
        assertFalse(RetroAchievementsGameStateStore.requestRetry(gameB))
        assertTrue(RetroAchievementsGameStateStore.requestRetry(gameA))
        val requestedVersion = RetroAchievementsGameStateStore.retryVersion(gameA)
        assertTrue(requestedVersion > 0L)
        assertEquals(0L, RetroAchievementsGameStateStore.retryVersion(gameB))
        val retryTicket = RetroAchievementsGameStateStore.begin(gameA)
        assertEquals(0L, RetroAchievementsGameStateStore.retryVersion(gameA))
        RetroAchievementsGameStateStore.finishFailed(retryTicket, gameA)
    }

    @Test
    fun changingSavedAccountCanInvalidateActiveSession() {
        val first = NativeCredentials("Tester", "a".repeat(32))
        val same = NativeCredentials("Tester", "a".repeat(32))
        val changed = NativeCredentials("Tester", "b".repeat(32))
        assertTrue(RetroAchievementsGameSessionRunner.sameCredentials(first, same))
        assertFalse(RetroAchievementsGameSessionRunner.sameCredentials(first, changed))
    }

    @Test
    fun failedNativeSessionRemainsVisibleWithoutLeakingToAnotherGame() {
        val gameA = "a".repeat(32)
        val gameB = "b".repeat(32)
        val failedTicket = RetroAchievementsGameStateStore.begin(gameA)
        RetroAchievementsGameStateStore.finishFailed(failedTicket, gameA)
        assertEquals(
            RetroAchievementsDisplayState.Unavailable,
            RetroAchievementsGameStateStore.snapshots.value.state,
        )
        RetroAchievementsGameStateStore.clear(failedTicket)
        assertEquals(
            RetroAchievementsDisplayState.Unavailable,
            RetroAchievementsGameStateStore.snapshots.value.state,
        )

        val nextTicket = RetroAchievementsGameStateStore.begin(gameB)
        RetroAchievementsGameStateStore.finishFailed(failedTicket, gameA)
        assertEquals(gameB, RetroAchievementsGameStateStore.snapshots.value.romMd5)
        assertEquals(
            RetroAchievementsDisplayState.Connecting,
            RetroAchievementsGameStateStore.snapshots.value.state,
        )
        RetroAchievementsGameStateStore.clear(nextTicket)
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
