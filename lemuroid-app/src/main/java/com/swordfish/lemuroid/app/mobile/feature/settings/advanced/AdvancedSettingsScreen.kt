package com.swordfish.lemuroid.app.mobile.feature.settings.advanced

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.swordfish.lemuroid.BuildConfig
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.main.MainRoute
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsChoice
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsGroup
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsLink
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsPage
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsSlider
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsSwitch
import com.swordfish.lemuroid.app.shared.diagnostics.OpenNesDiagnosticReport
import com.swordfish.lemuroid.app.utils.android.settings.booleanPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.intPreferenceState

@Composable
fun AdvancedSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: AdvancedSettingsViewModel,
    navController: NavHostController,
) {
    val uiState =
        viewModel.uiState
            .collectAsState()
            .value

    OpenNesSettingsPage(
        modifier = modifier.fillMaxSize(),
    ) {
        if (uiState?.cache == null) {
            return@OpenNesSettingsPage
        }

        InputSettings()
        GeneralSettings(uiState.cache, viewModel, navController)
        SupportDiagnostics()
    }
}

@Composable
private fun InputSettings() {
    OpenNesSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_input)) },
    ) {
        val rumbleEnabled = booleanPreferenceState(R.string.pref_key_enable_rumble, false)
        OpenNesSettingsSwitch(
            state = rumbleEnabled,
            title = { Text(text = stringResource(id = R.string.settings_title_enable_rumble)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_rumble)) },
        )
        OpenNesSettingsSwitch(
            enabled = rumbleEnabled.value,
            state = booleanPreferenceState(R.string.pref_key_enable_device_rumble, false),
            title = { Text(text = stringResource(id = R.string.settings_title_enable_device_rumble)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_device_rumble)) },
        )
        OpenNesSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_rewind_enabled, true),
            title = { Text(text = stringResource(R.string.settings_title_rewind_enabled)) },
            subtitle = { Text(text = stringResource(R.string.settings_description_rewind_enabled)) },
        )
        OpenNesSettingsSlider(
            state =
                intPreferenceState(
                    key = stringResource(id = R.string.pref_key_tilt_sensitivity_index),
                    default = 6,
                ),
            steps = 10,
            valueRange = 0f..10f,
            enabled = true,
            title = { Text(text = stringResource(R.string.settings_title_tilt_sensitivity)) },
        )
    }
}

@Composable
private fun GeneralSettings(
    cacheState: AdvancedSettingsViewModel.CacheState,
    viewModel: AdvancedSettingsViewModel,
    navController: NavController,
) {
    val factoryResetDialogState = remember { mutableStateOf(false) }

    OpenNesSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_general)) },
    ) {
        OpenNesSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_low_latency_audio, false),
            title = { Text(text = stringResource(id = R.string.settings_title_low_latency_audio)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_low_latency_audio)) },
        )
        OpenNesSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_nes_stable_audio, true),
            title = { Text(text = stringResource(id = R.string.settings_title_nes_stable_audio)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_nes_stable_audio)) },
        )
        OpenNesSettingsChoice(
            title = { Text(text = stringResource(R.string.settings_title_maximum_cache_usage)) },
            items = cacheState.displayNames,
            state =
                indexPreferenceState(
                    R.string.pref_key_max_cache_size,
                    cacheState.default,
                    cacheState.values,
                ),
        )
        OpenNesSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_allow_direct_game_load, true),
            title = { Text(text = stringResource(id = R.string.settings_title_direct_game_load)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_direct_game_load)) },
        )
        OpenNesSettingsLink(
            title = { Text(text = stringResource(id = R.string.settings_title_reset_settings)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_reset_settings)) },
            onClick = { factoryResetDialogState.value = true },
        )
    }

    if (factoryResetDialogState.value) {
        FactoryResetDialog(factoryResetDialogState, viewModel, navController)
    }
}

@Composable
private fun SupportDiagnostics() {
    val context = LocalContext.current
    val copied = remember { mutableStateOf(false) }

    OpenNesSettingsGroup(
        title = { Text(stringResource(R.string.opennes_support_section)) },
    ) {
        OpenNesSettingsLink(
            title = { Text(stringResource(R.string.opennes_support_copy)) },
            subtitle = { Text(stringResource(R.string.opennes_support_description)) },
            onClick = {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val report =
                    OpenNesDiagnosticReport.build(
                        appVersion = BuildConfig.VERSION_NAME,
                        versionCode = BuildConfig.VERSION_CODE.toLong(),
                        androidSdk = Build.VERSION.SDK_INT,
                        deviceAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                        packageVariant = if (BuildConfig.DEBUG) "debug" else "release",
                        sourceCommit = BuildConfig.SOURCE_COMMIT,
                    )
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("OpenNES diagnostics", report))
                    copied.value = true
                }
            },
        )
        if (copied.value) {
            Text(stringResource(R.string.opennes_support_copied))
        }
    }
}

@Composable
private fun FactoryResetDialog(
    factoryResetDialogState: MutableState<Boolean>,
    viewModel: AdvancedSettingsViewModel,
    navController: NavController,
) {
    val onDismiss = {
        factoryResetDialogState.value = false
    }
    AlertDialog(
        title = { Text(stringResource(id = R.string.reset_settings_warning_message_title)) },
        text = { Text(stringResource(id = R.string.reset_settings_warning_message_description)) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.resetAllSettings()
                    navController.popBackStack(MainRoute.SETTINGS.route, false)
                },
            ) {
                Text(text = stringResource(id = R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
    )
}
