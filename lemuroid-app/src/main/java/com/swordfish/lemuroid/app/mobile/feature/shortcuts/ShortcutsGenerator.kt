package com.swordfish.lemuroid.app.mobile.feature.shortcuts

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.app.shared.covers.NesBoxArtUrls
import com.swordfish.lemuroid.app.shared.deeplink.DeepLink
import com.swordfish.lemuroid.common.bitmap.toBitmap
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.io.InputStream

class ShortcutsGenerator(
    private val appContext: Context,
    retrofit: Retrofit,
) {
    private val thumbnailsApi = retrofit.create(ThumbnailsApi::class.java)

    suspend fun pinShortcutForGame(game: Game) {
        if (!supportShortcuts()) return

        val shortcutManager =
            appContext.getSystemService(ShortcutManager::class.java) ?: return
        val shortcutInfo =
            ShortcutInfo.Builder(appContext, "game_${game.id}")
                .setShortLabel(game.displayName())
                .setLongLabel(game.displayName())
                .setIntent(DeepLink.launchIntentForGame(appContext, game))
                .setIcon(Icon.createWithBitmap(retrieveBitmap(game)))
                .build()

        // Android delegates shortcut placement to the user's chosen launcher.
        // The launcher may display its own confirmation and placement dialog.
        shortcutManager.requestPinShortcut(shortcutInfo, null)
    }

    private suspend fun retrieveBitmap(game: Game): Bitmap =
        withContext(Dispatchers.IO) {
            val size = getDesiredIconSize().coerceIn(96, 512)
            val image = customCover(game) ?: remoteCover(game)
            image?.let { fitBoxArtIcon(it, size) } ?: retrieveFallbackBitmap(game, size)
        }

    private fun customCover(game: Game): Bitmap? {
        val uri = game.customCoverUri ?: return null
        return runCatching {
            appContext.contentResolver.openInputStream(Uri.parse(uri))
                ?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    private suspend fun remoteCover(game: Game): Bitmap? {
        // This matches the library's visible NES artwork. Previously we read
        // coverFrontUrl!! only, so filename-matched art became a "TLZ" icon.
        for (url in NesBoxArtUrls.remoteCandidates(game)) {
            val cover =
                runCatching {
                    val response = thumbnailsApi.downloadThumbnail(url)
                    if (response.isSuccessful) {
                        response.body()?.use { BitmapFactory.decodeStream(it) }
                    } else {
                        response.body()?.close()
                        null
                    }
                }.getOrNull()
            if (cover != null) return cover
        }
        return null
    }

    /** Preserve the complete 5:7 NES box rather than cropping its lettering. */
    private fun fitBoxArtIcon(
        artwork: Bitmap,
        iconSize: Int,
    ): Bitmap {
        val icon = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(icon)
        canvas.drawColor(Color.rgb(28, 32, 39))

        val available = iconSize * 0.86f
        val scale =
            minOf(
                available / artwork.width.coerceAtLeast(1),
                available / artwork.height.coerceAtLeast(1),
            )
        val width = artwork.width * scale
        val height = artwork.height * scale
        val left = (iconSize - width) / 2f
        val top = (iconSize - height) / 2f
        val destination = RectF(left, top, left + width, top + height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(artwork, null, destination, paint)

        paint.color = Color.rgb(92, 93, 99)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (iconSize / 180f).coerceAtLeast(1f)
        canvas.drawRect(destination, paint)
        return icon
    }

    private fun retrieveFallbackBitmap(
        game: Game,
        iconSize: Int,
    ): Bitmap = CoverUtils.getFallbackDrawable(game).toBitmap(iconSize, iconSize)

    private fun getDesiredIconSize(): Int {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        return manager?.launcherLargeIconSize ?: 256
    }

    fun supportShortcuts(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return appContext.getSystemService(ShortcutManager::class.java)
            ?.isRequestPinShortcutSupported == true
    }

    interface ThumbnailsApi {
        @GET
        @Streaming
        suspend fun downloadThumbnail(
            @Url url: String,
        ): Response<InputStream>
    }
}
