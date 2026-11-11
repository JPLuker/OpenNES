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
}
