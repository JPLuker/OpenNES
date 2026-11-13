package com.swordfish.lemuroid.app.shared.covers

/**
 * Spread limited background fetches across visible games before spending
 * bandwidth on alternate artwork for any individual game.
 */
internal object NesCoverPrefetchOrder {
    fun select(
        candidates: List<List<String>>,
        limit: Int,
    ): List<String> {
        require(limit >= 0)
        val selected = linkedSetOf<String>()
        for (index in 0 until (candidates.maxOfOrNull { it.size } ?: 0)) {
            for (game in candidates) {
                val url = game.getOrNull(index) ?: continue
                if (url.startsWith("https://thumbnails.libretro.com/")) {
                    selected += url
                    if (selected.size >= limit) return selected.take(limit)
                }
            }
        }
        return selected.take(limit)
    }
}
