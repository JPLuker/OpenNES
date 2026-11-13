package com.swordfish.lemuroid.app.mobile.feature.game

import com.swordfish.touchinput.radial.layouts.NesEditableControl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NesDragHandleSelectionTest {
    @Test
    fun groupHandleOnlyAppearsWithoutIndividualButtonMeasurements() {
        val empty = NesDragHandleSelection.visibleControls(emptySet())
        assertTrue(NesEditableControl.FACE_BUTTONS in empty)
        for (measured in listOf(NesEditableControl.A, NesEditableControl.B)) {
            val visible = NesDragHandleSelection.visibleControls(setOf(measured))
            assertFalse(NesEditableControl.FACE_BUTTONS in visible)
            assertTrue(measured in visible)
        }
    }

    @Test
    fun dpadCalibrationHitboxesAreNotVisualDragHandles() {
        val visible = NesDragHandleSelection.visibleControls(NesEditableControl.entries.toSet())
        assertTrue(NesEditableControl.DPAD in visible)
        assertTrue(NesEditableControl.A in visible)
        assertTrue(NesEditableControl.B in visible)
        assertFalse(NesEditableControl.FACE_BUTTONS in visible)
        assertFalse(NesEditableControl.DPAD_UP in visible)
        assertFalse(NesEditableControl.DPAD_DOWN in visible)
        assertFalse(NesEditableControl.DPAD_LEFT in visible)
        assertFalse(NesEditableControl.DPAD_RIGHT in visible)
    }
}
