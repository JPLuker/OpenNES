package com.swordfish.lemuroid.app.shared.covers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CoverMemoryKeyTest {
    @Test
    fun stableAcrossSurfacesAndThumbnailSizes() {
        val url =
            "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%20Entertainment%20System/" +
                "Named_Boxarts/Game.png"
        val shelfKey = CoverUtils.coverMemoryKey(url)
        val gameDetailsKey = CoverUtils.coverMemoryKey(url)
        val pausedGameKey = CoverUtils.coverMemoryKey(url)
        assertEquals(shelfKey, gameDetailsKey)
        assertEquals(shelfKey, pausedGameKey)
    }

    @Test
    fun aChangedCoverNeverReusesOldArt() {
        val first = CoverUtils.coverMemoryKey("https://thumbnails.libretro.com/first.png")
        val changed = CoverUtils.coverMemoryKey("content://local/updated-cover.png")
        assertNotEquals(first, changed)
    }
}
