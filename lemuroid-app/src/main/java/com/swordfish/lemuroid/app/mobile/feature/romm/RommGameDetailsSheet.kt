package com.swordfish.lemuroid.app.mobile.feature.romm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.home.NesBoxArtwork
import com.swordfish.lemuroid.app.shared.romm.RommApiClient
import com.swordfish.lemuroid.app.shared.romm.RommLibraryCatalog
import com.swordfish.lemuroid.lib.library.db.entity.Game
import java.util.Locale

/**
 * RomM-only game details: real metadata but never a fake playable ROM.
 * Download, verification and library indexing are visibly staged. Once Room
 * indexes the actual local file, Play receives only the real Game record.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RommGameDetailsSheet(
    rom: RommApiClient.Rom,
    catalog: RommLibraryCatalog.State,
    hasRomFolder: Boolean,
    installedGame: Game? = null,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onPlay: (Game) -> Unit = {},
) {
    val context = LocalContext.current
    var dismissedFailure by remember(rom.id) { mutableStateOf<String?>(null) }
    val progress = catalog.transferProgress[rom.id]
    val queued = rom.id in catalog.queuedIds
    val downloading = rom.id in catalog.downloadingIds
    val indexing = rom.id in catalog.awaitingIndexIds
    val delayed = rom.id in catalog.indexingDelayedIds
    val busy = installedGame == null && (queued || downloading || (indexing && !delayed))
    val error = catalog.downloadFailures[rom.id]
    LaunchedEffect(queued, downloading) {
        if (queued || downloading) dismissedFailure = null
    }
    val appColors = com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalOpenNesAppColors.current
    val foreground = appColors.foreground
    val muted = appColors.muted
    val red = appColors.red
    val surface = appColors.panel
    val border = appColors.border

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = surface,
        contentColor = foreground,
    ) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 330.dp, max = 680.dp)) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    NesBoxArtwork(
                        RommLibraryCatalog.asPreview(rom),
                        Modifier.width(106.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.opennes_system_nes),
                            color = red,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            rom.name,
                            color = foreground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 21.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(
                                if (installedGame != null) {
                                    R.string.opennes_romm_installed
                                } else {
                                    R.string.opennes_romm_available_remote
                                },
                            ),
                            color = muted,
                            fontSize = 13.sp,
                        )
                    }
                }

                // Keep the primary action directly below the title, never after
                // a long metadata description.
                if (installedGame != null) {
                    Button(
                        onClick = { onPlay(installedGame) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = red,
                                contentColor = appColors.onAccent,
                            ),
                    ) {
                        Text(stringResource(R.string.opennes_play))
                    }
                } else {
                    Button(
                        onClick = onDownload,
                        enabled = hasRomFolder && !busy && !indexing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = red,
                                contentColor = appColors.onAccent,
                            ),
                    ) {
                        // The button itself shows progress; the sheet stays usable.
                        val portion =
                            progress?.totalBytes?.takeIf { it > 0 }?.let {
                                (progress.receivedBytes * 100 / it).toInt().coerceIn(0, 100)
                            }
                        Text(
                            when {
                                queued -> stringResource(R.string.opennes_romm_phase_queued)
                                downloading ->
                                    when (progress?.stage) {
                                        RommApiClient.ImportStage.DOWNLOADING ->
                                            if (portion != null) {
                                                stringResource(R.string.opennes_romm_downloading_percent, portion)
                                            } else {
                                                stringResource(R.string.opennes_romm_downloading_short)
                                            }
                                        RommApiClient.ImportStage.VERIFYING,
                                        RommApiClient.ImportStage.IMPORTING,
                                        RommApiClient.ImportStage.INDEXING,
                                        -> stringResource(R.string.opennes_romm_phase_indexing)
                                        else -> stringResource(R.string.opennes_romm_downloading_short)
                                    }
                                indexing -> stringResource(R.string.opennes_romm_phase_indexing)
                                error != null -> stringResource(R.string.opennes_romm_retry_download)
                                else -> stringResource(R.string.opennes_romm_download)
                            },
                        )
                    }
                    // Anything blocking or failing the download is stated
                    // beside the button, not below a long description.
                    if (!hasRomFolder) {
                        Text(
                            stringResource(R.string.opennes_romm_download_requires_folder),
                            color = red,
                            fontSize = 13.sp,
                        )
                    }
                    if (error != null) {
                        Text(error, color = red, fontSize = 13.sp)
                    }
                }

                Text(
                    rom.description
                        ?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.opennes_romm_no_description),
                    color = if (rom.description.isNullOrBlank()) muted else foreground,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
                Text(
                    rom.fileName,
                    color = muted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (rom.sizeBytes > 0) {
                    Text(
                        String.format(Locale.ROOT, "%.1f MB", rom.sizeBytes / 1_048_576.0),
                        color = muted,
                        fontSize = 12.sp,
                    )
                }
                if (installedGame == null && delayed) {
                    Text(
                        stringResource(R.string.opennes_romm_indexing_delayed),
                        color = foreground,
                        fontSize = 13.sp,
                    )
                    OutlinedButton(
                        onClick = { RommLibraryCatalog.retryLibraryIndex(context, rom.id) },
                        border = BorderStroke(1.dp, border),
                    ) {
                        Text(stringResource(R.string.opennes_romm_retry_indexing))
                    }
                }
            }
        }
    }
    if (error != null && dismissedFailure != error) {
        AlertDialog(
            onDismissRequest = { dismissedFailure = error },
            title = { Text(stringResource(R.string.opennes_romm_download_failed)) },
            text = { Text(error) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dismissedFailure = error
                        onDownload()
                    },
                ) {
                    Text(stringResource(R.string.opennes_romm_retry_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissedFailure = error }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}
