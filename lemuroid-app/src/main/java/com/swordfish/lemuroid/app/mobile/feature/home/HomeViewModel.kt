package com.swordfish.lemuroid.app.mobile.feature.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.lemuroid.app.mobile.feature.main.OpenNesStartupGate
import com.swordfish.lemuroid.app.shared.covers.NesCoverPrefetcher
import com.swordfish.lemuroid.app.shared.covers.NesFixMatchCover
import com.swordfish.lemuroid.app.shared.library.LibraryScanStatus
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.app.shared.metadata.WikidataNesMetadataSource
import com.swordfish.lemuroid.app.shared.metadata.needsOnlineMetadata
import com.swordfish.lemuroid.app.shared.romm.RommApiClient
import com.swordfish.lemuroid.app.shared.romm.RommCoverBackfill
import com.swordfish.lemuroid.app.shared.romm.RommLibraryCatalog
import com.swordfish.lemuroid.app.shared.settings.StorageFrameworkPickerLauncher
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.library.metadata.GameMetadata
import com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider
import com.swordfish.lemuroid.lib.library.metadata.NesGameTitle
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
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
        private const val ONLINE_BATCH_SIZE = 12
        private const val ONLINE_BATCH_GAP_MS = 2_500L
        private const val ONLINE_RETRY_INTERVAL_MS = 30L * 24L * 60L * 60L * 1000L
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
        val libraryReady: Boolean = false,
        val scanInProgress: Boolean = false,
        val hasRomDirectory: Boolean = false,
        val scanError: String? = null,
        val showNoNotificationPermissionCard: Boolean = false,
        val showNoGamesCard: Boolean = false,
    )

    private val notificationsPermissionEnabledState = MutableStateFlow(true)
    private val uiStates = MutableStateFlow(UIState())
    private val context = appContext.applicationContext
    val indexedNesFileNames: Flow<Set<String>> =
        retrogradeDb.gameDao().observeNesFileNames().map { it.toSet() }

    fun refreshRomMLibrary(force: Boolean = false) {
        viewModelScope.launch { RommLibraryCatalog.refresh(context, force) }
    }

    /** Give downloaded RomM games without art the cover RomM shows for them. */
    fun backfillRommCovers(remote: List<RommApiClient.Rom>) {
        if (remote.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val dao = retrogradeDb.gameDao()
            val local =
                dao.selectAllNesGames().map {
                    RommCoverBackfill.LocalGame(it.id, it.fileName, it.coverFrontUrl, it.customCoverUri)
                }
            RommCoverBackfill.covers(local, remote).forEach { (gameId, url) ->
                dao.updateCatalogueCover(gameId, url)
            }
        }
    }

    fun checkRomMConnectivity() {
        viewModelScope.launch { RommLibraryCatalog.checkConnectivity(context) }
    }

    fun downloadFromRomM(rom: RommApiClient.Rom) {
        viewModelScope.launch { RommLibraryCatalog.download(context, rom) }
    }

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
                    val direct = metadataProvider.searchMetadata(query)
                    if (direct.isNotEmpty()) {
                        direct
                    } else {
                        // The offline database sometimes stores inverted articles
                        // ("Legend of Zelda, The") and extra ROM revision tags.
                        val withoutArticle =
                            query.trim().replace(
                                Regex("""^(?:The|An|A)\s+""", RegexOption.IGNORE_CASE),
                                "",
                            )
                        if (withoutArticle.length >= 3 && withoutArticle != query.trim()) {
                            metadataProvider.searchMetadata(withoutArticle)
                        } else {
                            emptyList()
                        }
                    }
                }
            // OpenNES is NES-only. Duplicate catalogue rows should not
            // crowd the user's "Find details" results or offer non-NES art.
            onComplete(
                matches
                    .asSequence()
                    .filter { it.system == SystemID.NES.dbname }
                    .distinctBy { Triple(it.name?.trim()?.lowercase(), it.developer, it.thumbnail) }
                    .toList(),
            )
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
    ): Job =
        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    wikidataSource.lookup(title)
                }
            onResult(result)
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
                        val canFill = old.needsOnlineMetadata(candidate)
                        if (!canFill) return@runCatching null

                        val changed =
                            dao.fillMissingOnlineGameInfo(
                                gameId = gameId,
                                genre = candidate.genre,
                                releaseYear = candidate.releaseYear,
                                description = candidate.description,
                                sourceId = candidate.entityId,
                                wikipediaTitle = candidate.wikipediaArticleTitle,
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

    /** Plex-style manual matching: present verified NES candidates first. */
    fun searchFixMatch(
        query: String,
        onResult: (WikidataNesMetadataSource.CandidateResults) -> Unit,
    ): Job =
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { wikidataSource.searchManualCandidates(query) }
            onResult(result)
        }

    fun resolveFixMatch(
        candidateId: String,
        onResult: (WikidataNesMetadataSource.Result) -> Unit,
    ): Job =
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { wikidataSource.resolveManualCandidate(candidateId) }
            onResult(result)
        }

    /** A selected, verified candidate replaces editorial metadata only. */
    fun applyFixMatch(
        gameId: Int,
        candidate: WikidataNesMetadataSource.Result.Match,
        selectedTitle: String,
        onResult: (Game?) -> Unit,
    ) {
        viewModelScope.launch {
            val updated =
                withContext(Dispatchers.IO) {
                    runCatching {
                        if (!candidate.entityId.matches(Regex("Q[1-9][0-9]*")) ||
                            selectedTitle.trim().length !in 1..120
                        ) {
                            return@runCatching null
                        }
                        val dao = retrogradeDb.gameDao()
                        if (
                            dao.applySelectedOnlineMatch(
                                gameId = gameId,
                                selectedTitle = selectedTitle.trim(),
                                genre = candidate.genre,
                                releaseYear = candidate.releaseYear,
                                description = candidate.description?.takeIf { it.isNotBlank() },
                                sourceId = candidate.entityId,
                                wikipediaTitle = candidate.wikipediaArticleTitle,
                                checkedAt = System.currentTimeMillis(),
                            ) == 0
                        ) {
                            return@runCatching null
                        }
                        // Show the corrected game's box art too. Only an exact
                        // title in the bundled catalogue is used; otherwise the
                        // current cover stays.
                        fixMatchCover(selectedTitle.trim())?.let { cover ->
                            dao.updateCatalogueCover(gameId, cover)
                        }
                        dao.selectById(gameId)
                    }.onFailure {
                        Timber.e(it, "Could not apply selected NES Fix Match result")
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
    private suspend fun fixMatchCover(selectedTitle: String): String? {
        val catalogue =
            NesFixMatchCover.searchTerms(selectedTitle)
                .flatMap { term -> metadataProvider.searchMetadata(term) }
        return NesFixMatchCover.choose(selectedTitle, catalogue)
    }

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
                        val cacheKey = NesGameTitle.clean(game.title).lowercase(java.util.Locale.ROOT)
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
                                        val canFill = latest?.needsOnlineMetadata(result) == true
                                        if (canFill) {
                                            dao.fillMissingOnlineGameInfo(
                                                gameId = game.id,
                                                genre = result.genre,
                                                releaseYear = result.releaseYear,
                                                description = result.description,
                                                sourceId = result.entityId,
                                                wikipediaTitle = result.wikipediaArticleTitle,
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
        scanInProgress: Boolean,
        hasRomDirectory: Boolean,
        scanError: String?,
        notificationsPermissionEnabled: Boolean,
    ): UIState {
        val noGames = recentGames.isEmpty() && favoritesGames.isEmpty() && discoveryGames.isEmpty()

        return UIState(
            favoritesGames = favoritesGames,
            recentGames = recentGames,
            discoveryGames = discoveryGames,
            indexInProgress = indexInProgress,
            scanInProgress = scanInProgress,
            hasRomDirectory = hasRomDirectory,
            scanError = scanError,
            // First-run setup is more important than the optional notification prompt.
            showNoNotificationPermissionCard = !notificationsPermissionEnabled && !noGames,
            showNoGamesCard = noGames,
        )
    }

    init {
        viewModelScope.launch {
            val operationsMonitor = PendingOperationsMonitor(appContext)
            val workState =
                combine(
                    operationsMonitor.anyLibraryOperationInProgress(),
                    operationsMonitor.isDirectoryScanInProgress(),
                ) { indexing, scanning ->
                    indexing to scanning
                }

            // The picker writes this legacy preference. Observing it ensures
            // returning from Android's document picker refreshes onboarding
            // immediately, even when the user cancels selection.
            val folderKey = appContext.getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder)
            val folderFlow =
                FlowSharedPreferences(SharedPreferencesHelper.getLegacySharedPreferences(appContext))
                    .getString(folderKey)
                    .asFlow()
            val scanErrorFlow =
                FlowSharedPreferences(LibraryScanStatus.preferences(appContext))
                    .getString(LibraryScanStatus.ERROR_KEY)
                    .asFlow()
            val setupState =
                combine(
                    folderFlow,
                    notificationsPermissionEnabledState,
                    scanErrorFlow,
                ) { directory, notificationsEnabled, scanError ->
                    Triple(directory.isNotBlank(), notificationsEnabled, scanError.takeIf { it.isNotBlank() })
                }

            val uiStatesFlow =
                combine(
                    favoritesGames(retrogradeDb),
                    recentGames(retrogradeDb),
                    discoveryGames(retrogradeDb),
                    workState,
                    setupState,
                ) { favorites, recent, discovery, work, setup ->
                    buildViewState(
                        favoritesGames = favorites,
                        recentGames = recent,
                        discoveryGames = discovery,
                        indexInProgress = work.first,
                        scanInProgress = work.second,
                        hasRomDirectory = setup.first,
                        scanError = setup.third,
                        notificationsPermissionEnabled = setup.second,
                    )
                }

            uiStatesFlow
                .debounce(DEBOUNCE_TIME)
                .flowOn(Dispatchers.IO)
                .collect { state ->
                    // The initial UI state is empty until the library flows
                    // produce a snapshot. Do not briefly present an empty
                    // shelf as if no games exist at startup.
                    uiStates.value = state.copy(libraryReady = true)
                    OpenNesStartupGate.markLibraryReady()
                    val games = state.recentGames + state.favoritesGames + state.discoveryGames
                    // Cache just shelf covers; never download an entire ROM collection.
                    launch(Dispatchers.IO) { coverPrefetcher.prefetch(games) }
                }
        }
    }

    private fun discoveryGames(retrogradeDb: RetrogradeDatabase) = retrogradeDb.gameDao().observeAllNesGames()

    private fun recentGames(retrogradeDb: RetrogradeDatabase) =
        retrogradeDb.gameDao().selectRecentGames(CAROUSEL_MAX_ITEMS)

    private fun favoritesGames(retrogradeDb: RetrogradeDatabase) =
        retrogradeDb.gameDao().selectFirstFavorites(CAROUSEL_MAX_ITEMS)
}
