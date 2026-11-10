package com.swordfish.lemuroid.app.shared.covers

/**
 * Tiny session-only memory of which legitimate cover URL actually decoded.
 * A ROM with an outdated catalogue cover should open the previously working
 * filename fallback directly, instead of flashing through two failed requests
 * every time the player changes between shelves, details and search.
 *
 * A changed source URL changes the key: custom artwork and metadata updates
 * cannot be masked by a previously successful but now unrelated cover.
 */
internal object NesCoverResolutionCache {
    private const val MAX_ITEMS = 256
    private val successful =
        object : LinkedHashMap<String, String>(MAX_ITEMS, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
                size > MAX_ITEMS
        }

    @Synchronized
    fun preferred(
        catalogueUrl: String?,
        alternateUrl: String?,
    ): String? {
        if (catalogueUrl.isNullOrBlank() || alternateUrl.isNullOrBlank()) return null
        val saved = successful[key(catalogueUrl, alternateUrl)]
        return saved?.takeIf { it == catalogueUrl || it == alternateUrl }
    }

    @Synchronized
    fun recordSuccess(
        catalogueUrl: String?,
        alternateUrl: String?,
        decodedUrl: String?,
    ) {
        if (catalogueUrl.isNullOrBlank() || alternateUrl.isNullOrBlank() ||
            decodedUrl != catalogueUrl && decodedUrl != alternateUrl
        ) {
            return
        }
        successful[key(catalogueUrl, alternateUrl)] = decodedUrl
    }

    private fun key(catalogueUrl: String, alternateUrl: String): String =
        "${catalogueUrl.length}:$catalogueUrl:${alternateUrl.length}:$alternateUrl"
}
