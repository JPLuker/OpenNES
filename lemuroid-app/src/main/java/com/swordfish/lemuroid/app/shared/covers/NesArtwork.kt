package com.swordfish.lemuroid.app.shared.covers

import com.swordfish.lemuroid.lib.library.db.entity.Game

/**
 * Optional artwork published in Libretro's NES thumbnail catalogue.
 *
 * Only derive related art from a *known* Libretro box-art URL. This prevents
 * generating unrelated art for custom covers or ROMs that were never matched.
 * Coil downloads and caches these images when the game details are opened.
 */
object NesArtwork {
    private const val NES_BOXART_PATH =
        "thumbnails.libretro.com/Nintendo - Nintendo Entertainment System/Named_Boxarts/"

    data class Preview(
        val kind: Kind,
        val url: String,
    )

    enum class Kind {
        TITLE_SCREEN,
        GAMEPLAY,
    }

    fun previews(game: Game): List<Preview> {
        val url =
            game.coverFrontUrl
                ?.replace("http://thumbnails.libretro.com/", "https://thumbnails.libretro.com/")
                ?: return emptyList()

        if (!url.startsWith("https://$NES_BOXART_PATH") || !url.endsWith(".png")) {
            return emptyList()
        }

        return listOf(
            Preview(
                Kind.TITLE_SCREEN,
                url.replace("/Named_Boxarts/", "/Named_Titles/"),
            ),
            Preview(
                Kind.GAMEPLAY,
                url.replace("/Named_Boxarts/", "/Named_Snaps/"),
            ),
        )
    }
}
