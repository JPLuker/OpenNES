package com.swordfish.touchinput.radial.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.LocalNesControllerPalette
import com.swordfish.touchinput.radial.layouts.LocalNesControlBoundsReporter
import com.swordfish.touchinput.radial.layouts.LocalNesLandscapeMinimal
import com.swordfish.touchinput.radial.layouts.NesEditableControl

/**
 * A single, continuous twelve-corner D-pad silhouette replaces five
 * independent rectangular GlassSurfaces. PadKit still owns the input,
 * diagonals and the actual tap region. This canvas never accepts taps.
 */
@Composable
fun NesCrossForeground(directionState: State<Offset>) {
    val palette = LocalNesControllerPalette.current
    val opacity = LocalLemuroidPadTheme.current.opacity
    val minimal = LocalNesLandscapeMinimal.current

    Canvas(modifier = Modifier.fillMaxSize().padding(2.dp)) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val arm = size.minDimension * 0.165f
        val reach = size.minDimension * 0.455f
        val strokeWidth = 2.dp.toPx()
        val path =
            Path().apply {
                moveTo(cx - arm, cy - reach)
                lineTo(cx + arm, cy - reach)
                lineTo(cx + arm, cy - arm)
                lineTo(cx + reach, cy - arm)
                lineTo(cx + reach, cy + arm)
                lineTo(cx + arm, cy + arm)
                lineTo(cx + arm, cy + reach)
                lineTo(cx - arm, cy + reach)
                lineTo(cx - arm, cy + arm)
                lineTo(cx - reach, cy + arm)
                lineTo(cx - reach, cy - arm)
                lineTo(cx - arm, cy - arm)
                close()
            }

        drawPath(
            path = path,
            color = palette.dpad.copy(alpha = opacity * if (minimal) 0.70f else 1f),
        )
        drawPath(
            path = path,
            color = palette.outline.copy(alpha = 0.85f * opacity),
            style = Stroke(width = strokeWidth),
        )

        val active = directionState.value
        val activeColor = palette.dpadPressed.copy(alpha = 0.82f * opacity)
        val inside = arm * 0.87f
        val length = reach - arm - 2.dp.toPx()
        if (active.x < -0.25f) {
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(cx - reach + strokeWidth, cy - inside),
                size = Size(length, inside * 2f),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
        if (active.x > 0.25f) {
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(cx + arm + strokeWidth, cy - inside),
                size = Size(length, inside * 2f),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
        // PadKit's direction state uses positive Y for UP, unlike
        // Canvas coordinates (positive Y draws downward). Invert only the
        // visual pressed highlight; never change gameplay input mapping.
        if (active.y > 0.25f) {
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(cx - inside, cy - reach + strokeWidth),
                size = Size(inside * 2f, length),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
        if (active.y < -0.25f) {
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(cx - inside, cy + arm + strokeWidth),
                size = Size(inside * 2f, length),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }

        // Restrained molded grooves, not overlapping raised squares.
        val grooveColor = palette.outline.copy(alpha = 0.42f * opacity)
        val groove = reach * 0.74f
        for (line in -1..1) {
            val shift = line * 3.dp.toPx()
            drawLine(
                color = grooveColor,
                start = Offset(cx - arm * 0.43f, cy - groove + shift),
                end = Offset(cx + arm * 0.43f, cy - groove + shift),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = grooveColor,
                start = Offset(cx - arm * 0.43f, cy + groove + shift),
                end = Offset(cx + arm * 0.43f, cy + groove + shift),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = grooveColor,
                start = Offset(cx - groove + shift, cy - arm * 0.43f),
                end = Offset(cx - groove + shift, cy + arm * 0.43f),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = grooveColor,
                start = Offset(cx + groove + shift, cy - arm * 0.43f),
                end = Offset(cx + groove + shift, cy + arm * 0.43f),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        drawCircle(
            color = palette.dpadPressed.copy(alpha = 0.88f * opacity),
            radius = arm * 0.76f,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = palette.outline.copy(alpha = 0.68f * opacity),
            radius = arm * 0.76f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

/**
 * One outlined action disc per button. No square button backplates,
 * composite foreground or nested glass shadows.
 */
@Composable
fun NesFaceButtonForeground(
    pressed: State<Boolean>,
    label: String,
) {
    val palette = LocalNesControllerPalette.current
    val opacity = LocalLemuroidPadTheme.current.opacity
    val minimal = LocalNesLandscapeMinimal.current
    val report = LocalNesControlBoundsReporter.current
    val control =
        when (label) {
            "A" -> NesEditableControl.A
            "B" -> NesEditableControl.B
            else -> null
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    control?.let { report(it, coordinates.boundsInRoot()) }
                },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (size.minDimension * 0.44f).coerceAtLeast(0f)
            drawCircle(
                color = palette.outline.copy(alpha = if (minimal) 0.42f * opacity else opacity),
                radius = radius,
                center = center,
            )
            drawCircle(
                color =
                    (if (pressed.value) palette.actionPressed else palette.action)
                        .copy(alpha = opacity),
                radius = (radius - 2.dp.toPx()).coerceAtLeast(0f),
                center = center,
            )
            if (!pressed.value && !minimal) {
                drawArc(
                    color = palette.label.copy(alpha = 0.14f * opacity),
                    startAngle = 195f,
                    sweepAngle = 105f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.76f, center.y - radius * 0.76f),
                    size = Size(radius * 1.52f, radius * 1.52f),
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = label,
            color = palette.label.copy(alpha = opacity),
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun NesPillButtonForeground(
    pressed: State<Boolean>,
    label: String,
    expanded: Boolean = false,
    large: Boolean = false,
) {
    val palette = LocalNesControllerPalette.current
    val opacity = LocalLemuroidPadTheme.current.opacity
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(if (expanded) 0.94f else 0.90f)
                    .height(
                        when {
                            large -> 38.dp
                            expanded -> 26.dp
                            else -> 22.dp
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.height * 0.5f
                drawRoundRect(
                    color =
                        (if (pressed.value) palette.secondaryPressed else palette.secondary)
                            .copy(alpha = opacity),
                    cornerRadius = CornerRadius(radius, radius),
                )
                drawRoundRect(
                    color = palette.outline.copy(alpha = 0.85f * opacity),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            Text(
                text = label,
                color = palette.label.copy(alpha = opacity),
                fontWeight = FontWeight.Bold,
                fontSize =
                    when {
                        large -> 11.sp
                        expanded -> 9.sp
                        else -> 8.sp
                    },
                letterSpacing = 0.25.sp,
                maxLines = 1,
            )
        }
    }
}
