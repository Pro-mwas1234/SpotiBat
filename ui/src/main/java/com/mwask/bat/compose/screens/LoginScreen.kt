package com.mwask.bat.compose.screens

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mwask.bat.backend.core.auth.EncoreAuth
import com.mwask.bat.backend.core.auth.LoginFlow
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreTheme
import com.mwask.bat.backend.core.webview.LOGIN_URL
import com.mwask.bat.backend.core.webview.LoginWebViewHost
import com.mwask.bat.backend.core.webview.PLAYER_URL
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import com.mwask.bat.backend.core.log.Logs
import androidx.compose.ui.res.stringResource
import com.mwask.bat.compose.R

private val log = Logs.of(Logs.Module.LOGIN)

private sealed interface Resolve {
    data class Ok(val account: EncoreAuth.Account) : Resolve
    data object Guest : Resolve
    data object NotReady : Resolve
}

/**
 * the account behind it and hands it to [onLoggedIn].
 *
 * When signing into an *additional* account, pass [isDuplicateAccount]: the
 * moment the resolved account matches one already saved on this device the
 * login is refused and [onDuplicateLogin] fires instead — the same account
 * cannot be added twice.
 *
 * System back walks the webview back through its history first (a wrong
 * Google account pick goes back to the chooser instead of restarting the
 * flow). Only when no earlier page is left does [onBackExhausted] run; when
 * it is null the login is left the ordinary way (back out of the app).
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    isDuplicateAccount: ((EncoreAuth.Account) -> Boolean)? = null,
    onDuplicateLogin: ((EncoreAuth.Account) -> Unit)? = null,
    onBackExhausted: (() -> Unit)? = null,
) {

    val context = LocalContext.current
    var webviewAttempts by remember { mutableStateOf(0) }
    val host = remember(webviewAttempts) {
        runCatching { LoginWebViewHost(context) }.getOrElse { t ->
            log.w("login webview unusable: ${t.cause?.message ?: t.message}")
            null
        }
    }
    if (host == null) {
        WebViewUnavailableScreen(onRetry = { webviewAttempts++ })
        return
    }
    var splash by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    val navSignal = remember { Channel<Unit>(Channel.CONFLATED) }
    val webAlpha by animateFloatAsState(
        targetValue = if (splash) 0f else 1f,
        animationSpec = tween(
            durationMillis = if (splash) 200 else 320,
            easing = if (splash) FastOutLinearInEasing else LinearOutSlowInEasing,
        ),
        label = "webviewAlpha",
    )

    DisposableEffect(Unit) {
        host.onPlayerReached = { navSignal.trySend(Unit) }
        host.onBackAvailableChanged = { canGoBack = it }
        canGoBack = host.goBackAvailable()
        onDispose {
            host.onPlayerReached = null
            host.onBackAvailableChanged = null
            host.destroy()
        }
    }

    BackHandler {
        if (onBackExhausted != null) {
            onBackExhausted()
        } else {
            log.i("login back exhausted, leaving the app")
            (context as? Activity)?.finish()
        }
    }
    BackHandler(enabled = canGoBack) {
        host.goBack()
    }

    LaunchedEffect(Unit) {
        log.i("poll loop started")
        var first = true
        var busy = false
        var lastSpDc = ""
        var lastTryAt = 0L
        var rescues = 0
        while (isActive) {
            if (!first) withTimeoutOrNull(1500) { navSignal.receive() }
            first = false
            if (busy) continue
            if (!host.isOnPlayer) {
                if (splash) log.i("left the player, hiding splash")
                splash = false
                continue
            }
            val cookies = EncoreAuth.captureCookies(context) ?: continue
            val spDc = EncoreAuth.spDcOf(cookies)
            if (spDc.isBlank()) continue

            val now = SystemClock.elapsedRealtime()
            if (spDc == lastSpDc && now - lastTryAt < 4000) continue
            lastSpDc = spDc
            lastTryAt = now

            log.i("resolving url=${host.webView.url} sp_dc len=${spDc.length}")
            busy = true
            splash = true
            val started = SystemClock.elapsedRealtime()
            val result = withTimeoutOrNull(30_000) {
                resolveAccount(host, spDc) { log.i(it) }
            } ?: Resolve.NotReady
            busy = false

            when (result) {
                is Resolve.Ok -> {
                    log.i("resolved '${result.account.name}' in ${SystemClock.elapsedRealtime() - started}ms")
                    if (isDuplicateAccount != null && isDuplicateAccount(result.account)) {
                        log.w("login refused: '${result.account.name}' (${result.account.id}) is already saved here")
                        onDuplicateLogin?.invoke(result.account)
                        return@LaunchedEffect
                    }
                    EncoreAuth.saveLogin(context, cookies!!, result.account)
                    val userId = EncoreAuth.userId(context)
                    log.i("account id resolved='$userId'")
                    LoginFlow.harvest(context, log = { log.i(it) })
                    onLoggedIn()
                    return@LaunchedEffect
                }

                Resolve.Guest -> {
                    log.w("guest session on the player, reopening login")
                    splash = false
                    host.load(LOGIN_URL)
                }

                Resolve.NotReady -> {
                    log.w("not ready (${SystemClock.elapsedRealtime() - started}ms)")
                    rescues++; if (rescues >= 2) {
                        log.w("giving up on the player, reopening login")
                        splash = false
                        host.load(LOGIN_URL)
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EncoreColors.Background),
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .alpha(webAlpha),
            factory = { host.container },
        )

        AnimatedVisibility(
            visible = splash,
            enter = fadeIn(
                tween(durationMillis = 340, easing = LinearOutSlowInEasing),
            ) + scaleIn(
                animationSpec = tween(durationMillis = 340, easing = LinearOutSlowInEasing),
                initialScale = 0.94f,
            ),
            exit = fadeOut(
                tween(durationMillis = 300, easing = FastOutLinearInEasing),
            ) + scaleOut(
                animationSpec = tween(durationMillis = 300, easing = FastOutLinearInEasing),
                targetScale = 1.06f,
            ),
        ) {
            SplashScreen()
        }
    }
}

/** Shown instead of the login webview when the system WebView provider
 * cannot be loaded at all (package missing, disabled or mid-update). */
