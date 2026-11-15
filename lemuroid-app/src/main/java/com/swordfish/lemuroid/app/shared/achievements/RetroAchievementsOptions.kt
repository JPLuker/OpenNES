package com.swordfish.lemuroid.app.shared.achievements

import android.content.Context
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

/**
 * Player options for RetroAchievements. Stored with the shared settings so
 * the game process reads what the Settings screen wrote.
 */
internal class RetroAchievementsOptions(context: Context) {
    private val preferences = SharedPreferencesHelper.getSharedPreferences(context.applicationContext)

    private fun flag(
        key: String,
        default: Boolean,
    ) = preferences.getBoolean(key, default)

    val encore get() = flag(KEY_ENCORE, false)
    val unofficial get() = flag(KEY_UNOFFICIAL, false)
    val spectator get() = flag(KEY_SPECTATOR, false)
    val notifyStart get() = flag(KEY_NOTIFY_START, true)
    val notifyUnlocks get() = flag(KEY_NOTIFY_UNLOCKS, true)
    val notifyLeaderboards get() = flag(KEY_NOTIFY_LEADERBOARDS, true)
    val notifyProgress get() = flag(KEY_NOTIFY_PROGRESS, true)
    val showIndicators get() = flag(KEY_SHOW_INDICATORS, true)

    /** Bit flags for the native client: 1 encore, 2 unofficial, 4 spectator. */
    fun nativeFlags(): Int = (if (encore) 1 else 0) or (if (unofficial) 2 else 0) or (if (spectator) 4 else 0)

    companion object {
        const val KEY_ENCORE = "opennes_ra_encore"
        const val KEY_UNOFFICIAL = "opennes_ra_unofficial"
        const val KEY_SPECTATOR = "opennes_ra_spectator"
        const val KEY_NOTIFY_START = "opennes_ra_notify_start"
        const val KEY_NOTIFY_UNLOCKS = "opennes_ra_notify_unlocks"
        const val KEY_NOTIFY_LEADERBOARDS = "opennes_ra_notify_leaderboards"
        const val KEY_NOTIFY_PROGRESS = "opennes_ra_notify_progress"
        const val KEY_SHOW_INDICATORS = "opennes_ra_show_indicators"
    }
}
