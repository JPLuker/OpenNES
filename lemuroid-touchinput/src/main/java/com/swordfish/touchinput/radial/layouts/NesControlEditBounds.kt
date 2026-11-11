package com.swordfish.touchinput.radial.layouts

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Rect

enum class NesEditableControl {
    DPAD,
    A,
    B,
    START,
    SELECT,
    MENU,
}

/** Real rendered control bounds shared with the non-interactive drag editor. */
val LocalNesControlBoundsReporter =
    compositionLocalOf<(NesEditableControl, Rect) -> Unit> { { _, _ -> } }
