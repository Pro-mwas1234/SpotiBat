package com.project.lol.bridge

import android.app.Activity
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.project.lol.service.MediaNotificationService
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import androidx.core.content.edit
import com.project.lol.util.DebugLogStore
import com.project.lol.webview.helpers.AdIdStore

class SpotifyBridge(private val activityRef: WeakReference<Activity>) {

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"

        private val FILTERED_HEADERS = setOf(
            "x-requested-with",
            "sec-ch-ua-full-version-list",
            "sec-ch-ua-platform-version",
            "sec-ch-ua-arch",
            "sec-ch-ua-bitness",
            "sec-ch-ua-model"
        )
    }

    var onLoginDetected: (() -> Unit)? = null
    var onPlayLoaded: (() -> Unit)? = null
    var onMediaStatus: ((String) -> Unit)? = null
    var onMediaPosition: ((Long) -> Unit)? = null
    var onTimerDialogRequest: (() -> Unit)? = null
    var onEnterPipRequest: (() -> Unit)? = null
    var onEnterPipVideoRequest: ((Int, Int) -> Unit)? = null

    @Suppress("unused")
    @JavascriptInterface
    fun loginDetected() {
        val activity = activityRef.get() ?: return
        activity.getSharedPreferences("spotilol_prefs", Activity.MODE_PRIVATE)
            .edit {
                putBoolean("LoggedIn", true)
            }
        activity.runOnUiThread {
            onLoginDetected?.invoke()
        }
    }


    /**
     * Receives the bounded ad content ID list published by the page-level
     * AdStateHook (ported from Blockify's page-hook). Runs on the WebView's
     * JS-bridge thread - keep it fast: parse, validate, store, done.
     */
    @Suppress("unused")
    @JavascriptInterface
    fun recAdContentIds(json: String?) {
        if (json.isNullOrEmpty()) return
        try {
            val arr = org.json.JSONArray(json)
            val candidates = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                candidates.add(arr.optString(i))
            }
            if (AdIdStore.addAll(candidates)) {
                DebugLogStore.log("bridge", "ad-id store now " + AdIdStore.size())
            }
        } catch (_: Exception) {
            // Malformed page-provided payloads are ignored; AdIdStore re-validates.
        }
    }

    @Suppress("unused", "DEPRECATION")
    @JavascriptInterface
    fun deferMessage(msg: String?) {
        val activity = activityRef.get() ?: return
        if (msg == "adblock") return
        val display = when (msg) {
            "unlock" -> "Player unlocked"
            "reload" -> "Reloading..."
            else -> msg
        }
        activity.runOnUiThread {
            Toast.makeText(activity, display, Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun dbg(level: String?, msg: String?) {
        val m = msg ?: return
        val activity = activityRef.get() ?: return
        // Gate on "Collect Debug". The JS prelude gets nullified on toggle-off,
        // but its DevLog closure survives until page reload and calls us
        // directly - this native check is the actual enforcement.
        // SharedPreferences reads are in-memory map hits after first load,
        // so the cost here is negligible even under chatty debug builds.
        if (!activity.getSharedPreferences("spotilol_prefs", Activity.MODE_PRIVATE).getBoolean("DebugOverlay", false)) return
        val tag = when (level) {
            "w" -> "js.warn"
            "e" -> "js.err"
            "s" -> "js.sys"
            else -> "js"
        }
        DebugLogStore.log(tag, m)
    }

    @Suppress("unused")
    @JavascriptInterface
    fun isWoke(): Boolean {
        val activity = activityRef.get() ?: return false
        return activity.window?.decorView?.visibility == View.VISIBLE
    }

    @Suppress("unused")
    @JavascriptInterface
    fun wakeUp() {
    }

    @Suppress("unused")
    @JavascriptInterface
    fun wakeOff() {
    }

    @Suppress("unused")
    @JavascriptInterface
    fun cssInjected() {
    }

    @Suppress("unused")
    @JavascriptInterface
    fun playLoaded() {
        val activity = activityRef.get() ?: return
        activity.runOnUiThread {
            onPlayLoaded?.invoke()
        }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun recMediaPosition(position: Long) {
        onMediaPosition?.invoke(position)
        MediaNotificationService.instance?.updatePlaybackPosition(position)
    }

    @Suppress("unused")
    @JavascriptInterface
    fun recMediaStatus(json: String?) {
        json?.let {
            onMediaStatus?.invoke(it)
            MediaNotificationService.instance?.updateFromMediaStatus(it)
        }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun manageTShut(enabled: Boolean) {
    }

    @Suppress("unused")
    @JavascriptInterface
    fun manageTSleep(enabled: Boolean) {
    }

    @Suppress("unused")
    @JavascriptInterface
    fun recAccountName(name: String) {
        val activity = activityRef.get() ?: return
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            activity.getSharedPreferences("spotilol_prefs", Activity.MODE_PRIVATE)
                .edit {
                    putString("CurrentAccountName", trimmed)
                }
        }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun openTimerDialog() {
        val activity = activityRef.get() ?: return
        activity.runOnUiThread {
            onTimerDialogRequest?.invoke()
        }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun enterPip() {
        val activity = activityRef.get() ?: return
        activity.runOnUiThread {
            onEnterPipRequest?.invoke()
        }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun enterPipVideo(w: Int, h: Int) {
        val activity = activityRef.get() ?: return
        activity.runOnUiThread {
            onEnterPipVideoRequest?.invoke(w, h)
        }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun nFetch(url: String, optsJson: String?): String {
        val errorResult = { e: Exception ->
            try {
                JSONObject().apply {
                    put("status", 0)
                    put("body", e.toString())
                    put("headers", JSONObject())
                }.toString()
            } catch (_: Exception) {
                "{\"status\":0,\"body\":\"error\",\"headers\":{}}"
            }
        }

        var conn: HttpURLConnection? = null
        return try {
            val opts = if (optsJson.isNullOrBlank()) JSONObject() else JSONObject(optsJson)
            val method = opts.optString("method", "GET")
            val body = if (opts.has("body") && !opts.isNull("body")) opts.getString("body") else null
            val headersJson =
                if (opts.has("headers") && !opts.isNull("headers")) opts.getJSONObject("headers") else JSONObject()

            conn = URL(url).openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = method
                connectTimeout = 10000
                readTimeout = 10000
                val keys = headersJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (!FILTERED_HEADERS.contains(key.lowercase(Locale.ROOT))) {
                        setRequestProperty(key, headersJson.getString(key))
                    }
                }
                setRequestProperty("User-Agent", DESKTOP_UA)
                setRequestProperty("sec-ch-ua-platform", "\"Windows\"")
                setRequestProperty("sec-ch-ua-mobile", "?0")
                setRequestProperty("sec-ch-ua", "\"Not;A=Brand\";v=\"8\", \"Chromium\";v=\"150\", \"Google Chrome\";v=\"150\"")
                if (url.contains("spclient.spotify.com") || url.contains("scdn.co") || url.contains("spotify.com")) {
                    setRequestProperty("Origin", "https://open.spotify.com")
                    setRequestProperty("Referer", "https://open.spotify.com/")
                }
                val cookie = CookieManager.getInstance().getCookie(url)
                if (!cookie.isNullOrEmpty()) setRequestProperty("Cookie", cookie)
                if (!body.isNullOrEmpty()) {
                    doOutput = true
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }

            val code = conn.responseCode
            val headerFields = conn.headerFields

            // FIX (perf): single pass - harvest Set-Cookie and build the response
            // header JSON in one walk. headerFields' null key is the status line
            // pseudo-entry; skip it (previous code did too).
            var sawSetCookie = false
            val cookieManager = CookieManager.getInstance()
            val responseHeaders = JSONObject()
            headerFields.forEach { (key, values) ->
                if (key == null) return@forEach
                if (key.equals("Set-Cookie", ignoreCase = true)) {
                    sawSetCookie = true
                    values.forEach { cookieManager.setCookie(url, it) }
                }
                if (values.isNotEmpty()) responseHeaders.put(key, values.first())
            }
            // FIX (perf): flush() is synchronous disk I/O. Only pay it when we
            // actually wrote new cookies - read-only GETs (every page of a
            // playlist sort) skip it entirely.
            if (sawSetCookie) cookieManager.flush()

            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val responseBody = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""

            // NOTE (perf): no disconnect() on success. The fully-read response
            // returns the socket to the JVM keep-alive pool, so the next nFetch
            // to the same host reuses it and skips the TCP+TLS handshake.
            // Pool caps idle sockets per host and honors server keep-alive
            // expiry - nothing leaks if nFetch is never called again.
            JSONObject().apply {
                put("status", code)
                put("body", responseBody)
                put("headers", responseHeaders)
            }.toString()
        } catch (e: Exception) {
            try { conn?.disconnect() } catch (_: Exception) {}
            errorResult(e)
        }
    }
}