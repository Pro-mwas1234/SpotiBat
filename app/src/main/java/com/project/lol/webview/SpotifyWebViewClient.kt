package com.project.lol.webview

import android.graphics.Bitmap
import android.util.Log
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.project.lol.webview.helpers.*
import com.project.lol.webview.injections.*
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import androidx.core.net.toUri
import androidx.core.content.edit

class SpotifyWebViewClient(
    private val onLoginRequired: () -> Unit,
    private val onNavStateChanged: ((Boolean) -> Unit)? = null,
    private val onRenderProcessGone: (() -> Unit)? = null,
    private val onWebViewError: ((errorCode: Int, description: String) -> Unit)? = null
) : WebViewClient() {

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        onNavStateChanged?.invoke(view?.canGoBack() == true)
    }

    private var currentWebView: WebView? = null
    private var prefsListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var boundPrefs: android.content.SharedPreferences? = null

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view == null || url == null) return

        currentWebView = view
        registerPrefsListener(view)

        if (url.startsWith("https://www.facebook.com/privacy/consent/gdp/")) {
            onPageFinishedClean(view, "FbGdprBypass", FbGdprBypass.CONTENT)
            return
        }

        if (url.endsWith("/login")) {
            onPageFinishedClean(view, "ClassicLoginButton", ClassicLoginButton.CONTENT)
        }

        val loggedIn = view.context.getSharedPreferences("spotilol_prefs", 0)
            .getBoolean("LoggedIn", false)

        if (!loggedIn) {
            onPageFinishedClean(view, "LoginDetection", LoginDetection.CONTENT)
            return
        }

        view.postDelayed({
            injectPlayerControl(view)
        }, 500)

        view.evaluateJavascript(staticJs("LogoutCheck", LogoutCheck.CONTENT)) { result ->
            if (result == "\"out\"") {
                view.context.getSharedPreferences("spotilol_prefs", 0)
                    .edit { putBoolean("LoggedIn", false) }
                view.loadUrl("https://accounts.spotify.com/login")
            }
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        val useProxy = view?.context?.getSharedPreferences("spotilol_prefs", 0)
            ?.getString("ConnectionMode", "normal") == "proxy"
        val powerSave = view?.context?.getSharedPreferences("spotilol_prefs", 0)
            ?.getBoolean("PowerSave", false) ?: false
        view?.evaluateJavascript("window.__spotilolUseProxy=$useProxy;", null)
        // FIX: these payloads were injected raw - strip them like every other
        // injection, served from cache.
        if (isGoogleAuthUrl(url)) {
            view?.evaluateJavascript(staticJs("GoogleSpoof", GoogleSpoof.CONTENT), null)
        } else {
            view?.evaluateJavascript(staticJs("BrowserSpoof", BrowserSpoof.CONTENT), null)
        }
        view?.evaluateJavascript(staticJs("FetchOverride", FetchOverride.CONTENT), null)
        AdIdStore.clear()
        view?.evaluateJavascript(staticJs("AdStateHook", AdStateHook.CONTENT), null)
        view?.evaluateJavascript(staticJs("WorkerNeutralize", WorkerNeutralize.CONTENT), null)
        view?.evaluateJavascript(staticJs("GaBlocker", GaBlocker.CONTENT), null)
        view?.evaluateJavascript("window.__splPowerSavePref=$powerSave;", null)
        view?.evaluateJavascript(staticJs("PowerSave", PowerSave.CONTENT), null)
        view?.evaluateJavascript(staticJs("SettingsFix", SettingsFix.CONTENT), null)
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        Log.w(TAG, "Renderer process gone: crashed=${detail?.didCrash()}")
        // Don't touch view.parent here - detaching from the UI thread is the
        // activity's job. We just destroy the dead WebView (mandatory per docs:
        // "the WebView can no longer be used") and notify up so MainActivity can
        // tear down its references and rebuild via a composition key bump.
        try {
            view?.let {
                it.stopLoading()
                it.destroy()
            }
        } catch (_: Exception) {}
        onRenderProcessGone?.invoke()
        return true
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()
        if (url.contains("ms-store-badge") || url.contains("get.microsoft.com")) {
            val headers = mapOf("Access-Control-Allow-Origin" to "*")
            val contentType = if (url.contains("get.microsoft.com")) "image/svg+xml" else "text/javascript"
            return WebResourceResponse(contentType, "utf-8", 200, "OK", headers,
                ByteArrayInputStream(ByteArray(0)))
        }

        if (isAnalyticsDomain(url)) {
            val headers = mapOf("Access-Control-Allow-Origin" to "*")
            return WebResourceResponse("text/plain", "utf-8", 200, "OK", headers,
                ByteArrayInputStream(ByteArray(0)))
        }

        if (AdIdStore.matches(url)) {
            view.post { view.evaluateJavascript("AndBridge.deferMessage('adblock')", null) }
            val silent = view.context.assets?.open("silent.mp3") ?: return null
            return WebResourceResponse("audio/mpeg", null, silent)
        }

        if (view.context.getSharedPreferences("spotilol_prefs", 0)
                .getBoolean("PowerSave", false) && isPowerHogUrl(url)) {
            val headers = mapOf("Access-Control-Allow-Origin" to "*")
            return WebResourceResponse("text/plain", "utf-8", 200, "OK", headers,
                ByteArrayInputStream(ByteArray(0)))
        }

        val useProxy = view.context.getSharedPreferences("spotilol_prefs", 0)
            .getString("ConnectionMode", "normal") == "proxy"

        if (!useProxy) {
            val isGoogle = isGoogleAuthUrl(url)
            val adMatch = matchAdCdn(url)
            if (!isGoogle && adMatch == null) return null

            // FIX: known ad hosts get silence immediately - no pre-connect, no
            // content-type guessing. The old flow opened + abandoned a connection
            // to EVERY broad audio-CDN URL (akamaized.net/audio/, scdn.co/audio/)
            // before letting the WebView re-request it, which (a) poisoned Akamai
            // edges into stalling real streams -> auto-skip, and (b) swapped
            // silence over any legit stream served as audio/mpeg -> mute after
            // the ~10s buffer. Proxy mode only ever blocked matchAdCdn() hosts;
            // normal mode now matches that behavior.
            if (adMatch != null) {
                view.post { view.evaluateJavascript("AndBridge.deferMessage('adblock')", null) }
                val silent = view.context.assets?.open("silent.mp3") ?: return null
                return WebResourceResponse("audio/mpeg", null, silent)
            }

            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = request.method
                    conn.instanceFollowRedirects = true
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    for ((k, v) in request.requestHeaders) {
                        val lk = k.lowercase(Locale.ROOT)
                        if (lk != "x-requested-with" && lk != "sec-gpc" && !lk.startsWith("sec-ch-ua") &&
                            lk != "user-agent"
                        ) {
                            conn.setRequestProperty(k, v)
                        }
                    }
                    conn.setRequestProperty("User-Agent", DESKTOP_UA)
                    val cookie = CookieManager.getInstance().getCookie(url)
                    if (!cookie.isNullOrEmpty()) conn.setRequestProperty("Cookie", cookie)
                    conn.setRequestProperty("sec-gpc", "1")
                    conn.setRequestProperty("sec-ch-ua-platform", "\"Windows\"")
                    conn.setRequestProperty("sec-ch-ua-mobile", "?0")
                    conn.setRequestProperty("sec-ch-ua", "\"Not;A=Brand\";v=\"8\", \"Chromium\";v=\"150\", \"Google Chrome\";v=\"150\"")
                    conn.connect()
                    conn.headerFields.forEach { (key, values) ->
                        if (key != null && key.equals("Set-Cookie", ignoreCase = true)) {
                            values.forEach { CookieManager.getInstance().setCookie(url, it) }
                        }
                    }
                    CookieManager.getInstance().flush()
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                return null
            }
            return null
        }

        val adMatch = matchAdCdn(url)
        if (adMatch != null) {
            view.post { view.evaluateJavascript("AndBridge.deferMessage('adblock')", null) }
            val silent = view.context.assets?.open("silent.mp3") ?: return null
            return WebResourceResponse("audio/mpeg", null, silent)
        }

        return null
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame != true) return
        val code = try { error?.errorCode ?: -1 } catch (_: Exception) { -1 }
        val desc = try { error?.description?.toString() ?: "" } catch (_: Exception) { "" }
        onWebViewError?.invoke(code, desc)
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request?.isForMainFrame != true) return
        val status = try { errorResponse?.statusCode ?: 0 } catch (_: Exception) { 0 }
        if (status >= 400) {
            onWebViewError?.invoke(status, "HTTP $status")
        }
    }

    private fun isGoogleAuthUrl(url: String?): Boolean {
        if (url == null) return false
        val host = runCatching { url.toUri().host }.getOrNull()
            ?.lowercase() ?: return false
        return host == "google.com" ||
            host.endsWith(".google.com") ||
            host.contains(".google.") ||
            host.endsWith(".youtube.com") ||
            host == "youtube.com"
    }

    private fun injectPlayerControl(view: WebView) {
        val prefs = view.context.getSharedPreferences("spotilol_prefs", 0)
        val autoPlayMode = prefs.getString("APlayMode", "disabled") ?: "disabled"
        val closeNowPlay = prefs.getBoolean("CloseNowPlay", true)
        val amoledEnabled = prefs.getBoolean("AmoledTheme", false)
        val customCss = prefs.getString("CustomCss", "") ?: ""
        val playerMode = prefs.getString("PlayerMode", "spotilol") ?: "spotilol"
        val useProxy = prefs.getString("ConnectionMode", "normal") == "proxy"
        val debugOverlay = prefs.getBoolean("DebugOverlay", false)

        val js = buildString {
            append("window.autoPlayMode='$autoPlayMode';\n")
            append("window.closeNpPref=$closeNowPlay;\n")
            append("window.__spotilolUseProxy=$useProxy;\n")
            if (prefs.getBoolean("DebugOverlay", false)) {
                append(DevLogPrelude.js())
                append("\n")
            }
            append(PlayerCore.CONTENT)
            append(TrackObserver.CONTENT)
            append(ClassicBridge.CONTENT)
            append(MediaUpdater.CONTENT)
            append(LibraryFetcher.CONTENT)
            append(LibraryParser.CONTENT)
            append(PlaybackControls.CONTENT)
            append(MainLoop.CONTENT)
            append(AutoFeatures.CONTENT)
            append(AndroidTracker.CONTENT)
            append(SearchOverlay.CONTENT)
            append("""
                (function(){
                    var recAcc=function(){
                        try{
                            var uw=document.querySelector('[data-testid="user-widget-link"]');
                            if(uw){
                                var txt=(uw.textContent||'').split('\n')[0].trim();
                                if(txt) AndBridge.recAccountName(txt);
                            }
                        }catch(e){}
                    };
                    setTimeout(recAcc,5000);
                    setInterval(recAcc,60000);
                })();
            """.trimIndent())
            append(CssHack.CONTENT)
            append(ModalFix.CONTENT)
            append(ErrorDialogRestyle.CONTENT)
            append(ToastFix.CONTENT)
            append(LyricsSyncFix.CONTENT)
            if (playerMode == "spotilol") {
                append(SpotilolPlayer.CONTENT)
            }
        }
        // FIX (perf): cache key must uniquely determine `js` - only these five
        // inputs feed the stripped section. `amoledEnabled` and `customCss` are
        // deliberately EXCLUDED: they are appended after stripping and never
        // enter the scanner, so including them would only cause needless
        // cache misses on every theme/CSS change.
        val coreKey = "player-core|$autoPlayMode|$closeNowPlay|$useProxy|$debugOverlay|$playerMode"

        // overlay captures via AndBridge.dbg (dbg/DevLog call
        // sites), not by hooking console.log - stripping stays active in debug mode.
        val cleanJs = JsUtils.stripConsoleLogsCached(coreKey, js) + "\n" +
                buildAmoledJs(amoledEnabled) + "\n" +
                buildCustomCssJs(customCss)
        if (playerMode == "original") {
            view.evaluateJavascript("$cleanJs\n(function(){var s=document.createElement('style');s.id='spl-np-show';s.textContent='aside[data-testid=\"now-playing-bar\"]{display:flex!important}';document.head.appendChild(s);})();", null)
        } else {
            view.evaluateJavascript(cleanJs, null)
        }
    }

    private fun registerPrefsListener(view: WebView) {
        val prefs = view.context.getSharedPreferences("spotilol_prefs", 0)
        prefsListener?.let { boundPrefs?.unregisterOnSharedPreferenceChangeListener(it) }
        boundPrefs = prefs
        prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "PlayerMode") {
                val wv = currentWebView ?: return@OnSharedPreferenceChangeListener
                val mode = prefs.getString("PlayerMode", "spotilol") ?: "spotilol"
                switchPlayerMode(wv, mode)
            }
            if (key == "PowerSave") {
                val wv = currentWebView ?: return@OnSharedPreferenceChangeListener
                val on = prefs.getBoolean("PowerSave", false)
                wv.evaluateJavascript("if(window.__splApplyPowerSave) window.__splApplyPowerSave($on);", null)
            }
            if (key == "CloseNowPlay") {
                val wv = currentWebView ?: return@OnSharedPreferenceChangeListener
                val closeNp = prefs.getBoolean("CloseNowPlay", true)
                wv.evaluateJavascript("window.closeNpPref=$closeNp;", null)
            }
            if (key == "APlayMode") {
                val wv = currentWebView ?: return@OnSharedPreferenceChangeListener
                val mode = prefs.getString("APlayMode", "disabled") ?: "disabled"
                wv.evaluateJavascript("window.autoPlayMode='$mode';", null)
            }
            if (key == "AmoledTheme") {
                val wv = currentWebView ?: return@OnSharedPreferenceChangeListener
                val on = prefs.getBoolean("AmoledTheme", false)
                wv.evaluateJavascript(buildAmoledJs(on), null)
            }
            if (key == "CustomCss") {
                val wv = currentWebView ?: return@OnSharedPreferenceChangeListener
                val css = prefs.getString("CustomCss", "") ?: ""
                wv.evaluateJavascript(buildCustomCssJs(css), null)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun switchPlayerMode(view: WebView, mode: String) {
        if (mode == "original") {
            val js = """
                (function(){
                    var pl=document.getElementById('spotilolPlayerControls');
                    if(pl) pl.style.display='none';
                    var s=document.createElement('style');
                    s.id='spl-np-show';
                    s.textContent='aside[data-testid="now-playing-bar"]{display:flex!important}';
                    document.head.appendChild(s);
                })();
            """.trimIndent()
            view.evaluateJavascript(js, null)
        } else {
            // FIX: this path injected SpotilolPlayer.CONTENT raw while the initial
            // injection stripped it - its logs leaked via the mode-switch path.
            view.evaluateJavascript("if(typeof initSpotilolPlayer!=='function'){" + staticJs("SpotilolPlayer", SpotilolPlayer.CONTENT) + "}", null)
            val js = """
                (function(){
                    var s=document.getElementById('spl-np-show');
                    if(s) s.remove();
                    var npb=document.querySelector('aside[data-testid="now-playing-bar"]');
                    if(npb) npb.style.display='none';
                    var pl=document.getElementById('spotilolPlayerControls');
                    if(pl){pl.style.display='flex';}
                    else if(typeof initSpotilolPlayer==='function'){initSpotilolPlayer();}
                })();
            """.trimIndent()
            view.evaluateJavascript(js, null)
        }
    }

    private fun onPageFinishedClean(view: WebView, name: String, js: String) {
        view.evaluateJavascript(staticJs(name, js), null)
    }

    /**
     * Detaches the prefs listener and drops the WebView reference so the client
     * (and the whole view tree it points at) can be collected once the WebView
     * is destroyed. Called from MainActivity.destroyWebView().
     */
    fun release() {
        prefsListener?.let { boundPrefs?.unregisterOnSharedPreferenceChangeListener(it) }
        prefsListener = null
        boundPrefs = null
        currentWebView = null
    }

    /**
     * FIX (perf): static injection payloads are compile-time constants but were
     * re-scanned on every page event. Memoize them under stable name keys via
     * the bounded LRU cache - after the first call this is a map lookup.
     */
    private fun staticJs(name: String, js: String): String =
        JsUtils.stripConsoleLogsCached("static:$name", js)

    companion object {
        private const val TAG = "SpotifyWebViewClient"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"
    }
}
