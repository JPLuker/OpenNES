package com.swordfish.lemuroid.app.mobile.feature.game

import android.content.Context
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

/**
 * Whether the controller uses the left-handed layout (D-pad right, A/B left).
 * Kept with the other controller settings, which the game process also reads.
 */
class NesLeftHandedStore(context: Context) {
    private val preferences = SharedPreferencesHelper.getSharedPreferences(context.applicationContext)

    val leftHanded: Boolean
        get() = preferences.getBoolean(KEY, false)

    companion object {
        const val KEY = "opennes_left_handed"
    }
}
