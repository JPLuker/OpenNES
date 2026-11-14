package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RommCoverUrlTest {
    @Test
    fun acceptsOfficialIgdbHttpsImages() {
        val url = "https://images.igdb.com/igdb/image/upload/t_cover_big/co1234.jpg"
        assertEquals(url, RommCoverUrl.trusted(url))
    }

    @Test
    fun refusesUntrustedOrCredentialedArtwork() {
        for (url in listOf(
            "http://images.igdb.com/igdb/image/upload/t_cover_big/co1234.jpg",
            "https://images.igdb.com.evil.example/igdb/image/upload/t_cover_big/co1234.jpg",
            "https://evil.example/igdb/image/upload/t_cover_big/co1234.jpg",
            "https://user:secret@images.igdb.com/igdb/image/upload/t_cover_big/co1234.jpg",
            "https://images.igdb.com:8443/igdb/image/upload/t_cover_big/co1234.jpg",
            "https://images.igdb.com/igdb/image/upload/t_cover_big/co1234.jpg?token=secret",
            "https://images.igdb.com/login",
            "https://images.igdb.com/igdb/image/upload/evil.html",
            "https://192.168.86.163/cover.jpg",
        )) {
            assertNull(RommCoverUrl.trusted(url))
        }
        assertNull(RommCoverUrl.trusted(null))
    }

    @Test
    fun buildsCoverUrlsOnThePlayersOwnServer() {
        assertEquals(
            "http://192.168.1.5:8080/assets/romm/resources/roms/1/2/cover/big.png",
            RommCoverUrl.fromServer(
                "http://192.168.1.5:8080/",
                "/assets/romm/resources/roms/1/2/cover/big.png?ts=2026-11-14 10:00:00",
            ),
        )
        assertNull(RommCoverUrl.fromServer("https://romm.example", "/api/roms/1/content"))
        assertNull(RommCoverUrl.fromServer("https://romm.example", "/assets/romm/resources/../../etc/passwd.png"))
        assertNull(RommCoverUrl.fromServer("https://romm.example", "/assets/romm/resources/roms/1/cover.html"))
        assertNull(RommCoverUrl.fromServer("https://romm.example", ""))
    }

    @Test
    fun usableAcceptsIgdbAndServerResourcesOnly() {
        val server = "https://romm.example/assets/romm/resources/roms/1/2/cover/big.webp"
        assertEquals(server, RommCoverUrl.usable(server))
        assertNull(RommCoverUrl.usable("https://user:pw@romm.example/assets/romm/resources/a.png"))
        assertNull(RommCoverUrl.usable("https://romm.example/assets/romm/resources/a.png?token=x"))
        assertNull(RommCoverUrl.usable("https://example.com/a.jpg"))
    }
}
