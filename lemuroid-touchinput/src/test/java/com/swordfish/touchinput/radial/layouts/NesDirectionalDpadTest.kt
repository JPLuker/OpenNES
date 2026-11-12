package com.swordfish.touchinput.radial.layouts

import android.view.KeyEvent
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.ids.Id
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NesDirectionalDpadTest {
    @Test
    fun uneditedTargetsRemainInTheOriginalFourCardinalArms() {
        val geometry = TouchControllerSettingsManager.ButtonGeometry()
        val up = nesDpadAnchor(Id.Key(KeyEvent.KEYCODE_DPAD_UP), 0f, -0.72f, geometry)
        val down = nesDpadAnchor(Id.Key(KeyEvent.KEYCODE_DPAD_DOWN), 0f, 0.72f, geometry)
        val left = nesDpadAnchor(Id.Key(KeyEvent.KEYCODE_DPAD_LEFT), -0.72f, 0f, geometry)
        val right = nesDpadAnchor(Id.Key(KeyEvent.KEYCODE_DPAD_RIGHT), 0.72f, 0f, geometry)
        assertEquals(-0.72f, up.position.y, 0.001f)
        assertEquals(0.72f, down.position.y, 0.001f)
        assertEquals(-0.72f, left.position.x, 0.001f)
        assertEquals(0.72f, right.position.x, 0.001f)
        assertEquals(0.38f, up.size, 0.001f)
    }

    @Test
    fun directionalCalibrationIsBoundedAndCannotLeavePadSpace() {
        val geometry =
            TouchControllerSettingsManager.ButtonGeometry(
                size = 99f,
                offsetX = 50f,
                offsetY = -50f,
                dragX = 500f,
                dragY = -500f,
            )
        val anchor = nesDpadAnchor(Id.Key(KeyEvent.KEYCODE_DPAD_RIGHT), 0.72f, 0f, geometry)
        assertTrue(anchor.position.x <= 1f)
        assertTrue(anchor.position.y >= -1f)
        assertEquals(0.38f * 1.25f, anchor.size, 0.001f)
    }

    @Test
    fun oneThumbDiagonalIncludesBothNeighboringDirections() {
        val geometry = TouchControllerSettingsManager.ButtonGeometry()
        val upId = Id.Key(KeyEvent.KEYCODE_DPAD_UP)
        val rightId = Id.Key(KeyEvent.KEYCODE_DPAD_RIGHT)
        val up = nesDpadAnchor(upId, 0f, -0.72f, geometry)
        val right = nesDpadAnchor(rightId, 0.72f, 0f, geometry)
        val diagonal = nesDpadDiagonal(up, right)
        assertEquals(0.54f, diagonal.position.x, 0.001f)
        assertEquals(-0.54f, diagonal.position.y, 0.001f)
        assertTrue(diagonal.buttons.contains(upId))
        assertTrue(diagonal.buttons.contains(rightId))
    }
}
