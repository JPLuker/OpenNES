package com.swordfish.lemuroid.app.shared.achievements

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-process view of an actual game-owned rc_client session.
 *
 * GameActivity and the pause menu run in the same :game process. Tickets
 * prevent a late callback/cleanup from an old ROM corrupting the new game's
 * achievement state. Read-only website data never enters this store.
 */
internal object RetroAchievementsGameStateStore {
    data class Snapshot(
        val romMd5: String? = null,
        val state: RetroAchievementsDisplayState = RetroAchievementsDisplayState.NotConnected,
    )

    private val mutable = MutableStateFlow(Snapshot())
    val snapshots: StateFlow<Snapshot> = mutable
    private var ticketCounter = 0L
    private var currentTicket = 0L

    @Synchronized
    fun begin(romMd5: String): Long {
        val ticket = ++ticketCounter
        currentTicket = ticket
        mutable.value = Snapshot(romMd5, RetroAchievementsDisplayState.Connecting)
        return ticket
    }

    @Synchronized
    fun publish(
        ticket: Long,
        romMd5: String,
        state: RetroAchievementsDisplayState,
    ) {
        if (ticket != currentTicket || mutable.value.romMd5 != romMd5) return
        mutable.value = Snapshot(romMd5, state)
    }

    @Synchronized
    fun clear(ticket: Long) {
        if (currentTicket != ticket) return
        currentTicket = 0
        mutable.value = Snapshot()
    }
}
