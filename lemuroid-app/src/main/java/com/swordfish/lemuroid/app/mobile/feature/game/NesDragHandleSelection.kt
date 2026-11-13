package com.swordfish.lemuroid.app.mobile.feature.game

import com.swordfish.touchinput.radial.layouts.NesEditableControl

/**
 * Prevent group hit areas from stealing gestures intended for real controls.
 * Individual face-button geometry can arrive one frame before its sibling.
 */
internal object NesDragHandleSelection {
    fun visibleControls(reported: Set<NesEditableControl>): List<NesEditableControl> =
        NesEditableControl.entries.filter { control ->
            when (control) {
                NesEditableControl.DPAD_UP,
                NesEditableControl.DPAD_DOWN,
                NesEditableControl.DPAD_LEFT,
                NesEditableControl.DPAD_RIGHT,
                -> false
                NesEditableControl.FACE_BUTTONS ->
                    NesEditableControl.A !in reported && NesEditableControl.B !in reported
                else -> true
            }
        }
}
