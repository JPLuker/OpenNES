package com.swordfish.lemuroid.app.shared.covers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NesArtworkTest {
    @Test
    fun unencodedCatalogueFolderProducesBothOfficialPreviewTypes() {
        val root =
            "https://thumbnails.libretro.com/Nintendo - Nintendo Entertainment System/"
        val entries = NesArtwork.previewsForUrl(root + "Named_Boxarts/RoboCop (USA).png")
        assertEquals(2, entries.size)
        assertEquals(NesArtwork.Kind.TITLE_SCREEN, entries[0].kind)
        assertEquals(root + "Named_Titles/RoboCop (USA).png", entries[0].url)
        assertEquals(NesArtwork.Kind.GAMEPLAY, entries[1].kind)
    }

    @Test
    fun percentEncodedFolderAndLegacyHttpProduceVerifiedPreviewUrls() {
        val source =
            "http://thumbnails.libretro.com/Nintendo%20-%20Nintendo%20Entertainment%20System/" +
                "Named_Boxarts/The%20Legend%20of%20Zelda%20(USA).png"
        val entries = NesArtwork.previewsForUrl(source)
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.url.startsWith("https://thumbnails.libretro.com/") })
        assertTrue(entries[0].url.contains("Named_Titles"))
        assertTrue(entries[1].url.contains("Named_Snaps"))
    }

    @Test
    fun unrelatedOrCustomCoversCannotGenerateAssumedGameScreenshots() {
        val sources =
            listOf(
                null,
                "content://covers/user.png",
                "https://example.org/Nintendo%20-%20Nintendo%20Entertainment%20System/Named_Boxarts/X.png",
                "https://thumbnails.libretro.com/Other/Named_Boxarts/X.png",
                "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%20Entertainment%20System/Named_Boxarts/X.jpg",
            )
        sources.forEach { assertTrue(NesArtwork.previewsForUrl(it).isEmpty()) }
    }
}
