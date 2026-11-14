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
    data class UnlockNotice(
        val id: Int,
        val title: String,
        val sequence: Long,
        // Small heading above the title; null for an achievement unlock.
        val label: String? = null,
    )

    data class Snapshot(
        val romMd5: String? = null,
        val state: RetroAchievementsDisplayState = RetroAchievementsDisplayState.NotConnected,
        val notice: UnlockNotice? = null,
        // Live challenge and leaderboard indicators, keyed so they can be removed.
        val indicators: Map<String, String> = emptyMap(),
    )

    private val mutable = MutableStateFlow(Snapshot())
    val snapshots: StateFlow<Snapshot> = mutable
    private var ticketCounter = 0L
    private var currentTicket = 0L
    private var retrySequence = 0L
    private var retryRomHash: String? = null
    private var unlockSequence = 0L
    private val notifiedAchievementIds = mutableSetOf<Int>()

    @Synchronized
    fun begin(romMd5: String): Long {
        val ticket = ++ticketCounter
        currentTicket = ticket
        retryRomHash = null
        notifiedAchievementIds.clear()
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
        mutable.value = mutable.value.copy(state = state)
    }

    /**
     * An unlock banner can only originate from the current native rc_client
     * game ticket. Never generate it from cached or website achievements.
     */
    @Synchronized
    fun notifyUnlock(
        ticket: Long,
        romMd5: String,
        achievementId: Int,
        title: String,
    ): Boolean {
        if (ticket != currentTicket || mutable.value.romMd5 != romMd5 ||
            achievementId <= 0 || title.isBlank() || title.any(Char::isISOControl)
        ) {
            return false
        }
        if (!notifiedAchievementIds.add(achievementId)) return false
        val notice = UnlockNotice(achievementId, title.take(220), ++unlockSequence)
        mutable.value = mutable.value.copy(notice = notice)
        return true
    }

    /** A short message from the current native session (mode, leaderboard, progress). */
    @Synchronized
    fun notifyMessage(
        ticket: Long,
        romMd5: String,
        message: String,
        label: String? = null,
    ): Boolean {
        if (ticket != currentTicket || mutable.value.romMd5 != romMd5 ||
            message.isBlank() || message.any(Char::isISOControl)
        ) {
            return false
        }
        mutable.value =
            mutable.value.copy(
                notice = UnlockNotice(0, message.take(220), ++unlockSequence, label?.take(60)),
            )
        return true
    }

    /** Show ([text] set) or remove ([text] null) one live indicator. */
    @Synchronized
    fun setIndicator(
        ticket: Long,
        romMd5: String,
        key: String,
        text: String?,
    ) {
        if (ticket != currentTicket || mutable.value.romMd5 != romMd5) return
        val current = mutable.value.indicators
        val next =
            if (text.isNullOrBlank()) {
                current - key
            } else if (current.size >= MAX_INDICATORS && key !in current) {
                current
            } else {
                current + (key to text.take(80))
            }
        if (next != current) mutable.value = mutable.value.copy(indicators = next)
    }

    private const val MAX_INDICATORS = 6

    /** A delayed dismissal must not dismiss a newer unlock or newer ROM. */
    @Synchronized
    fun dismissUnlock(sequence: Long) {
        if (mutable.value.notice?.sequence != sequence) return
        mutable.value = mutable.value.copy(notice = null)
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
        notifiedAchievementIds.clear()
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
    fun retryVersion(romMd5: String): Long = if (retryRomHash == romMd5) retrySequence else 0L

    @Synchronized
    fun clear(ticket: Long) {
        if (currentTicket != ticket) return
        currentTicket = 0
        notifiedAchievementIds.clear()
        mutable.value = Snapshot()
    }
}
