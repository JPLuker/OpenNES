package com.swordfish.touchinput.radial.layouts

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Rect
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager

enum class NesEditableControl {
    DPAD,
    A,
    B,
    START,
    SELECT,
    MENU,
}

val LocalNesControlBoundsReporter =
    compositionLocalOf<(NesEditableControl, Rect) -> Unit> { { _, _ -> } }

/** Bound each drag handle to the visible PadKit button, not a whole group. */
internal fun faceEditRect(
    dial: Rect,
    baseX: Float,
    geometry: TouchControllerSettingsManager.ButtonGeometry,
): Rect {
    val x =
        (baseX + geometry.offsetX.coerceIn(-1f, 1f) * 0.30f + geometry.dragX / 96f)
            .coerceIn(-1f, 1f)
    val y =
        (geometry.offsetY.coerceIn(-1f, 1f) * 0.40f + geometry.dragY / 96f)
            .coerceIn(-1f, 1f)
    val radius = (dial.width * 0.18f * geometry.size.coerceIn(0.75f, 1.25f)).coerceAtLeast(16f)
    val centerX = dial.center.x + dial.width * 0.35f * x
    val centerY = dial.center.y + dial.height * 0.35f * y
    return Rect(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
}
