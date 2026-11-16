package com.mwask.bat.compose.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Extracts a muted dominant color from remote artwork for player gradients
 * and the canvas visualizer. Falls back while the bitmap is unavailable.
 */
@Composable
fun rememberArtworkDominantColor(
    imageUrl: String?,
    fallbackColor: Color = Color(0xFF282828)
): Color {
    var dominant by remember(imageUrl) { mutableStateOf(fallbackColor) }
    val context = LocalContext.current
    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) return@LaunchedEffect
        val extracted = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        .size(96)
                        .allowHardware(false)
                        .build()
                ).drawable
                drawable?.toBitmap(96, 96)?.let(::dominantColorOf)
            }.getOrNull()
        }
        if (extracted != null) dominant = extracted
    }
    return dominant
}

private fun dominantColorOf(bitmap: Bitmap): Color {
    var bestR = 0
    var bestG = 0
    var bestB = 0
    var bestScore = 0f
    val buckets = HashMap<Int, IntArray>(32)
    for (x in 0 until bitmap.width) {
        for (y in 0 until bitmap.height) {
            val pixel = bitmap.getPixel(x, y)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val high = max(r, max(g, b))
            if (high == 0) continue
            val low = min(r, min(g, b))
            val saturation = (high - low).toFloat() / high
            if (saturation < 0.2f) continue
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            val bucket = buckets.getOrPut(key) { IntArray(4) }
            bucket[0] += r
            bucket[1] += g
            bucket[2] += b
            bucket[3]++
            val score = bucket[3] * saturation
            if (score > bestScore) {
                bestScore = score
                bestR = bucket[0] / bucket[3]
                bestG = bucket[1] / bucket[3]
                bestB = bucket[2] / bucket[3]
            }
        }
    }
    if (bestScore <= 0f) return Color(0xFF282828)
    return vividColorOf(bestR, bestG, bestB)
}

/**
 * player uses: hue is preserved, saturation and lightness are pulled into a
 * range that reads as color on an OLED panel instead of near-black.
 */
private fun vividColorOf(red: Int, green: Int, blue: Int): Color {
    val r = red / 255f
    val g = green / 255f
    val b = blue / 255f
    val high = max(r, max(g, b))
    val low = min(r, min(g, b))
    val delta = high - low
    if (delta == 0f) return Color(0xFF3B5B7A)
    var hue = when (high) {
        r -> ((g - b) / delta) % 6f
        g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    } * 60f
    if (hue < 0f) hue += 360f
    val lightness = (high + low) / 2f
    val saturation = delta / (1f - kotlin.math.abs(2f * lightness - 1f)).coerceAtLeast(0.01f)
    val sat = saturation.coerceIn(0.45f, 0.85f)
    val light = lightness.coerceIn(0.34f, 0.50f)
    val chroma = (1f - kotlin.math.abs(2f * light - 1f)) * sat
    val secondary = chroma * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
    val match = light - chroma / 2f
    val (r1, g1, b1) = when {
        hue < 60f -> Triple(chroma, secondary, 0f)
        hue < 120f -> Triple(secondary, chroma, 0f)
        hue < 180f -> Triple(0f, chroma, secondary)
        hue < 240f -> Triple(0f, secondary, chroma)
        hue < 300f -> Triple(secondary, 0f, chroma)
        else -> Triple(chroma, 0f, secondary)
    }
    return Color(
        red = ((r1 + match) * 255f).toInt().coerceIn(0, 255),
        green = ((g1 + match) * 255f).toInt().coerceIn(0, 255),
        blue = ((b1 + match) * 255f).toInt().coerceIn(0, 255),
    )
}
