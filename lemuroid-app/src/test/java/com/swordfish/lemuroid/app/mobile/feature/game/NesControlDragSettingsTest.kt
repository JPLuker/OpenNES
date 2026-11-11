package com.swordfish.lemuroid.app.mobile.feature.game

import com.swordfish.touchinput.radial.layouts.NesEditableControl
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import org.junit.Assert.assertEquals
import org.junit.Test

class NesControlDragSettingsTest {
    @Test
    fun everyTargetMovesWithoutRepositioningOtherFive() {
        val original = TouchControllerSettingsManager.Settings()
        val controls = NesEditableControl.entries
        for (target in controls) {
            val changed = NesControlDragSettings.move(original, target, 31f, -17f)
            assertEquals(31f, offsetX(changed, target), 0.001f)
            assertEquals(-17f, offsetY(changed, target), 0.001f)
            for (other in controls.filterNot { it == target }) {
                assertEquals(0f, offsetX(changed, other), 0.001f)
                assertEquals(0f, offsetY(changed, other), 0.001f)
            }
        }
    }

    @Test
    fun preservesExistingFineTuneAndRejectsInvalidMovement() {
        val saved =
            TouchControllerSettingsManager.Settings(
                leftOffsetX = 0.4f,
                aButton = TouchControllerSettingsManager.ButtonGeometry(offsetX = -0.2f),
            )
        val moved = NesControlDragSettings.move(saved, NesEditableControl.A, 41f, 20f)
        assertEquals(0.4f, moved.leftOffsetX, 0.001f)
        assertEquals(-0.2f, moved.aButton.offsetX, 0.001f)
        assertEquals(saved, NesControlDragSettings.move(saved, NesEditableControl.A, Float.NaN, 5f))
    }

    private fun offsetX(
        settings: TouchControllerSettingsManager.Settings,
        control: NesEditableControl,
    ): Float =
        when (control) {
            NesEditableControl.DPAD -> settings.leftDragX
            NesEditableControl.A -> settings.aButton.dragX
            NesEditableControl.B -> settings.bButton.dragX
            NesEditableControl.START -> settings.startButton.dragX
            NesEditableControl.SELECT -> settings.selectButton.dragX
            NesEditableControl.MENU -> settings.menuButton.dragX
        }

    private fun offsetY(
        settings: TouchControllerSettingsManager.Settings,
        control: NesEditableControl,
    ): Float =
        when (control) {
            NesEditableControl.DPAD -> settings.leftDragY
            NesEditableControl.A -> settings.aButton.dragY
            NesEditableControl.B -> settings.bButton.dragY
            NesEditableControl.START -> settings.startButton.dragY
            NesEditableControl.SELECT -> settings.selectButton.dragY
            NesEditableControl.MENU -> settings.menuButton.dragY
        }
}
