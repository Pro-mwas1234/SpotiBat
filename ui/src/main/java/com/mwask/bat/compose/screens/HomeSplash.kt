package com.mwask.bat.compose.screens

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

private const val SPLASH_TARGETS = 28
private const val SPLASH_LOADED_FRACTION = 0.9f
private const val SPLASH_MIN_MS = 700L
private const val SPLASH_SETTLE_MS = 4_500L
private const val SPLASH_LIMIT_MS = 20_000L

private object HomeSplashSeen {
    @Volatile
    var value = false
}

/** Lets a fresh session (account switch, logout, new login) show the splash
 * over the home load again. */
fun resetHomeSplash() {
    HomeSplashSeen.value = false
}

@Composable
fun HomeSplashHost(
    loading: Boolean,
    urls: List<String>,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(
            visible = rememberHomeSplashVisible(loading, urls),
            exit = fadeOut(
                tween(durationMillis = 320, easing = FastOutLinearInEasing),
            ) + scaleOut(
                animationSpec = tween(durationMillis = 320, easing = FastOutLinearInEasing),
                targetScale = 1.04f,
            ),
        ) {
            SplashScreen()
        }
    }
}

@Composable
private fun rememberHomeSplashVisible(loading: Boolean, urls: List<String>): Boolean {
    val context = LocalContext.current
    val loader = context.imageLoader
    var visible by remember { mutableStateOf(!HomeSplashSeen.value) }
    var settled by remember { mutableIntStateOf(0) }
    val composedAt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val targets = remember(urls) {
        urls.asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .take(SPLASH_TARGETS)
            .toList()
    }

    LaunchedEffect(targets) {
        if (targets.isEmpty() || !visible) return@LaunchedEffect
        coroutineScope {
            targets.map { url ->
                async {
                    runCatching {
                        loader.execute(
                            ImageRequest.Builder(context)
                                .data(url)
                                .build(),
                        )
                    }
                    settled++
                }
            }.awaitAll()
        }
    }

    LaunchedEffect(loading, settled, targets.size) {
        if (!visible || loading) return@LaunchedEffect
        val total = targets.size
        val ratio = if (total == 0) 1f else settled.toFloat() / total
        if (ratio < SPLASH_LOADED_FRACTION) return@LaunchedEffect
        val left = SPLASH_MIN_MS - (SystemClock.elapsedRealtime() - composedAt)
        if (left > 0) delay(left)
        HomeSplashSeen.value = true
        visible = false
    }

    LaunchedEffect(loading, visible) {
        if (!visible || loading) return@LaunchedEffect
        delay(SPLASH_SETTLE_MS)
        HomeSplashSeen.value = true
        visible = false
    }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        delay(SPLASH_LIMIT_MS)
        HomeSplashSeen.value = true
        visible = false
    }

    return visible
}
