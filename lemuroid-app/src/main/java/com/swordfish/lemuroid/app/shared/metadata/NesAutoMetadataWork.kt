package com.swordfish.lemuroid.app.shared.metadata

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.swordfish.lemuroid.app.shared.covers.NesCoverPrefetcher
import com.swordfish.lemuroid.lib.injection.AndroidWorkerInjection
import com.swordfish.lemuroid.lib.injection.WorkerKey
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.library.metadata.NesGameTitle
import dagger.Binds
import dagger.android.AndroidInjector
import dagger.multibindings.IntoMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Enriches NES ROMs asynchronously, after indexing. Only game titles leave
 * the device. A small rate-limited batch avoids exhausting Wikimedia APIs
 * and WorkManager's execution window. Further batches are queued separately.
 */
class NesAutoMetadataWork(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    @Inject
    lateinit var db: RetrogradeDatabase

    override suspend fun doWork(): Result {
        AndroidWorkerInjection.inject(this)
        if (!NesAutoMetadataScheduler.enabled(applicationContext)) return Result.success()

        val dao = db.gameDao()
        val candidates =
            try {
                dao.selectOnlineMetadataCandidates(
                    retryBefore = System.currentTimeMillis() - RETRY_INTERVAL_MS,
                    limit = MAX_GAMES_PER_RUN + 1,
                )
            } catch (e: Exception) {
                Timber.w(e, "Unable to load NES metadata candidates")
                return Result.retry()
            }
        if (candidates.isEmpty()) return Result.success()

        val source = WikidataNesMetadataSource()
        val resultsByTitle = mutableMapOf<String, WikidataNesMetadataSource.Result>()
        val images = mutableListOf<Game>()

        for ((index, game) in candidates.take(MAX_GAMES_PER_RUN).withIndex()) {
            if (!coroutineContext.isActive || !NesAutoMetadataScheduler.enabled(applicationContext)) {
                return Result.success()
            }

            val titleKey = NesGameTitle.clean(game.title).lowercase(Locale.ROOT)
            val result =
                resultsByTitle[titleKey] ?: source.lookup(game.title).also {
                    if (it !is WikidataNesMetadataSource.Result.Unavailable) {
                        resultsByTitle[titleKey] = it
                    }
                }

            if (result is WikidataNesMetadataSource.Result.Unavailable) {
                Timber.w("NES metadata provider temporarily unavailable")
                return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
            }

            val checkedAt = System.currentTimeMillis()
            val current = dao.selectById(game.id)
            if (current != null && current.systemId == "nes") {
                when (result) {
                    is WikidataNesMetadataSource.Result.Match -> {
                        val canFill = current.needsOnlineMetadata(result)
                        if (canFill) {
                            dao.fillMissingOnlineGameInfo(
                                gameId = game.id,
                                genre = result.genre,
                                releaseYear = result.releaseYear,
                                description = result.description,
                                sourceId = result.entityId,
                                wikipediaTitle = result.wikipediaArticleTitle,
                                checkedAt = checkedAt,
                            )
                            dao.selectById(game.id)?.let { images.add(it) }
                        } else {
                            dao.markOnlineMetadataChecked(game.id, checkedAt)
                        }
                    }
                    WikidataNesMetadataSource.Result.NoMatch -> {
                        dao.markOnlineMetadataChecked(game.id, checkedAt)
                    }
                    WikidataNesMetadataSource.Result.Unavailable -> Unit
                }
            }

            if (index < MAX_GAMES_PER_RUN - 1 && index < candidates.lastIndex) {
                delay(BETWEEN_REQUESTS_MS)
            }
        }

        // Best effort: cache current artwork in the same storage used by the library.
        // Failed cover requests never block a finished metadata batch.
        if (images.isNotEmpty()) {
            try {
                NesCoverPrefetcher(applicationContext).prefetch(images)
            } catch (e: Exception) {
                Timber.d(e, "Background NES cover caching unavailable")
            }
        }

        if (candidates.size > MAX_GAMES_PER_RUN &&
            NesAutoMetadataScheduler.enabled(applicationContext)
        ) {
            NesAutoMetadataScheduler.schedule(applicationContext)
        }
        return Result.success()
    }

    @dagger.Module(subcomponents = [Subcomponent::class])
    abstract class Module {
        @Binds
        @IntoMap
        @WorkerKey(NesAutoMetadataWork::class)
        abstract fun bindWorker(builder: Subcomponent.Builder): AndroidInjector.Factory<out ListenableWorker>
    }

    @dagger.Subcomponent
    interface Subcomponent : AndroidInjector<NesAutoMetadataWork> {
        @dagger.Subcomponent.Builder
        abstract class Builder : AndroidInjector.Builder<NesAutoMetadataWork>()
    }

    companion object {
        private const val MAX_GAMES_PER_RUN = 8
        private const val MAX_RETRIES = 3
        private const val BETWEEN_REQUESTS_MS = 2_500L
        private const val RETRY_INTERVAL_MS = 30L * 24L * 60L * 60L * 1000L
    }
}
