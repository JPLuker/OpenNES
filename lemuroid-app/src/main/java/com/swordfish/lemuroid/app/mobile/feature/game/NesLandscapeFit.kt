package com.swordfish.lemuroid.app.mobile.feature.game

/**
 * Both framed and handheld side-panel layouts must use equal grip widths.
 * A centered 4:3 viewport takes priority; never shrink it to accommodate
 * a radial secondary-button ring. On truly narrow screens, prefer overlay.
 */
internal object NesLandscapeFit {
    private const val NES_ASPECT_RATIO = 4f / 3f
    private const val MINIMUM_TOUCH_GRIP_DP = 120f

    fun sideWidthDp(
        screenWidthDp: Float,
        screenHeightDp: Float,
    ): Float = ((screenWidthDp - screenHeightDp * NES_ASPECT_RATIO) / 2f).coerceAtLeast(0f)

    fun canShowSideControls(
        screenWidthDp: Float,
        screenHeightDp: Float,
    ): Boolean =
        screenWidthDp.isFinite() &&
            screenHeightDp.isFinite() &&
            screenWidthDp > 0f &&
            screenHeightDp > 0f &&
            sideWidthDp(screenWidthDp, screenHeightDp) >= MINIMUM_TOUCH_GRIP_DP
}
