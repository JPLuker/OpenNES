package com.swordfish.lemuroid.app.shared.covers

import org.junit.Assert.assertEquals
import org.junit.Test

class NesCoverPrefetchOrderTest {
    private val host = "https://thumbnails.libretro.com/"

    @Test
    fun prioritizesVisibleGamesBeforeFallbackArtwork() {
        val urls =
            NesCoverPrefetchOrder.select(
                listOf(
                    listOf(host + "a.png", host + "a-alt.png"),
                    listOf(host + "b.png", host + "b-alt.png"),
                    listOf(host + "c.png"),
                ),
                limit = 3,
            )
        assertEquals(listOf(host + "a.png", host + "b.png", host + "c.png"), urls)
    }

    @Test
    fun includesFallbacksWhenCapacityRemainsAndDeduplicatesUrls() {
        val urls =
            NesCoverPrefetchOrder.select(
                listOf(
                    listOf(host + "a.png", host + "fallback.png"),
                    listOf(host + "a.png", host + "b.png"),
                ),
                limit = 4,
            )
        assertEquals(listOf(host + "a.png", host + "fallback.png", host + "b.png"), urls)
    }

    @Test
    fun ignoresUntrustedAndCustomOnlyEntries() {
        val urls =
            NesCoverPrefetchOrder.select(
                listOf(
                    emptyList(),
                    listOf("http://thumbnails.libretro.com/unsafe.png", host + "safe.png"),
                    listOf("https://example.com/other.png"),
                ),
                limit = 2,
            )
        assertEquals(listOf(host + "safe.png"), urls)
    }

    @Test
    fun zeroLimitNeverReturnsArtwork() {
        assertEquals(
            emptyList<String>(),
            NesCoverPrefetchOrder.select(listOf(listOf(host + "a.png")), 0),
        )
    }
}
