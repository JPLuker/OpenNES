package com.swordfish.lemuroid.app.mobile.feature.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.home.NesBoxArtwork
import com.swordfish.lemuroid.app.mobile.feature.romm.RommGameDetailsSheet
import com.swordfish.lemuroid.app.shared.romm.RommApiClient
import com.swordfish.lemuroid.app.shared.romm.RommLibraryCatalog
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import java.util.Locale

/** NES-only library search, sharing the artwork and visual language of OpenNES Home. */
private object SearchColors {
    val background = Color(0xFF111317)
    val panel = Color(0xFF1C2027)
    val border = Color(0xFF414650)
    val foreground = Color(0xFFF2EDE6)
    val muted = Color(0xFFB2B2B4)
    val red = Color(0xFFB73439)
}

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel,
    searchQuery: String,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onGameFavoriteToggle: (Game, Boolean) -> Unit,
    onResetSearchQuery: () -> Unit,
) {
    val games = viewModel.searchResults.collectAsLazyPagingItems()
    val romm = RommLibraryCatalog.state.collectAsState().value
    val indexedNames = viewModel.indexedNesFileNames.collectAsState(emptySet()).value
    val installedGames = viewModel.installedNesGames.collectAsState(emptyList()).value
    val context = LocalContext.current

    @Suppress("DEPRECATION")
    val folderPreferences =
        remember(context) {
            SharedPreferencesHelper.getLegacySharedPreferences(context.applicationContext)
        }
    val folderKey = stringResource(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder)
    val hasRomFolder = !folderPreferences.getString(folderKey, null).isNullOrBlank()
    var selectedRemote by remember { mutableStateOf<RommApiClient.Rom?>(null) }
    val visibleRemote =
        remember(romm.games, indexedNames, searchQuery) {
            val query = searchQuery.trim().lowercase(Locale.ROOT)
            if (query.isEmpty()) {
                emptyList()
            } else {
                RommLibraryCatalog.available(romm.games, indexedNames)
                    .filter {
                        it.name.lowercase(Locale.ROOT).contains(query) ||
                            it.fileName.lowercase(Locale.ROOT).contains(query)
                    }
            }
        }

    LaunchedEffect(Unit) {
        onResetSearchQuery()
        viewModel.refreshRomMLibrary()
    }
    LaunchedEffect(searchQuery) { viewModel.queryString.value = searchQuery }
    LaunchedEffect(indexedNames, romm.awaitingIndexIds) {
        RommLibraryCatalog.reconcileIndexed(indexedNames)
        if (romm.awaitingIndexIds.isNotEmpty()) {
            kotlinx.coroutines.delay(26_000)
            RommLibraryCatalog.reconcileIndexed(indexedNames)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().background(SearchColors.background).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.opennes_search_heading),
            color = SearchColors.red,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(top = 16.dp).semantics { heading() },
        )

        when {
            searchQuery.isBlank() -> SearchMessage(stringResource(R.string.opennes_search_hint))
            games.loadState.refresh is LoadState.Loading && visibleRemote.isEmpty() -> {
                CircularProgressIndicator(
                    color = SearchColors.red,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 28.dp),
                )
            }
            games.loadState.refresh is LoadState.Error && visibleRemote.isEmpty() -> {
                SearchMessage(stringResource(R.string.opennes_search_error))
            }
            games.itemCount == 0 && visibleRemote.isEmpty() -> {
                SearchMessage(stringResource(R.string.opennes_search_no_results))
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                ) {
                    items(games.itemCount, key = { games[it]?.id ?: it }) { index ->
                        val game = games[index] ?: return@items
                        SearchGameRow(
                            game = game,
                            onPlay = { onGameClick(game) },
                            onMore = { onGameLongClick(game) },
                            onFavorite = { onGameFavoriteToggle(game, !game.isFavorite) },
                        )
                    }
                    items(visibleRemote.size, key = { "romm:" + visibleRemote[it].id }) { index ->
                        val remote = visibleRemote[index]
                        val game = RommLibraryCatalog.asPreview(remote)
                        SearchGameRow(
                            game = game,
                            onPlay = { selectedRemote = remote },
                            onMore = { selectedRemote = remote },
                            onFavorite = {},
                            remote = true,
                            downloading = remote.id in romm.downloadingIds,
                            queued = remote.id in romm.queuedIds,
                            awaitingIndex = remote.id in romm.awaitingIndexIds,
                            hasRomFolder = hasRomFolder,
                            onDownload = {
                                selectedRemote = remote
                                viewModel.downloadFromRomM(remote)
                            },
                        )
                    }
                }
            }
        }
    }
    selectedRemote?.let { remote ->
        RommGameDetailsSheet(
            rom = remote,
            catalog = romm,
            hasRomFolder = hasRomFolder,
            installedGame =
                installedGames.firstOrNull {
                    it.fileName.equals(remote.fileName, ignoreCase = true)
                },
            onDismiss = { selectedRemote = null },
            onDownload = { viewModel.downloadFromRomM(remote) },
            onPlay = {
                selectedRemote = null
                onGameClick(it)
            },
        )
    }
}

