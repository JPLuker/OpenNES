package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.swordfish.touchinput.radial.LocalNesControllerPalette

/**
 * A single NES-inspired handheld body. The video area stays truly transparent:
 * the real LibretroDroid view is already behind this canvas, and these four
 * housing strips leave its measured viewport uncovered.
 *
 * This surface never handles pointers; existing PadKit controls remain the
 * only touch targets. The same user-editable palette drives all materials.
 */
@Composable
internal fun NesHandheldShell(
    modifier: Modifier = Modifier,
    viewport: Rect?,
) {
    val palette = LocalNesControllerPalette.current
    Canvas(modifier) {
        val v = viewport ?: return@Canvas
        val left = v.left.coerceIn(0f, size.width)
        val top = v.top.coerceIn(0f, size.height)
        val right = v.right.coerceIn(0f, size.width)
        val bottom = v.bottom.coerceIn(0f, size.height)
        if (right <= left || bottom <= top) return@Canvas

        // Fill only the housing around the measured game view. Drawing one
        // full-screen rect would obscure gameplay and misalign touch targets.
        drawRect(
            color = palette.panel,
            topLeft = Offset.Zero,
            size = Size(size.width, top),
        )
        drawRect(
            color = palette.panel,
            topLeft = Offset(0f, bottom),
            size = Size(size.width, size.height - bottom),
        )
        drawRect(
            color = palette.panel,
            topLeft = Offset(0f, top),
            size = Size(left, bottom - top),
        )
        drawRect(
            color = palette.panel,
            topLeft = Offset(right, top),
            size = Size(size.width - right, bottom - top),
        )

        val frameMargin = 3.dp.toPx()
        val frameSize =
            Size(
                (right - left - 2f * frameMargin).coerceAtLeast(0f),
                (bottom - top - 2f * frameMargin).coerceAtLeast(0f),
            )
        if (frameSize.width > 0f && frameSize.height > 0f) {
            drawRoundRect(
                color = palette.outline,
                topLeft = Offset(left + frameMargin, top + frameMargin),
                size = frameSize,
                cornerRadius = CornerRadius(5.dp.toPx()),
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        // Subtle top and bottom bezel seams link both side grips into one
        // continuous console, instead of two disconnected rectangular pads.
        val seam = 12.dp.toPx()
        if (top > seam * 2f) {
            drawLine(
                color = palette.accent,
                start = Offset(left, top - seam),
                end = Offset(right, top - seam),
                strokeWidth = 2.dp.toPx(),
            )
        }
        if (size.height - bottom > seam * 2f) {
            drawLine(
                color = palette.accent,
                start = Offset(left, bottom + seam),
                end = Offset(right, bottom + seam),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}
