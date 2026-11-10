package com.swordfish.lemuroid.app.mobile.feature.game

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.controller.ControllerAppearancePreview
import com.swordfish.lemuroid.app.mobile.shared.compose.controller.NesColorWheelDialog
import com.swordfish.touchinput.radial.NesColorPart
import com.swordfish.touchinput.radial.NesControllerPalette
import com.swordfish.touchinput.radial.NesControllerPresets

/**
 * Deliberately shown only after a NES game exposes touch controls. The user
 * can see the real controller behind the sheet and immediately drag it.
 * A separate persistent flag prevents interruptions on later game launches.
 */
internal class NesControllerOnboardingStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            "opennes_controller_onboarding_v1",
            Context.MODE_PRIVATE,
        )

    fun shouldShow(): Boolean = !preferences.getBoolean(COMPLETED_KEY, false)

    fun complete() {
        preferences.edit().putBoolean(COMPLETED_KEY, true).apply()
    }

    fun requestAgain() {
        preferences.edit().putBoolean(COMPLETED_KEY, false).apply()
    }

    companion object {
        private const val COMPLETED_KEY = "completed"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NesControllerSetupSheet(
    currentPresetId: String,
    currentPalette: NesControllerPalette,
    onChoosePreset: (String) -> Unit,
    onChooseColor: (NesColorPart, String) -> Unit,
    onCustomizeLayout: () -> Unit,
    onUseDefaults: () -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.78f).dp
    var colorsExpanded by remember { mutableStateOf(false) }
    var selectedColorPart by remember { mutableStateOf<NesColorPart?>(null) }
    ModalBottomSheet(onDismissRequest = onUseDefaults) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.opennes_setup_step), color = androidx.compose.ui.graphics.Color.Gray)
            Text(stringResource(R.string.opennes_setup_title))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.opennes_setup_explanation))
                Text(stringResource(R.string.opennes_setup_preview))
                ControllerAppearancePreview(currentPalette)

                // Each choice immediately updates the shared live controller preview.
                // Show housing, D-pad and A/B swatches, not just an action-button dot.
                NesControllerPresets.all.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pair.forEach { preset ->
                            val selected = preset.id == currentPresetId
                            OutlinedButton(
                                onClick = { onChoosePreset(preset.id) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    listOf(
                                        preset.palette.panel,
                                        preset.palette.dpad,
                                        preset.palette.action,
                                    ).forEach { swatch ->
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(12.dp)
                                                    .background(swatch, CircleShape),
                                        )
                                    }
                                    Text(
                                        text = if (selected) "✓ " + preset.title else preset.title,
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { colorsExpanded = !colorsExpanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.opennes_setup_custom_colors))
                }
                if (colorsExpanded) {
                    Text(stringResource(R.string.opennes_setup_color_hint))
                    NesColorPart.values().forEach { part ->
                        TextButton(
                            onClick = { selectedColorPart = part },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(22.dp)
                                            .background(currentPalette.color(part), CircleShape),
                                )
                                Text(part.label)
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Button(onClick = onUseDefaults, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.opennes_setup_confirm))
            }
            OutlinedButton(onClick = onCustomizeLayout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.opennes_setup_customize))
            }
            Text(
                text = stringResource(R.string.opennes_setup_change_later),
                modifier = Modifier.fillMaxWidth(),
                color = androidx.compose.ui.graphics.Color.Gray,
            )
        }
    }
    selectedColorPart?.let { part ->
        NesColorWheelDialog(
            part = part,
            initialColor = currentPalette.color(part),
            onDismiss = { selectedColorPart = null },
            onApply = { hex ->
                onChooseColor(part, hex)
                selectedColorPart = null
            },
        )
    }
}
