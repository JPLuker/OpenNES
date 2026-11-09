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
}
