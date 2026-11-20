/*
 *  _   __                          _   
 * | | / /                         | |  
 * | |/ / _ __ ___  ___ _ __   ___ | |_ 
 * |    \| '__/ _ \/ __| '_ \ / _ \| __|
 * | |\  \ | |  __/\__ \ |_) | (_) | |_ 
 * \_| \_/_|  \___||___/ .__/ \___/ \__|
 *                     | |              
 *                     |_|              
 *
 * Copyright (c) 2026 23fpsz (SpotiBat). All Rights Reserved.
 * Original repository: https://github.com/Pro-mwas1234/SpotiBat
 *
 * PROPRIETARY / SOURCE-AVAILABLE MODULE:
 * You may view and audit this source code for personal or educational inspection.
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.mwask.bat.backend.core.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.mwask.bat.backend.core.auth.EncoreAuth
import com.mwask.bat.backend.core.locale.AppLocale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume
import com.mwask.bat.backend.core.log.Logs

const val LOGIN_URL =
    "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"
const val PLAYER_URL = "https://open.spotify.com/"

private val log = Logs.of(Logs.Module.WEBVIEW)
private const val SPOTIFY_ORIGIN = "https://open.spotify.com"
private const val ACCOUNTS_ORIGIN = "https://accounts.spotify.com"
private const val GOOGLE_ORIGIN = "https://accounts.google.com"

private val LOGIN_PATH = Regex("^/(?:[a-zA-Z0-9]{2,8}(?:-[a-zA-Z0-9]{2,8})?/)?login/?$")

class LoginWebViewHost(val context: Context, private val startUrl: String = LOGIN_URL) {

    val container: FrameLayout = FrameLayout(context)

    /** The login webviews run under a pinned en-US configuration whatever the
     *  device language is, so Spotify does not negotiate it into login URLs. */
    private val webContext: Context = AppLocale.loginContext(context)

    val webView: WebView = buildWebView(webContext, container)

    private var tokenResult: CompletableDeferred<String>? = null

    @Volatile
    var onPlayerReached: (() -> Unit)? = null

    /** Fired on every navigation with whether system back has an earlier
     * page to walk to now (see [goBack]). */
    @Volatile
    var onBackAvailableChanged: ((Boolean) -> Unit)? = null

    private fun notifyPlayerReached(url: String?) {
        if (url == null || !url.startsWith(SPOTIFY_ORIGIN)) return
        onPlayerReached?.invoke()
    }

    private fun isLoginPageUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        if (uri.host?.lowercase() != "accounts.spotify.com") return false
        val path = uri.path ?: return false
        return LOGIN_PATH.matches(path)
    }

    private fun notifyBackAvailable() {
        onBackAvailableChanged?.invoke(goBackAvailable())
    }

    /** The top-most popup (SSO window) when one is open, else null. */
    private fun topPopup(): WebView? {
        for (index in (0 until container.childCount).reversed()) {
            val child = container.getChildAt(index)
            if (child is WebView && child !== webView) return child
        }
        return null
    }

    private fun closePopup(popup: WebView) {
        log.i("back closed popup")
        container.removeView(popup)
        runCatching { popup.destroy() }
    }

    /** Whether system back would do anything inside the login webview. */
    fun goBackAvailable(): Boolean = topPopup() != null || webView.canGoBack()

    /**
     * System back for the login flow. A wrong turn (say, logging in with the
     * wrong Google account) walks the top-most webview — an SSO popup when
     * one is open — back through its history, closing the popup once its own
     * history runs out; the main webview goes back one page otherwise.
     *
     * Returns false when there is no earlier page left, so the caller can
     * fall back to its own back behavior (leave the login, exit the app).
     */
    fun goBack(): Boolean {
        val popup = topPopup()
        if (popup != null) {
            if (popup.canGoBack()) {
                log.i("back inside popup ${popup.url}")
                popup.goBack()
            } else {
                closePopup(popup)
            }
            notifyBackAvailable()
            return true
        }
        if (webView.canGoBack()) {
            log.i("back to ${webView.url}")
            webView.goBack()
            return true
        }
        return false
    }

    private val bridge = object {
        @JavascriptInterface
        fun token(value: String?) {
            log.i("bridge.token(len=${value?.length ?: 0})")
            tokenResult?.complete(value.orEmpty())
        }

        @JavascriptInterface
        fun name(value: String?) {
            log.i("bridge.name(${value?.take(40)})")
        }
    }

    init {
        AppLocale.pushLoginLocale()
        container.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        webView.addJavascriptInterface(bridge, "AndLogin")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                log.i("onPageStarted $url")
                if (url == null) return
                when {
                    url.startsWith(GOOGLE_ORIGIN) -> view?.evaluateJavascript(BrowserSpoof.GOOGLE_CONTENT, null)
                    !url.startsWith(ACCOUNTS_ORIGIN) -> view?.evaluateJavascript(BrowserSpoof.CONTENT, null)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                log.i("onPageFinished $url progress=${view?.progress}")
                view?.evaluateJavascript(
                    """
                    document.querySelectorAll('a.fuckd').forEach(el => el.remove());
                    """.trimIndent(),
                    null
                )
                notifyPlayerReached(url)
                notifyBackAvailable()
                if (url != null && isLoginPageUrl(url)) {
                    log.d("classic login pill fallback injected")
                    view?.evaluateJavascript(ClassicLoginButton.CONTENT, null)
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                log.d("history $url reload=$isReload")
                notifyPlayerReached(url)
                notifyBackAvailable()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    log.e("onReceivedError ${request.url} ${error?.errorCode} ${error?.description}")
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true) {
                    log.e("onReceivedHttpError ${request.url} ${errorResponse?.statusCode}")
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                log.e("onReceivedSslError ${error?.url} ${error?.primaryError}")
                super.onReceivedSslError(view, handler, error)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                log.i("onPermissionRequest ${request?.origin} ${request?.resources?.joinToString(",")}"
                )
                request?.grant(request.resources)
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?,
            ): Boolean {
                log.i("onCreateWindow dialog=$isDialog gesture=$isUserGesture")
                val child = buildWebView(webContext, container)
                child.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(v, url, favicon)
                        log.i("popup.onPageStarted $url")
                        if (url == null) return
                        if (url.startsWith(GOOGLE_ORIGIN)) {
                            v?.evaluateJavascript(BrowserSpoof.GOOGLE_CONTENT, null)
                        } else if (!url.startsWith(ACCOUNTS_ORIGIN) && !url.startsWith(SPOTIFY_ORIGIN)) {
                            v?.evaluateJavascript(BrowserSpoof.CONTENT, null)
                        }
                    }

                    override fun onPageFinished(v: WebView?, url: String?) {
                        super.onPageFinished(v, url)
                        log.i("popup.onPageFinished $url")
                    }

                    override fun doUpdateVisitedHistory(v: WebView?, url: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(v, url, isReload)
                        notifyBackAvailable()
                    }
                }
                child.webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest?) {
                        log.i("popup.onPermissionRequest ${request?.origin} ${request?.resources?.joinToString(",")}"
                        )
                        request?.grant(request.resources)
                    }

                    override fun onCloseWindow(window: WebView?) {
                        log.i("popup.onCloseWindow")
                        container.removeView(window)
                        window?.destroy()
                    }
                }
                installSpoofScripts(child)
                NetworkTap.install(child, context)
                container.addView(
                    child,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                (resultMsg?.obj as? WebView.WebViewTransport)?.webView = child
                resultMsg?.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                if (window !== webView) {
                    log.i("onCloseWindow popup")
                    container.removeView(window)
                    window?.destroy()
                }
            }
        }
        installSpoofScripts(webView)
        NetworkTap.install(webView, context)
        log.i("loading $startUrl")
        webView.loadUrl(startUrl)
    }

    fun load(url: String) {
        if (webView.url == url) {
            log.i("load skipped, already at $url")
            return
        }
        log.i("load $url (from ${webView.url})")
        webView.loadUrl(url)
    }

    val isOnPlayer: Boolean
        get() = webView.url?.startsWith(SPOTIFY_ORIGIN) == true

    suspend fun awaitPlayerLoaded(timeoutMs: Long = 15_000): Boolean {
        log.i("awaitPlayerLoaded url=${webView.url} progress=${webView.progress}")
        if (isOnPlayer && webView.progress >= 100) {
            log.i("awaitPlayerLoaded already loaded")
            return true
        }
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (isOnPlayer && webView.progress >= 100) {
                log.i("awaitPlayerLoaded ready after ${timeoutMs - (deadline - SystemClock.uptimeMillis())} ms")
                return true
            }
            delay(150)
        }
        log.w("awaitPlayerLoaded TIMEOUT url=${webView.url} progress=${webView.progress}")
        return isOnPlayer
    }

    suspend fun readToken(): String? {
        log.i("readToken on ${webView.url}")
        readSessionToken()?.let { return it }
        tokenResult?.cancel()
        val gate = CompletableDeferred<String>()
        tokenResult = gate
        webView.evaluateJavascript(
            """
            (function(){
                var done = false;
                function send(v){ if(!done){ done = true; AndLogin.token(v || ''); } }
                try {
                    fetch('/get_access_token?reason=transport&productType=web_player', {credentials:'include'})
                        .then(function(r){ return r.text(); })
                        .then(function(t){ send(t); })
                        .catch(function(e){ send(''); });
                } catch(e) { send(''); }
                setTimeout(function(){ send(''); }, 7000);
            })();
            """.trimIndent(),
        ) { raw -> log.d("readToken js=$raw") }
        val raw = withTimeoutOrNull(9_000) { gate.await() }
        if (raw.isNullOrBlank()) {
            log.w("readToken empty")
            return null
        }
        val json = runCatching { JSONObject(raw) }.getOrNull()
        if (json == null) {
            log.w("readToken not-json ${raw.replace("\n", " ").take(120)}")
            return null
        }
        val anonymous = json.optBoolean("isAnonymous", false)
        val token = json.optString("accessToken")
        log.i("readToken anonymous=$anonymous tokenLen=${token.length}")
        return token.takeIf { it.isNotBlank() && !anonymous }
    }

    data class DomUser(
        val name: String,
        val avatarUrl: String,
        val isGuest: Boolean,
    )

    suspend fun readUser(attempts: Int = 8): DomUser? {
        repeat(attempts) { i ->
            val user = readUserOnce()
            if (user != null) return user
            log.d("readUser attempt ${i + 1}/$attempts: widget not there yet")
            delay(300)
        }
        return null
    }

    private suspend fun readUserOnce(): DomUser? {
        val raw = evalString(READ_USER_JS)
        if (raw.isNullOrBlank()) {
            log.w("readUser empty")
            return null
        }
        val json = runCatching { JSONObject(raw) }.getOrNull()
        if (json == null) {
            log.w("readUser not-json ${raw.take(120)}")
            return null
        }
        val name = json.optString("name").trim()
        val avatar = json.optString("avatar")
        val guest = json.optBoolean("guest", false)
        val title = json.optString("title")
        val bodyLen = json.optInt("bodyLen", -1)
        val error = json.optString("error")
        log.i("readUser name='$name' avatar=${avatar.isNotBlank()} guest=$guest " +
                "title='$title' bodyLen=$bodyLen err=$error"
        )
        if (name.isBlank() && !guest) return null
        return DomUser(name, avatar, guest)
    }

    data class Session(
        val token: String,
        val expiresAtMs: Long,
        val anonymous: Boolean,
    ) {
        val ttlMs: Long get() = if (expiresAtMs <= 0L) -1L else expiresAtMs - System.currentTimeMillis()
    }

    suspend fun readSession(): Session? {
        val raw = evalString(SESSION_JS) ?: return null
        if (raw.isBlank()) {
            log.i("readSession: no #session script")
            return null
        }
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val anonymous = json.optBoolean("anon", false)
        val token = json.optString("token")
        val exp = json.optLong("exp", 0L)
        val ttl = if (exp <= 0L) -1L else exp - System.currentTimeMillis()
        log.i("readSession anonymous=$anonymous tokenLen=${token.length} exp=$exp ttl=${ttl / 1000}s"
        )
        if (token.isBlank() || anonymous) return null
        return Session(token = token, expiresAtMs = exp, anonymous = anonymous)
    }

    suspend fun readSessionToken(): String? = readSession()?.token

    private suspend fun evalString(js: String): String? = suspendCancellableCoroutine { cont ->
        webView.evaluateJavascript(js) { raw ->
            val value = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
            if (cont.isActive) cont.resume(value)
        }
    }

    fun destroy() {
        log.i("destroy")
        try {
            webView.removeJavascriptInterface("AndLogin")
            webView.stopLoading()
            for (index in (0 until container.childCount).reversed()) {
                val popup = container.getChildAt(index)
                if (popup is WebView && popup !== webView) {
                    container.removeViewAt(index)
                    runCatching { popup.destroy() }
                }
            }
            container.removeAllViews()
            webView.destroy()
        } catch (_: Exception) {
        }
        AppLocale.popLoginLocale()
    }

    private fun installSpoofScripts(view: WebView) {
        val supported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        log.i("installSpoofScripts supported=$supported")
        if (!supported) return
        runCatching {
            WebViewCompat.addDocumentStartJavaScript(view, BrowserSpoof.CONTENT, setOf(ACCOUNTS_ORIGIN))
            WebViewCompat.addDocumentStartJavaScript(view, BrowserSpoof.GOOGLE_CONTENT, setOf(GOOGLE_ORIGIN))
            WebViewCompat.addDocumentStartJavaScript(view, BrowserSpoof.CONTENT, setOf(SPOTIFY_ORIGIN))
        }.onFailure { log.e("installSpoofScripts failed", it) }
    }

    companion object {
        private val SESSION_JS = """
            (function(){
                try {
                    var el = document.getElementById('session');
                    if (el) {
                        var j = JSON.parse(el.textContent || '{}');
                        if (j.accessToken) {
                            return JSON.stringify({
                                token: j.accessToken,
                                anon: !!j.isAnonymous,
                                exp: j.accessTokenExpirationTimestampMs || 0
                            });
                        }
                    }
                    var scripts = document.getElementsByTagName('script');
                    for (var i = 0; i < scripts.length; i++) {
                        var t = scripts[i].textContent || '';
                        if (t.indexOf('accessToken') < 0) continue;
                        var m = t.match(/"accessToken"\s*:\s*"([A-Za-z0-9\-_\.]+)"/);
                        if (!m) continue;
                        var e = t.match(/"accessTokenExpirationTimestampMs"\s*:\s*(\d+)/);
                        return JSON.stringify({
                            token: m[1],
                            anon: false,
                            exp: e ? parseInt(e[1], 10) : 0
                        });
                    }
                    return '';
                } catch(e) { return ''; }
            })()
        """.trimIndent()

        private val READ_USER_JS = """
            (function(){
                try {
                    var title = document.title || '';
                    var bodyLen = document.body ? document.body.innerText.length : -1;
                    var link = document.querySelector('[data-testid="user-widget-link"]');
                    if (!link) {
                        var guest = document.querySelector('[data-testid="login-button"]')
                            || document.querySelector('[data-testid="signup-button"]');
                        return JSON.stringify({ name: '', avatar: '', guest: !!guest, title: title, bodyLen: bodyLen });
                    }
                    var name = (link.getAttribute('aria-label') || link.innerText || '').trim();
                    var img = link.querySelector('img');
                    var avatar = img ? img.src : '';
                    if (!avatar) {
                        var styled = link.querySelector('[style*="background-image"]');
                        if (styled) {
                            var bg = getComputedStyle(styled).backgroundImage || '';
                            var m = bg.match(/url\(["']?(.*?)["']?\)/);
                            if (m) avatar = m[1];
                        }
                    }
                    return JSON.stringify({ name: name, avatar: avatar || '', guest: false, title: title, bodyLen: bodyLen });
                } catch(e) {
                    return JSON.stringify({ name: '', avatar: '', guest: false, error: String(e) });
                }
            })()
        """.trimIndent()

        @SuppressLint("SetJavaScriptEnabled")
        private fun buildWebView(context: Context, parent: ViewGroup): WebView {
            val view = WebView(context)
            cookieManagerOrNull()?.let { manager ->
                manager.setAcceptCookie(true)
                manager.setAcceptThirdPartyCookies(view, true)
            }
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                allowFileAccess = false
                allowContentAccess = false
                mediaPlaybackRequiresUserGesture = false
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            view.setBackgroundColor(0xFF000000.toInt())
            return view
        }
    }
}
