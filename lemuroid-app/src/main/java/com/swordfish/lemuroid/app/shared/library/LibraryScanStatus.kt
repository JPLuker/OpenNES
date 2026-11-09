package com.swordfish.lemuroid.app.shared.library

import android.content.Context
import android.content.SharedPreferences

/** Last completed scan error, kept independently from the indexed game database. */
object LibraryScanStatus {
    const val FOLDER_UNAVAILABLE = "folder_unavailable"
    const val SCAN_FAILED = "scan_failed"
    const val ERROR_KEY = "last_scan_error"

    private const val PREFERENCES_NAME = "opennes_library_scan"

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun setError(
        context: Context,
        error: String?,
    ) {
        preferences(context).edit().putString(ERROR_KEY, error).apply()
    }
}
