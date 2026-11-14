package com.swordfish.lemuroid.app.mobile.feature.romm

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.settings.OpenNesSettingsGroup
import com.swordfish.lemuroid.app.shared.romm.RommTwoWaySync
import com.swordfish.lemuroid.app.shared.romm.RommTwoWaySyncPlan
import com.swordfish.lemuroid.app.shared.romm.RommTwoWaySyncState
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.launch

/**
 * Settings → RomM → Save sync: the on/off switch, the last result, Sync now,
 * and one decision per game whose save changed on both sides.
 */
@Composable
internal fun RommSaveSyncSection(loadGames: suspend () -> List<Game>) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val status by RommTwoWaySync.statusFlow.collectAsState()
    var pendingChoice by remember { mutableStateOf<Pair<RommTwoWaySyncState.Conflict, Boolean>?>(null) }
    LaunchedEffect(context) { RommTwoWaySync.refreshStatus(context) }
    val state = status.state

    OpenNesSettingsGroup(title = { Text(stringResource(R.string.opennes_romm_sync_title)) }) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val switchLabel = stringResource(R.string.opennes_romm_sync_switch)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(switchLabel, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.opennes_romm_sync_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    modifier = Modifier.semantics { contentDescription = switchLabel },
                    checked = state.enabled,
                    enabled = !status.running,
                    onCheckedChange = { on ->
                        scope.launch { RommTwoWaySync.setEnabled(context, on, loadGames()) }
                    },
                )
            }

            if (status.running) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.opennes_romm_sync_running))
                }
            } else {
                state.lastRun?.let { run ->
                    Text(lastRunText(run), fontSize = 13.sp)
                    run.problem?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }

            if (state.enabled) {
                Button(
                    enabled = !status.running,
                    onClick = { scope.launch { RommTwoWaySync.syncAll(context, loadGames()) } },
                ) { Text(stringResource(R.string.opennes_romm_sync_now)) }
            }

            Text(
                stringResource(R.string.opennes_romm_sync_token_help),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )

            if (state.conflicts.isNotEmpty()) {
                Text(
                    stringResource(R.string.opennes_romm_sync_conflicts_title),
                    fontWeight = FontWeight.Bold,
                )
                state.conflicts.forEach { conflict ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(conflict.title, fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(reasonText(conflict.reason)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    enabled = !status.running && conflict.localMd5 != null,
                                    onClick = { pendingChoice = conflict to true },
                                ) { Text(stringResource(R.string.opennes_romm_sync_keep_phone)) }
                                OutlinedButton(
                                    enabled = !status.running && conflict.saveId != null,
                                    onClick = { pendingChoice = conflict to false },
                                ) { Text(stringResource(R.string.opennes_romm_sync_use_server)) }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingChoice?.let { (conflict, keepPhone) ->
        AlertDialog(
            onDismissRequest = { pendingChoice = null },
            title = {
                Text(
                    stringResource(
                        if (keepPhone) R.string.opennes_romm_sync_keep_phone else R.string.opennes_romm_sync_use_server,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (keepPhone) {
                            R.string.opennes_romm_sync_keep_phone_body
                        } else {
                            R.string.opennes_romm_sync_use_server_body
                        },
                        conflict.title,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingChoice = null
                        scope.launch { RommTwoWaySync.resolve(context, conflict, keepPhone, loadGames()) }
                    },
                ) { Text(stringResource(R.string.opennes_romm_sync_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingChoice = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun lastRunText(run: RommTwoWaySyncState.LastRun): String {
    val ago =
        DateUtils.getRelativeTimeSpanString(
            run.atMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    return stringResource(R.string.opennes_romm_sync_last_run, ago, run.uploaded, run.downloaded)
}

private fun reasonText(reason: RommTwoWaySyncPlan.ConflictReason): Int =
    when (reason) {
        RommTwoWaySyncPlan.ConflictReason.BOTH_CHANGED,
        RommTwoWaySyncPlan.ConflictReason.PHONE_CHANGED_TOO,
        RommTwoWaySyncPlan.ConflictReason.SERVER_CHANGED_DURING_UPLOAD,
        -> R.string.opennes_romm_sync_reason_both
        RommTwoWaySyncPlan.ConflictReason.NO_SHARED_HISTORY -> R.string.opennes_romm_sync_reason_first
    }
