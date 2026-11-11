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
}
