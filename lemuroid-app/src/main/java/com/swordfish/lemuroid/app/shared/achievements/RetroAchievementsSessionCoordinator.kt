package com.swordfish.lemuroid.app.shared.achievements

/**
 * A fail-closed boundary between future native rc_client callbacks and the UI.
 *
 * Only the authenticated native bridge may call confirmation methods. The
 * read-only website API and local ROM hash must never construct an Active
 * session. Each event is tied to a new game generation to reject late HTTP
 * callbacks from a previously unloaded ROM.
 *
 * This class does not perform login, issue awards, or contact the server.
 */
internal class RetroAchievementsSessionCoordinator {
    data class Ticket(
        val generation: Long,
        val romMd5: String,
    )

    private enum class Phase {
        DISCONNECTED,
        IDENTIFYING,
        AUTHENTICATING,
        LOADING,
        ACTIVE,
        OFFLINE,
        NO_MATCH,
    }

    private var generation = 0L
    private var ticket: Ticket? = null
    private var phase = Phase.DISCONNECTED
    private var username: String? = null
    private var nativeGameId: Int? = null
    private val achievements = linkedMapOf<Int, VerifiedAchievement>()

    @Synchronized
    fun beginGame(rawMd5: String): Ticket {
        val hash = rawMd5.lowercase()
        require(hash.matches(MD5_PATTERN)) { "A verified NES ROM fingerprint is required" }
        generation += 1L
        val next = Ticket(generation, hash)
        ticket = next
        username = null
        nativeGameId = null
        achievements.clear()
        phase = Phase.IDENTIFYING
        return next
    }

    @Synchronized
    fun beginAuthentication(request: Ticket): Boolean {
        if (!isCurrent(request) || phase != Phase.IDENTIFYING) return false
        phase = Phase.AUTHENTICATING
        return true
    }

    @Synchronized
    fun confirmAuthenticated(
        request: Ticket,
        confirmedUsername: String,
    ): Boolean {
        if (!isCurrent(request) || phase != Phase.AUTHENTICATING) return false
        val name = confirmedUsername.trim()
        if (name.isEmpty() || name.length > 80 || name.any { it.isISOControl() }) return false
        username = name
        phase = Phase.LOADING
        return true
    }

    /** A native game ID and matching official hash are mandatory. */
    @Synchronized
    fun confirmGameActivated(
        request: Ticket,
        confirmedRomMd5: String,
        confirmedGameId: Int,
        verifiedAchievements: List<VerifiedAchievement>,
    ): Boolean {
        if (!isCurrent(request) || phase != Phase.LOADING || username == null) return false
        if (!confirmedRomMd5.equals(request.romMd5, ignoreCase = true) || confirmedGameId <= 0) return false
        if (verifiedAchievements.any { it.id <= 0 || it.points < 0 }) return false
        if (verifiedAchievements.map { it.id }.toSet().size != verifiedAchievements.size) return false
        nativeGameId = confirmedGameId
        achievements.clear()
        verifiedAchievements.forEach { achievements[it.id] = it }
        phase = Phase.ACTIVE
        return true
    }

    /** Only an actual native unlock confirmation for the active game may modify progress. */
    @Synchronized
    fun confirmUnlocked(
        request: Ticket,
        confirmedGameId: Int,
        achievementId: Int,
    ): Boolean {
        if (!isCurrent(request) || phase != Phase.ACTIVE || nativeGameId != confirmedGameId) return false
        val existing = achievements[achievementId] ?: return false
        if (existing.unlocked) return false
        achievements[achievementId] = existing.copy(unlocked = true, progressPercent = null)
        return true
    }

    @Synchronized
    fun noMatchingGame(request: Ticket): Boolean {
        if (!isCurrent(request) || phase != Phase.LOADING) return false
        username = null
        nativeGameId = null
        achievements.clear()
        phase = Phase.NO_MATCH
        return true
    }

    /** Never display stale awards as a live authenticated session during an outage. */
    @Synchronized
    fun transportLost(request: Ticket): Boolean {
        if (!isCurrent(request) || phase == Phase.DISCONNECTED) return false
        username = null
        nativeGameId = null
        achievements.clear()
        phase = Phase.OFFLINE
        return true
    }

    /** Reauthentication is required after connectivity returns; do not resume cached awards. */
    @Synchronized
    fun restartAuthentication(request: Ticket): Boolean {
        if (!isCurrent(request) || phase != Phase.OFFLINE) return false
        phase = Phase.AUTHENTICATING
        return true
    }

    @Synchronized
    fun stop(request: Ticket) {
        if (!isCurrent(request)) return
        ticket = null
        username = null
        nativeGameId = null
        achievements.clear()
        phase = Phase.DISCONNECTED
    }

    @Synchronized
    fun displayState(): RetroAchievementsDisplayState =
        when (phase) {
            Phase.DISCONNECTED, Phase.IDENTIFYING, Phase.OFFLINE -> RetroAchievementsDisplayState.NotConnected
            Phase.AUTHENTICATING, Phase.LOADING -> RetroAchievementsDisplayState.Connecting
            Phase.NO_MATCH -> RetroAchievementsDisplayState.NoMatchingGame
            Phase.ACTIVE -> {
                val activeTicket = requireNotNull(ticket)
                val activeUsername = requireNotNull(username)
                RetroAchievementsDisplayState.Active(
                    romMd5 = activeTicket.romMd5,
                    username = activeUsername,
                    achievements = achievements.values.toList(),
                )
            }
        }

    private fun isCurrent(request: Ticket) = ticket == request

    companion object {
        private val MD5_PATTERN = Regex("[0-9a-f]{32}")
    }
}
