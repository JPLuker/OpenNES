package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RommDeviceRegistrationTest {
    private val serverA = "a".repeat(64)
    private val serverB = "b".repeat(64)
    private val seed = ByteArray(32) { it.toByte() }

    @Test
    fun deviceAliasDoesNotExposeRealDeviceInformationAndIsAccountBound() {
        val first = RommDeviceAlias.derive(seed, serverA, 42L)
        assertTrue(first.matches(Regex("opennes-[0-9a-f]{16}")))
        assertEquals(first, RommDeviceAlias.derive(seed, serverA, 42L))
        assertNotEquals(first, RommDeviceAlias.derive(seed, serverB, 42L))
        assertNotEquals(first, RommDeviceAlias.derive(seed, serverA, 43L))
        assertNotEquals(first, RommDeviceAlias.derive(ByteArray(32) { 4 }, serverA, 42L))
        assertThrows(IllegalArgumentException::class.java) {
            RommDeviceAlias.derive(byteArrayOf(1, 2), serverA, 42L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RommDeviceAlias.derive(seed, "not-a-server", 42L)
        }
    }

    @Test
    fun registrationIsExplicitAndDisablesRemoteInstallCapability() {
        val alias = RommDeviceAlias.derive(seed, serverA, 42L)
        val json = RommDeviceRegistration.request(alias, "0.1.2")
        assertEquals(alias, json.getString("hostname"))
        assertEquals("android", json.getString("platform"))
        assertEquals("opennes", json.getString("client"))
        assertEquals("api", json.getString("sync_mode"))
        assertTrue(json.getBoolean("allow_existing"))
        assertFalse(json.getBoolean("allow_duplicate"))
        assertFalse(json.getBoolean("reset_syncs"))
        assertFalse(json.getJSONObject("capabilities").getBoolean("remote_install"))
        assertFalse(json.has("mac_address"))
        assertFalse(json.has("ip_address"))
    }

    @Test
    fun unsafeDeviceNamesVersionsAndIdentifiersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RommDeviceRegistration.request("actual-phone-hostname", "0.1.2")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RommDeviceRegistration.request("opennes-" + "1".repeat(16), "unknown")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RommDeviceRegistration.request("opennes-" + "1".repeat(16), "0.1.2", "bad\nname")
        }
        assertThrows(IOException::class.java) {
            RommDeviceRegistration.parse("""{"device_id":"not-a-uuid","name":"Android"}""")
        }
        assertThrows(IOException::class.java) {
            RommDeviceRegistration.parse("""{"device_id":"3f1c2b9e-8a4d-4c7e-9f21-6d0b5a7e1c34"}""")
        }
    }

    @Test
    fun validServerDeviceIdCanBePinnedWithoutTrustingAnArbitraryResponse() {
        val result =
            RommDeviceRegistration.parse(
                """{"device_id":"3f1c2b9e-8a4d-4c7e-9f21-6d0b5a7e1c34","name":"OpenNES Android"}""",
            )
        assertEquals("3f1c2b9e-8a4d-4c7e-9f21-6d0b5a7e1c34", result.deviceId)
        assertTrue(RommDeviceAlias.validDeviceId(result.deviceId))
        assertEquals("OpenNES Android", result.name)
    }
}
