package com.swordfish.lemuroid.app.mobile.feature.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.romm.RommGameDetailsSheet
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.app.shared.covers.NesArtwork
import com.swordfish.lemuroid.app.shared.covers.NesBoxArtUrls
import com.swordfish.lemuroid.app.shared.covers.NesCoverResolutionCache
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.shared.library.LibraryScanStatus
import com.swordfish.lemuroid.app.shared.metadata.WikidataNesMetadataSource
import com.swordfish.lemuroid.app.shared.metadata.needsOnlineMetadata
import com.swordfish.lemuroid.app.shared.romm.RommLibraryCatalog
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
import com.swordfish.lemuroid.common.displayDetailsSettingsScreen
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.library.metadata.GameMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Original OpenNES home palette. The rest of the application will adopt these in M2. */
private const val NES_BOX_ART_ASPECT_RATIO = 5f / 7.125f

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
    onPinShortcut: (Game) -> Unit,
    pinShortcutSupported: Boolean,
    onFavoriteToggle: (Game, Boolean) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current

    ComposableLifecycle { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            viewModel.updatePermissions(context.applicationContext)
            viewModel.refreshRomMLibrary()
        }
    }

    val permissionsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                context.displayDetailsSettingsScreen()
            }
        }

    val state = viewModel.getViewStates().collectAsState(HomeViewModel.UIState()).value
    val romm = RommLibraryCatalog.state.collectAsState().value
    val localFileNames = viewModel.indexedNesFileNames.collectAsState(emptySet()).value
    val remoteGames =
        remember(romm.games, localFileNames) {
            RommLibraryCatalog.available(romm.games, localFileNames)
                .map(RommLibraryCatalog::asPreview)
        }
    var selectedGame by remember { mutableStateOf<Game?>(null) }
    var browsingAll by remember { mutableStateOf(false) }
    val allGames =
        remember(state.discoveryGames, remoteGames) {
            (state.discoveryGames + remoteGames)
                .sortedBy { it.displayName().lowercase(java.util.Locale.ROOT) }
        }

    LaunchedEffect(Unit) { viewModel.refreshRomMLibrary() }
    LaunchedEffect(localFileNames, romm.awaitingIndexIds) {
        RommLibraryCatalog.reconcileIndexed(localFileNames)
        if (romm.awaitingIndexIds.isNotEmpty()) {
            delay(26_000)
            RommLibraryCatalog.reconcileIndexed(localFileNames)
        }
    }

    OpenNesLibrary(
        modifier = modifier,
        state = state,
        allGames = allGames,
        romm = romm,
        onBrowseAll = { browsingAll = true },
        onOpenRemoteStatus = { selectedGame = RommLibraryCatalog.asPreview(it) },
        onGameClick = onGameClick,
        onGameLongClick = { game ->
            if (game.id < 0) {
                selectedGame = game
            } else {
                onGameLongClick(game)
            }
        },
        onGameDetailsClick = { selectedGame = it },
        onSearchClick = onSearchClick,
        onSettingsClick = onSettingsClick,
        onEnableNotificationsClicked = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onSetDirectoryClicked = { viewModel.changeLocalStorageFolder(context) },
        onRescanClicked = { LibraryIndexScheduler.scheduleLibrarySync(context.applicationContext) },
    )

    if (browsingAll) {
        LibraryBrowserDialog(
            games = allGames,
            onDismiss = { browsingAll = false },
            onSelect = {
                browsingAll = false
                selectedGame = it
            },
        )
    }

    selectedGame?.let { game ->
        val remote = RommLibraryCatalog.remoteFor(game, romm)
        if (remote != null) {
            RommGameDetailsSheet(
                rom = remote,
                catalog = romm,
                hasRomFolder = state.hasRomDirectory,
                installedGame =
                    state.discoveryGames.firstOrNull {
                        it.fileName.equals(remote.fileName, ignoreCase = true)
                    },
                onDismiss = { selectedGame = null },
                onDownload = { viewModel.downloadFromRomM(remote) },
                onPlay = {
                    selectedGame = null
                    onGameClick(it)
                },
            )
        } else if (game.id > 0) {
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
                onPinShortcut = { onPinShortcut(game) },
                pinShortcutSupported = pinShortcutSupported,
                onFavoriteToggle = { favorite ->
                    onFavoriteToggle(game, favorite)
                    selectedGame = game.copy(isFavorite = favorite)
                },
                onSearchMetadata = { query, onResults ->
                    viewModel.searchMetadata(query, onResults)
                },
                onApplyMetadata = { match, onApplied ->
                    viewModel.applyMetadataMatch(game.id, match, onApplied)
                },
                onGameUpdated = { updatedGame -> selectedGame = updatedGame },
                onSaveProfile = { genre, year, description, onSaved ->
                    viewModel.saveGameProfile(game.id, genre, year, description, onSaved)
                },
                onLookupOnline = { onResult ->
                    viewModel.lookupWikidata(game.title, onResult)
                },
                onApplyOnline = { match, onApplied ->
                    viewModel.applyWikidata(game.id, match, onApplied)
                },
                onSearchFixMatch = { query, onResult ->
                    viewModel.searchFixMatch(query, onResult)
                },
                onResolveFixMatch = { candidateId, onResult ->
                    viewModel.resolveFixMatch(candidateId, onResult)
                },
                onApplyFixMatch = { match, onApplied ->
                    viewModel.applyFixMatch(game.id, match, onApplied)
                },
            )
        }
    }
}

