package com.swordfish.lemuroid.app.mobile.feature.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.shared.covers.NesCoverPrefetcher
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.app.shared.settings.StorageFrameworkPickerLauncher
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.library.metadata.GameMetadata
import com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@OptIn(FlowPreview::class)
class HomeViewModel(
    appContext: Context,
    retrogradeDb: RetrogradeDatabase,
    private val metadataProvider: GameMetadataProvider,
) : ViewModel() {
    companion object {
        const val CAROUSEL_MAX_ITEMS = 10
        const val DEBOUNCE_TIME = 100L
    }

    class Factory(
        val appContext: Context,
        val retrogradeDb: RetrogradeDatabase,
        val metadataProvider: GameMetadataProvider,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(appContext, retrogradeDb, metadataProvider) as T
        }
    }

    data class UIState(
        val favoritesGames: List<Game> = emptyList(),
        val recentGames: List<Game> = emptyList(),
        val discoveryGames: List<Game> = emptyList(),
        val indexInProgress: Boolean = true,
        val showNoNotificationPermissionCard: Boolean = false,
        val showNoGamesCard: Boolean = false,
    )

    private val notificationsPermissionEnabledState = MutableStateFlow(true)
    private val uiStates = MutableStateFlow(UIState())
    private val coverPrefetcher = NesCoverPrefetcher(appContext.applicationContext)

    fun getViewStates(): Flow<UIState> {
        return uiStates
    }

    /**
     * Search the bundled NES catalogue explicitly; no remote account, scraping
     * credentials or modifications to existing games are involved.
     */
    fun searchMetadata(
        query: String,
        onComplete: (List<GameMetadata>) -> Unit,
    ) {
        viewModelScope.launch {
            val matches =
                withContext(Dispatchers.IO) {
                    metadataProvider.searchMetadata(query)
                }
            onComplete(matches)
        }
    }

    /**
     * Apply only the catalogue fields explicitly selected by the player.
     * Load the latest database row first so saves, favorites, custom art,
     * custom names and time tracked since opening the detail sheet survive.
     */
    fun applyMetadataMatch(
        gameId: Int,
        selected: GameMetadata,
        onComplete: (Game?) -> Unit,
    ) {
        viewModelScope.launch {
            val updated =
                withContext(Dispatchers.IO) {
                    runCatching {
                        if (selected.system != SystemID.NES.dbname) return@runCatching null
                        val selectedTitle = selected.name?.trim()?.takeIf { it.isNotEmpty() }
                            ?: return@runCatching null
                        val current = retrogradeDb.gameDao().selectById(gameId) ?: return@runCatching null
                        val corrected =
                            current.copy(
                                title = selectedTitle,
                                developer = selected.developer,
                                coverFrontUrl = selected.thumbnail,
                                lastMetadataCheckedAt = System.currentTimeMillis(),
                            )
                        retrogradeDb.gameDao().update(corrected)
                        corrected
                    }.onFailure {
                        Timber.e(it, "Failed to update selected NES game metadata")
                    }.getOrNull()
                }
            onComplete(updated)
        }
    }

    fun changeLocalStorageFolder(context: Context) {
        StorageFrameworkPickerLauncher.pickFolder(context)
    }

    fun updatePermissions(context: Context) {
        notificationsPermissionEnabledState.value = isNotificationsPermissionGranted(context)
    }

    private fun isNotificationsPermissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        val permissionResult =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            )

        return permissionResult == PackageManager.PERMISSION_GRANTED
    }

    private fun buildViewState(
        favoritesGames: List<Game>,
        recentGames: List<Game>,
        discoveryGames: List<Game>,
        indexInProgress: Boolean,
        notificationsPermissionEnabled: Boolean,
    ): UIState {
        val noGames = recentGames.isEmpty() && favoritesGames.isEmpty() && discoveryGames.isEmpty()

        return UIState(
            favoritesGames = favoritesGames,
            recentGames = recentGames,
            discoveryGames = discoveryGames,
            indexInProgress = indexInProgress,
            showNoNotificationPermissionCard = !notificationsPermissionEnabled,
            showNoGamesCard = noGames,
        )
    }

    init {
        viewModelScope.launch {
            val uiStatesFlow =
                combine(
                    favoritesGames(retrogradeDb),
                    recentGames(retrogradeDb),
                    discoveryGames(retrogradeDb),
                    indexingInProgress(appContext),
                    notificationsPermissionEnabledState,
                    ::buildViewState,
                )

            uiStatesFlow
                .debounce(DEBOUNCE_TIME)
                .flowOn(Dispatchers.IO)
                .collect { state ->
                    uiStates.value = state
                    val games = state.recentGames + state.favoritesGames + state.discoveryGames
                    // Cache just shelf covers; never download an entire ROM collection.
                    launch(Dispatchers.IO) { coverPrefetcher.prefetch(games) }
                }
        }
    }

    private fun indexingInProgress(appContext: Context) =
        PendingOperationsMonitor(appContext).anyLibraryOperationInProgress()

    private fun discoveryGames(retrogradeDb: RetrogradeDatabase) =
        retrogradeDb.gameDao().selectFirstNotPlayed(CAROUSEL_MAX_ITEMS)

    private fun recentGames(retrogradeDb: RetrogradeDatabase) =
        retrogradeDb.gameDao().selectRecentGames(CAROUSEL_MAX_ITEMS)

    private fun favoritesGames(retrogradeDb: RetrogradeDatabase) =
        retrogradeDb.gameDao().selectFirstFavorites(CAROUSEL_MAX_ITEMS)
}
