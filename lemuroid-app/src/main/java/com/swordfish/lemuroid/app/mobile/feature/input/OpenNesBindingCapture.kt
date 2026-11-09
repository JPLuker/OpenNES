package com.swordfish.lemuroid.app.mobile.feature.input

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsColors

/**
 * Focused input capture for one physical controller. No touch button or game
 * selection changes are made until the existing binding updater receives input.
 */
@Composable
internal fun OpenNesBindingCapture(
    title: String,
    instructions: String,
    progress: String?,
    onCancel: () -> Unit,
    onInput: (KeyEvent) -> Boolean,
) {
    val focusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onKeyEvent { onInput(it.nativeKeyEvent) }
                    .focusable(),
            color = OpenNesSettingsColors.panel,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, OpenNesSettingsColors.border),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.opennes_binding_label),
                    color = OpenNesSettingsColors.red,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = title,
                    color = OpenNesSettingsColors.foreground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = instructions,
                    color = OpenNesSettingsColors.muted,
                    fontSize = 14.sp,
                )
                Text(
                    text = progress ?: stringResource(R.string.opennes_binding_waiting),
                    color = if (progress == null) OpenNesSettingsColors.red else OpenNesSettingsColors.foreground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = OpenNesSettingsColors.foreground,
                            ),
                        border = BorderStroke(1.dp, OpenNesSettingsColors.border),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
        LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    }
}
