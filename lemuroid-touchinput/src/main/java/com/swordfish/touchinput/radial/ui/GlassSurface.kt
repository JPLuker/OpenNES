package com.swordfish.touchinput.radial.ui

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * Controller dimensions change continuously while the player drags the size
 * sliders. A bounded LRU prevents every intermediate shadow bitmap from
 * remaining in memory for the entire game session.
 */
private object ShadowCache {
    private const val MAX_ENTRIES = 40
    private const val MAX_PIXELS = 2_000_000L

    private data class CachedBitmap(val image: ImageBitmap, val pixels: Long)

    private val bitmapCache = LinkedHashMap<String, CachedBitmap>(48, 0.75f, true)
    private var cachedPixels = 0L

    @Synchronized
    fun getOrCreate(
        width: Int,
        height: Int,
        cornerRadius: Float,
        shadowColor: Color,
        blurRadius: Float,
        padding: Int,
    ): ImageBitmap {
        val key = "$width-$height-$cornerRadius-${shadowColor.toArgb()}-$blurRadius-$padding"
        bitmapCache[key]?.let { return it.image }

        val bitmapWidth = width + padding * 2
        val bitmapHeight = height + padding * 2
        val bitmap = ImageBitmap(bitmapWidth, bitmapHeight)
        val canvas = Canvas(bitmap)

        val frameworkPaint =
            android.graphics.Paint().apply {
                isAntiAlias = true
                color = shadowColor.toArgb()
                maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
            }
        canvas.nativeCanvas.drawRoundRect(
            padding.toFloat(),
            padding.toFloat(),
            padding.toFloat() + width.toFloat(),
            padding.toFloat() + height.toFloat(),
            cornerRadius,
            cornerRadius,
            frameworkPaint,
        )

        val pixels = bitmapWidth.toLong() * bitmapHeight
        if (pixels <= MAX_PIXELS) {
            bitmapCache[key] = CachedBitmap(bitmap, pixels)
            cachedPixels += pixels
            val iterator = bitmapCache.entries.iterator()
            while ((cachedPixels > MAX_PIXELS || bitmapCache.size > MAX_ENTRIES) && iterator.hasNext()) {
                cachedPixels -= iterator.next().value.pixels
                iterator.remove()
            }
        }
        return bitmap
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = Dp.Infinity,
    fillColor: Color = Color.White.copy(alpha = 0.15f),
    shadowColor: Color = Color.Black.copy(alpha = 0.3f),
    shadowWidth: Dp = 1.dp,
    content: @Composable BoxWithConstraintsScope.() -> Unit = { },
) {
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier =
            modifier.drawWithCache {
                val blurRadiusPx = shadowWidth.toPx()

                val expandedWidth = size.width
                val expandedHeight = size.height

                if (expandedWidth <= 0f || expandedHeight <= 0f) {
                    return@drawWithCache onDrawWithContent {
                        drawContent()
                    }
                }

                val expandedSize = Size(expandedWidth, expandedHeight)

                val cornerRadiusPx = minOf(cornerRadius.toPx(), expandedSize.minDimension / 2f)

                val shouldDrawShadow = shadowColor.alpha > 0f && blurRadiusPx > 0f
                val shadowPaddingPx = if (shouldDrawShadow) blurRadiusPx else 0f
                val shadowPadding = ceil(shadowPaddingPx).toInt().coerceAtLeast(0)

                val shadowBitmap =
                    if (shouldDrawShadow) {
                        ShadowCache.getOrCreate(
                            width = expandedSize.width.toInt().coerceAtLeast(1),
                            height = expandedSize.height.toInt().coerceAtLeast(1),
                            cornerRadius = cornerRadiusPx,
                            shadowColor = shadowColor,
                            blurRadius = blurRadiusPx,
                            padding = shadowPadding,
                        )
                    } else {
                        null
                    }

                onDrawWithContent {
                    if (shadowBitmap != null) {
                        val shadowOffset = -Offset(shadowPadding.toFloat(), shadowPadding.toFloat())
                        drawImage(shadowBitmap, topLeft = shadowOffset)
                    }

                    drawRoundRect(
                        color = fillColor,
                        topLeft = Offset(0f, 0f),
                        size = expandedSize,
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    )

                    drawContent()
                }
            },
        content = content,
    )
}
