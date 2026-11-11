package com.swordfish.lemuroid.app.mobile.feature.romm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
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
    val progress = catalog.transferProgress[rom.id]
    val queued = rom.id in catalog.queuedIds
    val downloading = rom.id in catalog.downloadingIds
    val indexing = rom.id in catalog.awaitingIndexIds
    val delayed = rom.id in catalog.indexingDelayedIds
    val busy = installedGame == null && (queued || downloading || (indexing && !delayed))
    val error = catalog.downloadFailures[rom.id]
    val foreground = Color(0xFFF2EDE6)
    val muted = Color(0xFFB2B2B4)
    val red = Color(0xFFB73439)
    val surface = Color(0xFF1C2027)
    val border = Color(0xFF414650)

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
                        .then(if (busy) Modifier.blur(14.dp) else Modifier)
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
                if (installedGame == null && !hasRomFolder) {
                    Text(
                        stringResource(R.string.opennes_romm_download_requires_folder),
                        color = red,
                        fontSize = 13.sp,
                    )
                }
                if (installedGame == null && error != null) {
                    Text(error, color = red, fontSize = 13.sp)
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

                if (installedGame != null) {
                    Button(
                        onClick = { onPlay(installedGame) },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = red,
                                contentColor = foreground,
                            ),
                    ) {
                        Text(stringResource(R.string.opennes_play))
                    }
                } else if (!indexing) {
                    Button(
                        onClick = onDownload,
                        enabled = hasRomFolder && !downloading && !queued,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = red,
                                contentColor = foreground,
                            ),
                    ) {
                        Text(stringResource(R.string.opennes_romm_download))
                    }
                }
            }

            if (busy) {
                val phase = progress?.stage ?: RommApiClient.ImportStage.CONNECTING
                val message =
                    when {
                        queued -> R.string.opennes_romm_phase_queued
                        phase == RommApiClient.ImportStage.CONNECTING -> R.string.opennes_romm_phase_connecting
                        phase == RommApiClient.ImportStage.DOWNLOADING -> R.string.opennes_romm_phase_downloading
                        phase == RommApiClient.ImportStage.VERIFYING -> R.string.opennes_romm_phase_verifying
                        phase == RommApiClient.ImportStage.IMPORTING -> R.string.opennes_romm_phase_importing
                        else -> R.string.opennes_romm_phase_indexing
                    }
                /*
                        */
                val portion =
                    progress?.totalBytes?.takeIf { it > 0 }?.let {
                        (progress.receivedBytes.toFloat() / it.toFloat()).coerceIn(0f, 1f)
                    }
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(Color(0xD0111317))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {},
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = Color(0xFF272B33),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, border),
                        modifier = Modifier.fillMaxWidth(0.84f),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            CircularProgressIndicator(color = red)
                            Text(
                                stringResource(message),
                                color = foreground,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            )
                            Text(
                                rom.name,
                                color = muted,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (phase == RommApiClient.ImportStage.DOWNLOADING) {
                                if (portion != null) {
                                    LinearProgressIndicator(
                                        progress = { portion },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = red,
                                    )
                                    Text(
                                        "${(portion * 100).toInt()}%",
                                        color = foreground,
                                        fontSize = 13.sp,
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = red)
                                }
                            }
                            Text(
                                stringResource(R.string.opennes_romm_can_leave_download),
                                color = muted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
