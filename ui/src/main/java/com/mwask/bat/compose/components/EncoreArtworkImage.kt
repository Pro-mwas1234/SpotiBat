package com.mwask.bat.compose.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mwask.bat.compose.theme.EncoreTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 */
fun resolveRemoteImageUrl(rawUrl: String?): String? {
    if (rawUrl.isNullOrBlank()) return null
    val trimmed = rawUrl.trim()
    return when {
        trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("file://", ignoreCase = true) || trimmed.startsWith("content://", ignoreCase = true) -> trimmed
        trimmed.startsWith("spotify:image:", ignoreCase = true) -> {
            val id = trimmed.substringAfter("spotify:image:")
            "https://i.scdn.co/image/$id"
        }
        trimmed.length in 30..50 && trimmed.all { it.isDigit() || (it in 'a'..'f') || (it in 'A'..'F') } -> {
            "https://i.scdn.co/image/$trimmed"
        }
        else -> trimmed
    }
}

/**
 * Renders high-resolution remote artwork with crossfade transitions and
 * elegant fallback placeholders.
 */
@Composable
fun EncoreArtworkImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    cornerRadius: Dp? = null,
    fallbackColor: Color = Color(0xFF282828),
    contentScale: ContentScale = ContentScale.Crop,
    requestSize: Int = 256
) {
    val effectiveShape = shape ?: (if (cornerRadius != null && cornerRadius > 0.dp) RoundedCornerShape(cornerRadius) else null)
    val clipModifier = if (effectiveShape != null) modifier.clip(effectiveShape) else modifier
    val resolvedUrl = resolveRemoteImageUrl(imageUrl)
    var loaded by remember(resolvedUrl) { mutableStateOf(false) }
    var attempt by remember(resolvedUrl) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val request = remember(resolvedUrl, attempt) {
        ImageRequest.Builder(context)
            .data(resolvedUrl)
            .crossfade(true)
            .size(requestSize)
            .setParameter("retry", attempt)
            .build()
    }
    val scope = rememberCoroutineScope()
    val artworkAlpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "artworkReveal"
    )

    Box(
        modifier = clipModifier.background(fallbackColor),
        contentAlignment = Alignment.Center
    ) {
        if (!resolvedUrl.isNullOrBlank()) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
                onSuccess = { loaded = true },
                onError = {
                    if (attempt < 2) {
                        scope.launch {
                            delay(400L * (attempt + 1))
                            attempt++
                        }
                    } else {
                        loaded = true
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = artworkAlpha }
            )
            if (!loaded) {
                ShimmerHost {
                    ShimmerBlock(
                        modifier = Modifier.fillMaxSize(),
                        shape = RectangleShape,
                        base = fallbackColor,
                        highlight = fallbackColor.copy(alpha = 0.45f).compositeOver(ShimmerHighlightTone)
                    )
                }
            }
        } else {
            Text(
                text = "♪",
                style = EncoreTheme.typography.titleMedium.copy(color = Color.White.copy(alpha = 0.5f))
            )
        }
    }
}

private val ShimmerHighlightTone = Color(0xFF2E2E2E)
