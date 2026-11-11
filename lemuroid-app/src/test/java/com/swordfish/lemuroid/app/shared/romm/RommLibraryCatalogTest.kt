package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
    fun completedImportBecomesPlayableOnlyAfterRoomIndexesRealFile() {
        val remote = rom(42, "Metroid (USA).nes")
        val waiting =
            RommLibraryCatalog.State(
                games = listOf(remote),
                awaitingIndexIds = setOf(42),
                transferProgress =
                    mapOf(
                        42 to RommApiClient.ImportProgress(RommApiClient.ImportStage.INDEXING),
                    ),
            )
        val notIndexed =
            RommLibraryCatalog.reconciled(waiting, emptySet(), 100L, mapOf(42 to 0L))
        assertEquals(setOf(42), notIndexed.awaitingIndexIds)
        val indexed =
            RommLibraryCatalog.reconciled(
                waiting,
                setOf("METROID (usa).NES"),
                100L,
                mapOf(42 to 0L),
            )
        assertTrue(indexed.awaitingIndexIds.isEmpty())
        assertTrue(indexed.transferProgress.isEmpty())
    }

    @Test
    fun slowIndexingShowsRecoveryWithoutRedownloadingRom() {
        val remote = rom(3, "Zelda.nes")
        val waiting = RommLibraryCatalog.State(games = listOf(remote), awaitingIndexIds = setOf(3))
        val delayed =
            RommLibraryCatalog.reconciled(waiting, emptySet(), 26_000L, mapOf(3 to 0L))
        assertEquals(setOf(3), delayed.indexingDelayedIds)
        assertEquals(setOf(3), delayed.awaitingIndexIds)
        val recovered =
            RommLibraryCatalog.reconciled(delayed, setOf("Zelda.nes"), 30_000L, mapOf(3 to 0L))
        assertTrue(recovered.indexingDelayedIds.isEmpty())
        assertTrue(recovered.awaitingIndexIds.isEmpty())
    }

    @Test
    fun doesNotSuppressDifferentFileForSameTitle() {
        val games = listOf(rom(1, "Game (USA).nes"), rom(2, "Game (Europe).nes"))
        assertEquals(2, RommLibraryCatalog.available(games, setOf("Game (Japan).nes")).size)
    }
}
