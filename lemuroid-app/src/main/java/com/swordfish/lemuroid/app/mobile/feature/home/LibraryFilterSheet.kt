package com.swordfish.lemuroid.app.mobile.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R

/** Filters stay on the home screen: no second browse page or extra navbar. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LibraryFilterSheet(
    location: LibraryLocation,
    sort: LibrarySort,
    genre: String?,
    genres: List<String>,
    onLocation: (LibraryLocation) -> Unit,
    onSort: (LibrarySort) -> Unit,
    onGenre: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C2027),
        contentColor = Color(0xFFF2EDE6),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.opennes_library_filter_title),
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.opennes_library_filter_location))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryLocation.entries.forEach { option ->
                    FilterChip(
                        selected = location == option,
                        onClick = { onLocation(option) },
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        LibraryLocation.ALL -> R.string.opennes_romm_filter_all
                                        LibraryLocation.INSTALLED -> R.string.opennes_romm_filter_installed
                                        LibraryLocation.ROMM -> R.string.opennes_romm_filter_remote
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            Text(stringResource(R.string.opennes_library_filter_sort))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibrarySort.entries.forEach { option ->
                    FilterChip(
                        selected = sort == option,
                        onClick = { onSort(option) },
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        LibrarySort.TITLE -> R.string.opennes_library_sort_title
                                        LibrarySort.RECENT -> R.string.opennes_library_sort_recent
                                        LibrarySort.RELEASE_YEAR -> R.string.opennes_library_sort_year
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            Text(stringResource(R.string.opennes_library_filter_genre))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = genre == null,
                    onClick = { onGenre(null) },
                    label = { Text(stringResource(R.string.opennes_romm_filter_all)) },
                )
                genres.forEach { available ->
                    FilterChip(
                        selected = genre == available,
                        onClick = { onGenre(available) },
                        label = { Text(available) },
                    )
                }
            }
            if (genres.isEmpty()) {
                Text(
                    stringResource(R.string.opennes_library_genre_unavailable),
                    color = Color(0xFFB2B2B4),
                )
            }
        }
    }
}
