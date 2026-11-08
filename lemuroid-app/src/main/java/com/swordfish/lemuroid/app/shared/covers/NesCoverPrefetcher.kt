package com.swordfish.lemuroid.app.shared.covers

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
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
    private val attempted = mutableSetOf<String>()

    suspend fun prefetch(games: List<Game>) {
        val urls =
            games.asSequence()
                .mapNotNull { it.coverFrontUrl }
                .map { it.replace("http://thumbnails.libretro.com/", "https://thumbnails.libretro.com/") }
                .filter { it.startsWith("https://thumbnails.libretro.com/") }
                .distinct()
                .take(MAX_COVERS)
                .toList()

        mutex.withLock {
            for (url in urls) {
                if (!attempted.add(url)) continue
                runCatching {
                    imageLoader.execute(
                        ImageRequest.Builder(appContext)
                            .data(url)
                            .size(320, 440)
                            .build(),
                    )
                }.onFailure {
                    Timber.d(it, "NES cover prefetch skipped: $url")
                }
            }
        }
    }

    companion object {
        private const val MAX_COVERS = 24
    }
}
