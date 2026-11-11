package com.swordfish.lemuroid.app.mobile.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.launch
import java.util.Locale

private enum class LibrarySource {
    ALL,
    LOCAL,
    ROMM,
}

/**
 * Browsable counterpart to the compact physical NES shelf. A long horizontal
 * rail must never be the only way to navigate a multi-hundred-game RomM library.
 * A preview represents metadata only; selecting it opens Download details.
 */
@Composable
internal fun LibraryBrowserDialog(
    games: List<Game>,
    onDismiss: () -> Unit,
    onSelect: (Game) -> Unit,
) {
    val foreground = Color(0xFFF2EDE6)
    val muted = Color(0xFFB2B2B4)
    val background = Color(0xFF111317)
    val panel = Color(0xFF272B33)
    val red = Color(0xFFB73439)

    var search by rememberSaveable { mutableStateOf("") }
    var source by remember { mutableStateOf(LibrarySource.ALL) }
    val filtered =
        remember(games, search, source) {
            val term = search.trim().lowercase(Locale.ROOT)
            games.asSequence()
                .filter {
                    when (source) {
                        LibrarySource.ALL -> true
                        LibrarySource.LOCAL -> it.id > 0
                        LibrarySource.ROMM -> it.id < 0
                    }
                }
                .filter {
                    term.isBlank() ||
                        it.displayName().lowercase(Locale.ROOT).contains(term) ||
                        it.fileName.lowercase(Locale.ROOT).contains(term)
                }
                .sortedWith(
                    compareBy<Game> { it.displayName().lowercase(Locale.ROOT) }
                        .thenBy { it.fileName.lowercase(Locale.ROOT) },
                )
                .toList()
        }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(search, source) { gridState.scrollToItem(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            color = background,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.opennes_discover_shelf),
                            color = foreground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 23.sp,
                        )
                        Text(
                            stringResource(R.string.opennes_romm_browser_count, filtered.size),
                            color = muted,
                            fontSize = 13.sp,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = foreground,
                        )
                    }
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    label = { Text(stringResource(R.string.opennes_romm_browser_search)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (choice in LibrarySource.entries) {
                        val label =
                            when (choice) {
                                LibrarySource.ALL -> R.string.opennes_romm_filter_all
                                LibrarySource.LOCAL -> R.string.opennes_romm_filter_installed
                                LibrarySource.ROMM -> R.string.opennes_romm_filter_remote
                            }
                        FilterChip(
                            selected = source == choice,
                            onClick = { source = choice },
                            label = { Text(stringResource(label)) },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = red,
                                    selectedLabelColor = foreground,
                                    containerColor = panel,
                                    labelColor = foreground,
                                ),
                            border = BorderStroke(1.dp, Color(0xFF414650)),
                        )
                    }
                }

                if (search.isBlank() && filtered.size > 24) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        items(26) { index ->
                            val letter = ('A' + index).toString()
                            TextButton(
                                onClick = {
                                    val target =
                                        filtered.indexOfFirst {
                                            it.displayName().uppercase(Locale.ROOT) >= letter
                                        }
                                    if (target >= 0) {
                                        scope.launch { gridState.animateScrollToItem(target) }
                                    }
                                },
                            ) {
                                Text(letter, color = red, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    Text(
                        stringResource(R.string.opennes_romm_browser_empty),
                        modifier = Modifier.padding(22.dp),
                        color = muted,
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 118.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(filtered, key = { if (it.id < 0) "romm:${it.id}" else "local:${it.id}" }) { game ->
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(game) },
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                NesBoxArtwork(
                                    game = game,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = game.displayName(),
                                    color = foreground,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (game.id < 0) {
                                    Text(
                                        stringResource(R.string.opennes_romm_download),
                                        color = muted,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
