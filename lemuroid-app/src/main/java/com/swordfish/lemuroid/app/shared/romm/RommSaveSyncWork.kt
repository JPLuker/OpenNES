package com.swordfish.lemuroid.app.shared.romm

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.swordfish.lemuroid.lib.injection.AndroidWorkerInjection
import com.swordfish.lemuroid.lib.injection.WorkerKey
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import dagger.Binds
import dagger.android.AndroidInjector
import dagger.multibindings.IntoMap
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

    companion object {
        private const val UNIQUE_WORK = "opennes-romm-save-sync"
        private const val MAX_RETRIES = 3
        private const val ONLY_IF_DUE = "only_if_due"
        private const val MIN_INTERVAL_MS = 2 * 60 * 1000L

        /** For frequent triggers such as opening the library. */
        fun enqueueIfDue(context: Context) = enqueue(context, onlyIfDue = true)

        /** Queue one sync; a sync already queued is kept, not duplicated. */
        fun enqueue(
            context: Context,
            delaySeconds: Long = 0,
            onlyIfDue: Boolean = false,
        ) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RommSaveSyncWork>()
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                    )
                    .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                    .setInputData(workDataOf(ONLY_IF_DUE to onlyIfDue))
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
