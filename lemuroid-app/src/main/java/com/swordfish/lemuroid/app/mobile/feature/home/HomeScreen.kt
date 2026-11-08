package com.swordfish.lemuroid.app.mobile.feature.home

import android.Manifest
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.app.shared.covers.NesArtwork
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
import com.swordfish.lemuroid.common.displayDetailsSettingsScreen
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.library.metadata.GameMetadata

/** Original OpenNES home palette. The rest of the application will adopt these in M2. */
private object LibraryColors {
    val background = Color(0xFF111317)
    val panel = Color(0xFF1C2027)
    val surface = Color(0xFF272B33)
    val border = Color(0xFF414650)
    val foreground = Color(0xFFF2EDE6)
    val muted = Color(0xFFB2B2B4)
    val red = Color(0xFFB73439)
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current

    ComposableLifecycle { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            viewModel.updatePermissions(context.applicationContext)
        }
    }

    val permissionsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                context.displayDetailsSettingsScreen()
            }
        }

    val state = viewModel.getViewStates().collectAsState(HomeViewModel.UIState()).value
    var selectedGame by remember { mutableStateOf<Game?>(null) }

    OpenNesLibrary(
        modifier = modifier,
        state = state,
        onGameClick = onGameClick,
        onGameLongClick = onGameLongClick,
        onGameDetailsClick = { selectedGame = it },
        onSearchClick = onSearchClick,
        onSettingsClick = onSettingsClick,
        onEnableNotificationsClicked = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onSetDirectoryClicked = { viewModel.changeLocalStorageFolder(context) },
    )

    selectedGame?.let { game ->
        GameDetailsSheet(
            game = game,
            onDismiss = { selectedGame = null },
            onPlay = {
                selectedGame = null
                onGameClick(game)
            },
            onMore = {
                selectedGame = null
                onGameLongClick(game)
            },
            onSearchMetadata = { query, onResults ->
                viewModel.searchMetadata(query, onResults)
            },
            onApplyMetadata = { match, onApplied ->
                viewModel.applyMetadataMatch(game.id, match, onApplied)
            },
            onGameUpdated = { updatedGame -> selectedGame = updatedGame },
        )
    }
}

@Composable
private fun OpenNesLibrary(
    modifier: Modifier,
    state: HomeViewModel.UIState,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onGameDetailsClick: (Game) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onEnableNotificationsClicked: () -> Unit,
    onSetDirectoryClicked: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(LibraryColors.background)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        LibraryHeader(onSearchClick, onSettingsClick)

        AnimatedVisibility(state.showNoNotificationPermissionCard) {
            LibraryNotice(
                title = stringResource(R.string.home_notification_title),
                message = stringResource(R.string.home_notification_message),
                action = stringResource(R.string.home_notification_action),
                onAction = onEnableNotificationsClicked,
            )
        }

        AnimatedVisibility(state.showNoGamesCard) {
            LibraryNotice(
                title = stringResource(R.string.home_empty_title),
                message = stringResource(R.string.home_empty_message),
                action = stringResource(R.string.home_empty_action),
                enabled = !state.indexInProgress,
                onAction = onSetDirectoryClicked,
            )
        }

        // A real, previously played game is the only eligible "Continue" item.
        state.recentGames.firstOrNull()?.let { game ->
            FeaturedGame(game, onGameClick)
        }

        GameShelf(
            title = stringResource(R.string.opennes_recent_shelf),
            games = state.recentGames,
            onGameClick = onGameDetailsClick,
            onGameLongClick = onGameLongClick,
        )
        GameShelf(
            title = stringResource(R.string.opennes_favorites_shelf),
            games = state.favoritesGames,
            onGameClick = onGameDetailsClick,
            onGameLongClick = onGameLongClick,
        )
        GameShelf(
            title = stringResource(R.string.opennes_discover_shelf),
            games = state.discoveryGames,
            onGameClick = onGameDetailsClick,
            onGameLongClick = onGameLongClick,
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun LibraryHeader(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "OPENNES",
                color = LibraryColors.red,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                letterSpacing = 3.sp,
            )
            Text(
                text = stringResource(R.string.opennes_library_title),
                color = LibraryColors.foreground,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            )
            Text(
                text = stringResource(R.string.opennes_library_subtitle),
                color = LibraryColors.muted,
                fontSize = 12.sp,
            )
        }
        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.title_search),
                tint = LibraryColors.foreground,
            )
        }
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.title_settings),
                tint = LibraryColors.foreground,
            )
        }
    }
}

