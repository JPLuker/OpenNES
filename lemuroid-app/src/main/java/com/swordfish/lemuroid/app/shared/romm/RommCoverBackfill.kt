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
    )

    /** Game id to the trusted RomM cover URL it should get. */
    fun covers(
        local: List<LocalGame>,
        remote: List<RommApiClient.Rom>,
    ): Map<Int, String> {
        val remoteCovers =
            remote
                .mapNotNull { rom -> RommCoverUrl.trusted(rom.coverUrl)?.let { key(rom.fileName) to it } }
                .toMap()
        if (remoteCovers.isEmpty()) return emptyMap()
        return local
            .filter { it.coverFrontUrl.isNullOrBlank() && it.customCoverUri.isNullOrBlank() }
            .mapNotNull { game -> remoteCovers[key(game.fileName)]?.let { game.id to it } }
            .toMap()
    }

    private fun key(fileName: String): String = fileName.substringAfterLast('/').lowercase(Locale.ROOT)
}
