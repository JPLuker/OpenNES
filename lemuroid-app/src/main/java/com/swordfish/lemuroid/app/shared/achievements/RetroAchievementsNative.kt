package com.swordfish.lemuroid.app.shared.achievements

/**
 * Library-link smoke check, NOT an emulator login or award engine.
 * Remains unused by the player UI until native authentication is implemented.
 */
internal object RetroAchievementsNative {
    init {
        System.loadLibrary("libretrodroid")
    }

    external fun preflight(): Boolean

    /**
     * Encode public game-hash resolution through pinned rcheevos.
     * Returns [HTTPS URL, URL-encoded POST form data].
     * This does not authenticate or award achievements.
     */
    external fun prepareGameResolveRequest(md5: String): Array<String>?

    /** Official API response parsing; zero means no verified game match. */
    external fun parseGameResolveResponse(
        jsonBytes: ByteArray,
        httpStatus: Int,
    ): Int

    /**
     * A temporary, authenticated rc_client session for verifying the saved
     * emulator token against the official server. Does not activate a game,
     * inspect emulated RAM, or award achievements.
     */
    external fun beginTokenValidation(
        username: String,
        token: String,
    ): Long

    /** One queued request: [id, official URL, URL-encoded POST body]. */
    external fun takeValidationRequest(session: Long): Array<String>?

    external fun completeValidationRequest(
        session: Long,
        requestId: Long,
        httpStatus: Int,
        responseBody: ByteArray,
    ): Boolean

    /** 0=waiting, 1=authenticated, -1=failed, -2=closed/stale. */
    external fun tokenValidationState(session: Long): Int

    external fun validatedTokenUsername(session: Long): String?

    external fun stopTokenValidation(session: Long)

    /**
     * Native diagnostic only. No account, ROM identification, network, awards
     * or leaderboard submission occurs while this probe is active.
     * Must be stopped before any future authenticated native session starts.
     */
    external fun startFrameProbe(): Boolean

    external fun stopFrameProbe()

    external fun observedProbeFrames(): Long

    /** Bytes in official NES memory mapping; zero until a core is available. */
    external fun mappedProbeBytes(): Long

    /** Actual safe 1-byte RAM reads on emulated frames, not just map size. */
    external fun successfulMemorySamples(): Long
}
