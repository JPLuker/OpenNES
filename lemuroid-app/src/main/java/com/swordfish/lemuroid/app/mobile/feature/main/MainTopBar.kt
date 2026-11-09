package com.swordfish.lemuroid.app.mobile.feature.main

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncWork

@Composable
fun MainTopBar(
    currentRoute: MainRoute,
    navController: NavHostController,
    onHelpPressed: () -> Unit,
    onUpdateQueryString: (String) -> Unit,
    mainUIState: MainViewModel.UiState,
) {
    Column {
        OpenNesTopAppBar(
            route = currentRoute,
            navController = navController,
            mainUIState = mainUIState,
            onHelpPressed = onHelpPressed,
            onUpdateQueryString = onUpdateQueryString,
        )

        AnimatedVisibility(mainUIState.operationInProgress) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenNesTopAppBar(
    route: MainRoute,
    navController: NavHostController,
    mainUIState: MainViewModel.UiState,
    onHelpPressed: () -> Unit,
    onUpdateQueryString: (String) -> Unit,
) {
    val context = LocalContext.current
    val topBarColor = Color(0xFF111317)

    TopAppBar(
        title = {
            if (route == MainRoute.SEARCH) {
                OpenNesSearchView(
                    mainUIState = mainUIState,
                    onUpdateQueryString = onUpdateQueryString,
                )
            } else {
                Text(text = stringResource(route.titleId), color = Color(0xFFF2EDE6))
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                scrolledContainerColor = topBarColor,
                containerColor = topBarColor,
            ),
        navigationIcon = {
            AnimatedVisibility(
                visible = route != MainRoute.HOME,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(id = R.string.back),
                        tint = Color(0xFFF2EDE6),
                    )
                }
            }
        },
        actions = {
            OpenNesTopBarActions(
                route = route,
                context = context,
                saveSyncEnabled = mainUIState.saveSyncEnabled,
                onHelpPressed = onHelpPressed,
                operationsInProgress = mainUIState.operationInProgress,
            )
        },
    )
}

@Composable
fun OpenNesTopBarActions(
    route: MainRoute,
    context: Context,
    saveSyncEnabled: Boolean,
    operationsInProgress: Boolean,
    onHelpPressed: () -> Unit,
) {
    Row {
        if (route == MainRoute.SETTINGS) {
            IconButton(onClick = onHelpPressed) {
                Icon(
                    Icons.Outlined.Info,
                    stringResource(R.string.mobile_settings_help),
                )
            }
        }
        if (route == MainRoute.SETTINGS && saveSyncEnabled) {
            IconButton(
                onClick = { SaveSyncWork.enqueueManualWork(context.applicationContext) },
                enabled = !operationsInProgress,
            ) {
                Icon(
                    Icons.Outlined.CloudSync,
                    stringResource(R.string.save_sync),
                )
            }
        }
    }
}

@Composable
private fun OpenNesSearchView(
    mainUIState: MainViewModel.UiState,
    onUpdateQueryString: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp, bottom = 8.dp, end = 8.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF272B33),
        ) { }

        TextField(
            value = mainUIState.searchQuery,
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFF2EDE6)),
            placeholder = { Text(stringResource(R.string.opennes_search_field_hint), color = Color(0xFFB2B2B4)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFB2B2B4))
            },
            onValueChange = { onUpdateQueryString(it) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions =
                KeyboardActions(
                    onSearch = { focusManager.clearFocus() },
                ),
            colors =
                TextFieldDefaults.colors(
                    focusedTextColor = Color(0xFFF2EDE6),
                    unfocusedTextColor = Color(0xFFF2EDE6),
                    cursorColor = Color(0xFFB73439),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
        )
    }
}
