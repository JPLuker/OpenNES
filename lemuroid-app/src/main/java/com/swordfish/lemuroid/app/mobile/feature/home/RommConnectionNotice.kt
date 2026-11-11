package com.swordfish.lemuroid.app.mobile.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.romm.RommLibraryCatalog

/** Does not obscure local games while RomM is offline or refreshing. */
@Composable
internal fun RommConnectionNotice(
    catalog: RommLibraryCatalog.State,
    onRetry: () -> Unit,
) {
    val recovering = catalog.showingOfflineCache
    val initialLoad = catalog.refreshing && catalog.games.isEmpty()
    if (!recovering && !initialLoad && catalog.error == null) return

    val title =
        when {
            catalog.error != null && catalog.games.isNotEmpty() -> R.string.opennes_romm_cache_offline
            catalog.error != null -> R.string.opennes_romm_connection_failed
            recovering -> R.string.opennes_romm_cache_refreshing
            else -> R.string.opennes_romm_library_loading
        }
    Surface(
        color = Color(0xFF22262D),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF414650)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (catalog.refreshing) {
                CircularProgressIndicator(
                    color = Color(0xFFB73439),
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(4.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(title),
                    color = Color(0xFFF2EDE6),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                if (catalog.error != null) {
                    Text(
                        stringResource(R.string.opennes_romm_offline_only_metadata),
                        color = Color(0xFFB2B2B4),
                        fontSize = 12.sp,
                    )
                }
            }
            if (catalog.error != null && !catalog.refreshing) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.opennes_romm_retry_connection))
                }
            }
        }
    }
}