@Composable
private fun WebViewUnavailableScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EncoreColors.Background)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.system_webview_unavailable),
                style = EncoreTheme.typography.titleMedium,
                color = EncoreColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.webview_missing_message),
                style = EncoreTheme.typography.bodyMedium,
                color = EncoreColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(EncoreColors.Green)
                    .clickable { onRetry() }
                    .padding(horizontal = 28.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.retry),
                    style = EncoreTheme.typography.marginalBold.copy(
                        color = EncoreColors.PureBlack,
                        letterSpacing = 1.2.sp,
                    ),
                )
            }
        }
    }
}

private suspend fun resolveAccount(
    host: LoginWebViewHost,
    spDc: String,
    log: (String) -> Unit,
): Resolve {
    if (!host.isOnPlayer) {
        log("not on the player yet (url=${host.webView.url})")
        return Resolve.NotReady
    }

    val session = host.readSession()
    val token = session?.token
    log(if (token != null) "session token ok (${token.length})" else "no session token")
    if (session != null) {
        EncoreAuth.saveToken(host.context, session.token, session.expiresAtMs)
        val account = EncoreAuth.fetchAccount(session.token)
            ?: EncoreAuth.profile(host.context, session.token)
        log(if (account != null) "account ok (${account.name})" else "account lookup failed")
        if (account != null && account.name.isNotBlank()) return Resolve.Ok(account)
    }

    log("reading the user widget")
    val user = host.readUser()
    if (user != null) {
        if (user.isGuest) {
            log("user widget says guest")
            return Resolve.Guest
        }
        log("user widget ok (${user.name})")
        return Resolve.Ok(EncoreAuth.Account(name = user.name, imageUrl = user.avatarUrl))
    }
    log("user widget not found")

    log("token over http")
    val httpToken = EncoreAuth.fetchToken(host.context, spDc)
    log(if (httpToken != null) "http token ok (${httpToken.length})" else "http token: none")
    if (httpToken != null) {
        val account = EncoreAuth.fetchAccount(httpToken)
            ?: EncoreAuth.profile(host.context, httpToken)
        log(if (account != null) "http account ok (${account.name})" else "http account failed")
        if (account != null && account.name.isNotBlank()) return Resolve.Ok(account)
    }
    return Resolve.NotReady
}
