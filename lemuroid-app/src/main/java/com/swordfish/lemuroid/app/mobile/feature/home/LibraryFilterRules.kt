package com.swordfish.lemuroid.app.mobile.feature.home

import com.swordfish.lemuroid.lib.library.db.entity.Game
import java.util.Locale

internal enum class LibraryLocation {
    ALL,
    INSTALLED,
    ROMM,
}

internal enum class LibrarySort {
    TITLE,
    RECENT,
    RELEASE_YEAR,
}

internal object LibraryFilterRules {
    fun availableGenres(games: List<Game>): List<String> =
        games.asSequence()
            .mapNotNull { it.genre?.trim()?.takeIf(String::isNotEmpty) }
            .flatMap { it.split(",", ";").asSequence() }
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedBy { it.lowercase(Locale.ROOT) }
            .toList()

    fun apply(
        games: List<Game>,
        location: LibraryLocation,
        genre: String?,
        sort: LibrarySort,
    ): List<Game> {
        val normalizedGenre = genre?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank)
        val filtered =
            games.asSequence()
                .filter {
                    when (location) {
                        LibraryLocation.ALL -> true
                        LibraryLocation.INSTALLED -> it.id > 0
                        LibraryLocation.ROMM -> it.id < 0
                    }
                }
                .filter { game ->
                    normalizedGenre == null ||
                        game.genre.orEmpty()
                            .split(",", ";")
                            .any { it.trim().lowercase(Locale.ROOT) == normalizedGenre }
                }
        return when (sort) {
            LibrarySort.TITLE ->
                filtered.sortedWith(
                    compareBy<Game> { it.displayName().lowercase(Locale.ROOT) }
                        .thenBy { it.fileName.lowercase(Locale.ROOT) },
                ).toList()
            LibrarySort.RECENT ->
                filtered.sortedWith(
                    compareByDescending<Game> { it.lastPlayedAt ?: 0L }
                        .thenBy { it.displayName().lowercase(Locale.ROOT) },
                ).toList()
            LibrarySort.RELEASE_YEAR ->
                filtered.sortedWith(
                    compareBy<Game> { it.releaseYear ?: Int.MAX_VALUE }
                        .thenBy { it.displayName().lowercase(Locale.ROOT) },
                ).toList()
        }
    }
}
