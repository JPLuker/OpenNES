package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class RommDeviceSyncProtocolTest {
    private fun response(rows: String, counts: String): String =
        """{"session_id":42,"operations":[$rows],$counts}"""

    private val emptyCounts =
        """"total_upload":0,"total_download":0,"total_conflict":0,"total_delete":0,"total_no_op":0"""

    @Test
    fun acceptsCurrentDeviceProtocolWithoutExecutingAnyOperation() {
        val rows =
            """
            {"action":"download","rom_id":17,"save_id":88,"file_name":"zelda.srm",
            "slot":"autosave","server_content_hash":"${"a".repeat(32)}"},
            {"action":"delete","rom_id":21,"save_id":null,"file_name":"old.srm","slot":"autosave"},
            {"action":"conflict","rom_id":17,"save_id":99,"file_name":"zelda2.srm","slot":"backup"}
            """.trimIndent()
        val counts =
            """"total_upload":0,"total_download":1,"total_conflict":1,"total_delete":1,"total_no_op":0"""
        val parsed = RommDeviceSyncProtocol.parseNegotiation(response(rows, counts), setOf(17, 21))
        assertTrue(parsed is RommDeviceSyncProtocol.ParseResult.Accepted)
        val session = (parsed as RommDeviceSyncProtocol.ParseResult.Accepted).negotiation
        assertEquals(42L, session.sessionId)
        assertEquals(3, session.operations.size)
        assertTrue(session.needsUserReview)
        assertEquals(RommDeviceSyncProtocol.Action.DELETE, session.operations[1].action)
        assertTrue(session.operations[1].action.requiresExplicitExecution)
    }

    @Test
    fun noOperationResponseDoesNotSuggestModifyingSaves() {
        val rows = """{"action":"no_op","rom_id":1,"save_id":1,"file_name":"a.srm","slot":"autosave"}"""
        val counts =
            """"total_upload":0,"total_download":0,"total_conflict":0,"total_delete":0,"total_no_op":1"""
        val result = RommDeviceSyncProtocol.parseNegotiation(response(rows, counts), setOf(1))
        assertTrue(result is RommDeviceSyncProtocol.ParseResult.Accepted)
        assertFalse((result as RommDeviceSyncProtocol.ParseResult.Accepted).negotiation.needsUserReview)
    }

    @Test
    fun legacyUuidSessionAndUnknownActionsAreRejected() {
        val legacy =
            """{"session_id":"550e8400-e29b-41d4-a716-446655440000",
            "operations":[{"type":"download","rom_id":1,"file":"x.srm"}]}"""
        assertEquals(
            RommDeviceSyncProtocol.ParseResult.Rejected(RommDeviceSyncProtocol.Rejection.UNSUPPORTED_SCHEMA),
            RommDeviceSyncProtocol.parseNegotiation(legacy, setOf(1)),
        )
        val unknown = response(
            """{"action":"execute","rom_id":1,"file_name":"x.srm"}""",
            """"total_upload":0,"total_download":0,"total_conflict":0,"total_delete":0,"total_no_op":1""",
        )
        assertEquals(
            RommDeviceSyncProtocol.ParseResult.Rejected(RommDeviceSyncProtocol.Rejection.UNSUPPORTED_SCHEMA),
            RommDeviceSyncProtocol.parseNegotiation(unknown, setOf(1)),
        )
    }

    @Test
    fun rejectsRemotePathsUnverifiedRomsAndMissingContentIdentifiers() {
        val counts =
            """"total_upload":0,"total_download":1,"total_conflict":0,"total_delete":0,"total_no_op":0"""
        val unsafe = response(
            """{"action":"download","rom_id":1,"save_id":88,"file_name":"../save.srm"}""",
            counts,
        )
        assertEquals(
            RommDeviceSyncProtocol.Rejection.INVALID_FILE_NAME,
            (RommDeviceSyncProtocol.parseNegotiation(unsafe, setOf(1)) as
                RommDeviceSyncProtocol.ParseResult.Rejected).reason,
        )
        val foreign = response(
            """{"action":"download","rom_id":99,"save_id":88,"file_name":"save.srm"}""",
            counts,
        )
        assertEquals(
            RommDeviceSyncProtocol.Rejection.UNVERIFIED_ROM,
            (RommDeviceSyncProtocol.parseNegotiation(foreign, setOf(1)) as
                RommDeviceSyncProtocol.ParseResult.Rejected).reason,
        )
        val missingSave = response(
            """{"action":"download","rom_id":1,"save_id":null,"file_name":"save.srm"}""",
            counts,
        )
        assertTrue(
            RommDeviceSyncProtocol.parseNegotiation(missingSave, setOf(1)) is
                RommDeviceSyncProtocol.ParseResult.Rejected,
        )
    }

    @Test
    fun countMismatchesDuplicateEntriesAndInjectedUrlsFailClosed() {
        val row =
            """{"action":"upload","rom_id":1,"file_name":"game.srm","slot":"autosave"}"""
        assertTrue(
            RommDeviceSyncProtocol.parseNegotiation(response(row, emptyCounts), setOf(1)) is
                RommDeviceSyncProtocol.ParseResult.Rejected,
        )
        val duplicate =
            response(
                "$row,$row",
                """"total_upload":2,"total_download":0,"total_conflict":0,"total_delete":0,"total_no_op":0""",
            )
        assertTrue(
            RommDeviceSyncProtocol.parseNegotiation(duplicate, setOf(1)) is
                RommDeviceSyncProtocol.ParseResult.Rejected,
        )
        val injected =
            response(
                """{"action":"download","rom_id":1,"save_id":5,"file_name":"game.srm",
                "source":"https://other.example.com/evil"}""",
                """"total_upload":0,"total_download":1,"total_conflict":0,"total_delete":0,"total_no_op":0""",
            )
        assertTrue(
            RommDeviceSyncProtocol.parseNegotiation(injected, setOf(1)) is
                RommDeviceSyncProtocol.ParseResult.Rejected,
        )
    }

    @Test
    fun doubleReadHashesBothProtocolsAndRejectsChangingContent() {
        val bytes = "abc".toByteArray()
        var reads = 0
        val result =
            RommDeviceSyncProtocol.fingerprint {
                reads++
                ByteArrayInputStream(bytes)
            }
        assertEquals(2, reads)
        assertEquals(3L, result.bytes)
        assertEquals("900150983cd24fb0d6963f7d28e17f72", result.md5)
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            result.sha256,
        )
        var changes = 0
        assertThrows(IOException::class.java) {
            RommDeviceSyncProtocol.fingerprint {
                changes++
                ByteArrayInputStream(byteArrayOf(changes.toByte()))
            }
        }
        assertThrows(IOException::class.java) {
            RommDeviceSyncProtocol.fingerprint { null }
        }
    }
}
