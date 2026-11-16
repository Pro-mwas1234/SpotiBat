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

package com.mwask.bat.backend.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import com.mwask.bat.backend.core.log.Logs

private val log = Logs.of(Logs.Module.NET)

private const val NETWORK_ATTEMPTS = 3
private const val NETWORK_BACKOFF_MS = 700L

object Net {

    @Volatile
    var enabled: Boolean = true

    @Volatile
    var onUnauthorized: (suspend (sentToken: String, reason: String) -> String?)? = null

    private val refreshGate = kotlinx.coroutines.sync.Mutex()

    private suspend fun refreshed(sent: String, reason: String): String? {
        val hook = onUnauthorized ?: return null
        return refreshGate.withLock {
            log.w("$reason 401, asking for a fresh access token")
            hook(sent, reason)
        }
    }

    private fun bearerOf(headers: Map<String, String>): String {
        val value = headers.entries
            .firstOrNull { it.key.equals("Authorization", ignoreCase = true) }
            ?.value.orEmpty()
        return value.removePrefix("Bearer ").trim()
    }

    private fun withBearer(headers: Map<String, String>, token: String): Map<String, String> {
        if (token.isBlank()) return headers
        val key = headers.keys.firstOrNull { it.equals("Authorization", ignoreCase = true) }
            ?: return headers
        return headers + (key to "Bearer $token")
    }

    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"

    data class Response(
        val code: Int,
        val body: String,
        val contentType: String,
        val millis: Long,
        val url: String,
    ) {
        val ok: Boolean get() = code in 200..299
        val bytes: Int get() = body.length
        fun json(): JSONObject? = runCatching { JSONObject(body) }.getOrNull()
        fun jsonArray(): JSONArray? = runCatching { JSONArray(body) }.getOrNull()
    }

    data class Blob(
        val code: Int,
        val bytes: ByteArray,
        val contentType: String,
        val millis: Long,
        val headers: Map<String, String> = emptyMap(),
    ) {
        val ok: Boolean get() = code in 200..299
        val size: Int get() = bytes.size
        fun header(name: String): String = headers[name.lowercase()].orEmpty()
        val retryAfterSeconds: Long? get() = header("retry-after").trim().toLongOrNull()
    }

    private val counter = AtomicInteger(0)

    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .build()

    private val longClient: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun contentTypeOf(headers: Map<String, String>): MediaType? =
        headers.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value?.toMediaTypeOrNull()

