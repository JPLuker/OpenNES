package com.swordfish.lemuroid.app.shared.covers

import android.content.Context
import android.widget.ImageView
import coil.ImageLoader
import coil.disk.DiskCache
import coil.imageLoader
import coil.load
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.swordfish.lemuroid.common.drawable.TextDrawable
import com.swordfish.lemuroid.common.graphics.ColorUtils
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

object CoverUtils {
    /**
     * Use the same Coil memory key for an NES cover in every shelf, details
     * panel and pause screen. The default ImageRequest sizing changes across
     * those surfaces; a shared key lets Coil reuse the decoded image instead
     * of temporarily rendering a title placeholder on every navigation.
     */
    fun coverMemoryKey(artworkUrl: String): String = "opennes:nes-cover:$artworkUrl"

    fun loadCover(
        game: Game,
        imageView: ImageView?,
    ) {
        if (imageView == null) return

        val artworkUrl = game.displayCoverUrl()
        imageView.load(artworkUrl, imageView.context.imageLoader) {
            val fallbackDrawable = getFallbackDrawable(game)
            if (!artworkUrl.isNullOrBlank()) {
                memoryCacheKey(coverMemoryKey(artworkUrl))
                placeholderMemoryCacheKey(coverMemoryKey(artworkUrl))
            }
            fallback(fallbackDrawable)
            error(fallbackDrawable)
        }
    }

    fun buildImageLoader(applicationContext: Context): ImageLoader {
        return ImageLoader.Builder(applicationContext)
            .diskCache(
                DiskCache.Builder()
                    .directory(applicationContext.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.20)
                    .build(),
            )
            .memoryCache {
                MemoryCache.Builder(applicationContext)
                    .maxSizePercent(0.20)
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .addNetworkInterceptor(ThrottleFailedThumbnailsInterceptor)
                    .build()
            }
            // Instant replacement avoids flickering a generic title card
            // when returning to shelves or switching between detail screens.
            .crossfade(false)
            .interceptorDispatcher(Dispatchers.IO)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false)
            .build()
    }

    fun getFallbackDrawable(game: Game) = TextDrawable(computeTitle(game), computeColor(game))

    fun getFallbackRemoteUrl(game: Game): String {
        val color = Integer.toHexString(computeColor(game)).substring(2)
        val title = computeTitle(game)
        return "https://fakeimg.pl/512x512/$color/fff/?font=bebas&text=$title"
    }

    private fun computeTitle(game: Game): String {
        val sanitizedName =
            game.displayName()
                .replace(Regex("\\(.*\\)"), "")

        return sanitizedName.asSequence()
            .filter { it.isDigit() or it.isUpperCase() or (it == '&') }
            .take(3)
            .joinToString("")
            .ifBlank { game.displayName().first().toString() }
            .capitalize()
    }

    private fun computeColor(game: Game): Int {
        return ColorUtils.randomColor(game.title)
    }
}
