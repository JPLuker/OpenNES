package com.swordfish.lemuroid.app.mobile.feature.main

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Decides how long the opening loading screen stays up.
 *
 * It stays until the library, the RomM catalogue and every cover in the
 * library have loaded, so nothing pops in after it lifts. A hard limit keeps
 * an offline server or image host from trapping the player on it.
 */
object OpenNesStartupGate {
    const val MINIMUM_MS = 1_000L
    const val MAXIMUM_MS = 20_000L

    private val libraryReady = MutableStateFlow(false)
    private val remoteReady = MutableStateFlow(false)
    private val coversReady = MutableStateFlow(false)

    /** Covers loaded so far and in total, for the loading screen's counter. */
    data class CoverProgress(val done: Int, val total: Int)

    private val coverProgressState = MutableStateFlow(CoverProgress(0, 0))
    val coverProgress: StateFlow<CoverProgress> = coverProgressState

    fun startCovers(total: Int) {
        coverProgressState.value = CoverProgress(0, total)
    }

    fun coverDone() {
        coverProgressState.update { it.copy(done = (it.done + 1).coerceAtMost(it.total)) }
    }

    /**
     * A new library screen is starting. Its covers must be ready again: the
     * flag would otherwise stay set from an earlier screen in this process,
     * after Android may have dropped the decoded images.
     */
    fun beginHandoff() {
        coversReady.value = false
        coverProgressState.value = CoverProgress(0, 0)
    }

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
