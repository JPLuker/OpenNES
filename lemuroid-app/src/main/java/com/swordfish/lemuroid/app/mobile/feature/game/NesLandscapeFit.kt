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
        val dialFactor = if (handheld) 0.72f else 1f
        val required =
            minimumGameWidth +
                TouchControllerSettingsManager.LANDSCAPE_HORIZONTAL_RESERVE_DP +
                (leftDialWidthDp + rightDialWidthDp) * dialFactor
        return if (handheld) required else maxOf(640f, required)
    }
}
