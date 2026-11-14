package com.swordfish.lemuroid.app.mobile.feature.main

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.game.GameActivity
import com.swordfish.lemuroid.app.mobile.feature.game.GameService
import com.swordfish.lemuroid.app.mobile.feature.game.NesControllerOnboardingStore
import com.swordfish.lemuroid.app.mobile.feature.home.HomeScreen
import com.swordfish.lemuroid.app.mobile.feature.home.HomeViewModel
import com.swordfish.lemuroid.app.mobile.feature.romm.RommSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.search.SearchScreen
import com.swordfish.lemuroid.app.mobile.feature.search.SearchViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.advanced.AdvancedSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.advanced.AdvancedSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.general.ControllerAppearanceScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.general.HardcoreSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.general.LibraryMaintenanceScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.general.RetroAchievementsSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.savesync.SaveSyncSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.savesync.SaveSyncSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.game.ActiveGameSession
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.GameLauncher
import com.swordfish.lemuroid.app.shared.game.GameProcessLock
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.main.BusyActivity
import com.swordfish.lemuroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
import com.swordfish.lemuroid.common.coroutines.safeLaunch
import com.swordfish.lemuroid.lib.android.RetrogradeComponentActivity
import com.swordfish.lemuroid.lib.injection.PerActivity
import com.swordfish.lemuroid.lib.library.covers.CustomCoverManager
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import dagger.Provides
import de.charlex.compose.material3.HtmlText
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.compose.ui.graphics.Color as ComposeColor

@OptIn(DelicateCoroutinesApi::class)
class MainActivity : RetrogradeComponentActivity(), BusyActivity {
    @Inject
    lateinit var gameLaunchTaskHandler: GameLaunchTaskHandler

    @Inject
    lateinit var saveSyncManager: SaveSyncManager

    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var gameMetadataProvider: GameMetadataProvider

    @Inject
    lateinit var gameInteractor: GameInteractor

