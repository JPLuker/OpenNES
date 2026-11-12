package com.swordfish.lemuroid.app.shared.game

import com.swordfish.libretrodroid.Controller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NesZapperControllerTest {
    @Test
    fun selectsZapperOnlyFromPortTwo() {
        val controllers =
            arrayOf(
                arrayOf(Controller(1, "Zapper"), Controller(2, "Gamepad")),
                arrayOf(Controller(3, "Gamepad"), Controller(258, "Zapper")),
            )
        assertEquals(258, NesZapperController.portTwoZapperId(controllers))
    }

    @Test
    fun neverRemapsFirstPortOrUnknownDevice() {
        assertNull(NesZapperController.portTwoZapperId(arrayOf(arrayOf(Controller(258, "Zapper")))))
        assertNull(
            NesZapperController.portTwoZapperId(
                arrayOf(
                    arrayOf(Controller(1, "Joypad")),
                    arrayOf(Controller(2, "Gamepad")),
                ),
            ),
        )
        assertNull(NesZapperController.portTwoZapperId(emptyArray()))
    }

    @Test
    fun excludesInvalidNativeControllerId() {
        assertNull(
            NesZapperController.portTwoZapperId(
                arrayOf(
                    arrayOf(Controller(1, "Joypad")),
                    arrayOf(Controller(-1, "Zapper")),
                ),
            ),
        )
    }

    @Test
    fun perRomKeysNeverContainFilenameOrOverlap() {
        assertEquals("zapper_game_1", NesZapperPreferences.keyFor(1))
        assertEquals("zapper_game_41", NesZapperPreferences.keyFor(41))
        try {
            NesZapperPreferences.keyFor(0)
            throw AssertionError("Invalid ID must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: no fallback to a shared or all-games preference key.
        }
    }
}
