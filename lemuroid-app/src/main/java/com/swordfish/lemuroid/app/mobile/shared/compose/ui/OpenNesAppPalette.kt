package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.swordfish.touchinput.radial.NesControllerPalette
import com.swordfish.touchinput.radial.NesControllerPresets

/**
 * The controller palette is the only source of app accent colors. Keep the
 * underlying dark surfaces and all text legible even for bright housing
 * colors such as Ivory Minimal.
 */
data class OpenNesAppPalette(
    val background: Color,
    val panel: Color,
    val surface: Color,
    val border: Color,
    val foreground: Color,
    val muted: Color,
    val red: Color,
    val onAccent: Color,
)

fun openNesAppPalette(controller: NesControllerPalette): OpenNesAppPalette {
    val accent = controller.action
    val contrastAccent =
        if (accent.luminance() < 0.18f) {
            lerp(accent, Color.White, 0.30f)
        } else {
            accent
        }
    val surfaceTint = controller.panel
    return OpenNesAppPalette(
        background = lerp(Color(0xFF111317), surfaceTint, 0.07f),
        panel = lerp(Color(0xFF1C2027), surfaceTint, 0.13f),
        surface = lerp(Color(0xFF272B33), surfaceTint, 0.18f),
        border = lerp(Color(0xFF414650), controller.accent, 0.22f),
        foreground = Color(0xFFF2EDE6),
        muted = Color(0xFFB2B2B4),
        red = contrastAccent,
        onAccent = if (contrastAccent.luminance() > 0.40f) Color.Black else Color.White,
    )
}

val LocalOpenNesAppColors =
    staticCompositionLocalOf { openNesAppPalette(NesControllerPresets.default.palette) }
