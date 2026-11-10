package com.swordfish.lemuroid.app.mobile.feature.settings.general

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.achievements.RetroAchievementsWebSection
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsPage

/**
 * Account setup belongs in Settings, not behind launching an NES game.
 * This is a read-only WEB API account until native rc_client login and
 * unlocks are implemented. Never present it as a live emulator session.
 */
@Composable
fun RetroAchievementsSettingsScreen(modifier: Modifier = Modifier) {
    OpenNesSettingsPage(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.opennes_ra_settings_explanation))
            RetroAchievementsWebSection(
                romMd5 = null,
                console = null,
                accountOnly = true,
            )
            Text(stringResource(R.string.opennes_ra_settings_native_pending))
        }
    }
}
