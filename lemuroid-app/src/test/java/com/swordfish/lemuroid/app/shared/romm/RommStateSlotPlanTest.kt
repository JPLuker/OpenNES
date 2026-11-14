package com.swordfish.lemuroid.app.shared.romm

import com.swordfish.lemuroid.app.shared.romm.RommStateSlotPlan.Action
import com.swordfish.lemuroid.app.shared.romm.RommStateSlotPlan.Baseline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RommStateSlotPlanTest {
    private val base = Baseline("aaa", "7|t1|10")

    @Test
    fun `nothing on either side does nothing`() {
        assertEquals(Action.NOTHING, RommStateSlotPlan.plan(null, null, null))
    }

    @Test
    fun `a first state on one side is copied to the other`() {
        assertEquals(Action.UPLOAD, RommStateSlotPlan.plan("aaa", null, null))
        assertEquals(Action.DOWNLOAD, RommStateSlotPlan.plan(null, "7|t1|10", null))
    }

    @Test
    fun `unchanged on both sides does nothing`() {
        assertEquals(Action.NOTHING, RommStateSlotPlan.plan("aaa", "7|t1|10", base))
    }

    @Test
    fun `only the changed side is transferred`() {
        assertEquals(Action.UPLOAD, RommStateSlotPlan.plan("bbb", "7|t1|10", base))
        assertEquals(Action.DOWNLOAD, RommStateSlotPlan.plan("aaa", "7|t2|12", base))
    }

    @Test
    fun `both changed or never synced compares the bytes`() {
        assertEquals(Action.COMPARE, RommStateSlotPlan.plan("bbb", "7|t2|12", base))
        assertEquals(Action.COMPARE, RommStateSlotPlan.plan("bbb", "7|t2|12", null))
    }

    @Test
    fun `a state removed on the server is not put back unless the phone's changed`() {
        assertEquals(Action.NOTHING, RommStateSlotPlan.plan("aaa", null, base))
        assertEquals(Action.UPLOAD, RommStateSlotPlan.plan("bbb", null, base))
    }

    @Test
    fun `a synced state missing on the phone is not downloaded again`() {
        assertEquals(Action.NOTHING, RommStateSlotPlan.plan(null, "7|t1|10", base))
        assertEquals(Action.DOWNLOAD, RommStateSlotPlan.plan(null, "7|t2|12", base))
    }

    @Test
    fun `when both changed the newer state wins and equal bytes need nothing`() {
        assertEquals(Action.NOTHING, RommStateSlotPlan.resolve("x", "x", 5, 9))
        assertEquals(Action.DOWNLOAD, RommStateSlotPlan.resolve("x", "y", 5, 9))
        assertEquals(Action.UPLOAD, RommStateSlotPlan.resolve("x", "y", 9, 5))
        assertEquals(Action.UPLOAD, RommStateSlotPlan.resolve("x", "y", 9, null))
    }

    @Test
    fun `slot file names follow RetroArch`() {
        assertEquals(
            "Kirby's Adventure (USA).state3",
            RommStateSlotPlan.remoteFileName("Kirby's Adventure (USA).nes", 3),
        )
    }

    @Test
    fun `listing keeps the newest fceumm state per slot and ignores the rest`() {
        val json =
            """
            [
              {"id":1,"rom_id":5,"file_name":"K.state1","emulator":"fceumm","file_size_bytes":10,
               "updated_at":"2026-11-14T10:00:00Z","missing_from_fs":false},
              {"id":2,"rom_id":5,"file_name":"K.STATE1","emulator":null,"file_size_bytes":11,
               "updated_at":"2026-11-14T11:00:00.123456Z"},
              {"id":3,"rom_id":5,"file_name":"K.state2","emulator":"nestopia","file_size_bytes":10,
               "updated_at":"2026-11-14T12:00:00Z"},
              {"id":4,"rom_id":5,"file_name":"K [2026-11-14 10-00-00-000].state","emulator":"fceumm",
               "file_size_bytes":10,"updated_at":"2026-11-14T12:00:00Z"},
              {"id":5,"rom_id":5,"file_name":"K.state4","emulator":"fceumm","file_size_bytes":10,
               "updated_at":"2026-11-14T12:00:00Z","missing_from_fs":true},
              {"id":6,"rom_id":5,"file_name":"K.state9","emulator":"fceumm","file_size_bytes":10,
               "updated_at":"2026-11-14T12:00:00Z"}
            ]
            """.trimIndent()
        val parsed = RommStateSlotPlan.parseRemote(json)
        assertEquals(setOf(5 to 1), parsed.keys)
        assertEquals(2L, parsed.getValue(5 to 1).id)
        assertEquals("2|2026-11-14T11:00:00.123456Z|11", parsed.getValue(5 to 1).stamp)
    }

    @Test
    fun `timestamps are read as UTC`() {
        assertEquals(1_794_650_400_000L, RommStateSlotPlan.parseUtcMillis("2026-11-14T10:00:00Z"))
        assertNull(RommStateSlotPlan.parseUtcMillis("soon"))
    }
}
