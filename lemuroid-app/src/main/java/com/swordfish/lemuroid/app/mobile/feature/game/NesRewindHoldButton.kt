package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R

/**
 * Distinct from PadKit controls: while this surface is pressed, the emulator
 * restores prior in-memory snapshots and never sends a gameplay button event.
 * Disposing the surface releases the hold on rotation, editor or navigation.
 */
@Composable
internal fun NesRewindHoldButton(
    modifier: Modifier = Modifier,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onAccessibleStep: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val latestPress by rememberUpdatedState(onPress)
    val latestRelease by rememberUpdatedState(onRelease)
    val latestAccessibleStep by rememberUpdatedState(onAccessibleStep)
    val accessibilityLabel = stringResource(R.string.opennes_rewind_hold_accessibility)

    DisposableEffect(Unit) {
        onDispose { latestRelease() }
    }

    Box(
        modifier =
            modifier
                .size(36.dp)
                .background(
                    if (pressed) androidx.compose.ui.graphics.Color(0xFF66666E) else androidx.compose.ui.graphics.Color(0xFF45454B),
                    RoundedCornerShape(18.dp),
                )
                .semantics {
                    contentDescription = accessibilityLabel
                    role = Role.Button
                    onClick {
                        latestAccessibleStep()
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            latestPress()
                            try {
                                tryAwaitRelease()
                            } finally {
                                pressed = false
                                latestRelease()
                            }
                        },
                    )
                }
                .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "⏪",
            color = androidx.compose.ui.graphics.Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