    @Inject
    lateinit var settingsInteractor: SettingsInteractor

    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext, saveSyncManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            val navController = rememberNavController()
            var showingStartup by remember { mutableStateOf(savedInstanceState == null) }
            // First-run setup opens instead of the library, so there is no
            // library snapshot to wait for.
            val opensLibrary = remember { !OpenNesFirstRunStore(applicationContext).shouldShow() }
            LaunchedEffect(showingStartup) {
                if (showingStartup) {
                    // Stay up until the first library snapshot (bounded),
                    // never for RomM or cover downloads.
                    OpenNesStartupGate.awaitHandoff(waitForLibrary = opensLibrary)
                    showingStartup = false
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                MainScreen(navController)
                if (showingStartup) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = ComposeColor(0xFF0F1013),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "OpenNES",
                                color = ComposeColor.White,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            CircularProgressIndicator(
                                modifier = Modifier.padding(top = 24.dp).size(26.dp),
                                color = ComposeColor(0xFFD77B80),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                stringResource(R.string.opennes_library_loading),
                                modifier = Modifier.padding(top = 18.dp),
                                color = ComposeColor(0xFFB9BAC0),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen(navController: NavHostController) {
        AppTheme {
            val firstRunStore = remember { OpenNesFirstRunStore(applicationContext) }
            val firstRoute =
                remember {
                    if (firstRunStore.shouldShow()) MainRoute.ONBOARDING.route else MainRoute.HOME.route
                }
            val navBackStackEntry = navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry.value?.destination
            val currentRoute =
                currentDestination?.route
                    ?.let { MainRoute.findByRoute(it) }
                    ?: MainRoute.HOME

            val infoDialogDisplayed =
                remember {
                    mutableStateOf(false)
                }

            LaunchedEffect(currentRoute) {
                mainViewModel.changeRoute(currentRoute)
            }

            val selectedGameState =
                remember {
                    mutableStateOf<Game?>(null)
                }

            val activeSession = remember { mutableStateOf(ActiveGameSession.current(applicationContext)) }
            val pendingSwitch = remember { mutableStateOf<Game?>(null) }
            val switching = remember { mutableStateOf(false) }
            val switchError = remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            ComposableLifecycle { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    activeSession.value = ActiveGameSession.current(applicationContext)
                }
            }

            val resumeGame = {
                if (ActiveGameSession.current(applicationContext) != null) {
                    startActivity(
                        Intent(this@MainActivity, GameActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                    )
                }
            }

            val onGameLongClick = { game: Game ->
                selectedGameState.value = game
            }

            val onGameClick = { game: Game ->
                val running = ActiveGameSession.current(applicationContext)
                activeSession.value = running
                when {
                    running == null -> gameInteractor.onGamePlay(game)
                    running.gameId == game.id -> resumeGame()
                    else -> {
                        switchError.value = false
                        pendingSwitch.value = game
                    }
                }
            }

            val onGameFavoriteToggle = { game: Game, isFavorite: Boolean ->
                gameInteractor.onFavoriteToggle(game, isFavorite)
            }

            val onHelpPressed = {
                infoDialogDisplayed.value = true
            }

            pendingSwitch.value?.let { next ->
                Dialog(
                    onDismissRequest = {
                        if (!switching.value) pendingSwitch.value = null
                    },
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                stringResource(R.string.opennes_switch_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(
                                    R.string.opennes_switch_body,
                                    activeSession.value?.title
                                        ?: stringResource(R.string.opennes_switch_current_game),
                                    next.displayName(),
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (switchError.value) {
                                Text(
                                    stringResource(R.string.opennes_switch_failed),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Button(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                enabled = !switching.value,
                                onClick = {
                                    switching.value = true
                                    switchError.value = false
                                    scope.launch {
                                        try {
                                            GameService.requestCloseRunningGame(applicationContext)
                                            val stopped =
                                                withContext(Dispatchers.IO) {
                                                    repeat(150) {
                                                        val locked =
                                                            runCatching {
                                                                GameProcessLock.isHeldByAnotherProcess(
                                                                    applicationContext,
                                                                )
                                                            }.getOrDefault(true)
                                                        if (!locked) return@withContext true
                                                        delay(100)
                                                    }
                                                    false
                                                }
                                            activeSession.value = ActiveGameSession.current(applicationContext)
                                            if (stopped) {
                                                pendingSwitch.value = null
                                                gameInteractor.onGamePlay(next)
                                            } else {
                                                switchError.value = true
                                            }
                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            switchError.value = true
                                        } finally {
                                            switching.value = false
                                        }
                                    }
                                },
                            ) {
                                if (switching.value) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(end = 12.dp).size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp,
                                    )
                                }
                                Text(
                                    stringResource(
                                        if (switching.value) {
                                            R.string.opennes_switch_working
                                        } else {
                                            R.string.opennes_switch_confirm
                                        },
                                    ),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    enabled = !switching.value,
                                    onClick = { pendingSwitch.value = null },
                                ) { Text(stringResource(R.string.cancel)) }
                                OutlinedButton(
                                    enabled = !switching.value,
                                    onClick = {
                                        pendingSwitch.value = null
                                        resumeGame()
                                    },
                                ) { Text(stringResource(R.string.opennes_switch_resume_current)) }
                            }
                        }
                    }
                }
            }

            val mainUIState =
                mainViewModel.state
                    .collectAsState(MainViewModel.UiState())
                    .value

            Scaffold(
                // Home and settings consume this padding once; include cutouts
                // as well as system bars when edge-to-edge is enforced.
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    if (currentRoute != MainRoute.HOME && currentRoute != MainRoute.ONBOARDING) {
                        MainTopBar(
                            currentRoute = currentRoute,
                            navController = navController,
                            onHelpPressed = onHelpPressed,
                            mainUIState = mainUIState,
                            onUpdateQueryString = { mainViewModel.changeQueryString(it) },
                        )
                    }
                },
            ) { padding ->
                NavHost(
                    modifier = Modifier.fillMaxSize(),
                    navController = navController,
                    startDestination = firstRoute,
                ) {
                    composable(MainRoute.ONBOARDING) {
                        OpenNesFirstRunGuide(
                            modifier = Modifier.padding(padding),
                            onOpenSettings = { navController.navigateToRoute(it) },
                            onFinish = {
                                firstRunStore.complete()
                                NesControllerOnboardingStore(applicationContext).complete()
                                navController.navigate(MainRoute.HOME.route) {
                                    popUpTo(MainRoute.ONBOARDING.route) { inclusive = true }
                                }
                            },
                        )
                    }
                    composable(MainRoute.HOME) {
                        HomeScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        HomeViewModel.Factory(
                                            applicationContext,
                                            retrogradeDb,
                                            gameMetadataProvider,
                                        ),
                                ),
                            onGameClick = onGameClick,
                            activeSession = activeSession.value,
                            onResumeActive = resumeGame,
                            onGameLongClick = onGameLongClick,
                            onPinShortcut = { gameInteractor.onCreateShortcut(it) },
                            pinShortcutSupported = gameInteractor.supportShortcuts(),
                            onFavoriteToggle = onGameFavoriteToggle,
                            onSearchClick = { navController.navigateToRoute(MainRoute.SEARCH) },
                            onSettingsClick = { navController.navigateToRoute(MainRoute.SETTINGS) },
                        )
                    }
                    composable(MainRoute.SEARCH) {
                        SearchScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory = SearchViewModel.Factory(retrogradeDb, applicationContext),
                                ),
                            searchQuery = mainUIState.searchQuery,
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                            onGameFavoriteToggle = onGameFavoriteToggle,
                            onResetSearchQuery = { mainViewModel.changeQueryString("") },
                        )
                    }
                    composable(MainRoute.SETTINGS) {
                        SettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        SettingsViewModel.Factory(
                                            applicationContext,
                                            settingsInteractor,
                                            saveSyncManager,
                                            FlowSharedPreferences(
                                                SharedPreferencesHelper.getLegacySharedPreferences(
                                                    applicationContext,
                                                ),
                                            ),
                                        ),
                                ),
                            navController = navController,
                        )
                    }
                    composable(MainRoute.SETTINGS_LIBRARY) {
                        LibraryMaintenanceScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        HomeViewModel.Factory(
                                            applicationContext,
                                            retrogradeDb,
                                            gameMetadataProvider,
                                        ),
                                ),
                        )
                    }
                    composable(MainRoute.SETTINGS_ADVANCED) {
                        AdvancedSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        AdvancedSettingsViewModel.Factory(
                                            applicationContext,
                                            settingsInteractor,
                                        ),
                                ),
                            navController = navController,
                        )
                    }
                    composable(MainRoute.SETTINGS_CONTROLLER_APPEARANCE) {
                        ControllerAppearanceScreen(modifier = Modifier.padding(padding))
                    }
                    composable(MainRoute.SETTINGS_INPUT_DEVICES) {
                        InputDevicesSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        InputDevicesSettingsViewModel.Factory(
                                            applicationContext,
                                            inputDeviceManager,
                                        ),
                                ),
                        )
                    }
                    composable(MainRoute.SETTINGS_SAVE_SYNC) {
                        SaveSyncSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        SaveSyncSettingsViewModel.Factory(
                                            application,
                                            saveSyncManager,
                                        ),
                                ),
                        )
                    }
                    composable(MainRoute.SETTINGS_ROMM) {
                        RommSettingsScreen(
                            modifier = Modifier.padding(padding),
                            loadGames = { retrogradeDb.gameDao().selectAllNesGames() },
                        )
                    }
                    composable(MainRoute.SETTINGS_RETROACHIEVEMENTS) {
                        RetroAchievementsSettingsScreen(modifier = Modifier.padding(padding))
                    }
                    composable(MainRoute.SETTINGS_HARDCORE) {
                        HardcoreSettingsScreen(modifier = Modifier.padding(padding))
                    }
                }
            }

            MainGameContextActions(
                selectedGameState = selectedGameState,
                shortcutSupported = gameInteractor.supportShortcuts(),
                onGamePlay = { gameInteractor.onGamePlay(it) },
                onGameRestart = { gameInteractor.onGameRestart(it) },
                onFavoriteToggle = { game: Game, isFavorite: Boolean ->
                    gameInteractor.onFavoriteToggle(game, isFavorite)
                },
                onCreateShortcut = { gameInteractor.onCreateShortcut(it) },
                onSetCustomName = { game, name -> gameInteractor.onSetCustomName(game, name) },
                onClearCustomName = { game -> gameInteractor.onClearCustomName(game) },
                onSetCustomCoverUri = { game, uri -> gameInteractor.onSetCustomCoverUri(game, uri) },
                onClearCustomCoverUri = { game -> gameInteractor.onClearCustomCoverUri(game) },
            )

            if (infoDialogDisplayed.value) {
                val message = remember { getString(R.string.lemuroid_help_content) }

                AlertDialog(
                    title = { Text(stringResource(R.string.mobile_settings_help)) },
                    text = { HtmlText(text = message) },
                    onDismissRequest = { infoDialogDisplayed.value = false },
                    confirmButton = {
                        TextButton(onClick = { infoDialogDisplayed.value = false }) {
                            Text(stringResource(R.string.close))
                        }
                    },
                )
            }
        }
    }

    override fun activity(): Activity = this

    override fun isBusy(): Boolean = mainViewModel.state.value.operationInProgress ?: false

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            BaseGameActivity.REQUEST_PLAY_GAME -> {
                GlobalScope.safeLaunch {
                    gameLaunchTaskHandler.handleGameFinish(
                        this@MainActivity,
                        resultCode,
                        data,
                    )
                }
            }
        }
    }

    @dagger.Module
    abstract class Module {
        @dagger.Module
        companion object {
            @Provides
            @PerActivity
            @JvmStatic
            fun settingsInteractor(
                activity: MainActivity,
                directoriesManager: DirectoriesManager,
            ) = SettingsInteractor(activity, directoriesManager)

            @Provides
            @PerActivity
            @JvmStatic
            fun gameInteractor(
                activity: MainActivity,
                retrogradeDb: RetrogradeDatabase,
                shortcutsGenerator: ShortcutsGenerator,
                gameLauncher: GameLauncher,
                customCoverManager: CustomCoverManager,
            ) = GameInteractor(activity, retrogradeDb, false, shortcutsGenerator, gameLauncher, customCoverManager)
        }
    }
}
