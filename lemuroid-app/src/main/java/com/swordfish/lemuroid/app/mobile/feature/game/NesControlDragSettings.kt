package com.swordfish.lemuroid.app.mobile.feature.game

import com.swordfish.touchinput.radial.layouts.NesEditableControl
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager

/**
 * Move exactly one virtual input surface. New dp fields preserve existing
 * normalized fine-tune values for old saved layouts and other orientations.
 */
internal object NesControlDragSettings {
    private const val MAX_ABSOLUTE_OFFSET_DP = 600f

    fun move(
        settings: TouchControllerSettingsManager.Settings,
        control: NesEditableControl,
        deltaXdp: Float,
        deltaYdp: Float,
    ): TouchControllerSettingsManager.Settings {
        if (!deltaXdp.isFinite() || !deltaYdp.isFinite()) return settings
        fun adjust(v: Float, delta: Float): Float =
            (v + delta).coerceIn(-MAX_ABSOLUTE_OFFSET_DP, MAX_ABSOLUTE_OFFSET_DP)
        fun button(
            geometry: TouchControllerSettingsManager.ButtonGeometry,
        ): TouchControllerSettingsManager.ButtonGeometry =
            geometry.copy(
                dragX = adjust(geometry.dragX, deltaXdp),
                dragY = adjust(geometry.dragY, deltaYdp),
            )

        return when (control) {
            NesEditableControl.DPAD ->
                settings.copy(
                    leftDragX = adjust(settings.leftDragX, deltaXdp),
                    leftDragY = adjust(settings.leftDragY, deltaYdp),
                )
            NesEditableControl.A -> settings.copy(aButton = button(settings.aButton))
            NesEditableControl.B -> settings.copy(bButton = button(settings.bButton))
            NesEditableControl.START -> settings.copy(startButton = button(settings.startButton))
            NesEditableControl.SELECT -> settings.copy(selectButton = button(settings.selectButton))
            NesEditableControl.MENU -> settings.copy(menuButton = button(settings.menuButton))
        }
    }
}
