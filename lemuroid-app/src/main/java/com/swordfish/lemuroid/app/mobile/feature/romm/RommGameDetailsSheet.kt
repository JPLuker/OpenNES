package com.swordfish.lemuroid.app.mobile.feature.romm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.home.NesBoxArtwork
import com.swordfish.lemuroid.app.shared.romm.RommApiClient
import com.swordfish.lemuroid.app.shared.romm.RommLibraryCatalog
import java.util.Locale

/**
 * Mirrors the regular game detail panel, but never presents a Play action for
 * a fake remote Game. Download is explicitly user initiated and never auto-plays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RommGameDetailsSheet(
    rom: RommApiClient.Rom,
    catalog: RommLibraryCatalog.State,
    hasRomFolder: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    val downloading = rom.id in catalog.downloadingIds
    val indexing = rom.id in catalog.awaitingIndexIds
    val foreground = Color(0xFFF2EDE6)
    val muted = Color(0xFFB2B2B4)
    val red = Color(0xFFB73439)

    // The next ordinary library indexing pass turns the game into a genuine
    // Room entry. Do not show a second synthetic copy in its place.
    LaunchedEffect(indexing) {
        if (indexing) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C2027),
        contentColor = foreground,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 630.dp).padding(24.dp),
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
                        stringResource(R.string.opennes_romm_available_remote),
                        color = muted,
                        fontSize = 13.sp,
                    )
                }
            }
            Text(rom.fileName, color = muted, fontSize = 13.sp)
            if (rom.sizeBytes > 0) {
                Text(
                    String.format(Locale.ROOT, "%.1f MB", rom.sizeBytes / 1_048_576.0),
                    color = muted,
                    fontSize = 13.sp,
                )
            }
            if (!hasRomFolder) {
                Text(
                    stringResource(R.string.opennes_romm_download_requires_folder),
                    color = red,
                    fontSize = 13.sp,
                )
            }
            catalog.downloadError?.let {
                Text(it, color = red, fontSize = 13.sp)
            }
            Button(
                onClick = onDownload,
                enabled = hasRomFolder && !downloading && !indexing,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = red,
                        contentColor = foreground,
                    ),
            ) {
                if (downloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(20.dp),
                        color = foreground,
                        strokeWidth = 2.dp,
                    )
                }
                Text(
                    stringResource(
                        when {
                            downloading -> R.string.opennes_romm_downloading
                            indexing -> R.string.opennes_romm_adding_to_library
                            else -> R.string.opennes_romm_download
                        },
                    ),
                )
            }
        }
    }
}