@Composable
private fun FeaturedGame(
    game: Game,
    onGameClick: (Game) -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        color = LibraryColors.panel,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, LibraryColors.border),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameCover(game = game, modifier = Modifier.width(112.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.opennes_continue),
                    color = LibraryColors.red,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = game.displayName(),
                    color = LibraryColors.foreground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = playTimeLabel(game),
                    color = LibraryColors.muted,
                    fontSize = 12.sp,
                )
                Text(
                    text = stringResource(R.string.opennes_achievements_future),
                    color = LibraryColors.muted,
                    fontSize = 11.sp,
                    maxLines = 2,
                )
                Button(
                    onClick = { onGameClick(game) },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = LibraryColors.red,
                            contentColor = LibraryColors.foreground,
                        ),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.opennes_play))
                }
            }
        }
    }
}

@Composable
private fun GameShelf(
    title: String,
    games: List<Game>,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
) {
    if (games.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 20.dp),
            color = LibraryColors.foreground,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(games, key = { it.id }) { game ->
                ShelfGame(
                    game = game,
                    onClick = { onGameClick(game) },
                    onLongClick = { onGameLongClick(game) },
                )
            }
        }

        // A recessed strip gives the row a shelf-like visual anchor.
        Spacer(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(3.dp)
                    .background(LibraryColors.border, RoundedCornerShape(3.dp)),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfGame(
    game: Game,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        color = LibraryColors.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, LibraryColors.border),
    ) {
        Column(
            modifier =
                Modifier
                    .width(148.dp)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    ),
        ) {
            GameCover(game = game, modifier = Modifier.fillMaxWidth())
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = game.displayName(),
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = LibraryColors.foreground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Text(
                    text = playTimeLabel(game),
                    color = LibraryColors.muted,
                    maxLines = 2,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameDetailsSheet(
    game: Game,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onMore: () -> Unit,
    onSearchMetadata: (String, (List<GameMetadata>) -> Unit) -> Unit,
    onApplyMetadata: (GameMetadata, (Game?) -> Unit) -> Unit,
    onGameUpdated: (Game) -> Unit,
) {
    var searchExpanded by remember(game.id) { mutableStateOf(false) }
    var searchText by remember(game.id) { mutableStateOf(game.title.substringBefore('(').trim()) }
    var searchResults by remember(game.id) { mutableStateOf(emptyList<GameMetadata>()) }
    var searched by remember(game.id) { mutableStateOf(false) }
    var searching by remember(game.id) { mutableStateOf(false) }
    var applying by remember(game.id) { mutableStateOf(false) }
    var matchFailed by remember(game.id) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LibraryColors.panel,
        contentColor = LibraryColors.foreground,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 30.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.Top,
            ) {
                GameCover(game, Modifier.width(110.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.opennes_system_nes),
                        color = LibraryColors.red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = game.displayName(),
                        color = LibraryColors.foreground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = playTimeLabel(game),
                        color = LibraryColors.muted,
                        fontSize = 13.sp,
                    )
                }
            }

            game.developer?.takeIf { it.isNotBlank() }?.let {
                GameDetailField(
                    label = stringResource(R.string.opennes_game_developer),
                    value = it,
                )
            }

            game.lastPlayedAt?.let { lastPlayed ->
                val date =
                    DateUtils.getRelativeTimeSpanString(
                        lastPlayed,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString()
                GameDetailField(
                    label = stringResource(R.string.opennes_game_last_played),
                    value = date,
                )
            }

            GameDetailField(
                label = stringResource(R.string.opennes_game_rom_file),
                value = game.fileName,
            )

            GameArtworkGallery(game)

            OutlinedButton(
                onClick = { searchExpanded = !searchExpanded },
                border = BorderStroke(1.dp, LibraryColors.border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LibraryColors.foreground),
            ) {
                Text(stringResource(R.string.opennes_find_game_info))
            }

            if (searchExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = {
                            searchText = it
                            searched = false
                        },
                        label = { Text(stringResource(R.string.opennes_search_game_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            searching = true
                            searched = false
                            onSearchMetadata(searchText) { matches ->
                                searchResults = matches
                                searched = true
                                searching = false
                            }
                        },
                        enabled = !searching && searchText.trim().length >= 3,
                        colors = ButtonDefaults.buttonColors(containerColor = LibraryColors.red),
                    ) {
                        Text(stringResource(R.string.opennes_search_catalogue))
                    }
                    if (searched && searchResults.isEmpty()) {
                        Text(
                            text = stringResource(R.string.opennes_no_catalog_matches),
                            color = LibraryColors.muted,
                            fontSize = 13.sp,
                        )
                    }
                    if (matchFailed) {
                        Text(
                            text = stringResource(R.string.opennes_metadata_update_failed),
                            color = LibraryColors.red,
                            fontSize = 13.sp,
                        )
                    }
                    searchResults.forEach { match ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = match.name.orEmpty(),
                                    color = LibraryColors.foreground,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                )
                                match.developer?.takeIf { it.isNotBlank() }?.let {
                                    Text(text = it, color = LibraryColors.muted, fontSize = 12.sp)
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    applying = true
                                    matchFailed = false
                                    onApplyMetadata(match) { updated ->
                                        applying = false
                                        if (updated == null) {
                                            matchFailed = true
                                        } else {
                                            searchExpanded = false
                                            onGameUpdated(updated)
                                        }
                                    }
                                },
                                enabled = !applying,
                                border = BorderStroke(1.dp, LibraryColors.border),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = LibraryColors.foreground),
                            ) {
                                Text(stringResource(R.string.opennes_use_catalog_match))
                            }
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.opennes_achievements_future),
                color = LibraryColors.muted,
                fontSize = 12.sp,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.weight(1f),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = LibraryColors.red,
                            contentColor = LibraryColors.foreground,
                        ),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.opennes_play))
                }
                OutlinedButton(
                    onClick = onMore,
                    border = BorderStroke(1.dp, LibraryColors.border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LibraryColors.foreground),
                ) {
                    Text(stringResource(R.string.opennes_game_more))
                }
            }
        }
    }
}

