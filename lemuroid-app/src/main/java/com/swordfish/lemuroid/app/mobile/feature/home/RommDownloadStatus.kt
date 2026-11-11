package com.swordfish.lemuroid.app.mobile.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.romm.RommApiClient
import com.swordfish.lemuroid.app.shared.romm.RommLibraryCatalog

/** Show progress even if a player dismisses the RomM details bottom sheet. */
@Composable
internal fun RommDownloadStatus(
    catalog: RommLibraryCatalog.State,
    onOpen: (RommApiClient.Rom) -> Unit,
) {
    val id =
        catalog.downloadingIds.firstOrNull()
            ?: catalog.awaitingIndexIds.firstOrNull()
            ?: catalog.downloadFailures.keys.firstOrNull()
            ?: return
    val rom = catalog.games.firstOrNull { it.id == id } ?: return
    val progress = catalog.transferProgress[id]
    val problem = catalog.downloadFailures[id]
    val label =
        when {
            problem != null -> R.string.opennes_romm_failed_tap
            id in catalog.indexingDelayedIds -> R.string.opennes_romm_indexing_delayed_short
            progress?.stage == RommApiClient.ImportStage.DOWNLOADING ->
                R.string.opennes_romm_phase_downloading
            progress?.stage == RommApiClient.ImportStage.VERIFYING ->
                R.string.opennes_romm_phase_verifying
            progress?.stage == RommApiClient.ImportStage.IMPORTING ->
                R.string.opennes_romm_phase_importing
            progress?.stage == RommApiClient.ImportStage.INDEXING ->
                R.string.opennes_romm_phase_indexing
            else -> R.string.opennes_romm_phase_connecting
        }
    val portion =
        progress?.totalBytes?.takeIf { it > 0 }?.let {
            (progress.receivedBytes.toFloat() / it).coerceIn(0f, 1f)
        }

    Surface(
        onClick = { onOpen(rom) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        color = Color(0xFF272B33),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF414650)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        rom.name,
                        color = Color(0xFFF2EDE6),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(label),
                        color = Color(0xFFB2B2B4),
                        fontSize = 12.sp,
                    )
                }
                Text(
                    stringResource(R.string.opennes_romm_view_status),
                    color = Color(0xFFB73439),
                    fontSize = 12.sp,
                )
            }
            if (problem == null &&
                progress?.stage == RommApiClient.ImportStage.DOWNLOADING &&
                portion != null
            ) {
                LinearProgressIndicator(
                    progress = { portion },
                    color = Color(0xFFB73439),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
