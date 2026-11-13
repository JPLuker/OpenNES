package com.swordfish.lemuroid.app.shared.romm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RommDeviceSyncNegotiationRequestTest {
    private val server = "a".repeat(64)
    private val device = "3f1c2b9e-8a4d-4c7e-9f21-6d0b5a7e1c34"
    private val fingerprint =
        RommDeviceSyncProtocol.StableFileFingerprint(
            md5 = "900150983cd24fb0d6963f7d28e17f72",
            sha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            bytes = 3,
        )
    private val local =
        RommDeviceSyncNegotiationRequest.LocalSave(
            romId = 12,
            fileName = "zelda.srm",
            slot = "autosave",
            fingerprint = fingerprint,
            updatedAt = "2026-11-12T21:00:00Z",
        )

    private fun build(
        saves: List<RommDeviceSyncNegotiationRequest.LocalSave> = listOf(local),
        roms: Set<Int> = setOf(12),
        deviceId: String = device,
        accountId: Long = 42,
        revision: String = "token-1",
    ): String =
        RommDeviceSyncNegotiationRequest.build(
            deviceId = deviceId,
            requestedRomIds = roms,
            localSaves = saves,
            currentServerFingerprint = server,
            currentCredentialRevision = revision,
            currentAccountId = accountId,
            pinnedServerFingerprint = server,
            pinnedCredentialRevision = "token-1",
            pinnedAccountId = 42,
        )

    @Test
    fun generatesReadOnlyBoundedNegotiationWithVerifiedDigestAndDevice() {
        val json = JSONObject(build())
        assertEquals(device, json.getString("device_id"))
        assertFalse(json.getBoolean("restore_unlisted"))
        assertTrue(json.isNull("emulators"))
        assertEquals(1, json.getJSONArray("rom_ids").length())
        val save = json.getJSONArray("saves").getJSONObject(0)
        assertEquals(12, save.getInt("rom_id"))
        assertEquals("zelda.srm", save.getString("file_name"))
        assertEquals("autosave", save.getString("slot"))
        assertEquals(fingerprint.md5, save.getString("content_hash"))
        assertEquals(3L, save.getLong("file_size_bytes"))
        assertFalse(save.has("sha256"))
    }

    @Test
    fun cannotUseAnotherAccountOrRotatedTokenOrUnregisteredDevice() {
        assertThrows(IllegalArgumentException::class.java) { build(accountId = 43) }
        assertThrows(IllegalArgumentException::class.java) { build(revision = "token-2") }
        assertThrows(IllegalArgumentException::class.java) { build(deviceId = "not-registered") }
    }

    @Test
    fun cannotNegotiateForeignRomsOrDuplicateSaveSlots() {
        assertThrows(IllegalArgumentException::class.java) { build(roms = setOf(99)) }
        assertThrows(IllegalArgumentException::class.java) { build(saves = listOf(local, local)) }
        assertThrows(IllegalArgumentException::class.java) { build(roms = emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { build(roms = setOf(-1)) }
    }

    @Test
    fun cannotUseUnsafeNamesOrUnverifiedContent() {
        assertThrows(IllegalArgumentException::class.java) { build(saves = listOf(local.copy(fileName = "../zelda.srm"))) }
        assertThrows(IllegalArgumentException::class.java) { build(saves = listOf(local.copy(fileName = "unrelated.exe"))) }
        assertThrows(IllegalArgumentException::class.java) { build(saves = listOf(local.copy(slot = "../../home"))) }
        assertThrows(IllegalArgumentException::class.java) { build(saves = listOf(local.copy(updatedAt = "yesterday"))) }
        assertThrows(IllegalArgumentException::class.java) {
            build(saves = listOf(local.copy(fingerprint = fingerprint.copy(md5 = "broken"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            build(saves = listOf(local.copy(fingerprint = fingerprint.copy(bytes = 0))))
        }
    }

    @Test
    fun rejectsImpossibleUtcDatesAndAcceptsLeapDay() {
        for (bad in listOf("2026-02-29T21:00:00Z", "2026-13-12T21:00:00Z", "2026-11-31T21:00:00Z",
                "2026-11-12T24:00:00Z", "2026-11-12T21:60:00Z")) {
            assertThrows(IllegalArgumentException::class.java) {
                build(saves = listOf(local.copy(updatedAt = bad)))
            }
        }
        val valid = JSONObject(build(saves = listOf(local.copy(updatedAt = "2024-02-29T09:00:00Z"))))
        assertEquals("2024-02-29T09:00:00Z", valid.getJSONArray("saves").getJSONObject(0).getString("updated_at"))
    }

    @Test
    fun emptyLocalSavesMayOnlyRequestKnownRomScope() {
        val json = JSONObject(build(saves = emptyList(), roms = setOf(5, 12)))
        assertEquals(0, json.getJSONArray("saves").length())
        assertEquals(5, json.getJSONArray("rom_ids").getInt(0))
        assertEquals(12, json.getJSONArray("rom_ids").getInt(1))
        assertFalse(json.getBoolean("restore_unlisted"))
    }
}
