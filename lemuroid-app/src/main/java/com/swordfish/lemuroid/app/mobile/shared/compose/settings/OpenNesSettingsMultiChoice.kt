package com.swordfish.lemuroid.app.mobile.shared.compose.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.storage.base.SettingValueState
import com.swordfish.lemuroid.R

/** A staged multi-select: dismissing does not silently change sync preferences. */
@Composable
fun OpenNesSettingsMultiChoice(
    enabled: Boolean = true,
    state: SettingValueState<Set<String>>,
    title: @Composable () -> Unit,
    entries: List<String>,
    entryValues: List<String>,
    subtitle: (@Composable () -> Unit)? = null,
    confirmButton: String,
) {
    require(entries.size == entryValues.size) {
        "Entry values and display names must have the same size."
    }

    var open by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(emptySet<String>()) }

    OpenNesSettingsLink(
        enabled = enabled && entries.isNotEmpty(),
        title = title,
        subtitle = subtitle,
        onClick = {
            pending = state.value
            open = true
        },
    )

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = OpenNesSettingsColors.panel,
            titleContentColor = OpenNesSettingsColors.foreground,
            textContentColor = OpenNesSettingsColors.foreground,
            title = title,
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    entries.forEachIndexed { index, display ->
                        val value = entryValues[index]
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        pending =
                                            if (value in pending) {
                                                pending - value
                                            } else {
                                                pending + value
                                            }
                                    }
                                    .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Checkbox(checked = value in pending, onCheckedChange = null)
                            Text(display, color = OpenNesSettingsColors.foreground)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Retain stored choices outside this NES-only selector.
                        state.value = (state.value - entryValues.toSet()) + (pending intersect entryValues.toSet())
                        open = false
                    },
                ) { Text(confirmButton, color = OpenNesSettingsColors.foreground) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.cancel), color = OpenNesSettingsColors.muted)
                }
            },
        )
    }
}
