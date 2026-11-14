package com.swordfish.lemuroid.app.shared.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.diagnostics.OpenNesFailureCategory
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.gamecrash.GameCrashActivity
import com.swordfish.lemuroid.app.shared.romm.RommSaveSyncWork
import com.swordfish.lemuroid.app.shared.romm.RommTwoWaySync
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncWork
import com.swordfish.lemuroid.app.shared.storage.cache.CacheCleanerWork
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game

class GameLaunchTaskHandler(private val retrogradeDb: RetrogradeDatabase) {
    fun handleGameStart(context: Context) {
        cancelBackgroundWork(context)
    }

    /**
     * Startup work was canceled, but Android never accepted the game launch.
     * Restore maintenance scheduling rather than leaving auto-sync disabled.
     */
    fun handleGameLaunchFailure(context: Context) {
        rescheduleBackgroundWork(context)
    }

    suspend fun handleGameFinish(
        activity: Activity,
        resultCode: Int,
        data: Intent?,
    ) {
        rescheduleBackgroundWork(activity.applicationContext)
        when (resultCode) {
            Activity.RESULT_OK -> handleSuccessfulGameFinish(data)
            BaseGameActivity.RESULT_ERROR,
            BaseGameActivity.RESULT_UNEXPECTED_ERROR,
            -> {
                val category =
                    data?.getStringExtra(BaseGameActivity.PLAY_GAME_RESULT_ERROR_CATEGORY)
                        ?.let { raw ->
                            OpenNesFailureCategory.values().firstOrNull { it.name == raw }
                        } ?: OpenNesFailureCategory.UNEXPECTED
                val message =
                    when (category) {
                        OpenNesFailureCategory.ACCESS -> R.string.opennes_support_error_access
                        OpenNesFailureCategory.IO -> R.string.opennes_support_error_io
                        OpenNesFailureCategory.INVALID_INPUT -> R.string.opennes_support_error_input
                        OpenNesFailureCategory.CORE -> R.string.opennes_game_error_core
                        OpenNesFailureCategory.ROM -> R.string.opennes_game_error_rom
                        OpenNesFailureCategory.SAVE -> R.string.opennes_game_error_save
                        OpenNesFailureCategory.GRAPHICS -> R.string.opennes_game_error_graphics
                        OpenNesFailureCategory.UNEXPECTED -> R.string.opennes_support_error_emulator
                    }
                handleUnsuccessfulGameFinish(
                    activity,
                    activity.getString(message),
                    activity.getString(R.string.opennes_game_error_code, category.name),
                    category == OpenNesFailureCategory.ROM,
                )
            }
        }
    }

    private fun cancelBackgroundWork(context: Context) {
        // No save sync while a game starts; it resumes when the game closes.
        RommSaveSyncWork.cancel(context)
        SaveSyncWork.cancelAutoWork(context)
        SaveSyncWork.cancelManualWork(context)
        CacheCleanerWork.cancelCleanCacheLRU(context)
    }

    private fun rescheduleBackgroundWork(context: Context) {
        RommTwoWaySync.clearLaunchingGame()
        // Let's slightly delay the sync. Maybe the user wants to play another game.
        SaveSyncWork.enqueueAutoWork(context, 5)
        // Upload the progress just made, if RomM save sync is on.
        RommSaveSyncWork.enqueue(context)
        CacheCleanerWork.enqueueCleanCacheLRU(context)
    }

    private fun handleUnsuccessfulGameFinish(
        activity: Activity,
        message: String,
        messageDetail: String?,
        showRomFolderAction: Boolean,
    ) {
        GameCrashActivity.launch(activity, message, messageDetail, showRomFolderAction)
    }

    private suspend fun handleSuccessfulGameFinish(data: Intent?) {
        val duration =
            data?.extras?.getLong(BaseGameActivity.PLAY_GAME_RESULT_SESSION_DURATION)
                ?: 0L
        val game = data?.extras?.getSerializable(BaseGameActivity.PLAY_GAME_RESULT_GAME) as Game

        updateGamePlayedTimestamp(game, duration)
    }

    private suspend fun updateGamePlayedTimestamp(
        game: Game,
        durationMillis: Long,
    ) {
        val currentGame = retrogradeDb.gameDao().selectById(game.id) ?: return
        val elapsedMillis = durationMillis.coerceAtLeast(0L)
        retrogradeDb.gameDao().update(
            currentGame.copy(
                lastPlayedAt = System.currentTimeMillis(),
                totalPlayTimeMillis = currentGame.totalPlayTimeMillis + elapsedMillis,
            ),
        )
    }
}