@Composable
private fun SearchMessage(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = SearchColors.muted,
        )
        Text(
            text = message,
            color = SearchColors.muted,
            fontSize = 14.sp,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchGameRow(
    game: Game,
    onPlay: () -> Unit,
    onMore: () -> Unit,
    onFavorite: () -> Unit,
    remote: Boolean = false,
    downloading: Boolean = false,
    queued: Boolean = false,
    awaitingIndex: Boolean = false,
    hasRomFolder: Boolean = true,
    onDownload: () -> Unit = {},
) {
    Surface(
        color = SearchColors.panel,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, SearchColors.border),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClickLabel = stringResource(R.string.opennes_open_game_details, game.displayName()),
                        onLongClickLabel = stringResource(R.string.opennes_game_actions_for, game.displayName()),
                        onClick = onPlay,
                        onLongClick = onMore,
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NesBoxArtwork(game = game, modifier = Modifier.width(66.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = game.displayName(),
                    color = SearchColors.foreground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (remote) {
                    game.description?.takeIf { it.isNotBlank() }?.let { summary ->
                        Text(
                            text = summary,
                            color = SearchColors.muted,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                game.developer?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        color = SearchColors.muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text =
                        if (remote) {
                            stringResource(R.string.opennes_romm_available_remote)
                        } else {
                            searchPlayTime(game)
                        },
                    color = SearchColors.muted,
                    fontSize = 12.sp,
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
            if (remote) {
                Button(onClick = onDownload, enabled = hasRomFolder && !downloading && !queued && !awaitingIndex) {
                    Text(
                        stringResource(
                            when {
                                queued -> R.string.opennes_romm_phase_queued
                                downloading -> R.string.opennes_romm_downloading
                                awaitingIndex -> R.string.opennes_romm_adding_to_library
                                else -> R.string.opennes_romm_download
                            },
                        ),
                    )
                }
            } else {
                IconButton(onClick = onFavorite) {
                    Icon(
                        imageVector =
                            if (game.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription =
                            stringResource(
                                if (game.isFavorite) {
                                    R.string.opennes_remove_favorite_for
                                } else {
                                    R.string.opennes_add_favorite_for
                                },
                                game.displayName(),
                            ),
                        tint = if (game.isFavorite) SearchColors.red else SearchColors.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun searchPlayTime(game: Game): String {
    val minutes = game.totalPlayTimeMillis / 60_000L
    return when {
        game.totalPlayTimeMillis <= 0L ->
            stringResource(
                if (game.lastPlayedAt == null) {
                    R.string.opennes_not_played
                } else {
                    R.string.opennes_playtime_not_recorded
                },
            )
        minutes == 0L -> stringResource(R.string.opennes_playtime_under_minute)
        minutes < 60L -> stringResource(R.string.opennes_playtime_minutes, minutes.toInt())
        else -> stringResource(R.string.opennes_playtime_hours, (minutes / 60L).toInt(), (minutes % 60L).toInt())
    }
}
