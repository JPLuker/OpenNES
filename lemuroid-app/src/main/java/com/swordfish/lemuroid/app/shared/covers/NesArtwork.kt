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
    private val knownBoxartRoots =
        setOf(
            "https://thumbnails.libretro.com/Nintendo - Nintendo Entertainment System/Named_Boxarts/",
            "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%20Entertainment%20System/Named_Boxarts/",
        )

    data class Preview(
        val kind: Kind,
        val url: String,
    )

    enum class Kind {
        TITLE_SCREEN,
        GAMEPLAY,
    }

    fun previews(game: Game): List<Preview> {
        val catalogue = game.coverFrontUrl
        val alternate = NesBoxArtUrls.romFilenameFallback(game)
        val selected = NesCoverResolutionCache.preferred(catalogue, alternate)
        return previewsForUrl(selected ?: catalogue ?: alternate)
    }

    /** Only the official NES box-art tree can generate related preview URLs. */
    internal fun previewsForUrl(source: String?): List<Preview> {
        val url =
            source
                ?.replace("http://thumbnails.libretro.com/", "https://thumbnails.libretro.com/")
                ?: return emptyList()

        if (knownBoxartRoots.none { url.startsWith(it) } || !url.endsWith(".png")) {
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
