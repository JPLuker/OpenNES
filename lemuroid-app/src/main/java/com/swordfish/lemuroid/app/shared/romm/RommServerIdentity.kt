package com.swordfish.lemuroid.app.shared.romm

import java.security.MessageDigest

/**
 * Non-secret, stable source-server fingerprint. Account identity still has
 * to be verified online before a future sync executor is allowed to act.
 * Do not substitute this hash for RomM authentication or proof of ownership.
 */
internal object RommServerIdentity {
    fun fingerprint(serverUrl: String?): String? {
        if (serverUrl.isNullOrBlank()) return null
        val normalized =
            runCatching { RommApiClient.validatedServerUrl(serverUrl) }
                .getOrNull() ?: return null
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }

    fun validFingerprint(value: String): Boolean = value.matches(Regex("[0-9a-f]{64}"))
}
