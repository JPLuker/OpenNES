package com.swordfish.touchinput.radial.layouts

/**
 * Visible drag bounds including camera cutout and navigation-safe insets.
 * All dimensions are pixels in the same root coordinate space.
 */
object NesDragBounds {
    fun clamp(
        deltaPx: Float,
        groupStartPx: Float,
        groupEndPx: Float,
        viewportStartPx: Float,
        viewportEndPx: Float,
        edgeInsetPx: Float,
        safeStartPx: Float,
        safeEndPx: Float,
    ): Float {
        val values =
            listOf(
                deltaPx, groupStartPx, groupEndPx, viewportStartPx, viewportEndPx,
                edgeInsetPx, safeStartPx, safeEndPx,
            )
        if (values.any { !it.isFinite() }) return 0f
        if (viewportEndPx < viewportStartPx || groupEndPx < groupStartPx) return 0f
        val inset = edgeInsetPx.coerceAtLeast(0f)
        val minimum = viewportStartPx + inset + safeStartPx.coerceAtLeast(0f) - groupStartPx
        val maximum = viewportEndPx - inset - safeEndPx.coerceAtLeast(0f) - groupEndPx
        // A huge custom control group cannot fit: keep its prior range rather
        // than using invalid reversed clamp bounds or an abrupt jump.
        return if (minimum <= maximum) deltaPx.coerceIn(minimum, maximum) else deltaPx
    }
}
