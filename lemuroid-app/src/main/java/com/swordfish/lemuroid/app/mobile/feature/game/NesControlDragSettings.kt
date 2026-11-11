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

        return when (control) {
            NesEditableControl.DPAD ->
                settings.copy(
                    leftDragX = adjust(settings.leftDragX, deltaXdp),
                    leftDragY = adjust(settings.leftDragY, deltaYdp),
                )
            NesEditableControl.A -> settings.copy(aButton = movedButton(settings.aButton, deltaXdp, deltaYdp))
            NesEditableControl.B -> settings.copy(bButton = movedButton(settings.bButton, deltaXdp, deltaYdp))
            NesEditableControl.START ->
                settings.copy(
                    startButton = movedButton(settings.startButton, deltaXdp, deltaYdp),
                )
            NesEditableControl.SELECT ->
                settings.copy(
                    selectButton = movedButton(settings.selectButton, deltaXdp, deltaYdp),
                )
            NesEditableControl.MENU -> settings.copy(menuButton = movedButton(settings.menuButton, deltaXdp, deltaYdp))
        }
    }

    private fun adjust(v: Float, delta: Float): Float = (v + delta).coerceIn(-MAX_ABSOLUTE_OFFSET_DP, MAX_ABSOLUTE_OFFSET_DP)

    private fun movedButton(
        geometry: TouchControllerSettingsManager.ButtonGeometry,
        dx: Float,
        dy: Float,
    ): TouchControllerSettingsManager.ButtonGeometry =
        geometry.copy(
            dragX = adjust(geometry.dragX, dx),
            dragY = adjust(geometry.dragY, dy),
        )
}
