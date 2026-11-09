package com.swordfish.lemuroid.app.mobile.shared.compose.controller

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.touchinput.radial.NesColorPart
import com.swordfish.touchinput.radial.NesControllerAppearanceStore
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import android.graphics.Color as AndroidColor

private data class HsvSelection(
    val hue: Float,
    val saturation: Float,
    val brightness: Float,
) {
    fun toColor(): Color =
        Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)))

    companion object {
        fun fromColor(color: Color): HsvSelection {
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(color.toArgb(), hsv)
            return HsvSelection(hsv[0], hsv[1], hsv[2])
        }
    }
}

/**
 * Real local hue/saturation wheel with value and precision sliders and a
 * six-digit HEX field. Changes are staged until Apply; the appearance store
 * remains the authority for persistence and live controller updates.
 */
@Composable
fun NesColorWheelDialog(
    part: NesColorPart,
    initialColor: Color,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var hsv by remember(part, initialColor) { mutableStateOf(HsvSelection.fromColor(initialColor)) }
    var hex by remember(part, initialColor) {
        mutableStateOf(NesControllerAppearanceStore.toHex(initialColor))
    }

    fun selectColor(selection: HsvSelection) {
        hsv = selection
        hex = NesControllerAppearanceStore.toHex(selection.toColor())
    }

    val valid = NesControllerAppearanceStore.isValidHex(hex)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.65f).dp

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(part.label) },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.opennes_controller_wheel_hint))
                HueSaturationWheel(
                    hue = hsv.hue,
                    saturation = hsv.saturation,
                    onPick = { hue, saturation ->
                        selectColor(hsv.copy(hue = hue, saturation = saturation))
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .background(hsv.toColor(), CircleShape)
                                .border(1.dp, Color.Gray, CircleShape),
                    )
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { text ->
                            if (text.length <= 7) {
                                hex = text.uppercase()
                                if (NesControllerAppearanceStore.isValidHex(hex)) {
                                    hsv = HsvSelection.fromColor(Color(AndroidColor.parseColor(hex)))
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.opennes_controller_hex_label)) },
                        isError = !valid,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = stringResource(R.string.opennes_controller_saturation),
                    modifier = Modifier.fillMaxWidth(),
                )
                Slider(
                    value = hsv.saturation,
                    onValueChange = { selectColor(hsv.copy(saturation = it)) },
                    valueRange = 0f..1f,
                )
                Text(
                    text = stringResource(R.string.opennes_controller_brightness),
                    modifier = Modifier.fillMaxWidth(),
                )
                Slider(
                    value = hsv.brightness,
                    onValueChange = { selectColor(hsv.copy(brightness = it)) },
                    valueRange = 0f..1f,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { onApply(hex) },
            ) {
                Text(stringResource(R.string.opennes_controller_color_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.opennes_controller_color_cancel))
            }
        },
    )
}

@Composable
private fun HueSaturationWheel(
    hue: Float,
    saturation: Float,
    onPick: (Float, Float) -> Unit,
) {
    val currentPick by rememberUpdatedState(onPick)
    val wheelDescription = stringResource(R.string.opennes_controller_color_wheel_accessibility)

    Canvas(
        modifier =
            Modifier
                .size(212.dp)
                .semantics { contentDescription = wheelDescription }
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        pickWheelColor(position, size, currentPick)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { position -> pickWheelColor(position, size, currentPick) },
                        onDrag = { change, _ ->
                            pickWheelColor(change.position, size, currentPick)
                            change.consume()
                        },
                    )
                },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.47f
        drawCircle(
            brush =
                Brush.sweepGradient(
                    listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red,
                    ),
                    center = center,
                ),
            radius = radius,
            center = center,
        )
        drawCircle(
            brush =
                Brush.radialGradient(
                    0f to Color.White,
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
            radius = radius,
            center = center,
        )
        drawCircle(
            color = Color.Gray,
            radius = radius,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )
        val angle = hue / 180f * PI.toFloat()
        val selected =
            Offset(
                center.x + cos(angle) * radius * saturation,
                center.y + sin(angle) * radius * saturation,
            )
        drawCircle(Color.Black, radius = 10.dp.toPx(), center = selected)
        drawCircle(
            Color.White,
            radius = 8.dp.toPx(),
            center = selected,
            style = Stroke(width = 2.5.dp.toPx()),
        )
    }
}

/** Wheel angle selects hue and distance from the center selects saturation. */
private fun pickWheelColor(
    point: Offset,
    bounds: IntSize,
    onPick: (Float, Float) -> Unit,
) {
    val centerX = bounds.width / 2f
    val centerY = bounds.height / 2f
    val dx = point.x - centerX
    val dy = point.y - centerY
    val radius = min(bounds.width, bounds.height) * 0.47f
    if (radius <= 0f) return
    val hue = ((atan2(dy, dx) * (180f / PI.toFloat())) + 360f) % 360f
    val saturation = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
    onPick(hue, saturation)
}
