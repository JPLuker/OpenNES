package com.swordfish.lemuroid.app.mobile.feature.gamemenu.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.BuildConfig
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuStyle
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsNative

/**
 * Source-built JNI diagnostic only. Hidden from production builds. Start from
 * the game menu, resume the *real* emulator, then reopen and refresh results.
 * The dormant rc_client has no account, active game or unlock capability.
 */
@Composable
internal fun NativeBridgeDiagnostics() {
    if (!BuildConfig.DEBUG) return

    var expanded by remember { mutableStateOf(false) }
    var observed by remember { mutableStateOf<Long?>(null) }
    var mappedBytes by remember { mutableStateOf<Long?>(null) }
    var status by remember { mutableStateOf<Int?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(R.string.opennes_ra_debug_title))
        }
        if (!expanded) return@Column

        Text(
            text = stringResource(R.string.opennes_ra_debug_instructions),
            color = GameMenuStyle.muted,
            fontSize = 12.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    status =
                        try {
                            if (RetroAchievementsNative.startFrameProbe()) {
                                R.string.opennes_ra_debug_started
                            } else {
                                R.string.opennes_ra_debug_failed
                            }
                        } catch (_: Throwable) {
                            R.string.opennes_ra_debug_failed
                        }
                },
            ) { Text(stringResource(R.string.opennes_ra_debug_start)) }
            OutlinedButton(
                onClick = {
                    try {
                        observed = RetroAchievementsNative.observedProbeFrames()
                        mappedBytes = RetroAchievementsNative.mappedProbeBytes()
                        status = R.string.opennes_ra_debug_refreshed
                    } catch (_: Throwable) {
                        status = R.string.opennes_ra_debug_failed
                    }
                },
            ) { Text(stringResource(R.string.opennes_ra_debug_refresh)) }
        }
        OutlinedButton(
            onClick = {
                try {
                    observed = RetroAchievementsNative.observedProbeFrames()
                    mappedBytes = RetroAchievementsNative.mappedProbeBytes()
                    RetroAchievementsNative.stopFrameProbe()
                    status = R.string.opennes_ra_debug_stopped
                } catch (_: Throwable) {
                    status = R.string.opennes_ra_debug_failed
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.opennes_ra_debug_stop)) }
        observed?.let { count ->
            Text(
                text = stringResource(R.string.opennes_ra_debug_frames, count),
                color = GameMenuStyle.foreground,
                fontWeight = FontWeight.SemiBold,
            )
        }
        mappedBytes?.let { count ->
            Text(
                text = stringResource(R.string.opennes_ra_debug_mapped, count),
                color = GameMenuStyle.foreground,
                fontWeight = FontWeight.SemiBold,
            )
        }
        status?.let {
            Text(
                text = stringResource(it),
                color = GameMenuStyle.muted,
                fontSize = 12.sp,
            )
        }
    }
}
