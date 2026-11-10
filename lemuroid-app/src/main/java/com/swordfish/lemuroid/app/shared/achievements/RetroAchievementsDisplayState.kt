package com.swordfish.lemuroid.app.shared.achievements

/**
 * Screen-ready data contract for authenticated, verified rc_client game sessions.
 * Only the game-owned native session may emit Active after authenticated
 * game loading and a readable emulator memory map. HTTP-only metadata and
 * a local ROM fingerprint cannot create an active award session.
 */
sealed interface RetroAchievementsDisplayState {
    data object NotConnected : RetroAchievementsDisplayState

    data object Connecting : RetroAchievementsDisplayState

    data object NoMatchingGame : RetroAchievementsDisplayState

    data class Active(
        val romMd5: String,
        val username: String,
        val achievements: List<VerifiedAchievement>,
    ) : RetroAchievementsDisplayState
}

data class VerifiedAchievement(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    val unlocked: Boolean,
    val progressPercent: Int? = null,
)
