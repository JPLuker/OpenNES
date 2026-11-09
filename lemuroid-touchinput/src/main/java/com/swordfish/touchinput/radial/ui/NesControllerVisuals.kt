package com.swordfish.touchinput.radial.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import gg.padkit.ui.DefaultCrossForeground

/**
 * NES-style cross with recessed horizontal/vertical ridges in place of the
 * modern arrow icons. PadKit still owns directional and diagonal hit testing.
 */
private enum class NesGripAxis {
    HORIZONTAL,
    VERTICAL,
}

@Composable
fun NesCrossForeground(directionState: State<Offset>) {
    DefaultCrossForeground(
        modifier = Modifier.fillMaxSize(),
        directionState = directionState,
        allowDiagonals = true,
        leftDial = { NesDPadDirection(it, NesGripAxis.VERTICAL) },
        rightDial = { NesDPadDirection(it, NesGripAxis.VERTICAL) },
        topDial = { NesDPadDirection(it, NesGripAxis.HORIZONTAL) },
        bottomDial = { NesDPadDirection(it, NesGripAxis.HORIZONTAL) },
        foregroundComposite = { NesDPadCenter(it) },
    )
}

@Composable
private fun NesDPadDirection(
    pressed: State<Boolean>,
    gripAxis: NesGripAxis,
) {
    val opacity = LocalLemuroidPadTheme.current.opacity
    GlassSurface(
        modifier = Modifier.fillMaxSize().padding(1.dp),
        cornerRadius = 3.dp,
        fillColor =
            (if (pressed.value) Color(0xFF111319) else Color(0xFF292B30))
                .copy(alpha = opacity),
        shadowColor = Color.Black.copy(alpha = 0.32f * opacity),
        shadowWidth = 2.dp,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val halfLength = size.minDimension * 0.22f
            val gap = size.minDimension * 0.16f
            val center = Offset(size.width / 2f, size.height / 2f)
            val gripColor =
                (if (pressed.value) Color(0xFF5D6067) else Color(0xFF666970))
                    .copy(alpha = 0.85f * opacity)

            for (line in -1..1) {
                val shift = gap * line
                val start =
                    if (gripAxis == NesGripAxis.HORIZONTAL) {
                        Offset(center.x - halfLength, center.y + shift)
                    } else {
                        Offset(center.x + shift, center.y - halfLength)
                    }
                val end =
                    if (gripAxis == NesGripAxis.HORIZONTAL) {
                        Offset(center.x + halfLength, center.y + shift)
                    } else {
                        Offset(center.x + shift, center.y + halfLength)
                    }
                drawLine(
                    color = gripColor,
                    start = start,
                    end = end,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun NesDPadCenter(pressed: State<Boolean>) {
    val opacity = LocalLemuroidPadTheme.current.opacity
    GlassSurface(
        modifier = Modifier.fillMaxSize().padding(1.dp),
        cornerRadius = 3.dp,
        fillColor =
            (if (pressed.value) Color(0xFF16181C) else Color(0xFF25272C))
                .copy(alpha = opacity),
        shadowColor = Color.Black.copy(alpha = 0.16f * opacity),
        shadowWidth = 1.dp,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.23f
            drawCircle(
                color = Color(0xFF15171B).copy(alpha = opacity),
                radius = radius,
            )
            drawCircle(
                color = Color(0xFF5D6065).copy(alpha = 0.62f * opacity),
                radius = radius,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

@Composable
fun NesFaceButtonForeground(
    pressed: State<Boolean>,
    label: String,
) {
    val opacity = LocalLemuroidPadTheme.current.opacity
    Box(
        modifier = Modifier.fillMaxSize().padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxSize(),
            fillColor = Color(0xFF181A1F).copy(alpha = 0.93f * opacity),
            shadowColor = Color.Black.copy(alpha = 0.35f * opacity),
            shadowWidth = 3.dp,
        )
        GlassSurface(
            modifier = Modifier.fillMaxSize().padding(5.dp),
            fillColor =
                (if (pressed.value) Color(0xFF842437) else Color(0xFFB52E43))
                    .copy(alpha = opacity),
            shadowColor = Color.Black.copy(alpha = 0.27f * opacity),
            shadowWidth = 2.dp,
        ) {
            Text(
                text = label,
                color = Color(0xFFF5F0E9).copy(alpha = opacity),
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
fun NesPillButtonForeground(
    pressed: State<Boolean>,
    label: String,
) {
    val opacity = LocalLemuroidPadTheme.current.opacity
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(0.92f).height(23.dp),
            cornerRadius = 12.dp,
            fillColor =
                (if (pressed.value) Color(0xFF202228) else Color(0xFF41434A))
                    .copy(alpha = opacity),
            shadowColor = Color.Black.copy(alpha = 0.32f * opacity),
            shadowWidth = 2.dp,
        ) {
            Text(
                text = label,
                color = Color(0xFFF3EEE7).copy(alpha = opacity),
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                maxLines = 1,
            )
        }
    }
}
