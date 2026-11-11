package com.swordfish.lemuroid.app.mobile.feature.home

import android.content.Context
import com.swordfish.lemuroid.app.shared.achievements.HardcoreRuntime
import java.security.MessageDigest

/** ROM-specific launch preference; never falls back to a global mode. */
internal class NesRomLaunchModeStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences("opennes_rom_launch_mode_v1", Context.MODE_PRIVATE)

    val hardcoreAvailable: Boolean
        get() = HardcoreRuntime.hardcoreAvailable()

    fun wantsHardcore(romUri: String): Boolean = hardcoreAvailable && preferences.getBoolean(keyFor(romUri), false)

    fun select(
        romUri: String,
        hardcore: Boolean,
    ): Boolean {
        if (hardcore && !hardcoreAvailable) return false
        preferences.edit().putBoolean(keyFor(romUri), hardcore).apply()
        return true
    }

    companion object {
        /** Never store private ROM file paths as SharedPreferences property names. */
        fun keyFor(romUri: String): String {
            val hash = MessageDigest.getInstance("SHA-256").digest(romUri.toByteArray(Charsets.UTF_8))
            return "rom_" + hash.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        }
    }
}
