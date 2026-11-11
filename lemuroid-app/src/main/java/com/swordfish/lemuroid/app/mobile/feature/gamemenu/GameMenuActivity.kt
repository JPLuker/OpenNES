@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.swordfish.lemuroid.app.mobile.feature.gamemenu

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.achievements.GameMenuAchievementsScreen
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.cheats.GameMenuCheatsScreen
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.coreoptions.GameMenuCoreOptionsScreen
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.coreoptions.GameMenuCoreOptionsViewModel
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.states.GameMenuStatesScreen
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.states.GameMenuStatesViewModel
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.GameMenuContract
import com.swordfish.lemuroid.app.shared.coreoptions.LemuroidCoreOption
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.common.kotlin.serializable
import com.swordfish.lemuroid.lib.android.RetrogradeComponentActivity
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import java.security.InvalidParameterException
import javax.inject.Inject

class GameMenuActivity : RetrogradeComponentActivity() {
    // The audio switch stays in the pause menu. Its selection is returned
    // when the player resumes, quits or closes the menu by system Back.
    private var pendingAudioEnabled: Boolean? = null
    private var pendingFastForwardEnabled: Boolean? = null

    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var statesManager: StatesManager

    @Inject
    lateinit var statesPreviewManager: StatesPreviewManager

    data class GameMenuRequest(
        val coreOptions: List<LemuroidCoreOption>,
        val advancedCoreOptions: List<LemuroidCoreOption>,
        val game: Game,
        val coreConfig: SystemCoreConfig,
        val audioEnabled: Boolean,
        val fastForwardSupported: Boolean,
        val fastForwardEnabled: Boolean,
        val numDisks: Int,
        val currentDisk: Int,
        val currentTiltConfiguration: TiltConfiguration,
        val allTiltConfigurations: List<TiltConfiguration>,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.containsKey(PENDING_AUDIO_KEY) == true) {
            pendingAudioEnabled = savedInstanceState.getBoolean(PENDING_AUDIO_KEY)
        }
        if (savedInstanceState?.containsKey(PENDING_SPEED_KEY) == true) {
            pendingFastForwardEnabled = savedInstanceState.getBoolean(PENDING_SPEED_KEY)
        }
        if (pendingAudioEnabled != null || pendingFastForwardEnabled != null) {
            setResult(RESULT_OK, Intent().apply { putPendingSwitches() })
        }

        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )

        val extras = intent.extras

        val gameMenuRequest =
            GameMenuRequest(
                coreOptions =
                    intent.serializable<Array<LemuroidCoreOption>>(GameMenuContract.EXTRA_CORE_OPTIONS)
                        ?.toList()
                        ?: throw InvalidParameterException("Missing EXTRA_CORE_OPTIONS"),
                advancedCoreOptions =
                    intent.serializable<Array<LemuroidCoreOption>>(GameMenuContract.EXTRA_ADVANCED_CORE_OPTIONS)
                        ?.toList()
                        ?: throw InvalidParameterException("Missing EXTRA_ADVANCED_CORE_OPTIONS"),
                game =
                    intent.serializable<Game>(GameMenuContract.EXTRA_GAME)
                        ?: throw InvalidParameterException("Missing EXTRA_GAME"),
                coreConfig =
                    intent.serializable<SystemCoreConfig>(GameMenuContract.EXTRA_SYSTEM_CORE_CONFIG)
                        ?: throw InvalidParameterException("Missing EXTRA_SYSTEM_CORE_CONFIG"),
                audioEnabled =
                    extras?.getBoolean(GameMenuContract.EXTRA_AUDIO_ENABLED, false) ?: false,
                fastForwardSupported =
                    extras?.getBoolean(GameMenuContract.EXTRA_FAST_FORWARD_SUPPORTED, false) ?: false,
                fastForwardEnabled =
                    extras?.getBoolean(GameMenuContract.EXTRA_FAST_FORWARD, false) ?: false,
                numDisks =
                    extras?.getInt(GameMenuContract.EXTRA_DISKS, 0) ?: 0,
                currentDisk =
                    extras?.getInt(GameMenuContract.EXTRA_CURRENT_DISK, 0) ?: 0,
                currentTiltConfiguration =
                    intent.serializable<TiltConfiguration>(GameMenuContract.EXTRA_CURRENT_TILT_CONFIG)
                        ?: TiltConfiguration.Disabled,
                allTiltConfigurations =
                    intent.serializable<Array<TiltConfiguration>>(GameMenuContract.EXTRA_TILT_ALL_CONFIGS)
                        ?.toList()
                        ?: emptyList(),
            )

        setContent {
            GameMenuScreen(gameMenuRequest)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingAudioEnabled?.let { outState.putBoolean(PENDING_AUDIO_KEY, it) }
        pendingFastForwardEnabled?.let { outState.putBoolean(PENDING_SPEED_KEY, it) }
        super.onSaveInstanceState(outState)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun GameMenuScreen(gameMenuRequest: GameMenuRequest) {
        AppTheme {
            var audioEnabled by rememberSaveable {
                mutableStateOf(pendingAudioEnabled ?: gameMenuRequest.audioEnabled)
            }
            var fastForwardEnabled by rememberSaveable {
                mutableStateOf(pendingFastForwardEnabled ?: gameMenuRequest.fastForwardEnabled)
            }
            val navController = rememberNavController()
            val navBackStackEntry = navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry.value?.destination

            val currentRoute =
                currentDestination?.route
                    ?.let { GameMenuRoute.findByRoute(it) }
                    ?: GameMenuRoute.HOME

            SideMenu(
                fullscreen = currentRoute == GameMenuRoute.ACHIEVEMENTS || currentRoute == GameMenuRoute.CHEATS,
            ) {
                if (currentRoute != GameMenuRoute.HOME) {
                    TopAppBar(
                    title = {
                        Text(
                            text = stringResource(currentRoute.titleId),
                            color = GameMenuStyle.foreground,
                            fontSize = if (currentRoute == GameMenuRoute.ACHIEVEMENTS) 19.sp else 22.sp,
                        )
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = GameMenuStyle.background,
                        ),
                    windowInsets = WindowInsets(0.dp),
                    navigationIcon = {
                        AnimatedContent(targetState = currentRoute.canGoBack(), label = "Back") { canGoBack ->
                            if (canGoBack) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        stringResource(R.string.back),
                                    )
                                }
                            } else {
                                IconButton(onClick = { onResult { } }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        stringResource(R.string.close),
                                    )
                                }
                            }
                        }
                    },
                )
                    Divider(modifier = Modifier.fillMaxWidth(), color = GameMenuStyle.border)
                }
                NavHost(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    navController = navController,
                    startDestination = GameMenuRoute.HOME.route,
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                ) {
                    composable(GameMenuRoute.HOME) {
                        GameMenuHomeScreen(
                            navController = navController,
                            gameMenuRequest = gameMenuRequest,
                            audioEnabled = audioEnabled,
                            onAudioChanged = { enabled ->
                                audioEnabled = enabled
                                pendingAudioEnabled = enabled
                                setResult(RESULT_OK, Intent().apply { putPendingSwitches() })
                            },
                            fastForwardEnabled = fastForwardEnabled,
                            onFastForwardChanged = { enabled ->
                                fastForwardEnabled = enabled
                                pendingFastForwardEnabled = enabled
                                setResult(RESULT_OK, Intent().apply { putPendingSwitches() })
                            },
                            onResult = ::onResult,
                        )
                    }
                    composable(GameMenuRoute.SAVE) {
                        GameMenuStatesScreen(
                            viewModel(
                                factory =
                                    GameMenuStatesViewModel.Factory(
                                        application,
                                        gameMenuRequest,
                                        statesManager,
                                        false,
                                        statesPreviewManager,
                                    ),
                            ),
                            onStateClicked = {
                                onResult { putExtra(GameMenuContract.RESULT_SAVE, it) }
                            },
                        )
                    }
                    composable(GameMenuRoute.LOAD) {
                        GameMenuStatesScreen(
                            viewModel(
                                factory =
                                    GameMenuStatesViewModel.Factory(
                                        application,
                                        gameMenuRequest,
                                        statesManager,
                                        true,
                                        statesPreviewManager,
                                    ),
                            ),
                            onStateClicked = {
                                onResult { putExtra(GameMenuContract.RESULT_LOAD, it) }
                            },
                        )
                    }
                    composable(GameMenuRoute.ACHIEVEMENTS) {
                        GameMenuAchievementsScreen(gameMenuRequest.game)
                    }
                    composable(GameMenuRoute.CHEATS) {
                        GameMenuCheatsScreen(gameMenuRequest.game)
                    }
                    composable(GameMenuRoute.OPTIONS) {
                        GameMenuCoreOptionsScreen(
                            viewModel(
                                factory = GameMenuCoreOptionsViewModel.Factory(inputDeviceManager),
                            ),
                            gameMenuRequest,
                        )
                    }
                }
                if (currentRoute == GameMenuRoute.ACHIEVEMENTS || currentRoute == GameMenuRoute.CHEATS) {
                    Button(
                        onClick = { onResult { } },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = GameMenuStyle.red,
                                contentColor = androidx.compose.ui.graphics.Color.White,
                            ),
                    ) {
                        Text(stringResource(R.string.game_context_menu_resume))
                    }
                }
            }
        }
    }

    @Composable
    private fun SideMenu(
        fullscreen: Boolean = false,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            val panelWidth =
                remember(maxWidth, fullscreen) {
                    if (fullscreen) maxWidth else minOf(maxWidth * 0.92f, 460.dp)
                }

            Surface(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(panelWidth),
                color = GameMenuStyle.background,
                shape =
                    if (fullscreen) {
                        RoundedCornerShape(0.dp)
                    } else {
                        RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
                    },
                border = BorderStroke(1.dp, GameMenuStyle.border),
            ) {
                // Keep the side panel's background edge-to-edge while its
                // buttons and scrollable pages avoid system bars and cutouts.
                // Game rendering outside this menu uses its own inset rules.
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End,
                                ),
                            ),
                ) {
                    content()
                }
            }
        }
    }

    private fun onResult(block: Intent.() -> Unit) {
        val resultIntent = Intent()
        resultIntent.block()
        resultIntent.putPendingSwitches()
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun Intent.putPendingSwitches() {
        pendingAudioEnabled?.let { putExtra(GameMenuContract.RESULT_ENABLE_AUDIO, it) }
        pendingFastForwardEnabled?.let { putExtra(GameMenuContract.RESULT_ENABLE_FAST_FORWARD, it) }
    }

    companion object {
        private const val PENDING_AUDIO_KEY = "opennes_menu_pending_audio_enabled"
        private const val PENDING_SPEED_KEY = "opennes_menu_pending_fast_forward_enabled"
    }

    @dagger.Module
    abstract class Module
}
