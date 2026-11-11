package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RommPairingTest {
    @Test
    fun parsesOfficialPairingLinkWithPrefix() {
        val pairing = RommPairing.parseQr("https://romm.example.com/base/pair?code=ABCD-EFGH")
        assertEquals("https://romm.example.com/base/", pairing.serverUrl)
        assertEquals("ABCDEFGH", pairing.code)
        assertFalse(pairing.toString().contains("ABCDEFGH"))
    }

    @Test
    fun acceptsPrivateLanHttpQrAndManualCode() {
        val pairing = RommPairing.parseQr("http://192.168.86.163:8081/pair?code=ABCD-EFGH")
        assertEquals("http://192.168.86.163:8081/", pairing.serverUrl)
        assertEquals("ABCDEFGH", RommPairing.normalizeCode("abcd-efgh"))
        assertTrue(RommApiClient.isPrivateLanHttp(pairing.serverUrl))
    }

    @Test
    fun rejectsUntrustedQrRoutesAndUnsupportedCodes() {
        for (value in listOf(
            "https://romm.example.com/pair?token=ABCDEFGH",
            "https://romm.example.com/pair?code=ABCDEFGH&callback=evil",
            "https://romm.example.com/other?code=ABCDEFGH",
            "https://user:pass@romm.example.com/pair?code=ABCDEFGH",
            "http://romm.example.com/pair?code=ABCDEFGH",
            "http://8.8.8.8/pair?code=ABCDEFGH",
            "https://romm.example.com/pair?code=AAAAAAAAA",
            "https://romm.example.com/pair?code=ABCD%20EFGH",
        )) {
            assertRejected { RommPairing.parseQr(value) }
        }
        assertRejected { RommPairing.normalizeCode("rmm_abcdef") }
    }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected rejection")
        } catch (_: IllegalArgumentException) {
            // Invalid links must never reach the network.
        }
    }
}
