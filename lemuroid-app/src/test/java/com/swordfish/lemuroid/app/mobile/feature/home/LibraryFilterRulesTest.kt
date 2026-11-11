package com.swordfish.lemuroid.app.mobile.feature.home

import com.swordfish.lemuroid.lib.library.db.entity.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFilterRulesTest {
    private fun game(
        id: Int,
        title: String,
        genre: String?,
        year: Int?,
        played: Long?,
    ) = Game(
        id = id,
        fileName = "test_${id}.nes",
        fileUri = if (id < 0) "romm-preview://game/${-id}" else "content://nes/$id",
        title = title,
        systemId = "nes",
        developer = null,
        coverFrontUrl = null,
        lastIndexedAt = 0L,
        genre = genre,
        releaseYear = year,
        lastPlayedAt = played,
    )

    private val collection =
        listOf(
            game(1, "Metroid", "Action, Adventure", 1986, 100L),
            game(-2, "Duck Hunt", "Shooter", 1984, null),
            game(3, "The Legend of Zelda", "Adventure", 1986, 200L),
        )

    @Test
    fun installedAndRemoteFiltersNeverChangePlayabilityIdentity() {
        assertEquals(
            listOf(1, 3),
            LibraryFilterRules.apply(collection, LibraryLocation.INSTALLED, null, LibrarySort.TITLE).map { it.id },
        )
        assertEquals(
            listOf(-2),
            LibraryFilterRules.apply(collection, LibraryLocation.ROMM, null, LibrarySort.TITLE).map { it.id },
        )
    }

    @Test
    fun genreUsesOnlyGenuineAvailableMetadata() {
        assertEquals(listOf("Action", "Adventure", "Shooter"), LibraryFilterRules.availableGenres(collection))
        assertEquals(
            listOf(1, 3),
            LibraryFilterRules.apply(collection, LibraryLocation.ALL, "adventure", LibrarySort.TITLE).map { it.id },
        )
        assertTrue(LibraryFilterRules.apply(collection, LibraryLocation.ALL, "RPG", LibrarySort.TITLE).isEmpty())
    }

    @Test
    fun sortsDeterministicallyWithoutDiscardingUndownloadedRomM() {
        assertEquals(
            listOf(3, 1, -2),
            LibraryFilterRules.apply(collection, LibraryLocation.ALL, null, LibrarySort.RECENT).map { it.id },
        )
        assertEquals(
            listOf(-2, 1, 3),
            LibraryFilterRules.apply(collection, LibraryLocation.ALL, null, LibrarySort.RELEASE_YEAR).map { it.id },
        )
    }
}
