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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
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

@Composable
internal fun NesControllerSetupSheet(
    currentPresetId: String,
    onChoosePreset: (String) -> Unit,
    onCustomizeLayout: () -> Unit,
    onUseDefaults: () -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.65f).dp
    ModalBottomSheet(onDismissRequest = onUseDefaults) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.opennes_setup_title))
            Text(stringResource(R.string.opennes_setup_explanation))

            // Original palettes: two concise options per row, no Nintendo marks.
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
                                Box(
                                    modifier =
                                        Modifier
                                            .size(12.dp)
                                            .background(preset.palette.action, CircleShape),
                                )
                                Text(
                                    text = if (selected) "✓ " + preset.title else preset.title,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }
            Button(onClick = onCustomizeLayout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.opennes_setup_customize))
            }
            TextButton(onClick = onUseDefaults, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.opennes_setup_use_defaults))
            }
        }
    }
}
