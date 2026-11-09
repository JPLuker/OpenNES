package com.swordfish.lemuroid.app.mobile.feature.settings.general

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.app.mobile.feature.home.HomeViewModel
import com.swordfish.lemuroid.app.mobile.feature.home.LibraryOnlineRefresh

/** Explicit library maintenance, kept out of the game-first Home dashboard. */
@Composable
fun LibraryMaintenanceScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
) {
    val state = viewModel.getViewStates().collectAsState(HomeViewModel.UIState()).value
    val batch = viewModel.onlineBatchState.collectAsState(HomeViewModel.OnlineBatchState()).value

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFF111317))
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
    ) {
        LibraryOnlineRefresh(
            batchState = batch,
            indexed = !state.indexInProgress,
            onStart = viewModel::startOnlineBatch,
            onStop = viewModel::stopOnlineBatch,
        )
    }
}
