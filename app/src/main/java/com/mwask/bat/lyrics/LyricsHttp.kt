package com.mwask.bat.lyrics

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP plumbing for the lyrics providers, ported from YumaPlayer's
 * provider clients (LrcLib, BetterLyrics, YouLyPlus, KuGou, SimpMusic, Unison).
 *
 * Every provider runs one short GET per candidate lookup, so the client keeps
 * Yuma's timeouts (10s connect / 15s read) instead of a long shared pool.
 */
internal object LyricsHttp {

    const val APP_USER_AGENT = "SpotiBat"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    class Response(val code: Int, val body: String) {
        val ok: Boolean get() = code in 200..299
    }

    /**
     * Blocking GET. Callers already run on Dispatchers.IO. Returns null when the
     * request could not be made at all (DNS, TLS, timeout) so providers can just
     * fall through to the next candidate.
     */
    fun get(
        url: String,
        params: Map<String, String?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): Response? {
        val base = url.toHttpUrlOrNull() ?: return null
        val builder = base.newBuilder()
        params.forEach { (key, value) ->
            if (value != null) builder.addQueryParameter(key, value)
        }
        val request = Request.Builder()
            .url(builder.build())
            .header("User-Agent", APP_USER_AGENT)
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                Response(response.code, response.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            null
        }
    }
}

/** Yuma discards whitespace-only lyrics, so every provider path does the same. */
internal fun String?.usableLyrics(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
