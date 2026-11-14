package com.swordfish.lemuroid.app.mobile.feature.main

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Decides how long the opening loading screen stays up.
 *
 * It covers the first library snapshot, the RomM catalogue and the covers of
 * the first shelves, so the library is complete when it appears. It is always
 * bounded: a slow database, server or image host cannot hold the screen.
 */
object OpenNesStartupGate {
    const val MINIMUM_MS = 1_000L
    const val MAXIMUM_MS = 8_000L

    private val libraryReady = MutableStateFlow(false)
    private val remoteReady = MutableStateFlow(false)
    private val coversReady = MutableStateFlow(false)

    /** The covers of the first shelves are decoded and ready to draw. */
    fun markCoversReady() {
        coversReady.value = true
    }

    fun markRemoteReady() {
        remoteReady.value = true
    }

    /** The library has produced its first real snapshot in this process. */
    fun markLibraryReady() {
        libraryReady.value = true
    }

    /**
     * Returns when the loading screen may be removed. The minimum avoids a
     * one-frame flash; the maximum bounds all network work.
     */
    suspend fun awaitHandoff(waitForLibrary: Boolean) {
        delay(MINIMUM_MS)
        if (waitForLibrary) {
            withTimeoutOrNull(MAXIMUM_MS - MINIMUM_MS) {
                combine(libraryReady, remoteReady, coversReady) { library, remote, covers ->
                    library && remote && covers
                }
                    .first { it }
            }
        }
    }
}