@Composable
private fun OpenNesLibrary(
    modifier: Modifier,
    state: HomeViewModel.UIState,
    allGames: List<Game>,
    romm: RommLibraryCatalog.State,
    onBrowseAll: () -> Unit,
    onOpenRemoteStatus: (com.swordfish.lemuroid.app.shared.romm.RommApiClient.Rom) -> Unit,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onGameDetailsClick: (Game) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onEnableNotificationsClicked: () -> Unit,
    onSetDirectoryClicked: () -> Unit,
    onRescanClicked: () -> Unit,
) {
    if (!state.libraryReady) {
        Box(
            modifier = modifier.fillMaxSize().background(LibraryColors.background),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = "OpenNES",
                    color = LibraryColors.foreground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                )
                CircularProgressIndicator(color = LibraryColors.red)
                Text(
                    text = stringResource(R.string.opennes_library_loading),
                    color = LibraryColors.muted,
                    fontSize = 13.sp,
                )
            }
        }
        return
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(LibraryColors.background)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        LibraryHeader(onSearchClick, onSettingsClick)

        // Remains accessible after the details sheet is dismissed, so a long
        // download or failed scan never becomes an invisible disabled action.
        RommDownloadStatus(catalog = romm, onOpen = onOpenRemoteStatus)

        AnimatedVisibility(state.showNoNotificationPermissionCard) {
            LibraryNotice(
                title = stringResource(R.string.home_notification_title),
                message = stringResource(R.string.home_notification_message),
                action = stringResource(R.string.home_notification_action),
                onAction = onEnableNotificationsClicked,
            )
        }

        AnimatedVisibility(
            (state.showNoGamesCard && (allGames.isEmpty() || !state.hasRomDirectory)) ||
                (state.scanError != null && !state.scanInProgress),
        ) {
            LibrarySetupCard(
                hasRomDirectory = state.hasRomDirectory,
                scanInProgress = state.scanInProgress,
                scanError = state.scanError,
                onChooseFolder = onSetDirectoryClicked,
                onRescan = onRescanClicked,
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
        if (state.favoritesGames.isNotEmpty()) {
            GameShelf(
                title = stringResource(R.string.opennes_favorites_shelf),
                games = state.favoritesGames,
                onGameClick = onGameDetailsClick,
                onGameLongClick = onGameLongClick,
            )
        }
        // Preserve the physical NES shelf, but keep it short. The full
        // collection is in a searchable, alphabet-jump grid instead.
        GameShelf(
            title = stringResource(R.string.opennes_discover_shelf),
            games = allGames.take(16),
            onGameClick = onGameDetailsClick,
            onGameLongClick = onGameLongClick,
            onViewAll = onBrowseAll,
            total = allGames.size,
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
internal fun LibraryOnlineRefresh(
    batchState: HomeViewModel.OnlineBatchState,
    indexed: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = LibraryColors.panel,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, LibraryColors.border),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.opennes_bulk_title),
                color = LibraryColors.foreground,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(
                text = stringResource(R.string.opennes_bulk_privacy),
                color = LibraryColors.muted,
                fontSize = 12.sp,
            )
            if (batchState.status == HomeViewModel.OnlineBatchStatus.RUNNING) {
                Text(
                    text =
                        stringResource(
                            R.string.opennes_bulk_progress,
                            batchState.checked,
                            batchState.total,
                            batchState.updated,
                        ),
                    color = LibraryColors.foreground,
                    fontSize = 12.sp,
                )
                batchState.currentTitle?.let {
                    Text(text = it, color = LibraryColors.muted, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onStop,
                    border = BorderStroke(1.dp, LibraryColors.border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LibraryColors.foreground),
                ) {
                    Text(stringResource(R.string.opennes_bulk_stop))
                }
            } else {
                when (batchState.status) {
                    HomeViewModel.OnlineBatchStatus.COMPLETE ->
                        Text(
                            text =
                                stringResource(
                                    R.string.opennes_bulk_complete,
                                    batchState.checked,
                                    batchState.updated,
                                ),
                            color = LibraryColors.muted,
                            fontSize = 12.sp,
                        )
                    HomeViewModel.OnlineBatchStatus.STOPPED ->
                        Text(
                            text = stringResource(R.string.opennes_bulk_stopped),
                            color = LibraryColors.muted,
                            fontSize = 12.sp,
                        )
                    HomeViewModel.OnlineBatchStatus.UNAVAILABLE ->
                        Text(
                            text = stringResource(R.string.opennes_bulk_unavailable),
                            color = LibraryColors.muted,
                            fontSize = 12.sp,
                        )
                    HomeViewModel.OnlineBatchStatus.NOTHING_TO_CHECK ->
                        Text(
                            text = stringResource(R.string.opennes_bulk_none),
                            color = LibraryColors.muted,
                            fontSize = 12.sp,
                        )
                    else -> Unit
                }
                Button(
                    onClick = onStart,
                    enabled = indexed,
                    colors = ButtonDefaults.buttonColors(containerColor = LibraryColors.red),
                ) {
                    Text(stringResource(R.string.opennes_bulk_start))
                }
            }
        }
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
                modifier = Modifier.semantics { heading() },
                color = LibraryColors.foreground,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
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

/**
 * Featured selection sits on the same visual shelf as the collection.
 * No giant rounded recommendation card or duplicate artwork title footer.
 */
@Composable
private fun FeaturedGame(
    game: Game,
    onGameClick: (Game) -> Unit,
) {
    val compactLayout =
        LocalConfiguration.current.screenWidthDp < 360 || LocalDensity.current.fontScale > 1.2f

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            NesBoxArtwork(game = game, modifier = Modifier.width(if (compactLayout) 96.dp else 124.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = playTimeLabel(game),
                    color = LibraryColors.muted,
                    fontSize = 12.sp,
                )
                game.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        color = LibraryColors.muted,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
        NesShelfRail()
    }
}

@Composable
private fun GameShelf(
    title: String,
    games: List<Game>,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onViewAll: (() -> Unit)? = null,
    total: Int = games.size,
) {
    if (games.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f).semantics { heading() },
                color = LibraryColors.foreground,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            if (onViewAll != null) {
                TextButton(onClick = onViewAll) {
                    Text(
                        stringResource(R.string.opennes_romm_browse_all, total),
                        color = LibraryColors.red,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp),
        ) {
            items(games, key = { it.id }) { game ->
                ShelfGame(
                    game = game,
                    onClick = { onGameClick(game) },
                    onLongClick = { onGameLongClick(game) },
                )
            }
        }
        NesShelfRail()
    }
}

/** A subtle charcoal ledge grounds the cases without framing them as cards. */
@Composable
private fun NesShelfRail() {
    Spacer(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(7.dp)
                .background(
                    brush =
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF646871),
                                Color(0xFF383C45),
                                Color(0xFF1A1C22),
                            ),
                        ),
                    shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
                ),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfGame(
    game: Game,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // Box art is the game card. A tap opens the profile; a long press shows
    // actions. Explicit TalkBack labels make both gestures discoverable.
    NesBoxArtwork(
        game = game,
        modifier =
            Modifier
                .width(128.dp)
                .combinedClickable(
                    onClickLabel = stringResource(R.string.opennes_open_game_details, game.displayName()),
                    onLongClickLabel = stringResource(R.string.opennes_game_actions_for, game.displayName()),
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameDetailsSheet(
    game: Game,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onMore: () -> Unit,
    onPinShortcut: () -> Unit,
    pinShortcutSupported: Boolean,
    onFavoriteToggle: (Boolean) -> Unit,
    onSearchMetadata: (String, (List<GameMetadata>) -> Unit) -> Unit,
    onApplyMetadata: (GameMetadata, (Game?) -> Unit) -> Unit,
    onGameUpdated: (Game) -> Unit,
    onSaveProfile: (String?, Int?, String?, (Game?) -> Unit) -> Unit,
    onLookupOnline: ((WikidataNesMetadataSource.Result) -> Unit) -> Unit,
    onApplyOnline: (WikidataNesMetadataSource.Result.Match, (Game?) -> Unit) -> Unit,
    onSearchFixMatch: (String, (WikidataNesMetadataSource.CandidateResults) -> Unit) -> Unit,
    onResolveFixMatch: (String, (WikidataNesMetadataSource.Result) -> Unit) -> Unit,
    onApplyFixMatch: (WikidataNesMetadataSource.Result.Match, (Game?) -> Unit) -> Unit,
) {
    var fixMatchExpanded by remember(game.id) { mutableStateOf(false) }
    var searchExpanded by remember(game.id) { mutableStateOf(false) }
    var onlineExpanded by remember(game.id) { mutableStateOf(false) }
    var editingProfile by remember(game.id) { mutableStateOf(false) }
    var menuExpanded by remember(game.id) { mutableStateOf(false) }
    var searchText by remember(game.id) { mutableStateOf(game.displayName()) }
    var searchResults by remember(game.id) { mutableStateOf(emptyList<GameMetadata>()) }
    var searched by remember(game.id) { mutableStateOf(false) }
    var searching by remember(game.id) { mutableStateOf(false) }
    var catalogueSearchVersion by remember(game.id) { mutableStateOf(0) }
    var applying by remember(game.id) { mutableStateOf(false) }
    var matchFailed by remember(game.id) { mutableStateOf(false) }
    val detailsScroll = rememberScrollState()
    val compactGameDetails =
        LocalConfiguration.current.screenWidthDp < 380 || LocalDensity.current.fontScale > 1.2f

    // Keep both actions near the box art on normal phones, but move them
    // underneath the header when narrow screens or large text require space.
    val gameActions: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            Box {
                OutlinedIconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(48.dp),
                    border = BorderStroke(1.dp, LibraryColors.border),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.opennes_game_actions_for, game.displayName()),
                        tint = LibraryColors.foreground,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = LibraryColors.surface,
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.opennes_profile_edit)) },
                        onClick = {
                            menuExpanded = false
                            editingProfile = true
                            onlineExpanded = false
                            searchExpanded = false
                            fixMatchExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.opennes_online_lookup)) },
                        onClick = {
                            menuExpanded = false
                            onlineExpanded = true
                            editingProfile = false
                            searchExpanded = false
                            fixMatchExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.opennes_find_game_info)) },
                        onClick = {
                            menuExpanded = false
                            searchExpanded = true
                            editingProfile = false
                            onlineExpanded = false
                            fixMatchExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.opennes_fix_match)) },
                        onClick = {
                            menuExpanded = false
                            fixMatchExpanded = true
                            editingProfile = false
                            onlineExpanded = false
                            searchExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (game.isFavorite) {
                                        R.string.game_context_menu_remove_from_favorites
                                    } else {
                                        R.string.game_context_menu_add_to_favorites
                                    },
                                ),
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onFavoriteToggle(!game.isFavorite)
                        },
                    )
                    if (pinShortcutSupported) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.game_context_menu_create_shortcut)) },
                            onClick = {
                                menuExpanded = false
                                onPinShortcut()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.opennes_other_game_actions)) },
                        onClick = {
                            menuExpanded = false
                            onMore()
                        },
                    )
                }
            }
        }
    }

    LaunchedEffect(editingProfile, onlineExpanded, searchExpanded, fixMatchExpanded) {
        if (editingProfile || onlineExpanded || searchExpanded || fixMatchExpanded) detailsScroll.scrollTo(0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LibraryColors.panel,
        contentColor = LibraryColors.foreground,
    ) {
        // Metadata can be long, but Play must never disappear below the fold.
        // Constrain the sheet and scroll its details independently of the actions.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.90f),
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(detailsScroll)
                        .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    NesBoxArtwork(game, Modifier.width(if (compactGameDetails) 90.dp else 110.dp))
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
                        if (!compactGameDetails) {
                            gameActions()
                        }
                    }
                }

                if (compactGameDetails) {
                    gameActions()
                }

                if (onlineExpanded) {
                    WikidataLookupSection(
                        game = game,
                        onLookup = onLookupOnline,
                        onApply = onApplyOnline,
                        onGameUpdated = onGameUpdated,
                    )
                }

                if (searchExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = {
                                searchText = it
                                catalogueSearchVersion++
                                searching = false
                                searched = false
                                searchResults = emptyList()
                                matchFailed = false
                            },
                            label = { Text(stringResource(R.string.opennes_search_game_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                catalogueSearchVersion++
                                val requestVersion = catalogueSearchVersion
                                searching = true
                                searched = false
                                onSearchMetadata(searchText.trim()) { matches ->
                                    // A slow search may return after the user
                                    // has edited the title or chosen another
                                    // query. Never show stale results.
                                    if (catalogueSearchVersion == requestVersion) {
                                        searchResults = matches
                                        searched = true
                                        searching = false
                                    }
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
                                    colors =
                                        ButtonDefaults.outlinedButtonColors(
                                            contentColor = LibraryColors.foreground,
                                        ),
                                ) {
                                    Text(stringResource(R.string.opennes_use_catalog_match))
                                }
                            }
                        }
                    }
                }

                game.developer?.takeIf { it.isNotBlank() }?.let {
                    GameDetailField(
                        label = stringResource(R.string.opennes_game_developer),
                        value = it,
                    )
                }

                GameProfileSection(
                    game = game,
                    editing = editingProfile,
                    onEditingChange = { editingProfile = it },
                    onSave = onSaveProfile,
                    onGameUpdated = onGameUpdated,
                )

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
            }
        }
    }

    // Fix Match must not be buried beneath the existing game-details sheet.
    // A separate, independently scrollable window has full keyboard-aware
    // height and places the editable search field above the soft keyboard.
    if (fixMatchExpanded) {
        Dialog(
            onDismissRequest = { fixMatchExpanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = LibraryColors.panel,
                    contentColor = LibraryColors.foreground,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, LibraryColors.border),
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.96f),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                    ) {
                        FixMatchSection(
                            game = game,
                            onSearch = onSearchFixMatch,
                            onResolve = onResolveFixMatch,
                            onApply = onApplyFixMatch,
                            onGameUpdated = { updated ->
                                onGameUpdated(updated)
                                fixMatchExpanded = false
                            },
                            onCancel = { fixMatchExpanded = false },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Plex-style Fix Match: suggest verified NES candidates, allow title/year
 * refinement, preview the full editorial metadata, then require a separate
 * explicit Apply action. Nothing is changed when a search or preview fails.
 */
@Composable
private fun FixMatchSection(
    game: Game,
    onSearch: (String, (WikidataNesMetadataSource.CandidateResults) -> Unit) -> Unit,
    onResolve: (String, (WikidataNesMetadataSource.Result) -> Unit) -> Unit,
    onApply: (WikidataNesMetadataSource.Result.Match, (Game?) -> Unit) -> Unit,
    onGameUpdated: (Game) -> Unit,
    onCancel: () -> Unit,
) {
    var titleQuery by remember(game.id) { mutableStateOf(game.displayName()) }
    var yearQuery by remember(game.id) { mutableStateOf("") }
    var candidates by remember(game.id) { mutableStateOf(emptyList<WikidataNesMetadataSource.Candidate>()) }
    var selected by remember(game.id) { mutableStateOf<WikidataNesMetadataSource.Candidate?>(null) }
    var preview by remember(game.id) { mutableStateOf<WikidataNesMetadataSource.Result.Match?>(null) }
    var searching by remember(game.id) { mutableStateOf(false) }
    var resolving by remember(game.id) { mutableStateOf(false) }
    var applying by remember(game.id) { mutableStateOf(false) }
    var status by remember(game.id) { mutableStateOf<Int?>(null) }
    var searchVersion by remember(game.id) { mutableStateOf(0) }
    var resolveVersion by remember(game.id) { mutableStateOf(0) }
    var focusedField by remember(game.id) { mutableStateOf(0) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val titleBringIntoView = remember { BringIntoViewRequester() }
    val yearBringIntoView = remember { BringIntoViewRequester() }

    // On some Android keyboards the focus event fires before the IME has
    // resized the dialog. Scroll again when the keyboard becomes visible.
    LaunchedEffect(imeVisible, focusedField) {
        if (imeVisible) {
            delay(80)
            when (focusedField) {
                1 -> titleBringIntoView.bringIntoView()
                2 -> yearBringIntoView.bringIntoView()
            }
        }
    }

    val startSearch: () -> Unit = {
        if (!searching && titleQuery.trim().length in 3..120) {
            searchVersion++
            val currentVersion = searchVersion
            searching = true
            resolveVersion++
            resolving = false
            selected = null
            preview = null
            candidates = emptyList()
            status = null
            onSearch(titleQuery.trim()) { results ->
                if (searchVersion == currentVersion) {
                    searching = false
                    when (results) {
                        is WikidataNesMetadataSource.CandidateResults.Found -> candidates = results.candidates
                        WikidataNesMetadataSource.CandidateResults.NoMatch ->
                            status = R.string.opennes_fix_match_none
                        WikidataNesMetadataSource.CandidateResults.Unavailable ->
                            status = R.string.opennes_online_unavailable
                    }
                }
            }
        }
    }

    LaunchedEffect(game.id) {
        startSearch()
    }

    Surface(
        color = LibraryColors.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, LibraryColors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.opennes_fix_match),
                color = LibraryColors.foreground,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
            Text(
                text = stringResource(R.string.opennes_fix_match_explanation),
                color = LibraryColors.muted,
                fontSize = 12.sp,
            )
            OutlinedTextField(
                value = titleQuery,
                onValueChange = {
                    titleQuery = it.take(120)
                    // Ignore an earlier request after the user changes the
                    // query. Never let a slow response replace newer intent.
                    searchVersion++
                    resolveVersion++
                    searching = false
                    resolving = false
                    selected = null
                    preview = null
                    candidates = emptyList()
                    status = null
                },
                label = { Text(stringResource(R.string.opennes_fix_match_title)) },
                singleLine = true,
                enabled = !resolving && !applying,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                    KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            startSearch()
                        },
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(titleBringIntoView)
                        .onFocusChanged {
                            if (it.isFocused) {
                                focusedField = 1
                                scope.launch {
                                    delay(140)
                                    titleBringIntoView.bringIntoView()
                                }
                            } else if (focusedField == 1) {
                                focusedField = 0
                            }
                        },
            )
            OutlinedTextField(
                value = yearQuery,
                onValueChange = { input ->
                    val next = input.filter(Char::isDigit).take(4)
                    if (next != yearQuery) {
                        yearQuery = next
                        // A previous preview is no longer necessarily in the
                        // filtered results. Never apply a hidden old match.
                        resolveVersion++
                        selected = null
                        preview = null
                        resolving = false
                        status = null
                    }
                },
                label = { Text(stringResource(R.string.opennes_fix_match_year)) },
                singleLine = true,
                enabled = !resolving && !applying,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        },
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(yearBringIntoView)
                        .onFocusChanged {
                            if (it.isFocused) {
                                focusedField = 2
                                scope.launch {
                                    delay(140)
                                    yearBringIntoView.bringIntoView()
                                }
                            } else if (focusedField == 2) {
                                focusedField = 0
                            }
                        },
            )
            Button(
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    startSearch()
                },
                enabled = !searching && !resolving && !applying && titleQuery.trim().length >= 3,
                colors = ButtonDefaults.buttonColors(containerColor = LibraryColors.red),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.opennes_fix_match_search))
            }
            if (searching || resolving || applying) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.opennes_fix_match_working),
                        color = LibraryColors.muted,
                        fontSize = 12.sp,
                    )
                }
            }
            status?.let {
                Text(stringResource(it), color = LibraryColors.muted, fontSize = 12.sp)
            }
            val year = yearQuery.toIntOrNull()
            val filtered =
                if (year == null) candidates else candidates.filter { it.releaseYear == year }
            if (!searching && candidates.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.opennes_fix_match_results),
                    color = LibraryColors.foreground,
                    fontWeight = FontWeight.SemiBold,
                )
                if (filtered.isEmpty()) {
                    Text(
                        text = stringResource(R.string.opennes_fix_match_year_none),
                        color = LibraryColors.muted,
                        fontSize = 12.sp,
                    )
                }
                filtered.forEach { candidate ->
                    OutlinedButton(
                        onClick = {
                            resolveVersion++
                            val currentResolveVersion = resolveVersion
                            selected = candidate
                            preview = null
                            resolving = true
                            status = null
                            onResolve(candidate.entityId) { resolved ->
                                if (resolveVersion == currentResolveVersion &&
                                    selected?.entityId == candidate.entityId
                                ) {
                                    resolving = false
                                    when (resolved) {
                                        is WikidataNesMetadataSource.Result.Match -> preview = resolved
                                        WikidataNesMetadataSource.Result.NoMatch ->
                                            status = R.string.opennes_fix_match_none
                                        WikidataNesMetadataSource.Result.Unavailable ->
                                            status = R.string.opennes_online_unavailable
                                    }
                                }
                            }
                        },
                        enabled = !searching && !resolving && !applying,
                        border = BorderStroke(1.dp, LibraryColors.border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LibraryColors.foreground),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text =
                                    listOfNotNull(
                                        candidate.title,
                                        candidate.releaseYear?.toString(),
                                    ).joinToString(" · "),
                                fontWeight = FontWeight.SemiBold,
                            )
                            candidate.shortDescription?.let {
                                Text(text = it, color = LibraryColors.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            val resolved = preview
            val chosen = selected
            if (resolved != null && chosen != null) {
                Text(
                    text = stringResource(R.string.opennes_fix_match_preview, chosen.title),
                    color = LibraryColors.foreground,
                    fontWeight = FontWeight.Bold,
                )
                resolved.description?.let {
                    GameDetailField(stringResource(R.string.opennes_game_description), it)
                }
                resolved.genre?.let {
                    GameDetailField(stringResource(R.string.opennes_game_genre), it)
                }
                resolved.releaseYear?.let {
                    GameDetailField(stringResource(R.string.opennes_game_release_year), it.toString())
                }
                Text(
                    text = stringResource(R.string.opennes_fix_match_confirm_notice),
                    color = LibraryColors.muted,
                    fontSize = 12.sp,
                )
                Button(
                    onClick = {
                        applying = true
                        status = null
                        onApply(resolved) { updated ->
                            applying = false
                            if (updated == null) {
                                status = R.string.opennes_online_apply_failed
                            } else {
                                onGameUpdated(updated)
                            }
                        }
                    },
                    enabled = !applying && !resolving,
                    colors = ButtonDefaults.buttonColors(containerColor = LibraryColors.red),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.opennes_fix_match_apply))
                }
            }
            OutlinedButton(
                onClick = onCancel,
                enabled = !applying,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun WikidataLookupSection(
    game: Game,
    onLookup: ((WikidataNesMetadataSource.Result) -> Unit) -> Unit,
    onApply: (WikidataNesMetadataSource.Result.Match, (Game?) -> Unit) -> Unit,
    onGameUpdated: (Game) -> Unit,
) {
    var searching by remember(game.id) { mutableStateOf(false) }
    var applying by remember(game.id) { mutableStateOf(false) }
    var candidate by remember(game.id) { mutableStateOf<WikidataNesMetadataSource.Result.Match?>(null) }
    var statusMessage by remember(game.id) { mutableStateOf<Int?>(null) }

    LaunchedEffect(game.id) {
        searching = true
        onLookup { result ->
            searching = false
            when (result) {
                is WikidataNesMetadataSource.Result.Match -> candidate = result
                WikidataNesMetadataSource.Result.NoMatch ->
                    statusMessage = R.string.opennes_online_no_match
                WikidataNesMetadataSource.Result.Unavailable ->
                    statusMessage = R.string.opennes_online_unavailable
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (searching) {
            Text(
                text = stringResource(R.string.opennes_online_searching),
                color = LibraryColors.muted,
                fontSize = 13.sp,
            )
        } else {
            OutlinedButton(
                onClick = {
                    searching = true
                    candidate = null
                    statusMessage = null
                    onLookup { result ->
                        searching = false
                        when (result) {
                            is WikidataNesMetadataSource.Result.Match -> candidate = result
                            WikidataNesMetadataSource.Result.NoMatch ->
                                statusMessage = R.string.opennes_online_no_match
                            WikidataNesMetadataSource.Result.Unavailable ->
                                statusMessage = R.string.opennes_online_unavailable
                        }
                    }
                },
                enabled = !applying,
                border = BorderStroke(1.dp, LibraryColors.border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LibraryColors.foreground),
            ) {
                Text(stringResource(R.string.opennes_online_retry))
            }
        }

        statusMessage?.let { message ->
            Text(
                text = stringResource(message),
                color = LibraryColors.muted,
                fontSize = 13.sp,
            )
        }

        candidate?.let { match ->
            val canFill = game.needsOnlineMetadata(match)
            if (canFill) {
                Text(
                    text = stringResource(R.string.opennes_online_verified_match),
                    color = LibraryColors.foreground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                match.genre?.takeIf { game.genre.isNullOrBlank() }?.let {
                    GameDetailField(stringResource(R.string.opennes_game_genre), it)
                }
                match.releaseYear?.takeIf { game.releaseYear == null }?.let {
                    GameDetailField(stringResource(R.string.opennes_game_release_year), it.toString())
                }
                match.description?.takeIf {
                    game.description.isNullOrBlank() ||
                        (game.wikidataEntityId != null && game.wikipediaArticleTitle == null)
                }?.let {
                    GameDetailField(stringResource(R.string.opennes_game_description), it)
                }
            }
            if (canFill) {
                Button(
                    onClick = {
                        applying = true
                        statusMessage = null
                        onApply(match) { updated ->
                            applying = false
                            if (updated == null) {
                                statusMessage = R.string.opennes_online_apply_failed
                            } else {
                                candidate = null
                                onGameUpdated(updated)
                            }
                        }
                    },
                    enabled = !searching && !applying,
                    colors = ButtonDefaults.buttonColors(containerColor = LibraryColors.red),
                ) {
                    Text(stringResource(R.string.opennes_online_apply))
                }
            }
            if (!canFill) {
                Text(
                    text = stringResource(R.string.opennes_online_all_filled),
                    color = LibraryColors.muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun GameProfileSection(
    game: Game,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onSave: (String?, Int?, String?, (Game?) -> Unit) -> Unit,
    onGameUpdated: (Game) -> Unit,
) {
    var genreInput by remember(game.id) { mutableStateOf(game.genre.orEmpty()) }
    var yearInput by remember(game.id) { mutableStateOf(game.releaseYear?.toString().orEmpty()) }
    var descriptionInput by remember(game.id) { mutableStateOf(game.description.orEmpty()) }
    var saving by remember(game.id) { mutableStateOf(false) }
    var saveFailed by remember(game.id) { mutableStateOf(false) }
    var invalidYear by remember(game.id) { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(editing) {
        if (editing) {
            genreInput = game.genre.orEmpty()
            yearInput = game.releaseYear?.toString().orEmpty()
            descriptionInput = game.description.orEmpty()
            saveFailed = false
            invalidYear = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!editing) {
            game.genre?.takeIf { it.isNotBlank() }?.let {
                GameDetailField(stringResource(R.string.opennes_game_genre), it)
            }
            game.releaseYear?.let {
                GameDetailField(stringResource(R.string.opennes_game_release_year), it.toString())
            }
            game.description?.takeIf { it.isNotBlank() }?.let {
                GameDetailField(stringResource(R.string.opennes_game_description), it)
            }
            val article = game.wikipediaArticleTitle?.takeIf { it.isNotBlank() }
            val entityId = game.wikidataEntityId?.takeIf { it.matches(Regex("Q[1-9][0-9]*")) }
            if (article != null || entityId != null) {
                // Attribution is a single compact, unobtrusive line, not two
                // full-size buttons repeating the description's source.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.opennes_sources_label),
                        color = LibraryColors.muted,
                        fontSize = 11.sp,
                    )
                    article?.let {
                        Text(
                            text = stringResource(R.string.opennes_wikipedia_credit),
                            modifier =
                                Modifier.clickable {
                                    val url = "https://en.wikipedia.org/wiki/" + Uri.encode(it.replace(' ', '_'))
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                },
                            color = LibraryColors.muted,
                            fontSize = 11.sp,
                        )
                    }
                    if (article != null && entityId != null) {
                        Text(text = "·", color = LibraryColors.muted, fontSize = 11.sp)
                    }
                    entityId?.let {
                        Text(
                            text = stringResource(R.string.opennes_wikidata_credit),
                            modifier =
                                Modifier.clickable {
                                    val uri = Uri.parse("https://www.wikidata.org/wiki/" + it)
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                },
                            color = LibraryColors.muted,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            if (game.genre.isNullOrBlank() && game.releaseYear == null && game.description.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.opennes_profile_empty),
                    color = LibraryColors.muted,
                    fontSize = 12.sp,
                )
            }
        } else {
            OutlinedTextField(
                value = genreInput,
                onValueChange = { if (it.length <= 80) genreInput = it },
                label = { Text(stringResource(R.string.opennes_game_genre)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = yearInput,
                onValueChange = {
                    if (it.length <= 4 && it.all(Char::isDigit)) {
                        yearInput = it
                        invalidYear = false
                    }
                },
                label = { Text(stringResource(R.string.opennes_game_release_year)) },
                isError = invalidYear,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (invalidYear) {
                Text(
                    text = stringResource(R.string.opennes_profile_invalid_year),
                    color = LibraryColors.red,
                    fontSize = 12.sp,
                )
            }
            OutlinedTextField(
                value = descriptionInput,
                onValueChange = { if (it.length <= 1500) descriptionInput = it },
                label = { Text(stringResource(R.string.opennes_game_description)) },
                minLines = 3,
                maxLines = 7,
                modifier = Modifier.fillMaxWidth(),
            )
            if (saveFailed) {
                Text(
                    text = stringResource(R.string.opennes_profile_failed),
                    color = LibraryColors.red,
                    fontSize = 12.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onEditingChange(false) },
                    enabled = !saving,
                    border = BorderStroke(1.dp, LibraryColors.border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LibraryColors.foreground),
                ) {
                    Text(stringResource(R.string.opennes_profile_cancel))
                }
                Button(
                    onClick = {
                        val parsedYear = yearInput.toIntOrNull()
                        if (yearInput.isNotBlank() && (parsedYear == null || parsedYear !in 1950..2100)) {
                            invalidYear = true
                        } else {
                            saving = true
                            saveFailed = false
                            onSave(
                                genreInput,
                                parsedYear,
                                descriptionInput,
                            ) { updated ->
                                saving = false
                                if (updated == null) {
                                    saveFailed = true
                                } else {
                                    onEditingChange(false)
                                    onGameUpdated(updated)
                                }
                            }
                        }
                    },
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = LibraryColors.red),
                ) {
                    Text(stringResource(R.string.opennes_profile_save))
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
    val allPreviews =
        remember(game.id, game.coverFrontUrl, game.fileName, game.customCoverUri) {
            NesArtwork.previews(game)
        }
    val failedUrls =
        remember(game.id, game.coverFrontUrl, game.fileName, game.customCoverUri) {
            mutableStateListOf<String>()
        }
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

/**
 * Authentic front-facing NES cardboard case: approximately 5 x 7.125 in.
 * A thin edge and low shadow suggest the original carton; no cropped art,
 * rounded app cards, title overlays or fake Nintendo marks.
 */
@Composable
fun NesBoxArtwork(
    game: Game,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(NES_BOX_ART_ASPECT_RATIO)
                .shadow(6.dp, RoundedCornerShape(1.dp))
                .background(Color(0xFF15171B))
                .border(1.dp, Color(0xFF55545A), RoundedCornerShape(1.dp)),
    ) {
        GameCover(game = game, modifier = Modifier.fillMaxSize())
        // The folded box edge remains subtle and never conceals the cover.
        Spacer(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent),
                        ),
                    ),
        )
    }
}

@Composable
fun GameCover(
    game: Game,
    modifier: Modifier = Modifier,
) {
    val fallbackDrawable =
        remember(game.id, game.systemId, game.displayName()) {
            CoverUtils.getFallbackDrawable(game)
        }
    val fallbackPainter = rememberDrawablePainter(drawable = fallbackDrawable)
    val primaryArtwork = game.displayCoverUrl()

    // Some old Libretro DB titles produce a missing thumbnail URL. For a ROM
    // with an explicit No-Intro-style region in its filename, try the matching
    // Libretro box-art filename only if the original URL fails. Keep custom
    // artwork and existing successful catalogue covers as first priority.
    val romArtwork =
        remember(game.fileName, game.systemId, game.customCoverUri) {
            NesBoxArtUrls.romFilenameFallback(game)
        }
    val preferredArtwork = NesCoverResolutionCache.preferred(primaryArtwork, romArtwork)
    var usingRomArtwork by remember(game.id, primaryArtwork, romArtwork) {
        mutableStateOf(preferredArtwork == romArtwork && romArtwork != primaryArtwork)
    }
    val artworkUrl =
        if (primaryArtwork.isNullOrBlank() || usingRomArtwork) romArtwork ?: primaryArtwork else primaryArtwork

    val hasKnownArtwork = !artworkUrl.isNullOrBlank()
    AsyncImage(
        model =
            ImageRequest.Builder(LocalContext.current)
                .data(artworkUrl)
                .apply {
                    if (hasKnownArtwork) {
                        // The same key is used by the library prefetcher and
                        // every other cover surface. On screen recreation,
                        // use the previous decoded cover as the placeholder
                        // rather than briefly painting a generated title card.
                        val key = CoverUtils.coverMemoryKey(artworkUrl!!)
                        memoryCacheKey(key)
                        placeholderMemoryCacheKey(key)
                    }
                }
                .listener(
                    onSuccess = { _, _ ->
                        NesCoverResolutionCache.recordSuccess(primaryArtwork, romArtwork, artworkUrl)
                    },
                    onError = { _, _ ->
                        if (!usingRomArtwork && romArtwork != null && artworkUrl != romArtwork) {
                            usingRomArtwork = true
                        }
                    },
                )
                .build(),
        contentDescription = game.displayName(),
        // Preserve the full printed NES carton rather than cropping it.
        modifier = modifier.aspectRatio(NES_BOX_ART_ASPECT_RATIO),
        contentScale = ContentScale.Fit,
        // Only titles genuinely missing cover data should show a title
        // placeholder. Known URLs display the previous cached artwork or
        // the neutral box surface while loading, without a title-card flash.
        placeholder = if (hasKnownArtwork) null else fallbackPainter,
        fallback = fallbackPainter,
        error = if (romArtwork != null && artworkUrl != romArtwork) null else fallbackPainter,
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

/**
 * Distinguishes first launch, an active scan and an empty scan result.
 * The picker is never launched automatically; the player chooses their folder.
 */
@Composable
private fun LibrarySetupCard(
    hasRomDirectory: Boolean,
    scanInProgress: Boolean,
    scanError: String?,
    onChooseFolder: () -> Unit,
    onRescan: () -> Unit,
) {
    val compactActions =
        LocalConfiguration.current.screenWidthDp < 360 || LocalDensity.current.fontScale > 1.2f

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = LibraryColors.panel,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, LibraryColors.border),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val title =
                when {
                    !hasRomDirectory -> R.string.opennes_setup_choose_title
                    scanInProgress -> R.string.opennes_setup_scanning_title
                    scanError == LibraryScanStatus.FOLDER_UNAVAILABLE -> R.string.opennes_setup_folder_error_title
                    scanError != null -> R.string.opennes_setup_scan_error_title
                    else -> R.string.opennes_setup_no_games_title
                }
            val message =
                when {
                    !hasRomDirectory -> R.string.opennes_setup_choose_description
                    scanInProgress -> R.string.opennes_setup_scanning_description
                    scanError == LibraryScanStatus.FOLDER_UNAVAILABLE -> R.string.opennes_setup_folder_error_description
                    scanError != null -> R.string.opennes_setup_scan_error_description
                    else -> R.string.opennes_setup_no_games_description
                }

            Text(
                text = stringResource(title),
                color = LibraryColors.foreground,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text(
                text = stringResource(message),
                color = LibraryColors.muted,
                fontSize = 14.sp,
            )

            when {
                !hasRomDirectory -> {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onChooseFolder,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = LibraryColors.red,
                                contentColor = LibraryColors.foreground,
                            ),
                    ) {
                        Text(stringResource(R.string.opennes_setup_choose_action))
                    }
                }
                scanInProgress -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = LibraryColors.red,
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.opennes_setup_scanning_progress),
                            color = LibraryColors.muted,
                            fontSize = 12.sp,
                        )
                    }
                }
                else -> {
                    if (compactActions) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onRescan,
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = LibraryColors.red,
                                        contentColor = LibraryColors.foreground,
                                    ),
                            ) {
                                Text(stringResource(R.string.opennes_setup_rescan_action))
                            }
                            OutlinedButton(
                                onClick = onChooseFolder,
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, LibraryColors.border),
                                colors =
                                    ButtonDefaults.outlinedButtonColors(
                                        contentColor = LibraryColors.foreground,
                                    ),
                            ) {
                                Text(stringResource(R.string.opennes_setup_change_action))
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onRescan,
                                modifier = Modifier.weight(1f),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = LibraryColors.red,
                                        contentColor = LibraryColors.foreground,
                                    ),
                            ) {
                                Text(stringResource(R.string.opennes_setup_rescan_action))
                            }
                            OutlinedButton(
                                onClick = onChooseFolder,
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, LibraryColors.border),
                                colors =
                                    ButtonDefaults.outlinedButtonColors(
                                        contentColor = LibraryColors.foreground,
                                    ),
                            ) {
                                Text(stringResource(R.string.opennes_setup_change_action))
                            }
                        }
                    }
                }
            }
        }
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