@Composable
private fun GameDetailField(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            color = LibraryColors.muted,
            fontSize = 11.sp,
        )
        Text(
            text = value,
            color = LibraryColors.foreground,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun GameArtworkGallery(game: Game) {
    val allPreviews = remember(game.id, game.coverFrontUrl) { NesArtwork.previews(game) }
    val failedUrls = remember(game.id, game.coverFrontUrl) { mutableStateListOf<String>() }
    val previews = allPreviews.filterNot { it.url in failedUrls }

    // No related art for unrecognized games; absent URLs disappear cleanly after
    // Coil reports a failure, rather than showing broken image placeholders.
    if (previews.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.opennes_artwork_gallery),
            color = LibraryColors.foreground,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(previews, key = { it.kind }) { preview ->
                Column(
                    modifier = Modifier.width(224.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AsyncImage(
                        model =
                            ImageRequest.Builder(LocalContext.current)
                                .data(preview.url)
                                .build(),
                        contentDescription =
                            when (preview.kind) {
                                NesArtwork.Kind.TITLE_SCREEN ->
                                    stringResource(R.string.opennes_artwork_title_screen)
                                NesArtwork.Kind.GAMEPLAY ->
                                    stringResource(R.string.opennes_artwork_gameplay)
                            },
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                        contentScale = ContentScale.Fit,
                        onError = { failedUrls.add(preview.url) },
                    )
                    Text(
                        text =
                            when (preview.kind) {
                                NesArtwork.Kind.TITLE_SCREEN ->
                                    stringResource(R.string.opennes_artwork_title_screen)
                                NesArtwork.Kind.GAMEPLAY ->
                                    stringResource(R.string.opennes_artwork_gameplay)
                            },
                        color = LibraryColors.muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GameCover(
    game: Game,
    modifier: Modifier = Modifier,
) {
    val fallbackDrawable = remember(game.id, game.systemId) { CoverUtils.getFallbackDrawable(game) }
    val fallbackPainter = rememberDrawablePainter(drawable = fallbackDrawable)

    AsyncImage(
        model =
            ImageRequest.Builder(LocalContext.current)
                .data(game.displayCoverUrl())
                .build(),
        contentDescription = game.displayName(),
        modifier = modifier.aspectRatio(0.78f),
        contentScale = ContentScale.Crop,
        fallback = fallbackPainter,
        error = fallbackPainter,
    )
}

@Composable
private fun playTimeLabel(game: Game): String {
    val elapsed = game.totalPlayTimeMillis
    if (elapsed == 0L) {
        return if (game.lastPlayedAt != null) {
            stringResource(R.string.opennes_playtime_not_recorded)
        } else {
            stringResource(R.string.opennes_not_played)
        }
    }

    val minutes = elapsed / 60_000L
    return when {
        minutes == 0L -> stringResource(R.string.opennes_playtime_under_minute)
        minutes < 60L -> stringResource(R.string.opennes_playtime_minutes, minutes.toInt())
        else ->
            stringResource(
                R.string.opennes_playtime_hours,
                (minutes / 60L).toInt(),
                (minutes % 60L).toInt(),
            )
    }
}

@Composable
private fun LibraryNotice(
    title: String,
    message: String,
    action: String,
    enabled: Boolean = true,
    onAction: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        color = LibraryColors.panel,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, LibraryColors.border),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                color = LibraryColors.foreground,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(
                text = message,
                color = LibraryColors.muted,
                fontSize = 14.sp,
            )
            OutlinedButton(
                modifier = Modifier.align(Alignment.End),
                enabled = enabled,
                onClick = onAction,
                border = BorderStroke(1.dp, LibraryColors.border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LibraryColors.foreground),
            ) {
                Text(action)
            }
        }
    }
}
