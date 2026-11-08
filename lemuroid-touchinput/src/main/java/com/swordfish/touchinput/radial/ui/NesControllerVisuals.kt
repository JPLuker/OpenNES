package com.swordfish.touchinput.radial.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import gg.padkit.ui.DefaultCrossForeground

/** Original NES-style visuals; PadKit retains the actual multi-touch hit testing. */
@Composable
fun NesCrossForeground(directionState: State<Offset>) {
    DefaultCrossForeground(
        modifier = Modifier.fillMaxSize(),
        directionState = directionState,
        allowDiagonals = true,
        leftDial = { NesDPadDirection(it, Icons.Default.KeyboardArrowLeft) },
        rightDial = { NesDPadDirection(it, Icons.Default.KeyboardArrowRight) },
        topDial = { NesDPadDirection(it, Icons.Default.KeyboardArrowUp) },
        bottomDial = { NesDPadDirection(it, Icons.Default.KeyboardArrowDown) },
        foregroundComposite = { NesDPadCenter(it) },
    )
}

@Composable
private fun NesDPadDirection(
    pressed: State<Boolean>,
    direction: ImageVector,
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
        content = {
            Icon(
                modifier = Modifier.size(maxWidth * 0.42f, maxHeight * 0.42f),
                imageVector = direction,
                contentDescription = null,
                tint = Color(0xFF787A80).copy(alpha = opacity),
            )
        },
    )
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
    )
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
