package com.swordfish.lemuroid.app.shared.game.viewmodel

import android.content.Context
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.shared.achievements.HardcoreRuntime
import com.swordfish.lemuroid.app.shared.achievements.HardcoreSessionPolicy
import com.swordfish.lemuroid.common.graphics.GraphicsUtils
import com.swordfish.lemuroid.common.graphics.takeScreenshot
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.IncompatibleStateException
import com.swordfish.lemuroid.lib.saves.SaveState
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.roundToInt

class GameViewModelSaves(
    private val appContext: Context,
    private val system: GameSystem,
    private val game: Game,
    private val systemCoreConfig: SystemCoreConfig,
    private val retroGameView: GameViewModelRetroGameView,
    private val settingsManager: SettingsManager,
    private val savesManager: SavesManager,
    private val statesManager: StatesManager,
    private val statesPreviewManager: StatesPreviewManager,
    private val sideEffects: GameViewModelSideEffects,
) {
    private var currentQuickSave: SaveState? = null

    // Background save and final Quit must never capture/write overlapping
    // snapshots. The last successful Quit snapshot must win on disk.
    private val sessionSnapshotWriteMutex = Mutex()

    data class SaveSnapshot(
        val sram: ByteArray,
        val autoSave: SaveState?,
    )

    suspend fun saveSlot(index: Int) {
        try {
            val state = getCurrentSaveState()
            if (state == null) {
                sideEffects.showToast(appContext.getString(R.string.opennes_save_state_failed))
                return
            }
            statesManager.setSlotSave(game, state, systemCoreConfig.coreID, index)
            // A preview failure should not invalidate a successfully
            // written state. But do report the preview failure in logs.
            runCatching { takeScreenshotPreview(index) }
                .onFailure { Timber.w(it, "Could not save state screenshot preview") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to save NES state slot %s", index)
            sideEffects.showToast(appContext.getString(R.string.opennes_save_state_failed))
        }
    }

    suspend fun loadSlot(index: Int) {
        if (!HardcoreRuntime.allow(HardcoreSessionPolicy.Operation.LOAD_STATE)) {
            sideEffects.showToast(appContext.getString(R.string.opennes_hardcore_state_blocked))
            return
        }
        try {
            val state = statesManager.getSlotSave(game, systemCoreConfig.coreID, index)
            if (state == null) {
                sideEffects.showToast(appContext.getString(R.string.game_toast_load_state_failed))
                return
            }
            val loaded =
                withContext(Dispatchers.IO) {
                    loadSaveState(state)
                }
            if (!loaded) {
                sideEffects.showToast(appContext.getString(R.string.game_toast_load_state_failed))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorMessageId =
                when (e) {
                    is IncompatibleStateException -> R.string.error_message_incompatible_state
                    else -> R.string.game_toast_load_state_failed
                }
            sideEffects.showToast(appContext.getString(errorMessageId))
        }
    }

    suspend fun captureSaveSnapshot(useEmulationThread: Boolean): SaveSnapshot? {
        val retroGameView = retroGameView.retroGameView ?: return null
        val sramState = retroGameView.serializeSRAM(useEmulationThread)
        val autoSaveState = if (isAutoSaveEnabled()) getCurrentSaveState(useEmulationThread) else null
        return SaveSnapshot(sramState, autoSaveState)
    }

    /** Rewind and background/quit saves must never race over core state. */
    suspend fun withExclusiveSessionState(block: suspend () -> Boolean): Boolean =
        sessionSnapshotWriteMutex.withLock { block() }

    suspend fun persistSessionSnapshot(useEmulationThread: Boolean): Boolean =
        sessionSnapshotWriteMutex.withLock {
            val snapshot = captureSaveSnapshot(useEmulationThread) ?: return@withLock false
            writeSaveSnapshot(snapshot)
            true
        }

    suspend fun writeSaveSnapshot(snapshot: SaveSnapshot?) {
        if (snapshot == null) return
        Timber.i(
            "GameViewModelSaves.write game=%s core=%s writingSram=%s writingAutoSave=%s",
            game.id,
            systemCoreConfig.coreID,
            true,
            snapshot.autoSave != null,
        )
        savesManager.setSaveRAM(game, snapshot.sram)
        snapshot.autoSave?.let { statesManager.setAutoSave(game, systemCoreConfig.coreID, it) }
    }

    // On some cores unserialize fails with no reason. So we need to try multiple times.
    suspend fun restoreAutoSaveAsync(saveState: SaveState) {
        if (!HardcoreRuntime.allow(HardcoreSessionPolicy.Operation.LOAD_STATE)) return
        // PPSSPP and Mupen64 initialize some state while rendering the first frame, so we have to wait before restoring
        // the autosave. Do not change thread here. Stick to the GL one to avoid issues with PPSSPP.
        if (!isAutoSaveEnabled()) return

        try {
            retroGameView.waitGLEvent<GLRetroView.GLRetroEvents.FrameRendered>()
            if (!restoreQuickSave(saveState)) {
                sideEffects.showToast(appContext.getString(R.string.game_toast_load_state_failed))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error while loading auto-save")
            sideEffects.showToast(appContext.getString(R.string.game_toast_load_state_failed))
        }
    }

    private fun getCurrentSaveState(useEmulationThread: Boolean = true): SaveState? {
        val retroGameView = retroGameView.retroGameView ?: return null
        val currentDisk =
            if (system.hasMultiDiskSupport) {
                retroGameView.getCurrentDisk(useEmulationThread)
            } else {
                0
            }
        val serializedState = retroGameView.serializeState(useEmulationThread)
        if (serializedState.isEmpty()) {
            Timber.w("NES core returned an empty serialized save state")
            return null
        }
        return SaveState(
            serializedState,
            SaveState.Metadata(currentDisk, systemCoreConfig.statesVersion),
        )
    }

    private suspend fun isAutoSaveEnabled(): Boolean {
        return systemCoreConfig.statesSupported && settingsManager.autoSave()
    }

    private suspend fun takeScreenshotPreview(index: Int) {
        val sizeInDp = StatesPreviewManager.PREVIEW_SIZE_DP
        val previewSize = GraphicsUtils.convertDpToPixel(sizeInDp, appContext).roundToInt()
        val preview = retroGameView.retroGameView?.takeScreenshot(previewSize, 3)
        if (preview != null) {
            statesPreviewManager.setPreviewForSlot(game, preview, systemCoreConfig.coreID, index)
        }
    }

    // Now that we wait for the first rendered frame this is probably no longer needed, but we'll keep it just to be sure
    private suspend fun restoreQuickSave(saveState: SaveState): Boolean {
        // Early frames can fail to restore valid state. Keep the bounded
        // retry policy but report an exhausted retry instead of silence.
        repeat(11) { attempt ->
            if (loadSaveState(saveState)) return true
            if (attempt < 10) delay(200)
        }
        return false
    }

    private fun loadSaveState(saveState: SaveState): Boolean {
        // Guard the final native state restore, not merely menu buttons:
        // a new caller cannot bypass Hardcore by skipping the UI.
        if (!HardcoreRuntime.allow(HardcoreSessionPolicy.Operation.LOAD_STATE)) return false
        val retroGameView = retroGameView.retroGameView ?: return false

        if (systemCoreConfig.statesVersion != saveState.metadata.version) {
            throw IncompatibleStateException()
        }

        if (system.hasMultiDiskSupport &&
            retroGameView.getAvailableDisks() > 1 &&
            retroGameView.getCurrentDisk() != saveState.metadata.diskIndex
        ) {
            retroGameView.changeDisk(saveState.metadata.diskIndex)
        }

        return retroGameView.unserializeState(saveState.state)
    }

    fun saveQuickSave() {
        try {
            val state = getCurrentSaveState()
            if (state == null) {
                sideEffects.showToast(appContext.getString(R.string.opennes_save_state_failed))
                return
            }
            currentQuickSave = state
            sideEffects.showToast(appContext.getString(R.string.game_toast_quick_save_saved))
        } catch (e: Exception) {
            Timber.e(e, "Failed to create NES quick save")
            sideEffects.showToast(appContext.getString(R.string.opennes_save_state_failed))
        }
    }

    fun loadQuickSave() {
        if (!HardcoreRuntime.allow(HardcoreSessionPolicy.Operation.LOAD_STATE)) {
            sideEffects.showToast(appContext.getString(R.string.opennes_hardcore_state_blocked))
            return
        }
        val state = currentQuickSave
        if (state == null) {
            sideEffects.showToast(appContext.getString(R.string.opennes_quick_save_missing))
            return
        }
        try {
            if (loadSaveState(state)) {
                sideEffects.showToast(appContext.getString(R.string.game_toast_quick_save_loaded))
            } else {
                sideEffects.showToast(appContext.getString(R.string.game_toast_load_state_failed))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load NES quick save")
            sideEffects.showToast(appContext.getString(R.string.game_toast_load_state_failed))
        }
    }
}
