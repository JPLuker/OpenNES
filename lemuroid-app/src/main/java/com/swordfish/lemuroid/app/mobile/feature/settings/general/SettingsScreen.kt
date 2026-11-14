package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Filter
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerticalAlignCenter
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavController
import com.swordfish.lemuroid.BuildConfig
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.main.MainRoute
import com.swordfish.lemuroid.app.mobile.feature.main.navigateToRoute
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsChoice
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsColors
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsGroup
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsLink
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsPage
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsSlider
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsSwitch
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.utils.android.settings.booleanPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.intPreferenceState
import com.swordfish.lemuroid.app.utils.android.stringListResource

/**
 * Settings, grouped the way a player looks for things: games, controls,
 * gameplay, picture, online services, saves, then the rarely needed rest.
 * Every preference key and default is unchanged from earlier builds.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
    navController: NavController,
) {
    val state =
        viewModel.uiState
            .collectAsState(SettingsViewModel.State())
            .value

    val scanInProgress =
        viewModel.directoryScanInProgress
            .collectAsState(false)
            .value

    val indexingInProgress =
        viewModel.indexingInProgress
            .collectAsState(false)
            .value

    OpenNesSettingsPage(modifier = modifier) {
        LibrarySettings(
            state = state,
            onChangeFolder = { viewModel.changeLocalStorageFolder() },
            indexingInProgress = indexingInProgress,
            scanInProgress = scanInProgress,
            onLibraryMaintenance = { navController.navigateToRoute(MainRoute.SETTINGS_LIBRARY) },
        )
        ControlsSettings(navController = navController)
        GameplaySettings()
        DisplaySettings()
        OnlineSettings(
            isSaveSyncSupported = state.isSaveSyncSupported,
            navController = navController,
        )
        OpenNesSaveMigrationSettings()
        MoreSettings(navController = navController)
        AboutSettings()
    }
}

@Composable
private fun SettingIcon(image: ImageVector) {
    Icon(imageVector = image, contentDescription = null, tint = OpenNesSettingsColors.muted)
}

@Composable
private fun LibrarySettings(
    state: SettingsViewModel.State,
    onChangeFolder: () -> Unit,
    indexingInProgress: Boolean,
    scanInProgress: Boolean,
    onLibraryMaintenance: () -> Unit,
) {
    val context = LocalContext.current

    val currentDirectory = state.currentDirectory
    val emptyDirectory = stringResource(R.string.none)

    val currentDirectoryName =
        remember(state.currentDirectory) {
            if (currentDirectory.isBlank()) {
                emptyDirectory
            } else {
                runCatching {
                    DocumentFile.fromTreeUri(context, Uri.parse(currentDirectory))?.name
                }.getOrNull() ?: emptyDirectory
            }
        }

    OpenNesSettingsGroup(title = { Text(text = stringResource(R.string.opennes_settings_library)) }) {
        OpenNesSettingsLink(
            icon = { SettingIcon(Icons.Outlined.Folder) },
            title = { Text(text = stringResource(R.string.opennes_setup_folder_setting)) },
            subtitle = {
                Text(
                    text =
                        if (currentDirectory.isBlank()) {
                            stringResource(R.string.opennes_setup_no_folder_selected)
                        } else {
                            currentDirectoryName
                        },
                )
            },
            onClick = onChangeFolder,
            enabled = !indexingInProgress,
        )
        if (scanInProgress) {
            OpenNesSettingsLink(
                icon = { SettingIcon(Icons.Outlined.Stop) },
                title = { Text(text = stringResource(id = R.string.stop)) },
                onClick = { LibraryIndexScheduler.cancelLibrarySync(context) },
            )
        } else if (currentDirectory.isNotBlank()) {
            OpenNesSettingsLink(
                icon = { SettingIcon(Icons.Outlined.Refresh) },
                title = { Text(text = stringResource(id = R.string.rescan)) },
                subtitle = { Text(text = stringResource(R.string.opennes_setup_rescan_description)) },
                onClick = { LibraryIndexScheduler.scheduleLibrarySync(context) },
                enabled = !indexingInProgress,
            )
        }
        OpenNesSettingsLink(
            icon = { SettingIcon(Icons.Outlined.Image) },
            title = { Text(text = stringResource(R.string.opennes_library_maintenance)) },
            subtitle = { Text(text = stringResource(R.string.opennes_library_maintenance_description)) },
            onClick = onLibraryMaintenance,
        )
    }
}

@Composable
private fun ControlsSettings(navController: NavController) {
    OpenNesSettingsGroup(title = { Text(text = stringResource(R.string.opennes_settings_controls)) }) {
        OpenNesSettingsLink(
            icon = { SettingIcon(Icons.Outlined.Palette) },
            title = { Text(text = stringResource(R.string.opennes_controller_appearance_title)) },
            subtitle = { Text(text = stringResource(R.string.opennes_controller_appearance_description)) },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_CONTROLLER_APPEARANCE) },
        )
        OpenNesSettingsChoice(
            icon = { SettingIcon(Icons.Outlined.Vibration) },
            state =
                indexPreferenceState(
                    R.string.pref_key_haptic_feedback_mode,
                    "press",
                    stringListResource(R.array.pref_key_haptic_feedback_mode_values),
                ),
            title = { Text(text = stringResource(id = R.string.settings_title_enable_touch_feedback)) },
            items = stringListResource(R.array.pref_key_haptic_feedback_mode_display_names),
        )
        OpenNesSettingsLink(
            icon = { SettingIcon(Icons.Outlined.SportsEsports) },
            title = { Text(text = stringResource(R.string.opennes_settings_gamepads)) },
            subtitle = { Text(text = stringResource(R.string.opennes_settings_gamepads_description)) },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_INPUT_DEVICES) },
        )
    }
}

@Composable
private fun GameplaySettings() {
    OpenNesSettingsGroup(title = { Text(text = stringResource(R.string.opennes_settings_gameplay)) }) {
        OpenNesSettingsSwitch(
            icon = { SettingIcon(Icons.Outlined.Save) },
            state = booleanPreferenceState(R.string.pref_key_autosave, true),
            title = { Text(text = stringResource(R.string.opennes_settings_resume_title)) },
            subtitle = { Text(text = stringResource(R.string.opennes_settings_resume_description)) },
        )
        OpenNesSettingsSwitch(
            icon = { SettingIcon(Icons.Outlined.History) },
            state = booleanPreferenceState(R.string.pref_key_rewind_enabled, true),
            title = { Text(text = stringResource(R.string.settings_title_rewind_enabled)) },
            subtitle = { Text(text = stringResource(R.string.opennes_settings_rewind_description)) },
        )
    }
}

@Composable
private fun DisplaySettings() {
    val hdMode = booleanPreferenceState(R.string.pref_key_hd_mode, false)
    val immersiveMode = booleanPreferenceState(R.string.pref_key_enable_immersive_mode, false)

    OpenNesSettingsGroup(title = { Text(text = stringResource(R.string.opennes_settings_display)) }) {
        OpenNesSettingsChoice(
            icon = { SettingIcon(Icons.Outlined.Filter) },
            enabled = !hdMode.value,
            state =
                indexPreferenceState(
                    R.string.pref_key_shader_filter,
                    "auto",
                    stringListResource(R.array.pref_key_shader_filter_values).toList(),
                ),
            title = { Text(text = stringResource(id = R.string.display_filter)) },
            items = stringListResource(R.array.pref_key_shader_filter_display_names),
        )
        OpenNesSettingsSwitch(
            icon = { SettingIcon(Icons.Outlined.HighQuality) },
            state = hdMode,
            title = { Text(text = stringResource(id = R.string.settings_title_hd_mode)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_hd_mode)) },
        )
        if (hdMode.value) {
            OpenNesSettingsSlider(
                enabled = true,
                state =
                    intPreferenceState(
                        key = stringResource(id = R.string.pref_key_hd_mode_quality),
                        default = 2,
                    ),
                steps = 1,
                valueRange = 0f..2f,
                title = { Text(text = stringResource(R.string.settings_title_hd_quality)) },
                subtitle = { Text(text = stringResource(id = R.string.settings_description_hd_quality)) },
            )
        }
        OpenNesSettingsChoice(
            icon = { SettingIcon(Icons.Outlined.VerticalAlignCenter) },
            state =
                indexPreferenceState(
                    R.string.pref_key_viewport_alignment,
                    "center",
                    stringListResource(R.array.pref_key_viewport_alignment_values).toList(),
                ),
            title = { Text(text = stringResource(R.string.opennes_settings_picture_position)) },
            items = stringListResource(R.array.pref_key_viewport_alignment_display_names),
        )
        OpenNesSettingsSwitch(
            icon = { SettingIcon(Icons.Outlined.Wallpaper) },
            state = immersiveMode,
            title = { Text(text = stringResource(id = R.string.settings_title_immersive_mode)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_immersive_mode)) },
        )
    }
}

@Composable
private fun OnlineSettings(
    isSaveSyncSupported: Boolean,
    navController: NavController,
) {
    OpenNesSettingsGroup(title = { Text(text = stringResource(R.string.opennes_settings_online)) }) {
        OpenNesSettingsLink(
            icon = { SettingIcon(Icons.Outlined.EmojiEvents) },
            title = { Text(text = stringResource(R.string.opennes_ra_settings_title)) },
            subtitle = { Text(text = stringResource(R.string.opennes_ra_settings_subtitle)) },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_RETROACHIEVEMENTS) },
        )
        OpenNesSettingsLink(
            icon = { SettingIcon(Icons.Outlined.Cloud) },
            title = { Text(text = stringResource(id = R.string.opennes_romm_title)) },
            subtitle = { Text(text = stringResource(id = R.string.opennes_romm_settings_description)) },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_ROMM) },
        )
        if (isSaveSyncSupported) {
            OpenNesSettingsLink(
                icon = { SettingIcon(Icons.Outlined.Cloud) },
                title = { Text(text = stringResource(id = R.string.settings_title_save_sync)) },
                subtitle = { Text(text = stringResource(id = R.string.settings_description_save_sync)) },
                onClick = { navController.navigateToRoute(MainRoute.SETTINGS_SAVE_SYNC) },
            )
        }
        OpenNesSettingsLink(
            icon = { SettingIcon(Icons.Outlined.Lock) },
            title = { Text(text = stringResource(R.string.opennes_hardcore_settings_title)) },
            subtitle = { Text(text = stringResource(R.string.opennes_hardcore_settings_subtitle)) },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_HARDCORE) },
        )
    }
}

@Composable
private fun MoreSettings(navController: NavController) {
    OpenNesSettingsGroup(title = { Text(text = stringResource(R.string.opennes_settings_more)) }) {
        OpenNesSettingsLink(
            icon = { SettingIcon(Icons.Outlined.Tune) },
            title = { Text(text = stringResource(id = R.string.settings_title_advanced_settings)) },
            subtitle = { Text(text = stringResource(R.string.opennes_settings_advanced_description)) },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_ADVANCED) },
        )
    }
}

/** Version, license, source and privacy: required reading for a GPL app on Google Play. */
@Composable
private fun AboutSettings() {
    val uriHandler = LocalUriHandler.current
    val open: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }
    OpenNesSettingsGroup(
        title = { Text(text = stringResource(R.string.opennes_about_title)) },
    ) {
        OpenNesSettingsLink(
            icon = { SettingIcon(Icons.Outlined.Info) },
            title = { Text(text = stringResource(R.string.opennes_about_version)) },
            subtitle = { Text(text = BuildConfig.VERSION_NAME) },
            action = {},
            onClick = {},
        )
        OpenNesSettingsLink(
            icon = { SettingIcon(Icons.Outlined.Code) },
            title = { Text(text = stringResource(R.string.opennes_about_source)) },
            subtitle = { Text(text = stringResource(R.string.opennes_about_source_description)) },
            onClick = { open(OPENNES_SOURCE_URL) },
        )
        OpenNesSettingsLink(
            icon = { SettingIcon(Icons.Outlined.Description) },
            title = { Text(text = stringResource(R.string.opennes_about_notices)) },
            subtitle = { Text(text = stringResource(R.string.opennes_about_notices_description)) },
            onClick = { open("$OPENNES_SOURCE_URL/blob/master/THIRD_PARTY_NOTICES.md") },
        )
        OpenNesSettingsLink(
            icon = { SettingIcon(Icons.Outlined.PrivacyTip) },
            title = { Text(text = stringResource(R.string.opennes_about_privacy)) },
            subtitle = { Text(text = stringResource(R.string.opennes_about_privacy_description)) },
            onClick = { open("$OPENNES_SOURCE_URL/blob/master/PRIVACY.md") },
        )
    }
}

private const val OPENNES_SOURCE_URL = "https://github.com/JPLuker/OpenNES"
