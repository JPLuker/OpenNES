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
     * Native diagnostic only. No account, ROM identification, network, awards
     * or leaderboard submission occurs while this probe is active.
     * Must be stopped before any future authenticated native session starts.
     */
    external fun startFrameProbe(): Boolean

    external fun stopFrameProbe()

    external fun observedProbeFrames(): Long

    /** Bytes in official NES memory mapping; zero until a core is available. */
    external fun mappedProbeBytes(): Long
}