    private fun buildRequest(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
    ): Request {
        val builder = Request.Builder().url(url)
        if (body != null) {
            builder.method(method, body.toRequestBody(contentTypeOf(headers)))
        } else {
            builder.method(method, null)
        }
        headers.forEach { (k, v) ->
            if (body != null && k.equals("Content-Type", ignoreCase = true)) return@forEach
            builder.header(k, v)
        }
        return builder.build()
    }

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        tag: String = "",
    ): Response? = request("GET", url, headers, null, tag)

    suspend fun postJson(
        url: String,
        json: String,
        headers: Map<String, String> = emptyMap(),
        tag: String = "",
    ): Response? = request(
        "POST",
        url,
        if (headers.containsKey("Content-Type")) {
            headers
        } else {
            headers + mapOf("Content-Type" to "application/json")
        },
        json,
        tag,
    )

    suspend fun putJson(
        url: String,
        json: String,
        headers: Map<String, String> = emptyMap(),
        tag: String = "",
    ): Response? = request(
        "PUT",
        url,
        if (headers.containsKey("Content-Type")) {
            headers
        } else {
            headers + mapOf("Content-Type" to "application/json")
        },
        json,
        tag,
    )

    suspend fun postBytes(
        url: String,
        bytes: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
        tag: String = "",
    ): Response? = requestBytes(
        "POST",
        url,
        headers + mapOf("Content-Type" to contentType),
        bytes,
        tag,
    )

    suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        tag: String = "",
    ): Response? = requestBytes(method, url, headers, body?.toByteArray(), tag, body)

    suspend fun requestBytes(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        tag: String = "",
        bodyPreview: String? = null,
    ): Response? {
        val first = attempt(method, url, headers, body, tag, bodyPreview)
        if (first == null || first.code != 401) return first
        val fresh = refreshed(bearerOf(headers), "$method ${short(url)}") ?: return first
        log.w("${tag.ifBlank { method }} retrying with a token ${fresh.length}B long after a 401")
        return attempt(method, url, withBearer(headers, fresh), body, tag, bodyPreview)
    }

    private suspend fun attempt(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        tag: String,
        bodyPreview: String?,
    ): Response? = withContext(Dispatchers.IO) {
        val id = counter.incrementAndGet()
        val label = if (tag.isBlank()) "#$id" else "#$id/$tag"
        logRequest(label, method, url, headers, bodyPreview, if (bodyPreview == null) body?.size ?: 0 else 0)

        var attempt = 1
        while (true) {
            val started = System.currentTimeMillis()
            val outcome = runCatching {
                baseClient.newCall(buildRequest(method, url, headers, body)).execute().use { raw ->
                    val text = raw.body?.string().orEmpty()
                    val type = raw.header("Content-Type").orEmpty()
                    val millis = System.currentTimeMillis() - started
                    logResponse(label, raw.code, millis, text, type)
                    Response(code = raw.code, body = text, contentType = type, millis = millis, url = url)
                }
            }
            outcome.onFailure {
                val millis = System.currentTimeMillis() - started
                log.e("$label ✗ ${it.javaClass.simpleName}: ${it.message} after ${millis}ms ${short(url)}")
            }

            val failure = outcome.exceptionOrNull()
            val neverLeftTheDevice = failure is java.net.UnknownHostException ||
                failure is java.net.ConnectException
            if (failure == null || !neverLeftTheDevice || attempt >= NETWORK_ATTEMPTS) {
                return@withContext outcome.getOrNull()
            }
            val wait = attempt * NETWORK_BACKOFF_MS
            log.w("$label ${failure.javaClass.simpleName} on attempt $attempt/$NETWORK_ATTEMPTS, retrying in ${wait}ms"
            )
            delay(wait)
            attempt++
        }
        null
    }

    suspend fun blob(
        url: String,
        headers: Map<String, String> = emptyMap(),
        tag: String = "",
    ): Blob? = withContext(Dispatchers.IO) {
        val id = counter.incrementAndGet()
        val label = if (tag.isBlank()) "#$id" else "#$id/$tag"
        log.i("$label → GET ${short(url)} ${headers.size} headers")
        val started = System.currentTimeMillis()
        val outcome = runCatching {
            longClient.newCall(buildRequest("GET", url, headers, null)).execute().use { raw ->
                val bytes = raw.body?.bytes() ?: ByteArray(0)
                val type = raw.header("Content-Type").orEmpty()
                val fromServer = received(raw.headers)
                val millis = System.currentTimeMillis() - started
                val mark = if (raw.code in 200..299) "←" else "✗"
                log.i("$label $mark ${raw.code} ${millis}ms ${bytes.size}B ${type.ifBlank { "?" }}")
                Blob(raw.code, bytes, type, millis, fromServer)
            }
        }
        outcome.onFailure {
            log.e("$label ✗ ${it.javaClass.simpleName}: ${it.message} ${short(url)}")
        }
        outcome.getOrNull()
    }

    suspend fun fetch(
        url: String,
        start: Int,
        end: Int,
        headers: Map<String, String> = emptyMap(),
        tag: String = "cdn",
    ): ByteArray? = blob(url, headers + mapOf("Range" to "bytes=$start-$end"), tag)
        ?.takeIf { it.ok }
        ?.bytes

    suspend fun postBlob(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
        tag: String = "",
    ): Blob? {
        val first = attemptPostBlob(url, body, contentType, headers, tag)
        if (first == null || first.code != 401) return first
        val fresh = refreshed(bearerOf(headers), "POST ${short(url)}") ?: return first
        log.w("${tag.ifBlank { "postBlob" }} retrying with a fresh token after a 401")
        return attemptPostBlob(url, body, contentType, withBearer(headers, fresh), tag)
    }

    private suspend fun attemptPostBlob(
        url: String,
        body: ByteArray,
        contentType: String,
        headers: Map<String, String>,
        tag: String,
    ): Blob? = withContext(Dispatchers.IO) {
        val id = counter.incrementAndGet()
        val label = if (tag.isBlank()) "#$id" else "#$id/$tag"
        log.i("$label → POST ${short(url)} body(${body.size}B binary)")
        if (enabled) headers.forEach { (k, v) -> log.i("$label   $k: ${redact(k, v)}") }
        val started = System.currentTimeMillis()
        val outcome = runCatching {
            longClient.newCall(
                buildRequest("POST", url, headers + mapOf("Content-Type" to contentType), body)
            ).execute().use { raw ->
                val bytes = raw.body?.bytes() ?: ByteArray(0)
                val type = raw.header("Content-Type").orEmpty()
                val fromServer = received(raw.headers)
                val millis = System.currentTimeMillis() - started
                val mark = if (raw.code in 200..299) "←" else "✗"
                log.i("$label $mark ${raw.code} ${millis}ms ${bytes.size}B ${type.ifBlank { "?" }}")
                Blob(raw.code, bytes, type, millis, fromServer)
            }
        }
        outcome.onFailure {
            log.e("$label ✗ ${it.javaClass.simpleName}: ${it.message} ${short(url)}")
        }
        outcome.getOrNull()
    }

    private fun received(headers: okhttp3.Headers): Map<String, String> {
        val out = LinkedHashMap<String, String>(headers.size)
        for (i in 0 until headers.size) {
            out[headers.name(i).lowercase()] = headers.value(i)
        }
        return out
    }

    private fun logRequest(
        label: String,
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        binaryBytes: Int = 0,
    ) {
        if (!enabled) return
        log.i("$label → $method ${short(url)}")
        headers.forEach { (k, v) -> log.i("$label   $k: ${redact(k, v)}") }
        if (binaryBytes > 0) {
            log.i("$label   body($binaryBytes B binary)")
        } else if (!body.isNullOrEmpty()) {
            log.i("$label   body(${body.length}B) ${preview(body)}")
        }
    }

    private fun logResponse(label: String, code: Int, millis: Long, body: String, type: String) {
        if (!enabled) return
        val mark = if (code in 200..299) "←" else "✗"
        log.i("$label $mark $code ${millis}ms ${body.length}B ${type.ifBlank { "?" }}")
        if (body.isNotBlank()) log.i("$label   ${preview(body)}")
    }

    private fun short(url: String): String =
        if (url.length <= 180) url else url.take(180) + "…(${url.length})"

    private fun preview(body: String): String {
        val flat = body.replace(Regex("\\s+"), " ").trim()
        val safe = tokenValue.replace(flat) { match ->
            "\"${match.groupValues[1]}\":\"…${match.groupValues[2].length}B\""
        }
        return if (safe.length <= 700) safe else safe.take(700) + "…+${safe.length - 700}"
    }

    private val tokenValue = Regex(
        "\"(accessToken|access_token|refreshToken|refresh_token|clientToken|client_token|id_token|token)\"\\s*:\\s*\"([^\"]*)\""
    )

    private fun redact(name: String, value: String): String {
        val lower = name.lowercase()
        val sensitive = lower == "authorization" ||
            lower == "cookie" ||
            lower == "client-token" ||
            lower == "set-cookie"
        if (!sensitive) return value
        val scheme = value.substringBefore(' ', "")
        val head = if (scheme == "Bearer" || scheme == "Basic") "$scheme " else ""
        return "$head${value.takeLast(6)} (${value.length}B)"
    }
}
