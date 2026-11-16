package com.mwask.bat.compose.util

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.mwask.bat.compose.components.resolveRemoteImageUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 */
val PALETTE_FALLBACKS = listOf(
    Color(0xFF144D3F), // Forest Green
    Color(0xFF432371), // Royal Violet
    Color(0xFF1E3264), // Midnight Blue
    Color(0xFF8D1624), // Velvet Crimson
    Color(0xFF503750), // Deep Plum
    Color(0xFFAF2896),
    Color(0xFF006450), // Dark Teal
    Color(0xFFBA5D07), // Amber Ochre
    Color(0xFF7D4B32), // Warm Umber
    Color(0xFF2E3D82), // Cobalt Indigo
    Color(0xFF3B4459), // Steel Slate
    Color(0xFF6B2D5C)  // Dark Fuchsia
)

/**
 * from a title, uri, or identifier string.
 */
fun paletteColorForSeed(seed: String?): Color {
    if (seed.isNullOrBlank()) return PALETTE_FALLBACKS[0]
    val index = abs(seed.hashCode()) % PALETTE_FALLBACKS.size
    return PALETTE_FALLBACKS[index]
}

/**
 * from an Android [Bitmap].
 */
fun extractDominantColor(bitmap: Bitmap): Color {
    val palette = Palette.from(bitmap)
        .maximumColorCount(16)
        .generate()

    val swatch = palette.dominantSwatch
        ?: palette.vibrantSwatch
        ?: palette.darkVibrantSwatch
        ?: palette.mutedSwatch
        ?: palette.darkMutedSwatch

    return swatch?.let { Color(it.rgb) } ?: Color(0xFF121212)
}

/**
 * Asynchronously loads the image at [imageUrl], executes Palette extraction,
 * and remembers the extracted [Color].
 *
 * If the image cannot be loaded or [imageUrl] is blank, [fallbackSeed] is used
 * distinct, visually rich gradients.
 */
@Composable
fun rememberDominantColor(
    imageUrl: String?,
    fallbackSeed: String? = null,
    defaultColor: Color? = null
): State<Color> {
    val context = LocalContext.current
    val fallback = defaultColor ?: paletteColorForSeed(fallbackSeed ?: imageUrl)
    val colorState = remember(imageUrl) { mutableStateOf(fallback) }

    LaunchedEffect(imageUrl) {
        val resolved = resolveRemoteImageUrl(imageUrl)
        if (resolved.isNullOrBlank()) {
            colorState.value = fallback
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(resolved)
                    .size(128) // a 128px decode is plenty for Palette
                    .allowHardware(false) // Required for Palette software bitmap access
                    .build()
                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.drawable.toBitmap()
                    val extracted = extractDominantColor(bitmap)
                    withContext(Dispatchers.Main) {
                        colorState.value = extracted
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    return colorState
}
