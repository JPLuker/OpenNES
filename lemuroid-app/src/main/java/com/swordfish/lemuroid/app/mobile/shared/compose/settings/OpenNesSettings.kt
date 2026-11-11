package com.swordfish.lemuroid.app.mobile.shared.compose.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alorma.compose.settings.storage.base.SettingValueState
import com.swordfish.lemuroid.R
import kotlin.math.roundToInt

/**
 * NES-only settings surfaces. These use the existing preference state objects;
 * no keys, values, controller mappings or other emulator behavior are changed.
 */
object OpenNesSettingsColors {
    val background: Color
        @Composable get() = com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalOpenNesAppColors.current.background
    val panel: Color
        @Composable get() = com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalOpenNesAppColors.current.panel
    val border: Color
        @Composable get() = com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalOpenNesAppColors.current.border
    val foreground: Color
        @Composable get() = com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalOpenNesAppColors.current.foreground
    val muted: Color
        @Composable get() = com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalOpenNesAppColors.current.muted
    val red: Color
        @Composable get() = com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalOpenNesAppColors.current.red
}

@Composable
fun OpenNesSettingsPage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(OpenNesSettingsColors.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
fun OpenNesSettingsGroup(
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = OpenNesSettingsColors.panel,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OpenNesSettingsColors.border),
    ) {
        Column {
            if (title != null) {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp)) {
                    androidx.compose.material3.ProvideTextStyle(
                        androidx.compose.ui.text.TextStyle(
                            color = OpenNesSettingsColors.red,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                        ),
                    ) {
                        title()
                    }
                }
                Spacer(modifier = Modifier.padding(top = 5.dp))
            }
            content()
            Spacer(modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
fun OpenNesSettingsLink(
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            icon?.invoke()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.material3.ProvideTextStyle(
                    androidx.compose.ui.text.TextStyle(
                        color =
                            if (enabled) {
                                OpenNesSettingsColors.foreground
                            } else {
                                OpenNesSettingsColors.muted
                            },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                ) { title() }
                subtitle?.let {
                    androidx.compose.material3.ProvideTextStyle(
                        androidx.compose.ui.text.TextStyle(color = OpenNesSettingsColors.muted, fontSize = 12.sp),
                    ) { it() }
                }
            }
            if (action != null) {
                action()
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = OpenNesSettingsColors.muted,
                )
            }
        }
    }
}

@Composable
fun OpenNesSettingsSwitch(
    enabled: Boolean = true,
    state: SettingValueState<Boolean>,
    icon: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = state.value,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = {
                        state.value = it
                        onCheckedChange(it)
                    },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon?.invoke()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            androidx.compose.material3.ProvideTextStyle(
                androidx.compose.ui.text.TextStyle(
                    color =
                        if (enabled) {
                            OpenNesSettingsColors.foreground
                        } else {
                            OpenNesSettingsColors.muted
                        },
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                ),
            ) { title() }
            subtitle?.let {
                androidx.compose.material3.ProvideTextStyle(
                    androidx.compose.ui.text.TextStyle(color = OpenNesSettingsColors.muted, fontSize = 12.sp),
                ) { it() }
            }
        }
        Switch(
            checked = state.value,
            enabled = enabled,
            // Parent toggleable row owns the action and accessibility label.
            onCheckedChange = null,
        )
    }
}

@Composable
fun OpenNesSettingsChoice(
    enabled: Boolean = true,
    state: SettingValueState<Int>,
    title: @Composable () -> Unit,
    items: List<String>,
    icon: (@Composable () -> Unit)? = null,
    useSelectedValueAsSubtitle: Boolean = true,
    subtitle: (@Composable () -> Unit)? = null,
    onItemSelected: ((Int, String) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val selected = state.value.takeIf { it in items.indices }
    val safeSubtitle: (@Composable () -> Unit)? =
        if (useSelectedValueAsSubtitle && selected != null) {
            { Text(items[selected]) }
        } else {
            subtitle
        }
    OpenNesSettingsLink(
        enabled = enabled && items.isNotEmpty(),
        title = title,
        subtitle = safeSubtitle,
        icon = icon,
        onClick = { open = true },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = OpenNesSettingsColors.panel,
            titleContentColor = OpenNesSettingsColors.foreground,
            textContentColor = OpenNesSettingsColors.foreground,
            title = title,
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    items.forEachIndexed { index, item ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selected == index,
                                        role = Role.RadioButton,
                                        onClick = {
                                            state.value = index
                                            onItemSelected?.invoke(index, item)
                                            open = false
                                        },
                                    )
                                    .heightIn(min = 48.dp)
                                    .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected == index, onClick = null)
                            Text(
                                text = item,
                                color = OpenNesSettingsColors.foreground,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = OpenNesSettingsColors.foreground,
                    )
                }
            },
        )
    }
}

@Composable
fun OpenNesSettingsSlider(
    modifier: Modifier = Modifier,
    state: SettingValueState<Int>,
    steps: Int,
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.material3.ProvideTextStyle(
            androidx.compose.ui.text.TextStyle(
                color = OpenNesSettingsColors.foreground,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
        ) { title() }
        androidx.compose.material3.ProvideTextStyle(
            androidx.compose.ui.text.TextStyle(color = OpenNesSettingsColors.muted, fontSize = 12.sp),
        ) { subtitle() }
        Slider(
            value = state.value.toFloat().coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { state.value = it.roundToInt() },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
        )
    }
}
