package com.swordfish.lemuroid.app.shared.covers

import com.swordfish.lemuroid.lib.library.db.entity.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NesBoxArtUrlsTest {
    private fun game(
        system: String = "nes",
        customCover: String? = null,
    ) = Game(
        fileName = "Example.nes",
        fileUri = "content://example/rom",
        title = "Example",
        systemId = system,
        developer = null,
        coverFrontUrl = "https://thumbnails.libretro.com/example.png",
        lastIndexedAt = 0L,
        customCoverUri = customCover,
    )

    @Test
    fun regularNesArtworkStillPrefetches() {
        assertEquals(
            listOf(
                "https://thumbnails.libretro.com/example.png",
                // No region in the file name: the US box art for the title is the fallback.
                "https://thumbnails.libretro.com/" +
                    "Nintendo%20-%20Nintendo%20Entertainment%20System/Named_Boxarts/Example%20(USA).png",
            ),
            NesBoxArtUrls.remoteCandidates(game()),
        )
    }

    @Test
    fun customArtworkSuppressesRemotePrefetch() {
        assertTrue(NesBoxArtUrls.remoteCandidates(game(customCover = "content://custom/cover")).isEmpty())
    }

    @Test
    fun unrelatedSystemsCannotRequestNesArtwork() {
        assertTrue(NesBoxArtUrls.remoteCandidates(game(system = "gb")).isEmpty())
    }
}
