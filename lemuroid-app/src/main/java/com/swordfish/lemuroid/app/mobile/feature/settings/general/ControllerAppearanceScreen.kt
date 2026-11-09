package com.swordfish.lemuroid.app.mobile.feature.settings.general

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.game.NesControllerOnboardingStore
import com.swordfish.lemuroid.app.mobile.shared.compose.controller.ControllerAppearancePreview
import com.swordfish.lemuroid.app.mobile.shared.compose.controller.NesColorWheelDialog
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsColors
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsGroup
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsPage
import com.swordfish.touchinput.radial.NesColorPart
import com.swordfish.touchinput.radial.NesControllerAppearanceStore
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
    val setupStore = remember(context) { NesControllerOnboardingStore(context) }
    var setupRequested by remember { mutableStateOf(false) }
    var colorWheelPart by remember { mutableStateOf<NesColorPart?>(null) }

    OpenNesSettingsPage(modifier = modifier) {
        OpenNesSettingsGroup(
            title = { Text(stringResource(R.string.opennes_controller_preview)) },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ControllerAppearancePreview(appearance.palette)
                OutlinedButton(
                    onClick = {
                        setupStore.requestAgain()
                        setupRequested = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.opennes_setup_run_again))
                }
                if (setupRequested) {
                    Text(
                        text = stringResource(R.string.opennes_setup_next_game),
                        color = OpenNesSettingsColors.muted,
                    )
                }
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
                    text = stringResource(R.string.opennes_controller_colors_wheel_hint),
                    color = OpenNesSettingsColors.muted,
                )
                NesColorPart.values().forEach { part ->
                    ColorPartEditor(
                        part = part,
                        color = appearance.palette.color(part),
                        onChange = { store.setColor(part, it) },
                        onPick = { colorWheelPart = part },
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

    colorWheelPart?.let { part ->
        NesColorWheelDialog(
            part = part,
            initialColor = appearance.palette.color(part),
            onDismiss = { colorWheelPart = null },
            onApply = { hex ->
                store.setColor(part, hex)
                colorWheelPart = null
            },
        )
    }
}

@Composable
private fun ColorPartEditor(
    part: NesColorPart,
    color: Color,
    onChange: (String) -> Unit,
    onPick: () -> Unit,
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
        val swatchDescription = stringResource(R.string.opennes_controller_color_wheel_accessibility)
        // Keep the entire circular preview tappable. The small palette badge
        // communicates that this is a picker, without changing HEX entry.
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onPick)
                    .semantics { contentDescription = "$swatchDescription: ${part.label}" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(38.dp)
                        .background(color, CircleShape)
                        .border(1.dp, Color.Gray, CircleShape),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(23.dp)
                        .background(Color(0xFF222632), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
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
