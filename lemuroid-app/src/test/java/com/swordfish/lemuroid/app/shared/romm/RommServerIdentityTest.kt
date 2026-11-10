package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RommServerIdentityTest {
    @Test
    fun stableSourceIdentityNormalizesEquivalentUrls() {
        val first = RommServerIdentity.fingerprint("https://romm.example.com/library")
        val normalized = RommServerIdentity.fingerprint("HTTPS://ROMM.EXAMPLE.COM/library/")
        assertEquals(first, normalized)
        assertTrue(first != null && RommServerIdentity.validFingerprint(first))
        assertNotEquals(first, RommServerIdentity.fingerprint("https://romm.example.com/other"))
        assertNotEquals(first, RommServerIdentity.fingerprint("https://another.example.com/library"))
    }

    @Test
    fun unsafeAndMissingServerIdentitiesCannotBeBound() {
        assertNull(RommServerIdentity.fingerprint(null))
        assertNull(RommServerIdentity.fingerprint("http://romm.example.com"))
        assertNull(RommServerIdentity.fingerprint("https://user:secret@romm.example.com"))
        assertFalse(RommServerIdentity.validFingerprint("invalid"))
    }
}
