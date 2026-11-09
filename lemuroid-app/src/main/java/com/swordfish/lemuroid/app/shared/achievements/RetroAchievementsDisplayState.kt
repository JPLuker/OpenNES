package com.swordfish.lemuroid.app.shared.achievements

/**
 * Screen-ready data contract for authenticated, verified rc_client game sessions.
 * The current emulator never emits Active until the native runtime exists.
 * Neither HTTP-only metadata nor local MD5 by themselves may create Active.
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
