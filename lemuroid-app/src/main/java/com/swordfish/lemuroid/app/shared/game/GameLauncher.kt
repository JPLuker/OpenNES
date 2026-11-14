package com.swordfish.lemuroid.app.shared.game

import android.app.Activity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.diagnostics.OpenNesFailureCategory
import com.swordfish.lemuroid.app.shared.gamecrash.GameCrashActivity
import com.swordfish.lemuroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.lemuroid.app.shared.romm.RommTwoWaySync
import com.swordfish.lemuroid.common.displayToast
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class GameLauncher(
    private val coresSelection: CoresSelection,
    private val gameLaunchTaskHandler: GameLaunchTaskHandler,
) {
    fun launchGameAsync(
        activity: Activity,
        game: Game,
        loadSave: Boolean,
        leanback: Boolean,
    ): Boolean {
        if (GameProcessLock.isHeldByAnotherProcess(activity.applicationContext)) {
            activity.displayToast(R.string.game_process_another_game_running)
            return false
        }

        // The core selection comes from a suspending preferences flow. Never
        // launch the emulator from an unstructured GlobalScope or a worker
        // thread: activity launch must occur on Main and must be cancelled
        // if the originating Activity is destroyed during folder selection.
        val host = activity as? LifecycleOwner
        if (host == null || activity.isFinishing || activity.isDestroyed) {
            return false
        }
        // The pre-launch save check can take a few seconds; a second tap on
        // Play meanwhile must not start the game twice.
        if (!launchInProgress.compareAndSet(false, true)) return false
        host.lifecycleScope.launch {
            var backgroundWorkPaused = false
            var launchDispatched = false
            try {
                val system = GameSystem.findById(game.systemId)
                val coreConfig = coresSelection.getCoreConfigForSystem(system)
                if (activity.isFinishing || activity.isDestroyed) return@launch
                // Pull a newer save made on another device. Bounded: an
                // unreachable server never holds up play.
                val slowNotice =
                    launch {
                        delay(800)
                        activity.displayToast(R.string.opennes_romm_sync_checking)
                    }
                try {
                    RommTwoWaySync.syncBeforeLaunch(activity.applicationContext, game)
                } finally {
                    slowNotice.cancel()
                }
                if (activity.isFinishing || activity.isDestroyed) return@launch
                RommTwoWaySync.protectLaunchingGame(game.id)
                gameLaunchTaskHandler.handleGameStart(activity.applicationContext)
                backgroundWorkPaused = true
                BaseGameActivity.launchGame(activity, coreConfig, game, loadSave, leanback)
                launchDispatched = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Do not log untrusted exception messages (ROM paths, URIs).
                Timber.w("NES launch preparation failed: %s", e.javaClass.simpleName)
                if (!activity.isFinishing && !activity.isDestroyed) {
                    GameCrashActivity.launch(
                        activity,
                        activity.getString(R.string.opennes_game_error_core),
                        activity.getString(
                            R.string.opennes_game_error_code,
                            OpenNesFailureCategory.CORE.name,
                        ),
                    )
                }
            } finally {
                // Lifecycle cancellation between background-work suspension
                // and Activity dispatch must not permanently disable work.
                if (backgroundWorkPaused && !launchDispatched) {
                    runCatching {
                        gameLaunchTaskHandler.handleGameLaunchFailure(activity.applicationContext)
                    }
                }
            }
        }.invokeOnCompletion { launchInProgress.set(false) }
        return true
    }

    companion object {
        private val launchInProgress = AtomicBoolean(false)
    }
}
