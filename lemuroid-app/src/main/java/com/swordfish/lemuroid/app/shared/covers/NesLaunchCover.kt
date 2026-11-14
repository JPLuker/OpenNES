package com.swordfish.lemuroid.app.shared.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * The game runs in its own process, which shares neither the library's
 * decoded covers nor (safely) its image cache. Before a game starts, the
 * library saves the cover it is showing to a small file; the pause menu
 * reads that file, so it shows the same cover at once, even offline.
 */
object NesLaunchCover {
    private const val DIR = "launch-covers"
    private const val SAVE_TIMEOUT_MS = 3_000L
    private const val KEEP = 12

    private fun file(
        context: Context,
        game: Game,
    ) = File(File(context.applicationContext.cacheDir, DIR), "game-${game.id}.jpg")

    /** Called in the library process just before launch. Best effort and bounded. */
    suspend fun save(
        context: Context,
        game: Game,
    ) {
        val app = context.applicationContext
        val primary = game.displayCoverUrl()?.takeIf { it.isNotBlank() }
        val fallback = NesBoxArtUrls.romFilenameFallback(game)
        val candidates =
            listOfNotNull(NesCoverResolutionCache.preferred(primary, fallback), primary, fallback).distinct()
        val target = file(app, game)
        if (candidates.isEmpty()) {
            withContext(Dispatchers.IO) { target.delete() }
            return
        }
        // First choice: the very bitmap the library is showing right now.
        val shown =
            candidates.firstNotNullOfOrNull { url ->
                app.imageLoader.memoryCache?.get(MemoryCache.Key(CoverUtils.coverMemoryKey(url)))?.bitmap
            }
        val bitmap =
            shown?.let { runCatching { it.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() }
                ?: withTimeoutOrNull(SAVE_TIMEOUT_MS) {
                    candidates.firstNotNullOfOrNull { url ->
                        val result =
                            app.imageLoader.execute(
                                ImageRequest.Builder(app)
                                    .data(url)
                                    .size(CoverUtils.COVER_DECODE_WIDTH_PX, CoverUtils.COVER_DECODE_HEIGHT_PX)
                                    .allowHardware(false)
                                    .build(),
                            )
                        (result as? SuccessResult)?.drawable?.toBitmap()
                    }
                }
                ?: return
        withContext(Dispatchers.IO) {
            val dir = target.parentFile ?: return@withContext
            if (!dir.mkdirs() && !dir.isDirectory) return@withContext
            val temp = File(dir, target.name + ".tmp")
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            target.delete()
            if (!temp.renameTo(target)) temp.delete()
            dir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(KEEP)
                ?.forEach { it.delete() }
        }
    }

    /** In the game process: the same game, showing the saved cover if there is one. */
    fun withSavedCover(
        context: Context,
        game: Game,
    ): Game {
        if (!game.customCoverUri.isNullOrBlank()) return game
        val saved = file(context, game)
        return if (saved.isFile && saved.length() > 0) {
            game.copy(customCoverUri = Uri.fromFile(saved).toString())
        } else {
            game
        }
    }

    /** In the game process: the saved cover, decoded at once (it is a small file). */
    fun load(
        context: Context,
        game: Game,
    ): Bitmap? =
        runCatching {
            file(context, game).takeIf { it.isFile && it.length() > 0 }?.let { BitmapFactory.decodeFile(it.path) }
        }.getOrNull()
}
