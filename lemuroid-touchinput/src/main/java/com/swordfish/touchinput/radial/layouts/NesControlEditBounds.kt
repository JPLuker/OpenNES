package com.swordfish.touchinput.radial.layouts

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Rect

enum class NesEditableControl {
    DPAD,
    FACE_BUTTONS,
    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT,
    A,
    B,
    START,
    SELECT,
    MENU,
}

/** Real rendered control bounds shared with the non-interactive drag editor. */
val LocalNesDpadEditMode = compositionLocalOf { false }

val LocalNesControlBoundsReporter =
    compositionLocalOf<(NesEditableControl, Rect) -> Unit> { { _, _ -> } }
