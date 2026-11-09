package com.swordfish.lemuroid.app.mobile.feature.settings.general

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.home.HomeViewModel
import com.swordfish.lemuroid.app.mobile.feature.home.LibraryOnlineRefresh
import com.swordfish.lemuroid.app.shared.metadata.NesAutoMetadataScheduler

/** Background game enrichment is optional; manual refresh remains available. */
@Composable
fun LibraryMaintenanceScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
) {
    val context = LocalContext.current
    val state = viewModel.getViewStates().collectAsState(HomeViewModel.UIState()).value
    val batch = viewModel.onlineBatchState.collectAsState(HomeViewModel.OnlineBatchState()).value
    var enabled by remember { mutableStateOf(NesAutoMetadataScheduler.enabled(context)) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFF111317))
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            color = Color(0xFF1C2027),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF414650)),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = enabled,
                            role = Role.Switch,
                            onValueChange = {
                                enabled = it
                                NesAutoMetadataScheduler.setEnabled(context.applicationContext, it)
                            },
                        )
                        .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.opennes_auto_metadata_title),
                        color = Color(0xFFF2EDE6),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.opennes_auto_metadata_description),
                        color = Color(0xFFB2B2B4),
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = enabled,
                    // The full labeled row is the switch's accessible touch target.
                    onCheckedChange = null,
                )
            }
        }

        LibraryOnlineRefresh(
            batchState = batch,
            indexed = !state.indexInProgress,
            onStart = viewModel::startOnlineBatch,
            onStop = viewModel::stopOnlineBatch,
        )
    }
}
