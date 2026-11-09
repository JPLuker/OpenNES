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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.home.GameCover
import com.swordfish.lemuroid.lib.library.db.entity.Game

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

    LaunchedEffect(Unit) { onResetSearchQuery() }
    LaunchedEffect(searchQuery) { viewModel.queryString.value = searchQuery }

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
            modifier = Modifier.padding(top = 16.dp),
        )

        when {
            searchQuery.isBlank() -> SearchMessage(stringResource(R.string.opennes_search_hint))
            games.loadState.refresh is LoadState.Loading -> {
                CircularProgressIndicator(
                    color = SearchColors.red,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 28.dp),
                )
            }
            games.loadState.refresh is LoadState.Error -> {
                SearchMessage(stringResource(R.string.opennes_search_error))
            }
            games.itemCount == 0 -> SearchMessage(stringResource(R.string.opennes_search_no_results))
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
                }
            }
        }
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
                    .combinedClickable(onClick = onPlay, onLongClick = onMore)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GameCover(game = game, modifier = Modifier.width(64.dp))
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
                    text = searchPlayTime(game),
                    color = SearchColors.muted,
                    fontSize = 12.sp,
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
            IconButton(onClick = onFavorite) {
                Icon(
                    imageVector = if (game.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription =
                        stringResource(
                            if (game.isFavorite) {
                                R.string.game_context_menu_remove_from_favorites
                            } else {
                                R.string.game_context_menu_add_to_favorites
                            },
                        ),
                    tint = if (game.isFavorite) SearchColors.red else SearchColors.muted,
                )
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
