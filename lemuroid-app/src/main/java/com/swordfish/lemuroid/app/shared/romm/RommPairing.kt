package com.swordfish.lemuroid.app.shared.romm

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/**
 * RomM 5.x Client API Token pairing. The QR is an ordinary URL:
 * https://server/pair?code=ABCD-EFGH (or a private-LAN HTTP equivalent).
 *
 * The code is short-lived and must be exchanged with the issuing server. A QR
 * never contains a bearer token and scanning it never changes the saved login.
 */
internal object RommPairing {
    class Request(val serverUrl: String, val code: String) {
        override fun toString(): String = "RommPairing.Request([redacted])"
    }

    private val validCode = Regex("[A-HJ-NP-Z2-9]{8}")

    fun normalizeCode(raw: String): String {
        val code = raw.trim().replace("-", "").uppercase(Locale.ROOT)
        require(validCode.matches(code)) { "Enter a valid eight-character RomM pairing code" }
        return code
    }

    fun parseQr(raw: String): Request {
        require(raw.length in 1..2048) { "Invalid RomM pairing QR" }
        val url = raw.trim().toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid RomM pairing QR")
        require(
            url.username.isEmpty() &&
                url.password.isEmpty() &&
                url.fragment == null &&
                url.pathSegments.lastOrNull() == "pair" &&
                url.queryParameterNames == setOf("code") &&
                url.queryParameterValues("code").size == 1,
        ) { "This QR is not a RomM pairing link" }
        val code = normalizeCode(url.queryParameter("code").orEmpty())
        val root = url.newBuilder().removePathSegment(url.pathSize - 1).query(null).build()
        return Request(RommApiClient.validatedServerUrl(root.toString()), code)
    }
}
