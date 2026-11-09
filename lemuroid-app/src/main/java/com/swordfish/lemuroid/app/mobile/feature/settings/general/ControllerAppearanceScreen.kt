package com.swordfish.lemuroid.app.mobile.feature.settings.general

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsColors
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsGroup
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsPage
import com.swordfish.touchinput.radial.NesColorPart
import com.swordfish.touchinput.radial.NesControllerAppearanceStore
import com.swordfish.touchinput.radial.NesControllerPalette
import com.swordfish.touchinput.radial.NesControllerPresets
import com.swordfish.touchinput.radial.rememberNesControllerAppearance

/**
 * Accessible directly from Settings, without loading a ROM.
 * No display-only controls: each preset and valid color is persisted.
 */
@Composable
fun ControllerAppearanceScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { NesControllerAppearanceStore(context) }
    val appearance = rememberNesControllerAppearance()

    OpenNesSettingsPage(modifier = modifier) {
        OpenNesSettingsGroup(
            title = { Text(stringResource(R.string.opennes_controller_preview)) },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ControllerAppearancePreview(appearance.palette)
                Text(
                    text = stringResource(R.string.opennes_controller_position_hint),
                    color = OpenNesSettingsColors.muted,
                )
            }
        }
        OpenNesSettingsGroup(
            title = { Text(stringResource(R.string.opennes_controller_presets)) },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NesControllerPresets.all.forEach { preset ->
                    OutlinedButton(
                        onClick = { store.selectPreset(preset.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(18.dp)
                                        .background(preset.palette.action, CircleShape),
                            )
                            Text(
                                text = preset.title,
                                modifier = Modifier.weight(1f),
                                color = OpenNesSettingsColors.foreground,
                            )
                            if (appearance.preset.id == preset.id) {
                                Text(
                                    text = stringResource(R.string.opennes_controller_preset_selected),
                                    color = OpenNesSettingsColors.red,
                                )
                            }
                        }
                    }
                }
            }
        }
        OpenNesSettingsGroup(
            title = { Text(stringResource(R.string.opennes_controller_colors)) },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.opennes_controller_colors_hint),
                    color = OpenNesSettingsColors.muted,
                )
                NesColorPart.values().forEach { part ->
                    ColorPartEditor(
                        part = part,
                        color = appearance.palette.color(part),
                        onChange = { store.setColor(part, it) },
                    )
                }
                OutlinedButton(
                    onClick = { store.reset() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.opennes_controller_reset_appearance))
                }
            }
        }
    }
}

@Composable
private fun ColorPartEditor(
    part: NesColorPart,
    color: Color,
    onChange: (String) -> Unit,
) {
    var value by remember(part, color) {
        mutableStateOf(NesControllerAppearanceStore.toHex(color))
    }
    val valid = NesControllerAppearanceStore.isValidHex(value)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(28.dp).background(color, CircleShape))
        OutlinedTextField(
            value = value,
            onValueChange = { text ->
                value = text
                if (NesControllerAppearanceStore.isValidHex(text)) onChange(text)
            },
            label = { Text(part.label) },
            isError = !valid,
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ControllerAppearancePreview(palette: NesControllerPalette) {
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
