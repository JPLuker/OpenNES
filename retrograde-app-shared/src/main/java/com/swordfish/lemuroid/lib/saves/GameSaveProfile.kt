package com.swordfish.lemuroid.lib.saves

import java.io.File

/**
 * Files in the pre-existing directories always belong to Casual. Hardcore
 * lives under separate private subdirectories and cannot read legacy SRAM,
 * auto-states, slots, or preview images by accident.
 */
enum class GameSaveProfile {
    CASUAL,
    HARDCORE,
}

object SaveProfileDirectories {
    fun sram(
        existingSavesDirectory: File,
        profile: GameSaveProfile,
    ): File =
        when (profile) {
            GameSaveProfile.CASUAL -> existingSavesDirectory
            GameSaveProfile.HARDCORE -> File(existingSavesDirectory, "hardcore")
        }

    fun states(
        existingCoreDirectory: File,
        profile: GameSaveProfile,
    ): File =
        when (profile) {
            GameSaveProfile.CASUAL -> existingCoreDirectory
            GameSaveProfile.HARDCORE -> File(existingCoreDirectory, "hardcore")
        }
}
