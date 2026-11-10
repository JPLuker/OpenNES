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
    private var retrySequence = 0L
    private var retryRomHash: String? = null

    @Synchronized
    fun begin(romMd5: String): Long {
        val ticket = ++ticketCounter
        currentTicket = ticket
        retryRomHash = null
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

    /**
     * Persist a generic, non-sensitive activation failure for this ROM until
     * the next session begins. A stale worker cannot modify a newer game.
     */
    @Synchronized
    fun finishFailed(
        ticket: Long,
        romMd5: String,
    ) {
        if (currentTicket != ticket || mutable.value.romMd5 != romMd5) return
        currentTicket = 0L
        mutable.value = Snapshot(romMd5, RetroAchievementsDisplayState.Unavailable)
    }

    /** Only the failed current ROM may request another connection attempt. */
    @Synchronized
    fun requestRetry(romMd5: String): Boolean {
        if (mutable.value.romMd5 != romMd5 ||
            mutable.value.state != RetroAchievementsDisplayState.Unavailable
        ) {
            return false
        }
        retryRomHash = romMd5
        retrySequence += 1L
        return true
    }

    @Synchronized
    fun retryVersion(romMd5: String): Long =
        if (retryRomHash == romMd5) retrySequence else 0L

    @Synchronized
    fun clear(ticket: Long) {
        if (currentTicket != ticket) return
        currentTicket = 0
        mutable.value = Snapshot()
    }
}
