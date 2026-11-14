package com.swordfish.lemuroid.app.shared.romm

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/**
 * Only allow public IGDB image CDN URLs already supplied by RomM.
 * Never send a RomM token to a cover host or fetch arbitrary server URLs.
 * Missing/unsupported art continues to use OpenNES's NES boxart fallback.
 */
internal object RommCoverUrl {
    fun trusted(candidate: String?): String? {
        val raw = candidate?.trim()?.takeIf { it.length in 1..600 } ?: return null
        val url = raw.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" ||
            url.host != "images.igdb.com" ||
            url.port != 443 ||
            url.username.isNotEmpty() ||
            url.password.isNotEmpty() ||
            url.query != null ||
            url.fragment != null
        ) {
            return null
        }
        val path = url.encodedPath
        if (!path.startsWith("/igdb/image/upload/")) return null
        val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return url.toString().takeIf { extension in setOf("jpg", "jpeg", "png", "webp") }
    }

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp")
    private const val RESOURCES = "/assets/romm/resources/"

    /**
     * The cover RomM itself stores for a game, served by the player's own
     * server without a token. [path] is RomM's `path_cover_large`.
     */
    fun fromServer(
        serverUrl: String,
        path: String?,
    ): String? {
        val clean = path?.trim()?.substringBefore('?')?.takeIf { it.length in 1..400 } ?: return null
        if (!clean.startsWith(RESOURCES) || ".." in clean || '\\' in clean) return null
        val root = serverUrl.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
        val url = root.newBuilder().addPathSegments(clean.trimStart('/')).build()
        return url.toString().takeIf { onRommServer(it) != null }
    }

    /** A RomM resource image on some server: no credentials, query or odd file type. */
    private fun onRommServer(candidate: String?): String? {
        val raw = candidate?.trim()?.takeIf { it.length in 1..700 } ?: return null
        val url = raw.toHttpUrlOrNull() ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null) {
            return null
        }
        val path = url.encodedPath
        if (RESOURCES !in path || ".." in path) return null
        val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return url.toString().takeIf { extension in imageExtensions }
    }

    /** Either kind of cover this app shows for a RomM game. */
    fun usable(candidate: String?): String? = trusted(candidate) ?: onRommServer(candidate)
}
