package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RommLibraryCatalogTest {
    private fun rom(
        id: Int,
        filename: String,
    ) = RommApiClient.Rom(
        id = id,
        platformId = 1,
        name = "NES game $id",
        fileName = filename,
        sizeBytes = 1024L,
        md5 = null,
    )

    @Test
    fun sameNameTitlesOnDifferentFilesRemainDistinct() {
        val remote = listOf(rom(100, "Zelda (USA).nes"), rom(101, "Zelda (Japan).nes"))
        val visible = RommLibraryCatalog.available(remote, setOf("ZELDA (usa).NES"))
        assertEquals(listOf(101), visible.map { it.id })
    }

    @Test
    fun previewCannotBeConfusedWithIndexedLocalGame() {
        val remote = rom(42, "Metroid (USA).nes")
        val preview = RommLibraryCatalog.asPreview(remote)
        val state = RommLibraryCatalog.State(games = listOf(remote))
        assertEquals(-42, preview.id)
        assertEquals("nes", preview.systemId)
        assertSame(remote, RommLibraryCatalog.remoteFor(preview, state))
        assertNull(RommLibraryCatalog.remoteFor(preview.copy(id = 42), state))
        assertNull(RommLibraryCatalog.remoteFor(preview.copy(fileName = "Other.nes"), state))
    }

    @Test
    fun doesNotSuppressDifferentFileForSameTitle() {
        val games = listOf(rom(1, "Game (USA).nes"), rom(2, "Game (Europe).nes"))
        assertEquals(2, RommLibraryCatalog.available(games, setOf("Game (Japan).nes")).size)
    }
}
