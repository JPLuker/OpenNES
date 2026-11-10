package com.swordfish.lemuroid.app.shared.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RetroAchievementsNativeHttpTransportTest {
    @Test
    fun acceptsOnlyOfficialHttpsAuthority() {
        val url =
            RetroAchievementsNativeHttpTransport.validatedEndpoint(
                "https://retroachievements.org/dorequest.php?r=login",
            )
        assertEquals("retroachievements.org", url.host)
    }

    @Test
    fun rejectsRedirectTargetsAndUntrustedHosts() {
        val rejected =
            listOf(
                "http://retroachievements.org/dorequest.php",
                "https://retroachievements.org.evil.example/dorequest.php",
                "https://example.org/dorequest.php",
                "https://retroachievements.org:8443/dorequest.php",
                "https://alice:secret@retroachievements.org/dorequest.php",
            )
        rejected.forEach { unsafe ->
            assertThrows(IllegalArgumentException::class.java) {
                RetroAchievementsNativeHttpTransport.validatedEndpoint(unsafe)
            }
        }
    }
}
