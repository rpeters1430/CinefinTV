package com.rpeters.cinefintv.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.roundToInt

object ThemeSeedColorCache {
    private const val MAX_SAMPLE_DIMENSION = 96

    private val cache = ConcurrentHashMap<String, Color>()

    fun getCached(imageUrl: String?): Color? = imageUrl?.let(cache::get)

    suspend fun getOrExtract(
        imageUrl: String,
        bitmapProvider: () -> Bitmap,
    ): Color? {
        cache[imageUrl]?.let { return it }

        return withContext(Dispatchers.Default) {
            cache[imageUrl]?.let { return@withContext it }

            val bitmap = bitmapProvider()
            val sampledBitmap = bitmap.downsampleForPalette()

            try {
                val palette = Palette.Builder(sampledBitmap)
                    .clearFilters()
                    .generate()
                val seedColor = palette.vibrantSwatch?.rgb
                    ?: palette.dominantSwatch?.rgb
                    ?: return@withContext null

                Color(seedColor).also { cache[imageUrl] = it }
            } finally {
                if (sampledBitmap !== bitmap && !sampledBitmap.isRecycled) {
                    sampledBitmap.recycle()
                }
            }
        }
    }

    private fun Bitmap.downsampleForPalette(): Bitmap {
        val maxDimension = max(width, height)
        if (maxDimension <= MAX_SAMPLE_DIMENSION) return this

        val scale = MAX_SAMPLE_DIMENSION.toFloat() / maxDimension.toFloat()
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }
}
