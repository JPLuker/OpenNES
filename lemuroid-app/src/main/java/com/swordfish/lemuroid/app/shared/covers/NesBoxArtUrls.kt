package com.swordfish.lemuroid.app.shared.covers

import android.net.Uri
import com.swordfish.lemuroid.lib.library.db.entity.Game

/**
 * Shared image candidates for the game shelf and Android launcher shortcuts.
 * The No-Intro filename fallback must match what the visible game shelf uses.
 */
object NesBoxArtUrls {
    fun romFilenameFallback(game: Game): String? {
        if (game.systemId != "nes" || !game.customCoverUri.isNullOrBlank()) return null

        val fileTitle =
            game.fileName.substringAfterLast('/')
                .replace(Regex("""\.(?:nes|zip|7z)$""", RegexOption.IGNORE_CASE), "")
        val region = Regex("""\((?:USA|Europe|Japan|World)(?:[,)]|\s)""", RegexOption.IGNORE_CASE)
        if (!region.containsMatchIn(fileTitle)) return null

        return "https://thumbnails.libretro.com/" +
            "Nintendo%20-%20Nintendo%20Entertainment%20System/Named_Boxarts/" +
            Uri.encode(fileTitle) + ".png"
    }

    fun remoteCandidates(game: Game): List<String> {
        // A custom cover is authoritative. Do not fetch unrelated catalogue
        // artwork in the background when the player has supplied their own.
        if (game.systemId != "nes" || !game.customCoverUri.isNullOrBlank()) return emptyList()
        return listOfNotNull(
            game.coverFrontUrl?.replace(
                "http://thumbnails.libretro.com/",
                "https://thumbnails.libretro.com/",
            ),
            romFilenameFallback(game),
        ).distinct()
    }
}
