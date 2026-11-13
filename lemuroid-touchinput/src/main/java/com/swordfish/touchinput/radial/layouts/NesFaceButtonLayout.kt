package com.swordfish.touchinput.radial.layouts

import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import kotlin.math.hypot

/**
 * Pixel geometry for the A and B buttons once either has been dragged in the
 * controller editor.
 *
 * PadKit only accepts touches inside a control's own rectangle, so a button
 * cannot leave its dial by changing an anchor. Dragged buttons are therefore
 * laid out here in real pixels and handed to PadKit as controls placed at
 * those pixels: the painted disc and the touch target always share one box.
 *
 * All coordinates are pixels relative to the top-left of the original dial.
 */
object NesFaceButtonLayout {
    const val A_BASE_X = 0.92f
    const val B_BASE_X = -0.92f

    /** Touch target diameter relative to the painted box of a separate button. */
    const val SEPARATE_HIT_SCALE = 1.2f

    // Matches the untouched dial: 0.92 anchors and 0.46 sizes give a dial
    // 1.045 times wider than the pair of buttons it contains.
    private const val CLUSTER_PADDING = 1.045f

    // Beyond this center distance (in mean button diameters) one thumb can
    // no longer cover both buttons, so each becomes its own touch target
    // and the space between them stops reacting to touches.
    private const val CLUSTER_MAX_SEPARATION = 1.6f
    private const val MAX_DRAG_DP = 4096f
    private const val COMPOSITE_DISTANCE = 0.85f

    data class Button(
        val centerX: Float,
        val centerY: Float,
        val diameter: Float,
    )

    /** Visible area a button must stay inside, in the same dial-relative pixels. */
    data class Bounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )

    sealed interface Arrangement

    /**
     * Both buttons share one PadKit face-button control, which keeps the
     * nearest-button touch behavior and the one-thumb A+B zones.
     * Anchor values are in PadKit's normalized (-1..1) control space.
     */
    data class Cluster(
        val left: Float,
        val top: Float,
        val side: Float,
        val aAnchorX: Float,
        val aAnchorY: Float,
        val aSize: Float,
        val bAnchorX: Float,
        val bAnchorY: Float,
        val bSize: Float,
        val compositeX: Float,
        val compositeY: Float,
    ) : Arrangement

    data class Separate(
        val a: Button,
        val b: Button,
    ) : Arrangement

    fun isIndependent(
        a: TouchControllerSettingsManager.ButtonGeometry,
        b: TouchControllerSettingsManager.ButtonGeometry,
    ): Boolean = drag(a.dragX) != 0f || drag(a.dragY) != 0f || drag(b.dragX) != 0f || drag(b.dragY) != 0f

    fun sizeFraction(geometry: TouchControllerSettingsManager.ButtonGeometry): Float =
        (0.46f * finite(geometry.size, 1f).coerceIn(0.75f, 1.25f)).coerceIn(0.34f, 0.58f)

    fun anchorX(
        baseX: Float,
        geometry: TouchControllerSettingsManager.ButtonGeometry,
    ): Float = (baseX + finite(geometry.offsetX, 0f).coerceIn(-1f, 1f) * 0.30f).coerceIn(-1f, 1f)

    fun anchorY(geometry: TouchControllerSettingsManager.ButtonGeometry): Float =
        (finite(geometry.offsetY, 0f).coerceIn(-1f, 1f) * 0.40f).coerceIn(-1f, 1f)

    /**
     * Where one button is painted and touched. With zero drag this is exactly
     * where PadKit places the same anchor in the untouched dial, so the first
     * drag never makes a button jump.
     */
    fun button(
        dialPx: Float,
        density: Float,
        baseX: Float,
        geometry: TouchControllerSettingsManager.ButtonGeometry,
        bounds: Bounds?,
    ): Button {
        val dial = finite(dialPx, 0f).coerceAtLeast(0f)
        val scale = finite(density, 1f).coerceAtLeast(0f)
        val diameter = dial * sizeFraction(geometry)
        val travel = (dial - diameter) / 2f
        val x = dial / 2f + anchorX(baseX, geometry) * travel + drag(geometry.dragX) * scale
        val y = dial / 2f + anchorY(geometry) * travel + drag(geometry.dragY) * scale
        if (bounds == null) return Button(x, y, diameter)
        return Button(
            centerX = clampCenter(x, diameter / 2f, bounds.left, bounds.right),
            centerY = clampCenter(y, diameter / 2f, bounds.top, bounds.bottom),
            diameter = diameter,
        )
    }

    /** Keep a whole button visible; center it when the area is too small. */
    fun clampCenter(
        center: Float,
        halfExtent: Float,
        start: Float,
        end: Float,
    ): Float {
        if (!start.isFinite() || !end.isFinite() || end <= start) return center
        if (end - start < halfExtent * 2f) return (start + end) / 2f
        return center.coerceIn(start + halfExtent, end - halfExtent)
    }

    fun arrange(
        a: Button,
        b: Button,
    ): Arrangement {
        val dx = a.centerX - b.centerX
        val dy = a.centerY - b.centerY
        val separation = hypot(dx, dy)
        val meanDiameter = (a.diameter + b.diameter) / 2f
        val largest = maxOf(a.diameter, b.diameter)
        if (meanDiameter <= 0f || separation > meanDiameter * CLUSTER_MAX_SEPARATION) {
            return Separate(a, b)
        }

        // A square around both buttons. Its inscribed circle (PadKit's touch
        // limit) contains both discs for any direction between them.
        val side = (separation + largest) * CLUSTER_PADDING
        val midX = (a.centerX + b.centerX) / 2f
        val midY = (a.centerY + b.centerY) / 2f
        val aTravel = (side - a.diameter) / 2f
        val bTravel = (side - b.diameter) / 2f
        // Unit vector from B to A; a coincident pair falls back to the
        // normal horizontal arrangement.
        val ux = if (separation > 0f) dx / separation else 1f
        val uy = if (separation > 0f) dy / separation else 0f
        return Cluster(
            left = midX - side / 2f,
            top = midY - side / 2f,
            side = side,
            aAnchorX = normalized(a.centerX - midX, aTravel),
            aAnchorY = normalized(a.centerY - midY, aTravel),
            aSize = a.diameter / side,
            bAnchorX = normalized(b.centerX - midX, bTravel),
            bAnchorY = normalized(b.centerY - midY, bTravel),
            bSize = b.diameter / side,
            // A+B zones sit either side of the line joining the buttons.
            compositeX = -uy * COMPOSITE_DISTANCE,
            compositeY = ux * COMPOSITE_DISTANCE,
        )
    }

    private fun normalized(
        offset: Float,
        travel: Float,
    ): Float = if (travel > 0f) (offset / travel).coerceIn(-1f, 1f) else 0f

    private fun drag(value: Float): Float = finite(value, 0f).coerceIn(-MAX_DRAG_DP, MAX_DRAG_DP)

    private fun finite(
        value: Float,
        fallback: Float,
    ): Float = if (value.isFinite()) value else fallback
}
