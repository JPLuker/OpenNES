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
