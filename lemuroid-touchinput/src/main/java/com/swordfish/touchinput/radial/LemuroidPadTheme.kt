package com.swordfish.touchinput.radial

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Original OpenNES controller materials: warm gray faceplate, raised charcoal
 * controls and deep-red action buttons. This module renders NES controls only.
 *
 * Visual opacity never affects PadKit's touch-hit regions or event mapping.
 */
class LemuroidPadTheme(opacity: Float = 1f) {
    val opacity: Float = opacity.coerceIn(0.35f, 1f)

    val foregroundPadding: Dp = 6.dp
    val padding: Dp = 4.dp

    private val iconsColor = Color(0xFFECE8E2)
    private val iconsPressedColor = Color(0xFFFFFFFF)

    private val level3Fill = Color(0xFF9B2938).copy(alpha = 0.95f * this.opacity)
    private val level3FillPressed = Color(0xFF6E1C29).copy(alpha = 0.95f * this.opacity)
    val level3Shadow = Color.Black.copy(alpha = 0.32f * this.opacity)
    val level3ShadowWidth = 3.dp

    private val level2Fill = Color(0xFF383B43).copy(alpha = 0.85f * this.opacity)
    private val level2FillPressed = Color(0xFF22252A).copy(alpha = 0.85f * this.opacity)
    val level2Shadow = Color.Black.copy(alpha = 0.24f * this.opacity)
    val level2ShadowWidth = 2.dp

    val level1Fill = Color(0xFF2A2C33).copy(alpha = 0.42f * this.opacity)
    val level1Shadow = Color.Black.copy(alpha = 0.15f * this.opacity)
    val level1ShadowWidth = 3.dp

    val level0CornerRadius = 0.dp
    val level0Fill = Color(0xFFAAA49A).copy(alpha = 0.73f * this.opacity)
    val level0Shadow = Color.Black.copy(alpha = 0.15f * this.opacity)
    val level0ShadowWidth = 2.dp

    fun compositeFill(pressed: Boolean): Color = if (pressed) level2FillPressed else level2Fill

    fun foregroundFill(pressed: Boolean): Color = if (pressed) level3FillPressed else level3Fill

    fun icons(pressed: Boolean): Color =
        (if (pressed) iconsPressedColor else iconsColor).copy(alpha = this.opacity)
}

val LocalLemuroidPadTheme =
    compositionLocalOf<LemuroidPadTheme> {
        error("LemuroidPadTheme is missing")
    }
