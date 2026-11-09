package com.swordfish.lemuroid.app.mobile.shared.compose.controller

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.swordfish.touchinput.radial.NesControllerPalette

/** The same NES controller mockup is used in Settings and first-game onboarding. */
@Composable
fun ControllerAppearancePreview(palette: NesControllerPalette) {
    Canvas(modifier = Modifier.fillMaxWidth().height(156.dp)) {
        val border = 8.dp.toPx()
        val top = 6.dp.toPx()
        val centerY = size.height * 0.52f
        drawRoundRect(
            color = palette.panel,
            topLeft = Offset(border, top),
            size = Size(size.width - 2f * border, size.height - 2f * top),
            cornerRadius = CornerRadius(18.dp.toPx()),
        )
        drawRoundRect(
            color = palette.outline,
            topLeft = Offset(border, top),
            size = Size(size.width - 2f * border, size.height - 2f * top),
            cornerRadius = CornerRadius(18.dp.toPx()),
            style = Stroke(1.5.dp.toPx()),
        )
        drawLine(
            color = palette.accent,
            start = Offset(border + 20.dp.toPx(), top + 7.dp.toPx()),
            end = Offset(size.width - border - 20.dp.toPx(), top + 7.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
        )

        val cx = size.width * 0.24f
        val arm = size.height * 0.095f
        val reach = size.height * 0.28f
        val cross =
            Path().apply {
                moveTo(cx - arm, centerY - reach)
                lineTo(cx + arm, centerY - reach)
                lineTo(cx + arm, centerY - arm)
                lineTo(cx + reach, centerY - arm)
                lineTo(cx + reach, centerY + arm)
                lineTo(cx + arm, centerY + arm)
                lineTo(cx + arm, centerY + reach)
                lineTo(cx - arm, centerY + reach)
                lineTo(cx - arm, centerY + arm)
                lineTo(cx - reach, centerY + arm)
                lineTo(cx - reach, centerY - arm)
                lineTo(cx - arm, centerY - arm)
                close()
            }
        drawPath(cross, color = palette.dpad)
        drawPath(cross, color = palette.outline, style = Stroke(1.dp.toPx()))
        drawCircle(
            color = palette.dpadPressed,
            radius = arm * 0.68f,
            center = Offset(cx, centerY),
        )

        val actionRadius = size.height * 0.125f
        val actionY = centerY - actionRadius * 0.27f
        val bx = size.width * 0.69f
        val ax = size.width * 0.83f
        listOf(bx, ax).forEach { x ->
            drawCircle(
                color = palette.outline,
                radius = actionRadius + 1.5.dp.toPx(),
                center = Offset(x, actionY),
            )
            drawCircle(
                color = palette.action,
                radius = actionRadius,
                center = Offset(x, actionY),
            )
        }
        drawRoundRect(
            color = palette.secondary,
            topLeft = Offset(size.width * 0.43f, centerY + actionRadius * 0.90f),
            size = Size(size.width * 0.18f, 13.dp.toPx()),
            cornerRadius = CornerRadius(7.dp.toPx()),
        )
    }
}
