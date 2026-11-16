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

package com.mwask.bat.backend.connect.dealer

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import android.os.SystemClock
import com.mwask.bat.backend.core.log.Logs

private val log = Logs.of(Logs.Module.DEALER)

private const val PING_MS = 30_000L

/** No server frame for this long means the socket is half dead: force a reconnect. */
private const val SILENCE_MS = 3 * PING_MS

class Dealer {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(0, TimeUnit.SECONDS)
        .build()

    fun open(host: String, token: String): Flow<DealerFrame> = callbackFlow {
        val request = Request.Builder()
            .url("wss://$host/?access_token=$token")
            .build()

        val listener = object : WebSocketListener() {

            /** Elapsed-realtime clock of the last received frame, pings included. */
            @Volatile
            var lastReceiveAt = SystemClock.elapsedRealtime()

            override fun onOpen(webSocket: WebSocket, response: Response) {
                log.i("open ${response.code} $host")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                lastReceiveAt = SystemClock.elapsedRealtime()
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (json.optString("type")) {
                    "ping" -> {
                        webSocket.send("""{"type":"pong"}""")
                        return
                    }
                    "pong" -> return
                }
                val ident = json.optString("message_ident")
                if (ident.isNotBlank()) {
                    val key = json.optString("key")
                    val compressed = json.optJSONObject("payload")?.optString("compressed").orEmpty()
                    val gzip = json.optJSONObject("headers")
                        ?.optString("Transfer-Encoding").equals("gzip", true)
                    val decoded = runCatching {
                        val raw = java.util.Base64.getDecoder().decode(compressed)
                        val bytes = if (gzip) java.util.zip.GZIPInputStream(raw.inputStream()).readBytes() else raw
                        String(bytes, Charsets.UTF_8)
                    }.getOrNull()
                    if (key.isNotBlank()) {
                        webSocket.send("""{"type":"reply","key":"$key","payload":{"success":true}}""")
                    }
                    if (decoded != null) {
                        trySend(DealerFrame(uri = ident, headers = emptyMap(), payloads = listOf(decoded)))
                    } else {
                        log.w("request $ident did not decode, ${compressed.length}B")
                    }
                    return
                }
                val uri = json.optString("uri")
                if (uri.isBlank()) return
                val headers = json.optJSONObject("headers")?.let { node ->
                    node.keys().asSequence().associateWith { node.optString(it) }
                }.orEmpty()
                val gzip = headers.entries.any { it.key.equals("Transfer-Encoding", true) &&
                        it.value.equals("gzip", true) }
                val payloads = json.optJSONArray("payloads")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        when (val item = array.opt(index)) {
                            null -> null
                            is String -> decodePayload(item, gzip)
                            else -> item.toString()
                        }
                    }
                }.orEmpty()
                trySend(DealerFrame(uri = uri, headers = headers, payloads = payloads))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log.w("failure ${t.javaClass.simpleName}: ${t.message} code=${response?.code ?: 0}")
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                log.i("closed $code $reason")
                close()
            }
        }

        val socket = client.newWebSocket(request, listener)

        val pinger = launch {
            while (true) {
                delay(PING_MS)
                if (!socket.send("""{"type":"ping"}""")) return@launch
                if (SystemClock.elapsedRealtime() - listener.lastReceiveAt > SILENCE_MS) {
                    log.w("dealer silent for ${SILENCE_MS / 1000}s, forcing a reconnect")
                    socket.cancel()
                    return@launch
                }
            }
        }

        awaitClose {
            pinger.cancel()
            socket.close(1000, null)
        }
    }

    private fun decodeGzipBase64(payload: String): String? = runCatching {
        val raw = java.util.Base64.getDecoder().decode(payload)
        String(java.util.zip.GZIPInputStream(raw.inputStream()).readBytes(), Charsets.UTF_8)
    }.getOrNull()

    private fun decodeBase64Utf8(payload: String): String? = runCatching {
        String(java.util.Base64.getDecoder().decode(payload), Charsets.UTF_8)
    }.getOrNull()

    private fun decodePayload(item: String, gzip: Boolean): String {
        if (item.trimStart().startsWith("{")) return item
        if (gzip) return decodeGzipBase64(item) ?: item
        return decodeGzipBase64(item) ?: decodeBase64Utf8(item) ?: item
    }
}
