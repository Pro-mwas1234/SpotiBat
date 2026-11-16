package com.mwask.bat.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.mwask.bat.backend.core.auth.EncoreAuth
import com.mwask.bat.backend.core.auth.LoginFlow
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.webview.LoginWebViewHost
import com.mwask.bat.backend.core.webview.PLAYER_URL

private val log = Logs.of(Logs.Module.CAPTURE)

@Composable
fun PathfinderCaptureScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val host = remember {
        runCatching {
            EncoreAuth.applySession(context)
            LoginFlow.markAttempt(context)
            log.i("starting capture pass on $PLAYER_URL")
            LoginWebViewHost(context, PLAYER_URL)
        }.getOrElse { t ->
            log.w("capture pass skipped, the webview is unusable: " +
                    "${t.cause?.message ?: t.message}")
            null
        }
    }

    LaunchedEffect(host) {
        if (host == null) {
            onDone()
            return@LaunchedEffect
        }
        LoginFlow.harvest(
            context = context,
            log = { log.i(it) },
            timeoutMs = 25_000,
            ready = { EncoreAuth.token(context).isNotBlank() },
        )
        host.readSession()?.let { session ->
            EncoreAuth.saveToken(context, session.token, session.expiresAtMs)
        }
        onDone()
    }

    DisposableEffect(host) {
        onDispose {
            if (host != null) {
                log.i("capture pass over, ops=${LoginFlow.ops()}")
                host.destroy()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EncoreColors.Background),
    ) {
        host?.let { captureHost ->
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0f),
                factory = { captureHost.container },
            )
        }
        SplashScreen()
    }
}
