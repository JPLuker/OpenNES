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
import com.swordfish.lemuroid.app.shared.metadata.WikidataNesMetadataSource
import com.swordfish.lemuroid.app.shared.settings.StorageFrameworkPickerLauncher
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.library.metadata.GameMetadata
import com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private val retrogradeDb: RetrogradeDatabase,
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
    private val wikidataSource = WikidataNesMetadataSource()

    enum class OnlineBatchStatus {
        IDLE,
        RUNNING,
        COMPLETE,
        STOPPED,
        UNAVAILABLE,
        NOTHING_TO_CHECK,
    }

    data class OnlineBatchState(
        val status: OnlineBatchStatus = OnlineBatchStatus.IDLE,
        val checked: Int = 0,
        val total: Int = 0,
        val updated: Int = 0,
        val unmatched: Int = 0,
        val currentTitle: String? = null,
    )

    private val batchState = MutableStateFlow(OnlineBatchState())
    val onlineBatchState: Flow<OnlineBatchState> = batchState
    private var onlineBatchJob: Job? = null

    companion object {
        private const val ONLINE_BATCH_SIZE = 12
        private const val ONLINE_BATCH_GAP_MS = 2_500L
        private const val ONLINE_RETRY_INTERVAL_MS = 30L * 24L * 60L * 60L * 1000L
    }

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
                        val selectedTitle =
                            selected.name?.trim()?.takeIf { it.isNotEmpty() }
                                ?: return@runCatching null
                        val gameDao = retrogradeDb.gameDao()
                        if (
                            gameDao.updateCatalogueMatch(
                                gameId = gameId,
                                title = selectedTitle,
                                developer = selected.developer,
                                coverFrontUrl = selected.thumbnail,
                                lastChecked = System.currentTimeMillis(),
                            ) == 0
                        ) {
                            return@runCatching null
                        }
                        gameDao.selectById(gameId)
                    }.onFailure {
                        Timber.e(it, "Failed to update selected NES game metadata")
                    }.getOrNull()
                }
            onComplete(updated)
        }
    }

    /**
     * Save user-authored game information without modifying unrelated fields.
     * Blank entries intentionally clear these optional profile fields.
     */
    fun saveGameProfile(
        gameId: Int,
        genre: String?,
        releaseYear: Int?,
        description: String?,
        onComplete: (Game?) -> Unit,
    ) {
        viewModelScope.launch {
            val updated =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val dao = retrogradeDb.gameDao()
                        val changed =
                            dao.updateGameProfile(
                                gameId = gameId,
                                genre = genre?.trim()?.takeIf { it.isNotEmpty() },
                                releaseYear = releaseYear,
                                description = description?.trim()?.takeIf { it.isNotEmpty() },
                            )
                        if (changed == 0) null else dao.selectById(gameId)
                    }.onFailure {
                        Timber.e(it, "Failed to save optional NES game information")
                    }.getOrNull()
                }
            onComplete(updated)
        }
    }

    /**
     * User-invoked online lookup. The service verifies an exact title match,
     * video-game identity and NES platform before returning any candidate.
     */
    fun lookupWikidata(
        title: String,
        onResult: (WikidataNesMetadataSource.Result) -> Unit,
    ) {
        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    wikidataSource.lookup(title)
                }
            onResult(result)
        }
    }

    /**
     * The user confirms the verified online record before it is applied.
     * Only previously blank editorial fields are populated; re-read the row
     * after a targeted update to avoid changing concurrent game state.
     */
    fun applyWikidata(
        gameId: Int,
        candidate: WikidataNesMetadataSource.Result.Match,
        onResult: (Game?) -> Unit,
    ) {
        viewModelScope.launch {
            val updated =
                withContext(Dispatchers.IO) {
                    runCatching {
                        if (!candidate.entityId.matches(Regex("Q[1-9][0-9]*"))) {
                            return@runCatching null
                        }
                        val dao = retrogradeDb.gameDao()
                        val old = dao.selectById(gameId) ?: return@runCatching null
                        val canFill =
                            (old.genre.isNullOrBlank() && !candidate.genre.isNullOrBlank()) ||
                                (old.releaseYear == null && candidate.releaseYear != null) ||
                                (old.description.isNullOrBlank() && !candidate.description.isNullOrBlank())
                        if (!canFill) return@runCatching null

                        val changed =
                            dao.fillMissingOnlineGameInfo(
                                gameId = gameId,
                                genre = candidate.genre,
                                releaseYear = candidate.releaseYear,
                                description = candidate.description,
                                sourceId = candidate.entityId,
                                checkedAt = System.currentTimeMillis(),
                            )
                        if (changed == 0) null else dao.selectById(gameId)
                    }.onFailure {
                        Timber.e(it, "Could not save verified Wikidata game information")
                    }.getOrNull()
                }
            onResult(updated)
        }
    }

    /**
     * On-demand bulk enrichment. Each tap checks up to 12 eligible NES titles.
     * Successful or unmatched lookups are cached for 30 days in Room.
     * Transient service errors stop the queue and remain retryable.
     *
     * This job belongs to the screen's ViewModel, not an indefinite background
     * worker: leaving the activity/process stops requests. No ROM data is sent.
     */
    fun startOnlineBatch() {
        if (onlineBatchJob?.isActive == true || uiStates.value.indexInProgress) return

        onlineBatchJob =
            viewModelScope.launch {
                batchState.value = OnlineBatchState(status = OnlineBatchStatus.RUNNING)
                val candidates =
                    runCatching {
                        withContext(Dispatchers.IO) {
                            retrogradeDb.gameDao().selectOnlineMetadataCandidates(
                                retryBefore = System.currentTimeMillis() - ONLINE_RETRY_INTERVAL_MS,
                                limit = ONLINE_BATCH_SIZE,
                            )
                        }
                    }.onFailure {
                        Timber.w(it, "Could not load eligible NES metadata candidates")
                    }.getOrNull()
                if (candidates == null) {
                    batchState.value = OnlineBatchState(status = OnlineBatchStatus.UNAVAILABLE)
                    return@launch
                }
                if (candidates.isEmpty()) {
                    batchState.value = OnlineBatchState(status = OnlineBatchStatus.NOTHING_TO_CHECK)
                    return@launch
                }

                batchState.value =
                    OnlineBatchState(
                        status = OnlineBatchStatus.RUNNING,
                        total = candidates.size,
                    )
                val resultsByTitle = mutableMapOf<String, WikidataNesMetadataSource.Result>()
                var checked = 0
                var updated = 0
                var unmatched = 0
                try {
                    for ((index, game) in candidates.withIndex()) {
                        if (!isActive) break
                        batchState.value =
                            batchState.value.copy(currentTitle = game.displayName())

                        // Don't issue an additional search for identical titles.
                        // Network requests for distinct games are sequential.
                        val cacheKey = game.title.trim().lowercase(java.util.Locale.ROOT)
                        val result =
                            resultsByTitle[cacheKey] ?: wikidataSource.lookup(game.title).also {
                                if (it !is WikidataNesMetadataSource.Result.Unavailable) {
                                    resultsByTitle[cacheKey] = it
                                }
                            }
                        if (!isActive) break
                        if (result is WikidataNesMetadataSource.Result.Unavailable) {
                            batchState.value =
                                batchState.value.copy(
                                    status = OnlineBatchStatus.UNAVAILABLE,
                                    currentTitle = null,
                                )
                            return@launch
                        }

                        val changed =
                            withContext(Dispatchers.IO) {
                                val dao = retrogradeDb.gameDao()
                                when (result) {
                                    is WikidataNesMetadataSource.Result.Match -> {
                                        // Re-read now; skip fields filled during the HTTP call.
                                        val latest = dao.selectById(game.id)
                                        val canFill =
                                            latest != null &&
                                                (
                                                    (latest.genre.isNullOrBlank() && !result.genre.isNullOrBlank()) ||
                                                        (latest.releaseYear == null && result.releaseYear != null) ||
                                                        (latest.description.isNullOrBlank() && !result.description.isNullOrBlank())
                                                )
                                        if (canFill) {
                                            dao.fillMissingOnlineGameInfo(
                                                gameId = game.id,
                                                genre = result.genre,
                                                releaseYear = result.releaseYear,
                                                description = result.description,
                                                sourceId = result.entityId,
                                                checkedAt = System.currentTimeMillis(),
                                            ) > 0
                                        } else {
                                            dao.markOnlineMetadataChecked(game.id, System.currentTimeMillis())
                                            false
                                        }
                                    }
                                    WikidataNesMetadataSource.Result.NoMatch -> {
                                        dao.markOnlineMetadataChecked(game.id, System.currentTimeMillis())
                                        false
                                    }
                                    WikidataNesMetadataSource.Result.Unavailable -> false
                                }
                            }
                        checked++
                        if (changed) updated++ else unmatched++
                        batchState.value =
                            OnlineBatchState(
                                status = OnlineBatchStatus.RUNNING,
                                checked = checked,
                                total = candidates.size,
                                updated = updated,
                                unmatched = unmatched,
                            )

                        // Limit the request rate even on fast or cached responses.
                        if (index < candidates.lastIndex) delay(ONLINE_BATCH_GAP_MS)
                    }
                    batchState.value =
                        batchState.value.copy(
                            status = OnlineBatchStatus.COMPLETE,
                            currentTitle = null,
                        )
                } finally {
                    if (!isActive) {
                        batchState.value =
                            batchState.value.copy(
                                status = OnlineBatchStatus.STOPPED,
                                currentTitle = null,
                            )
                    }
                }
            }
    }

    fun stopOnlineBatch() {
        onlineBatchJob?.cancel()
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
