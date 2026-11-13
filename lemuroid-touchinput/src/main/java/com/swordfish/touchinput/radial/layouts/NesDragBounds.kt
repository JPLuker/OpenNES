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
                deltaPx,
                groupStartPx,
                groupEndPx,
                viewportStartPx,
                viewportEndPx,
                edgeInsetPx,
                safeStartPx,
                safeEndPx,
            )
        if (values.any { !it.isFinite() }) return 0f
        if (viewportEndPx < viewportStartPx || groupEndPx < groupStartPx) return 0f
        val inset = edgeInsetPx.coerceAtLeast(0f)
        val minimum = viewportStartPx + inset + safeStartPx.coerceAtLeast(0f) - groupStartPx
        val maximum = viewportEndPx - inset - safeEndPx.coerceAtLeast(0f) - groupEndPx
        // An oversized control cannot fit between both safe edges. Bound
        // movement at the two edge-alignment positions rather than allowing
        // unbounded movement offscreen. Keep a stationary control stationary
        // when it already straddles both limits.
        if (minimum > maximum) {
            return when {
                deltaPx < maximum -> maximum
                deltaPx > minimum -> minimum
                else -> 0f
            }
        }
        return deltaPx.coerceIn(minimum, maximum)
    }
}
