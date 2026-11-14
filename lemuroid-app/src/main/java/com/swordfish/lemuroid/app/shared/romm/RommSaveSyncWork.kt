package com.swordfish.lemuroid.app.shared.romm

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.swordfish.lemuroid.app.shared.game.ActiveGameSession
import com.swordfish.lemuroid.app.shared.game.GameProcessLock
import com.swordfish.lemuroid.lib.injection.AndroidWorkerInjection
import com.swordfish.lemuroid.lib.injection.WorkerKey
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import dagger.Binds
import dagger.android.AndroidInjector
import dagger.multibindings.IntoMap
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Runs RomM two-way save sync in the background, e.g. after a game closes,
 * so the upload finishes even if the player leaves the app straight away.
 * Does nothing unless the player turned sync on.
 */
class RommSaveSyncWork(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    @Inject
    lateinit var db: RetrogradeDatabase

    override suspend fun doWork(): Result {
        AndroidWorkerInjection.inject(this)
        if (!RommTwoWaySync.isEnabled(applicationContext)) return Result.success()
        if (inputData.getBoolean(AFTER_GAME, false) && !waitForGameProcessToEnd()) {
            // The game is still shutting down (or was reopened); try again shortly.
            return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        }
        if (inputData.getBoolean(ONLY_IF_DUE, false)) {
            val last = RommTwoWaySync.lastRunAtMillis(applicationContext)
            if (last != null && System.currentTimeMillis() - last in 0 until MIN_INTERVAL_MS) return Result.success()
        }
        val games = db.gameDao().selectAllNesGames()
        return when (RommTwoWaySync.syncAll(applicationContext, games)) {
            // A network problem is retried by WorkManager with backoff.
            is RommTwoWaySync.Outcome.Failed -> if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
            else -> Result.success()
        }
    }

    /**
     * A closed game's process lives on for a moment while it shuts down, and
     * sync never touches a running game's save. Without this wait the
     * after-game upload would skip exactly the game just played.
     */
    private suspend fun waitForGameProcessToEnd(): Boolean {
        val deadline = System.currentTimeMillis() + GAME_EXIT_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            val stillRunning =
                ActiveGameSession.current(applicationContext) != null ||
                    runCatching { GameProcessLock.isHeldByAnotherProcess(applicationContext) }.getOrDefault(true)
            if (!stillRunning) return true
            delay(GAME_EXIT_POLL_MS)
        }
        return false
    }

    companion object {
        private const val AFTER_GAME = "after_game"
        private const val GAME_EXIT_WAIT_MS = 20_000L
        private const val GAME_EXIT_POLL_MS = 500L
        private const val UNIQUE_WORK = "opennes-romm-save-sync"
        private const val MAX_RETRIES = 3
        private const val ONLY_IF_DUE = "only_if_due"
        private const val MIN_INTERVAL_MS = 2 * 60 * 1000L
        private const val RETRY_BACKOFF_SECONDS = 15L

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK)
        }

        /** For frequent triggers such as opening the library. */
        fun enqueueIfDue(context: Context) = enqueue(context, onlyIfDue = true)

        /**
         * Upload the progress from a game that just closed. Queued after any
         * sync already pending or running, never dropped as a duplicate, and
         * never subject to the "only if due" interval.
         */
        fun enqueueAfterGame(context: Context) =
            enqueue(context, afterGame = true, policy = ExistingWorkPolicy.APPEND_OR_REPLACE)

        /** Queue one sync; a sync already queued is kept, not duplicated. */
        fun enqueue(
            context: Context,
            delaySeconds: Long = 0,
            onlyIfDue: Boolean = false,
            afterGame: Boolean = false,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
        ) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK,
                policy,
                OneTimeWorkRequestBuilder<RommSaveSyncWork>()
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                    )
                    .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
                    .setInputData(workDataOf(ONLY_IF_DUE to onlyIfDue, AFTER_GAME to afterGame))
                    .build(),
            )
        }
    }

    @dagger.Module(subcomponents = [Subcomponent::class])
    abstract class Module {
        @Binds
        @IntoMap
        @WorkerKey(RommSaveSyncWork::class)
        abstract fun bindMyWorkerFactory(builder: Subcomponent.Builder): AndroidInjector.Factory<out ListenableWorker>
    }

    @dagger.Subcomponent
    interface Subcomponent : AndroidInjector<RommSaveSyncWork> {
        @dagger.Subcomponent.Builder
        abstract class Builder : AndroidInjector.Builder<RommSaveSyncWork>()
    }
}
