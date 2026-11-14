package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RommTwoWaySyncStateTest {
    private val server = "f".repeat(64)
    private val owner = RommTwoWaySyncState.owner(server, 7)
    private val md5 = "a".repeat(32)

    private val full =
        RommTwoWaySyncState(
            owner = owner,
            enabled = true,
            baselines = mapOf(3 to md5, 11 to "b".repeat(32)),
            conflicts =
                listOf(
                    RommTwoWaySyncState.Conflict(
                        romId = 3,
                        gameId = 42,
                        title = "Zelda",
                        saveId = 9,
                        localMd5 = md5,
                        serverMd5 = "c".repeat(32),
                        reason = RommTwoWaySyncPlan.ConflictReason.BOTH_CHANGED,
                        detectedAtMillis = 1_700_000_000_000,
                    ),
                ),
            lastRun = RommTwoWaySyncState.LastRun(1_700_000_000_500, 1, 2, 1, 0, null),
        )

    @Test
    fun roundTripsEverythingItRemembers() {
        assertEquals(full, RommTwoWaySyncState.fromJson(full.toJson()))
    }

    @Test
    fun anotherServerOrAccountStartsCleanAndDisabled() {
        val other = full.forOwner(RommTwoWaySyncState.owner(server, 8))
        assertFalse(other.enabled)
        assertTrue(other.baselines.isEmpty())
        assertTrue(other.conflicts.isEmpty())
        assertEquals(full, full.forOwner(owner))
    }

    @Test
    fun damagedOrForeignFilesBecomeTheDisabledDefault() {
        assertEquals(RommTwoWaySyncState(), RommTwoWaySyncState.fromJson("not json"))
        assertEquals(RommTwoWaySyncState(), RommTwoWaySyncState.fromJson("""{"version":99,"enabled":true}"""))
        // Enabled without a valid owner can never switch sync on.
        assertFalse(RommTwoWaySyncState.fromJson("""{"version":1,"enabled":true,"owner":"x"}""").enabled)
    }

    @Test
    fun invalidBaselinesAndConflictsAreDroppedNotGuessed() {
        val json =
            """{"version":1,"owner":"$owner","enabled":true,""" +
                """"baselines":{"3":"$md5","0":"$md5","x":"$md5","4":"ABC"},""" +
                """"conflicts":[{"rom_id":5,"reason":"NOPE"},{"rom_id":0,"reason":"BOTH_CHANGED"}]}"""
        val state = RommTwoWaySyncState.fromJson(json)
        assertEquals(mapOf(3 to md5), state.baselines)
        assertTrue(state.conflicts.isEmpty())
    }

    @Test
    fun theFileIsReplacedWholeAndAMissingFileReadsAsDefault() {
        val dir = Files.createTempDirectory("sync-state").toFile()
        val store = RommTwoWaySyncStateFile(File(dir, "state.json"))
        assertEquals(RommTwoWaySyncState(), store.read())
        store.write(full)
        assertEquals(full, store.read())
        assertFalse(File(dir, "state.json.tmp").exists())
        dir.deleteRecursively()
    }
}
