package com.swordfish.lemuroid.app.mobile.feature.game

import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager

/**
 * Determine whether side-mounted controls leave enough width for NES video.
 * Handheld scales its actual PadKit dials to 72% and preserves a 4:3 picture;
 * framed controls retain their minimum 640dp comfort threshold.
 */
internal object NesLandscapeFit {
    fun minimumRequiredWidthDp(
        screenHeightDp: Float,
        leftDialWidthDp: Float,
        rightDialWidthDp: Float,
        handheld: Boolean,
    ): Float {
        val minimumGameWidth =
            if (handheld) {
                maxOf(TouchControllerSettingsManager.LANDSCAPE_MIN_GAME_WIDTH_DP, screenHeightDp * 4f / 3f)
            } else {
                TouchControllerSettingsManager.LANDSCAPE_MIN_GAME_WIDTH_DP
            }
        // Handheld assigns each cluster a whole side zone, with no wasted
        // fixed gutter. The actual PadKit dials are no longer shrunk to 72%.
        val required =
            minimumGameWidth +
                (leftDialWidthDp + rightDialWidthDp) +
                (if (handheld) 0f else TouchControllerSettingsManager.LANDSCAPE_HORIZONTAL_RESERVE_DP)
        return if (handheld) required else maxOf(640f, required)
    }
}
