package com.swordfish.lemuroid.app.shared.covers

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
            games.asSequence()
                .flatMap { NesBoxArtUrls.remoteCandidates(it).asSequence() }
                .filter { it.startsWith("https://thumbnails.libretro.com/") }
                .distinct()
                .take(MAX_COVERS)
                .toList()

        mutex.withLock {
            for (url in urls) {
                if (url in loaded) continue
                val now = System.currentTimeMillis()
                val failures = failedAttempts[url] ?: 0
                if (!NesCoverRetryPolicy.shouldAttempt(failures, lastFailureAt[url], now)) continue
                if (failures >= MAX_ATTEMPTS) {
                    failedAttempts.remove(url)
                    lastFailureAt.remove(url)
                }
                val result =
                    runCatching {
                        imageLoader.execute(
                            ImageRequest.Builder(appContext)
                                .data(url)
                                .size(CoverUtils.COVER_DECODE_WIDTH_PX, CoverUtils.COVER_DECODE_HEIGHT_PX)
                                .memoryCacheKey(CoverUtils.coverMemoryKey(url))
                                .build(),
                        )
                    }.onFailure {
                        Timber.d(it, "NES artwork cache fetch failed")
                    }.getOrNull()
                if (result is SuccessResult) {
                    loaded.add(url)
                    failedAttempts.remove(url)
                    lastFailureAt.remove(url)
                } else {
                    // Coil commonly returns ErrorResult rather than throwing.
                    // A transient offline launch must not permanently blacklist
                    // a cover for this session.
                    failedAttempts[url] = (failedAttempts[url] ?: 0) + 1
                    lastFailureAt[url] = System.currentTimeMillis()
                }
            }
        }
    }

    companion object {
        private const val MAX_COVERS = 24
        private const val MAX_ATTEMPTS = 3
    }
}
