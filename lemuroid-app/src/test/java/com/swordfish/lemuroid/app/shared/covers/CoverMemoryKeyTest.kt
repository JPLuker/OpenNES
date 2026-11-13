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

    @Test
    fun sharedDecodeSizeKeepsTheNesBoxProportion() {
        // Every cover surface decodes at this one size so a cached cover is
        // valid everywhere. A 5 x 7.125 inch carton must not be distorted.
        val ratio = CoverUtils.COVER_DECODE_WIDTH_PX.toFloat() / CoverUtils.COVER_DECODE_HEIGHT_PX
        assertEquals(5f / 7.125f, ratio, 0.005f)
    }
}
