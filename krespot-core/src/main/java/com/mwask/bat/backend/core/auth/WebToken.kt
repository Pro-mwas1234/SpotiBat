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

package com.mwask.bat.backend.core.auth

import android.content.Context
import com.mwask.bat.backend.core.webview.cookieManagerOrNull
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.net.Net
import org.json.JSONObject

private val log = Logs.of(Logs.Module.AUTH)

object WebToken {

    private const val ENDPOINT = "https://open.spotify.com/api/token"
    private const val SERVER_TIME = "https://open.spotify.com/api/server-time"
    private const val ORIGIN = "https://open.spotify.com"

    private const val SECRET = ",7/*F(\"rLJ2oxaKL^f+E1xvP@N"
    private const val VERSION = 61
    private const val PERIOD_SECONDS = 30L
    private const val MODULUS = 1_000_000

    suspend fun mint(context: Context, spDc: String = ""): String? {
        val cookie = cookieHeader(context, spDc.ifBlank { EncoreAuth.spDc(context) })
        if (cookie.isBlank()) {
            log.w("api/token skipped, there is no sp_dc to mint with")
            return null
        }
        val server = serverSeconds(cookie)
        val totp = code(System.currentTimeMillis())
        val totpServer = if (server > 0L) code(server * 1000L) else "unavailable"
        val url = "$ENDPOINT?reason=init&productType=web-player" +
                "&totp=$totp&totpServer=$totpServer&totpVer=$VERSION"
        val response = Net.get(url, headers(cookie), tag = "api-token")
        val json = response?.json()
        if (json == null) {
            log.w("api/token ${response?.code ?: "no response"} not-json")
            return null
        }
        if (json.optBoolean("isAnonymous", false)) {
            log.w("api/token ${response.code} came back anonymous")
            return null
        }
        val token = json.optString("accessToken")
        if (token.isBlank()) {
            log.w("api/token ${response.code} no accessToken ${response.body.take(160)}")
            return null
        }
        val expires = json.optLong("accessTokenExpirationTimestampMs", 0L)
        val previous = EncoreAuth.token(context)
        EncoreAuth.saveToken(context, token, expires)
        val ttl = if (expires > 0L) "${(expires - System.currentTimeMillis()) / 1000L}s" else "?"
        log.i("api/token ${response.code} ${token.length}B client=${json.optString("clientId").take(8)}… " +
                "ttl=$ttl totp=$totp changed=${previous != token}"
        )
        return token
    }

    fun code(millis: Long): String {
        val counter = millis / 1000L / PERIOD_SECONDS
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key(), "HmacSHA1"))
        val message = ByteArray(8)
        var value = counter
        for (index in 7 downTo 0) {
            message[index] = (value and 0xff).toByte()
            value = value shr 8
        }
        val digest = mac.doFinal(message)
        val offset = digest[19].toInt() and 0x0f
        val binary = ((digest[offset].toInt() and 0x7f) shl 24) or
                ((digest[offset + 1].toInt() and 0xff) shl 16) or
                ((digest[offset + 2].toInt() and 0xff) shl 8) or
                (digest[offset + 3].toInt() and 0xff)
        return (binary % MODULUS).toString().padStart(6, '0')
    }

    private fun key(): ByteArray {
        val joined = SECRET.mapIndexed { index, char ->
            (char.code xor (index % 33 + 9)).toString()
        }.joinToString("")
        return joined.toByteArray(Charsets.UTF_8)
    }

    private fun cookieHeader(context: Context, spDc: String): String {
        if (spDc.isNotBlank()) return "sp_dc=$spDc"
        return cookieManagerOrNull()?.getCookie(ORIGIN).orEmpty()
    }

    private suspend fun serverSeconds(cookie: String): Long {
        val response = Net.get(
            SERVER_TIME,
            mapOf(
                "Cookie" to cookie,
                "User-Agent" to EncoreAuth.DESKTOP_UA,
                "Accept" to "application/json, text/plain, */*",
                "Referer" to "$ORIGIN/",
                "Origin" to ORIGIN,
            ),
            tag = "server-time",
        )
        val json: JSONObject = response?.takeIf { it.ok }?.json() ?: return 0L
        return json.optLong("serverTime", 0L)
    }

    private fun headers(cookie: String): Map<String, String> = mapOf(
        "Cookie" to cookie,
        "User-Agent" to EncoreAuth.DESKTOP_UA,
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "en-US,en;q=0.9",
        "Referer" to "$ORIGIN/",
        "Origin" to ORIGIN,
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "same-origin",
        "app-platform" to "WebPlayer",
    )
}
