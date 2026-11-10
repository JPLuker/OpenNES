package com.swordfish.touchinput.radial.layouts

/**
 * Maximum center movement of the primary dial within its landscape grip.
 * Save files retain normalized [-1, 1] offsets; the editor and renderer
 * convert using the same dimensions so a drag never hits a hidden 72dp cap.
 */
object NesLandscapeDragLimits {
    private const val EDGE_INSET_DP = 10f

    fun horizontalTravelDp(
        sideWidthDp: Float,
        dialWidthDp: Float,
    ): Float =
        ((sideWidthDp - dialWidthDp) / 2f - EDGE_INSET_DP)
            .coerceAtLeast(0f)

    fun verticalTravelDp(
        sideHeightDp: Float,
        dialHeightDp: Float,
    ): Float =
        ((sideHeightDp - dialHeightDp) / 2f - EDGE_INSET_DP)
            .coerceAtLeast(0f)
}
