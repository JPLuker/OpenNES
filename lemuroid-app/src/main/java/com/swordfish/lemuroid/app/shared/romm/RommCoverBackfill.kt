package com.swordfish.lemuroid.app.shared.romm

import java.util.Locale

/**
 * A game downloaded from RomM gets its art from the library scan, which only
 * finds covers for files named the way the Libretro catalogue names them.
 * RomM already knows the game's cover, so a library game that ended up with
 * no cover borrows the one RomM shows for the same file.
 *
 * Never replaces a cover the library already has or one the player chose.
 */
internal object RommCoverBackfill {
    data class LocalGame(
        val id: Int,
        val fileName: String,
        val coverFrontUrl: String?,
        val customCoverUri: String?,
        val displayName: String = "",
        val customName: String? = null,
        val genre: String? = null,
        val description: String? = null,
    )

    /** RomM's name and details for a library game; null fields change nothing. */
    data class Details(val name: String?, val genre: String?, val description: String?)

    /**
     * Game id to the RomM details it is missing. RomM's name becomes the
     * shown name only when the player has not chosen one; genre and
     * description only fill blanks.
     */
    fun details(
        local: List<LocalGame>,
        remote: List<RommApiClient.Rom>,
    ): Map<Int, Details> {
        val byFile = remote.associateBy { key(it.fileName) }
        return local.mapNotNull { game ->
            val rom = byFile[key(game.fileName)] ?: return@mapNotNull null
            val name =
                rom.name.trim().takeIf {
                    game.customName == null && it.isNotBlank() && it != rom.fileName && it != game.displayName
                }
            val genre = rom.genres.joinToString(", ").takeIf { game.genre.isNullOrBlank() && it.isNotBlank() }
            val description = rom.description?.takeIf { game.description.isNullOrBlank() && it.isNotBlank() }
            if (name == null && genre == null && description == null) {
                null
            } else {
                game.id to
                    Details(
                        name,
                        genre,
                        description,
                    )
            }
        }.toMap()
    }

    /** Game id to the trusted RomM cover URL it should get. */
    fun covers(
        local: List<LocalGame>,
        remote: List<RommApiClient.Rom>,
    ): Map<Int, String> {
        val remoteCovers =
            remote
                .mapNotNull { rom -> RommCoverUrl.usable(rom.coverUrl)?.let { key(rom.fileName) to it } }
                .toMap()
        if (remoteCovers.isEmpty()) return emptyMap()
        return local
            .filter { it.coverFrontUrl.isNullOrBlank() && it.customCoverUri.isNullOrBlank() }
            .mapNotNull { game -> remoteCovers[key(game.fileName)]?.let { game.id to it } }
            .toMap()
    }

    private fun key(fileName: String): String = fileName.substringAfterLast('/').lowercase(Locale.ROOT)
}
