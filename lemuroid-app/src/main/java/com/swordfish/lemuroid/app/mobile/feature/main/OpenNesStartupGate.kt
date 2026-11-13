package com.swordfish.lemuroid.app.mobile.feature.main

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Decides how long the opening loading screen stays up.
 *
 * It covers the first library snapshot so the app never opens onto an empty
 * shelf, but it is always bounded: a slow database or a route that never
 * loads the library (first-run setup) cannot hold the screen.
 */
object OpenNesStartupGate {
    const val MINIMUM_MS = 1_000L
    const val MAXIMUM_MS = 4_000L

    private val libraryReady = MutableStateFlow(false)

    /** The library has produced its first real snapshot in this process. */
    fun markLibraryReady() {
        libraryReady.value = true
    }

    /**
     * Returns when the loading screen may be removed. The minimum avoids a
     * one-frame flash; network work (RomM, covers) is never waited for.
     */
    suspend fun awaitHandoff(waitForLibrary: Boolean) {
        delay(MINIMUM_MS)
        if (waitForLibrary) {
            withTimeoutOrNull(MAXIMUM_MS - MINIMUM_MS) { libraryReady.first { it } }
        }
    }
}
