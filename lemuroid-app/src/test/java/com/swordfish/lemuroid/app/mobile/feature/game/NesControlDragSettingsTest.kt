package com.swordfish.lemuroid.app.mobile.feature.game

import com.swordfish.touchinput.radial.layouts.NesEditableControl
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import org.junit.Assert.assertEquals
import org.junit.Test

class NesControlDragSettingsTest {
    @Test
    fun everyTargetMovesWithoutRepositioningOtherTargets() {
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

    @Test
    fun directionHitboxCalibrationIsOptInAndBounded() {
        val original = TouchControllerSettingsManager.Settings()
        assertEquals(false, original.individualDpadHitboxes)
        val adjusted = NesControlDragSettings.move(original, NesEditableControl.DPAD_UP, 500f, -500f)
        assertEquals(true, adjusted.individualDpadHitboxes)
        assertEquals(32f, adjusted.dpadUp.dragX, 0.001f)
        assertEquals(-32f, adjusted.dpadUp.dragY, 0.001f)
        assertEquals(original.dpadDown, adjusted.dpadDown)
        assertEquals(original.dpadLeft, adjusted.dpadLeft)
        assertEquals(original.dpadRight, adjusted.dpadRight)
        assertEquals(0f, adjusted.leftDragX, 0.001f)
    }

    @Test
    fun faceButtonDragsSavedByTheClippedDialLayoutAreDroppedOnce() {
        // Old builds divided A/B drags by 96 and clipped them to the dial, so
        // stored values could grow far beyond anything shown on screen.
        val legacy =
            TouchControllerSettingsManager.Settings(
                rightDragX = 12f,
                aButton = TouchControllerSettingsManager.ButtonGeometry(size = 1.1f, offsetX = 0.3f, dragX = 900f),
                bButton = TouchControllerSettingsManager.ButtonGeometry(dragY = -700f),
                startButton = TouchControllerSettingsManager.ButtonGeometry(dragX = 40f),
            )
        val current = legacy.withCurrentFaceLayout()
        assertEquals(TouchControllerSettingsManager.FACE_LAYOUT_VERSION, current.faceLayoutVersion)
        assertEquals(0f, current.aButton.dragX, 0f)
        assertEquals(0f, current.bButton.dragY, 0f)
        // Everything that was displayed correctly is kept.
        assertEquals(1.1f, current.aButton.size, 0f)
        assertEquals(0.3f, current.aButton.offsetX, 0f)
        assertEquals(40f, current.startButton.dragX, 0f)
        assertEquals(12f, current.rightDragX, 0f)
        assertEquals(current, current.withCurrentFaceLayout())
    }

    @Test
    fun aNewFaceButtonDragSurvivesTheNextLoad() {
        val dragged =
            NesControlDragSettings.move(
                TouchControllerSettingsManager.Settings(),
                NesEditableControl.B,
                -120f,
                64f,
            )
        val reloaded = dragged.withCurrentFaceLayout()
        assertEquals(-120f, reloaded.bButton.dragX, 0f)
        assertEquals(64f, reloaded.bButton.dragY, 0f)
    }

    private fun offsetX(
        settings: TouchControllerSettingsManager.Settings,
        control: NesEditableControl,
    ): Float =
        when (control) {
            NesEditableControl.DPAD -> settings.leftDragX
            NesEditableControl.FACE_BUTTONS -> settings.rightDragX
            NesEditableControl.DPAD_UP -> settings.dpadUp.dragX
            NesEditableControl.DPAD_DOWN -> settings.dpadDown.dragX
            NesEditableControl.DPAD_LEFT -> settings.dpadLeft.dragX
            NesEditableControl.DPAD_RIGHT -> settings.dpadRight.dragX
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
            NesEditableControl.FACE_BUTTONS -> settings.rightDragY
            NesEditableControl.DPAD_UP -> settings.dpadUp.dragY
            NesEditableControl.DPAD_DOWN -> settings.dpadDown.dragY
            NesEditableControl.DPAD_LEFT -> settings.dpadLeft.dragY
            NesEditableControl.DPAD_RIGHT -> settings.dpadRight.dragY
            NesEditableControl.A -> settings.aButton.dragY
            NesEditableControl.B -> settings.bButton.dragY
            NesEditableControl.START -> settings.startButton.dragY
            NesEditableControl.SELECT -> settings.selectButton.dragY
            NesEditableControl.MENU -> settings.menuButton.dragY
        }
}
