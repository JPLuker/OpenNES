package com.swordfish.lemuroid.app.shared.covers

import android.content.Context
import android.os.SystemClock
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber

/**
 * Best-effort image caching for the currently visible game shelves.
 * Work is bounded, serialized and reused through Coil's normal disk cache.
 * Custom user artwork is never overwritten.
 */
class NesCoverPrefetcher(context: Context) {
    private val appContext = context.applicationContext
    private val imageLoader = appContext.imageLoader
    private val mutex = Mutex()
    private val loaded = mutableSetOf<String>()
    private val failedAttempts = mutableMapOf<String, Int>()
    private val lastFailureAt = mutableMapOf<String, Long>()

    suspend fun prefetch(games: List<Game>) {
        val urls =
            NesCoverPrefetchOrder.select(
                games.map(NesBoxArtUrls::remoteCandidates),
                MAX_COVERS,
            )

        mutex.withLock {
            for (url in urls) {
                if (url in loaded) continue
                val now = SystemClock.elapsedRealtime()
                val failures = failedAttempts[url] ?: 0
                if (!NesCoverRetryPolicy.shouldAttempt(failures, lastFailureAt[url], now)) continue
                if (failures >= MAX_ATTEMPTS) {
                    failedAttempts.remove(url)
                    lastFailureAt.remove(url)
                }
                val result =
                    try {
                        imageLoader.execute(
                            ImageRequest.Builder(appContext)
                                .data(url)
                                .size(CoverUtils.COVER_DECODE_WIDTH_PX, CoverUtils.COVER_DECODE_HEIGHT_PX)
                                .memoryCacheKey(CoverUtils.coverMemoryKey(url))
                                .build(),
                        )
                    } catch (cancelled: CancellationException) {
                        // Cancellation is not a failed download. Stop the batch
                        // immediately and do not consume its retry budget.
                        throw cancelled
                    } catch (error: Exception) {
                        Timber.d(error, "NES artwork cache fetch failed")
                        null
                    }
                if (result is SuccessResult) {
                    loaded.add(url)
                    failedAttempts.remove(url)
                    lastFailureAt.remove(url)
                } else {
                    // Coil commonly returns ErrorResult rather than throwing.
                    // A transient offline launch must not permanently blacklist
                    // a cover for this session.
                    failedAttempts[url] = (failedAttempts[url] ?: 0) + 1
                    lastFailureAt[url] = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    /**
     * Decode the covers the library opens on, several at a time, so they are
     * already in memory when the loading screen lifts. Best effort.
     */
    suspend fun warm(urls: List<String>) {
        val gate = Semaphore(WARM_PARALLEL)
        coroutineScope {
            urls.distinct().take(WARM_MAX).forEach { url ->
                launch {
                    gate.withPermit {
                        runCatching {
                            imageLoader.execute(
                                ImageRequest.Builder(appContext)
                                    .data(url)
                                    .size(CoverUtils.COVER_DECODE_WIDTH_PX, CoverUtils.COVER_DECODE_HEIGHT_PX)
                                    .memoryCacheKey(CoverUtils.coverMemoryKey(url))
                                    .build(),
                            )
                        }.onFailure { if (it is CancellationException) throw it }
                    }
                }
            }
        }
    }

    companion object {
        private const val WARM_MAX = 30
        private const val WARM_PARALLEL = 6
        private const val MAX_COVERS = 24
        private const val MAX_ATTEMPTS = 3
    }
}
