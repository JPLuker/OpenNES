package com.swordfish.lemuroid.app.mobile.feature.gamemenu.coreoptions

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsChoice
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsGroup
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsPage
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsSwitch
import com.swordfish.lemuroid.app.shared.coreoptions.CoreOptionsPreferenceHelper
import com.swordfish.lemuroid.app.shared.coreoptions.LemuroidCoreOption
import com.swordfish.lemuroid.app.shared.settings.ControllerConfigsManager
import com.swordfish.lemuroid.app.utils.android.settings.booleanPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.lemuroid.lib.core.CoreVariablesManager
import com.swordfish.lemuroid.R

/**
 * OpenNES game-specific options. Uses the same stored core variables and
 * controller mappings as before; only the presentation is different.
 */
@Composable
fun GameMenuCoreOptionsScreen(
    viewModel: GameMenuCoreOptionsViewModel,
    gameMenuRequest: GameMenuActivity.GameMenuRequest,
) {
    val context = LocalContext.current
    val connectedGamePads by viewModel.connectedGamePads.collectAsState(0)
    val allOptions =
        remember(gameMenuRequest) {
            gameMenuRequest.coreOptions + gameMenuRequest.advancedCoreOptions
        }

    OpenNesSettingsPage(modifier = Modifier) {
        CoreOptions(gameMenuRequest.game.systemId, allOptions, context)
        ControllersOptions(gameMenuRequest, maxOf(1, connectedGamePads), context)
    }
}

@Composable
private fun CoreOptions(
    systemId: String,
    coreOptions: List<LemuroidCoreOption>,
    context: Context,
) {
    val validOptions = coreOptions.filter { it.getEntriesValues().isNotEmpty() }
    if (validOptions.isEmpty()) return

    OpenNesSettingsGroup(
        title = { Text(stringResource(R.string.core_settings_category_preferences)) },
    ) {
        validOptions.forEach { option ->
            val values = option.getEntriesValues()
            val key = CoreVariablesManager.computeSharedPreferenceKey(option.getKey(), systemId)
            if (values.toSet() == CoreOptionsPreferenceHelper.BOOLEAN_SET) {
                OpenNesSettingsSwitch(
                    state = booleanPreferenceState(key, option.getCurrentValue() == "enabled"),
                    title = { Text(option.getDisplayName(context)) },
                )
            } else {
                val defaultValue =
                    option.getCurrentValue().takeIf { it in values } ?: values.first()
                OpenNesSettingsChoice(
                    title = { Text(option.getDisplayName(context)) },
                    items = option.getEntries(context),
                    state = indexPreferenceState(key, defaultValue, values),
                )
            }
        }
    }
}

@Composable
private fun ControllersOptions(
    gameMenuRequest: GameMenuActivity.GameMenuRequest,
    connectedGamePads: Int,
    context: Context,
) {
    val controllers = gameMenuRequest.coreConfig.controllerConfigs
    val visibleControllers =
        (0 until connectedGamePads)
            .mapNotNull { port ->
                controllers[port]?.takeIf { it.size >= 2 }?.let { port to it }
            }
    if (visibleControllers.isEmpty()) return

    OpenNesSettingsGroup(
        title = { Text(stringResource(R.string.core_settings_category_controllers)) },
    ) {
        visibleControllers.forEach { (port, configs) ->
            val values = configs.map { it.name }
            OpenNesSettingsChoice(
                title = {
                    Text(context.getString(R.string.core_settings_controller, (port + 1).toString()))
                },
                items = configs.map { context.getString(it.displayName) },
                state =
                    indexPreferenceState(
                        ControllerConfigsManager.getSharedPreferencesId(
                            gameMenuRequest.game.systemId,
                            gameMenuRequest.coreConfig.coreID,
                            port,
                        ),
                        values.first(),
                        values,
                    ),
            )
        }
    }
}
