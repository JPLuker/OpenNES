package com.swordfish.lemuroid.app.mobile.feature.home

import android.content.Context
import com.swordfish.lemuroid.BuildConfig
import java.security.MessageDigest

/** Casual or Hardcore per game, falling back to the player's default. */
internal class NesRomLaunchModeStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences("opennes_rom_launch_mode_v1", Context.MODE_PRIVATE)

    /** OpenNES's own Hardcore mode is always available. */
    val hardcoreAvailable: Boolean
        get() = true

    /** The mode for games the player has not set individually. */
    var defaultHardcore: Boolean
        get() = preferences.getBoolean(DEFAULT_KEY, false)
        set(value) {
            preferences.edit().putBoolean(DEFAULT_KEY, value).apply()
        }

    /** Debug builds only: also report Hardcore to RetroAchievements before approval. Never on in a release build. */
    var testUnapproved: Boolean
        get() = BuildConfig.DEBUG && preferences.getBoolean(TEST_KEY, false)
        set(value) {
            preferences.edit().putBoolean(TEST_KEY, BuildConfig.DEBUG && value).apply()
        }

    fun wantsHardcore(romUri: String): Boolean = preferences.getBoolean(keyFor(romUri), defaultHardcore)

    fun select(
        romUri: String,
        hardcore: Boolean,
    ): Boolean {
        if (hardcore && !hardcoreAvailable) return false
        preferences.edit().putBoolean(keyFor(romUri), hardcore).apply()
        return true
    }

    companion object {
        private const val DEFAULT_KEY = "default_hardcore"
        private const val TEST_KEY = "test_unapproved_hardcore"

        /** Never store private ROM file paths as SharedPreferences property names. */
        fun keyFor(romUri: String): String {
            val hash = MessageDigest.getInstance("SHA-256").digest(romUri.toByteArray(Charsets.UTF_8))
            return "rom_" + hash.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        }
    }
}
