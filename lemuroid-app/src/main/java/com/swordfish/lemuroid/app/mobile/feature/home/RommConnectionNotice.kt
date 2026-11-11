package com.swordfish.lemuroid.app.mobile.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.romm.RommLibraryCatalog

/** Optional, unobtrusive notice. Never shows a passive reconnect spinner. */
@Composable
internal fun RommConnectionNotice(
    catalog: RommLibraryCatalog.State,
    onRetry: () -> Unit,
) {
    if (catalog.error == null) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text =
                stringResource(
                    if (catalog.games.isEmpty()) {
                        R.string.opennes_romm_connection_failed
                    } else {
                        R.string.opennes_romm_cache_offline
                    },
                ),
            modifier = Modifier.weight(1f),
            color = Color(0xFFB2B2B4),
            fontSize = 12.sp,
        )
        TextButton(enabled = !catalog.refreshing, onClick = onRetry) {
            Text(stringResource(R.string.opennes_romm_retry_connection))
        }
    }
}
