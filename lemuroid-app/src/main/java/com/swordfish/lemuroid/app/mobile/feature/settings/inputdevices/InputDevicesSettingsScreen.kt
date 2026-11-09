package com.swordfish.lemuroid.app.mobile.feature.settings.inputdevices

import android.content.Context
import android.content.Intent
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.input.GamePadBindingActivity
import com.swordfish.lemuroid.app.mobile.feature.input.GamePadShortcutBindingActivity
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsColors
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsGroup
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsLink
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsPage
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsSwitch
import com.swordfish.lemuroid.app.shared.input.InputBindingUpdater
import com.swordfish.lemuroid.app.shared.input.InputKey
import com.swordfish.lemuroid.app.shared.input.ShortcutBindingUpdater
import com.swordfish.lemuroid.app.shared.input.lemuroiddevice.getLemuroidInputDevice
import com.swordfish.lemuroid.app.shared.settings.GameShortcut
import com.swordfish.lemuroid.app.utils.android.settings.booleanPreferenceState

@Composable
fun InputDevicesSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: InputDevicesSettingsViewModel,
) {
    val state =
        viewModel.uiState
            .collectAsState(InputDevicesSettingsViewModel.State())
            .value

    OpenNesSettingsPage(modifier = modifier.fillMaxSize()) {
        if (state.devices.isEmpty() && state.bindings.isEmpty()) {
            OpenNesSettingsGroup(
                title = { Text(stringResource(R.string.settings_gamepad_category_enabled)) },
            ) {
                Text(
                    text = stringResource(R.string.opennes_no_gamepads),
                    color = OpenNesSettingsColors.muted,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            EnabledDeviceCategory(state)
            state.bindings.forEach { (device, bindings) ->
                DeviceBindingCategory(device, bindings)
            }
            GeneralOptionsCategory(viewModel)
        }
    }
}

@Composable
private fun DeviceBindingCategory(
    device: InputDevice,
    bindings: InputDevicesSettingsViewModel.BindingsView,
) {
    val context = LocalContext.current
    val customizableKeys = device.getLemuroidInputDevice().getCustomizableKeys()

    OpenNesSettingsGroup(title = { Text(text = device.name) }) {
        customizableKeys.forEach { retroKey ->
            val inputKey = bindings.keys[retroKey] ?: InputKey(KeyEvent.KEYCODE_UNKNOWN)

            OpenNesSettingsLink(
                title = { Text(text = retroKey.displayName(LocalContext.current)) },
                subtitle = { Text(text = inputKey.displayName()) },
                onClick = {
                    val intent =
                        Intent(context, GamePadBindingActivity::class.java).apply {
                            putExtra(InputBindingUpdater.REQUEST_DEVICE, device)
                            putExtra(InputBindingUpdater.REQUEST_RETRO_KEY, retroKey.keyCode)
                        }
                    context.startActivity(intent)
                },
            )
        }

        bindings.shortcuts.forEach {
            DeviceShortcutBinding(context, device, it)
        }
    }
}

@Composable
private fun DeviceShortcutBinding(
    context: Context,
    device: InputDevice,
    shortcut: GameShortcut,
) {
    OpenNesSettingsLink(
        title = { Text(text = shortcut.type.displayName()) },
        subtitle = { Text(text = shortcut.name) },
        onClick = {
            val intent =
                Intent(context, GamePadShortcutBindingActivity::class.java).apply {
                    putExtra(ShortcutBindingUpdater.REQUEST_DEVICE, device)
                    putExtra(ShortcutBindingUpdater.REQUEST_SHORTCUT_TYPE, shortcut.type.name)
                }
            context.startActivity(intent)
        },
    )
}

@Composable
private fun EnabledDeviceCategory(state: InputDevicesSettingsViewModel.State) {
    if (state.devices.isEmpty()) return
    OpenNesSettingsGroup(title = { Text(text = stringResource(R.string.settings_gamepad_category_enabled)) }) {
        state.devices.forEach { device ->
            OpenNesSettingsSwitch(
                state = booleanPreferenceState(key = device.key, default = device.enabledByDefault),
                title = { Text(text = device.name) },
            )
        }
    }
}

@Composable
private fun GeneralOptionsCategory(viewModel: InputDevicesSettingsViewModel) {
    var confirmReset by remember { mutableStateOf(false) }

    OpenNesSettingsGroup(title = { Text(stringResource(R.string.settings_gamepad_category_general)) }) {
        OpenNesSettingsLink(
            title = { Text(stringResource(R.string.settings_gamepad_title_reset_bindings)) },
            onClick = { confirmReset = true },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.opennes_reset_bindings_title)) },
            text = { Text(stringResource(R.string.opennes_reset_bindings_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        viewModel.resetAllBindings()
                    },
                ) {
                    Text(
                        stringResource(R.string.settings_gamepad_title_reset_bindings),
                        color = OpenNesSettingsColors.red,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = OpenNesSettingsColors.panel,
            titleContentColor = OpenNesSettingsColors.foreground,
            textContentColor = OpenNesSettingsColors.muted,
        )
    }
}
