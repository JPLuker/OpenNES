package com.swordfish.lemuroid.app.shared.romm

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Notification preference, not an access/refresh switch. The remote catalogue
 * is refreshed silently and local downloads remain playable regardless.
 * Defaults to warnings enabled; the user can turn them off in RomM settings.
 */
internal object RommConnectionPreferences {
    private const val FILE = "opennes_romm_ui"
    private const val KEY = "show_disconnection_warnings"
    private val warningsState = MutableStateFlow(true)

    val warnings: StateFlow<Boolean> = warningsState

    fun load(context: Context) {
        warningsState.value =
            context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getBoolean(KEY, true)
    }

    fun setWarnings(
        context: Context,
        enabled: Boolean,
    ) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, enabled)
            .apply()
        warningsState.value = enabled
    }
}
