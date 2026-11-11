package com.swordfish.lemuroid.app.shared.romm

import org.json.JSONObject
import java.io.IOException

/**
 * The authenticated RomM user ID is part of a future sync identity. A
 * username alone cannot distinguish renamed accounts or same-server users.
 * This class contains no credentials and cannot authorize save writes.
 */
internal object RommAccountIdentity {
    data class Profile(
        val username: String,
        val userId: Long?,
    )

    fun parseProfile(response: JSONObject): Profile {
        val username = response.optString("username").trim()
        if (username.isEmpty() || username.length > 128 || username.any { it.isISOControl() }) {
            throw IOException("RomM did not return a valid account name")
        }
        // An unknown or incompatible server ID leaves browsing available,
        // but the profile cannot be used to bind any future sync operation.
        val userId =
            (response.opt("id") as? Number)
                ?.toString()
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
        return Profile(username, userId)
    }

    /** A token revision change, account change or server change invalidates a pin. */
    fun matchesPin(
        serverFingerprint: String?,
        credentialRevision: String?,
        userId: Long?,
        pinnedServerFingerprint: String?,
        pinnedCredentialRevision: String?,
        pinnedUserId: Long?,
    ): Boolean =
        serverFingerprint != null &&
            RommServerIdentity.validFingerprint(serverFingerprint) &&
            serverFingerprint == pinnedServerFingerprint &&
            !credentialRevision.isNullOrBlank() &&
            credentialRevision == pinnedCredentialRevision &&
            userId != null &&
            userId > 0L &&
            userId == pinnedUserId
}
