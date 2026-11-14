package com.swordfish.lemuroid.app.mobile.feature.settings.general

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.BuildConfig
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.home.NesRomLaunchModeStore
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsColors
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsGroup
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsPage
import com.swordfish.lemuroid.app.shared.achievements.HardcoreGate

/**
 * Explains the proposed rules without offering a nonfunctional Hardcore switch.
 * The official mode is gated on native enforcement and RetroAchievements approval.
 */
@Composable
fun HardcoreSettingsScreen(modifier: Modifier = Modifier) {
    OpenNesSettingsPage(modifier = modifier) {
        OpenNesSettingsGroup(title = { Text(stringResource(R.string.opennes_hardcore_status_title)) }) {
            HardcoreInfoText(stringResource(R.string.opennes_hardcore_unavailable))
        }
        // Test builds only: lets the developer and RetroAchievements reviewers
        // exercise Hardcore before approval. Absent from release builds.
        if (BuildConfig.DEBUG && !HardcoreGate.EMULATOR_APPROVED) {
            val context = LocalContext.current
            val store = remember(context) { NesRomLaunchModeStore(context) }
            var testing by remember { mutableStateOf(store.testUnapproved) }
            OpenNesSettingsGroup(title = { Text(stringResource(R.string.opennes_hardcore_test_title)) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.opennes_hardcore_test_body),
                        modifier = Modifier.weight(1f),
                        color = OpenNesSettingsColors.foreground,
                    )
                    Switch(
                        checked = testing,
                        onCheckedChange = {
                            store.testUnapproved = it
                            testing = store.testUnapproved
                        },
                    )
                }
            }
        }
        OpenNesSettingsGroup(title = { Text(stringResource(R.string.opennes_hardcore_rules_title)) }) {
            HardcoreInfoText(stringResource(R.string.opennes_hardcore_rules_body))
        }
        OpenNesSettingsGroup(title = { Text(stringResource(R.string.opennes_hardcore_saves_title)) }) {
            HardcoreInfoText(stringResource(R.string.opennes_hardcore_saves_body))
        }
        OpenNesSettingsGroup(title = { Text(stringResource(R.string.opennes_hardcore_casual_title)) }) {
            HardcoreInfoText(stringResource(R.string.opennes_hardcore_casual_body))
        }
    }
}

@Composable
private fun HardcoreInfoText(value: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = value,
            color = OpenNesSettingsColors.foreground,
            fontWeight = FontWeight.Normal,
        )
    }
}
