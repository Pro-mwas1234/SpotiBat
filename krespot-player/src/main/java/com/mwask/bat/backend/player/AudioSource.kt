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

package com.mwask.bat.backend.player

import android.content.Context
import com.mwask.bat.backend.core.store.SecureStore
import com.mwask.bat.backend.connect.command.AudioFile
import com.mwask.bat.backend.core.auth.EncoreAuth
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.net.ClientToken
import com.mwask.bat.backend.core.net.Net
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

private val log = Logs.of(Logs.Module.SOURCE)

private const val RESOLVE_HOST = "guc3-spclient.spotify.com"

private const val RESOLVE_PREFIX = "/storage-resolve/v2/files/audio/interactive/{format}/"
private const val RESOLVE_QUERY = "?version=10000000&product=9&platform=39&alt=json"
private const val DEFAULT_FORMAT = 10
private const val FREE_BITRATE = 128_000

private val MP4_FORMATS = listOf(10, 11, 12, 13)

private val blockedFormats = mutableSetOf<Int>()
private const val SEEKTABLE_HOST = "https://seektables.spotifycdn.com/v1/seektable/"
private const val LICENSE_PATH = "widevine-license/v1/audio/license"
private const val LICENSE_QUERY =
    "keysystem=com.widevine.alpha&mediatype=audio&sdk_name=harmony&sdk_version=4.81.1"
private const val APP_VERSION = "1.3.8.509.g26b6eac7300d"
private const val ENDPOINT_FALLBACK_SECONDS = 60L

private const val KEY_HOST = "license_endpoint_host"

private const val KEY_URL = "license_endpoint_url"

private const val KEY_EXP = "license_endpoint_exp"


internal object PlayerHeaders {

    private const val WEB_ORIGIN = "https://open.spotify.com"

    private const val WEB_LANGUAGES = "en-US,en;q=0.9,fr-FR;q=0.8,fr;q=0.7"

    suspend fun of(context: Context, force: Boolean = false): Map<String, String> = mapOf(
        "Authorization" to "Bearer ${EncoreAuth.token(context)}",
        "client-token" to ClientToken.get(context, force),
        "app-platform" to "WebPlayer",
        "spotify-app-version" to APP_VERSION,
        "User-Agent" to Net.DESKTOP_UA,
        "Accept" to "*/*",
        "Accept-Language" to "en-US,en;q=0.9",
    )

    suspend fun license(context: Context, force: Boolean = false): Map<String, String> = mapOf(
        "Accept" to "*/*",
        "Accept-Language" to "en-US,en;q=0.9,id;q=0.8",
        "Authorization" to "Bearer ${EncoreAuth.token(context)}",
        "client-token" to ClientToken.get(context, force),
        "Cache-Control" to "no-cache",
        "Pragma" to "no-cache",
        "Priority" to "u=1, i",
        "Origin" to WEB_ORIGIN,
        "Referer" to "$WEB_ORIGIN/",
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "same-site",
        "User-Agent" to Net.DESKTOP_UA
    )
}

data class Seektable(
    val offset: Int,
    val sizes: List<Int>,
    val durations: List<Long>,
    val timescale: Int,
    val pssh: String,
) {
    val totalDuration: Long get() = durations.sum()

    val count: Int get() = sizes.size

    fun startOf(index: Int): Long {
        var sum = 0L
        for (i in 0 until index) sum += durations[i]
        return sum
    }

    fun startSeconds(index: Int): Double = startOf(index).toDouble() / timescale

    fun offsetOf(index: Int): Int {
        var sum = offset
        for (i in 0 until index) sum += sizes[i]
        return sum
    }
}

data class AudioSource(
    val fileId: String,
    val format: Int,
    val bitrate: Int,
    val quality: String,
    val url: String,
    val seektable: Seektable,
)

internal object SourceResolver {

    suspend fun manifest(context: Context, spclient: String, uri: String): List<AudioFile> =
        mediaManifest(context, spclient, uri)

    fun blockFormat(format: Int) {
        if (blockedFormats.add(format)) log.w("format $format refused by the licence, skipping it")
    }

    suspend fun resolve(
        context: Context,
        spclient: String,
        uri: String,
        fileId: String = "",
        format: Int = 0,
        prefetched: List<AudioFile>? = null,
    ): AudioSource? {
        val candidates = when {
            !prefetched.isNullOrEmpty() -> prefetched
            fileId.isNotBlank() -> listOf(AudioFile(fileId, format.takeIf { it > 0 } ?: DEFAULT_FORMAT, 0, "", 0.0))
            else -> mediaManifest(context, spclient, uri)
        }.filter { it.format !in blockedFormats }
        if (candidates.isEmpty()) {
            log.w("no candidate left for '${uri.takeLast(12)}'")
            return null
        }
        for (file in candidates) {
            val (table, url) = coroutineScope {
                val table = async { seektable(file.fileId) }
                val url = async { cdnUrl(context, file.fileId, file.format) }
                table.await() to url.await()
            }
            if (table == null || url == null) {
                log.w("source ${file.fileId.take(12)} fmt=${file.format} unusable, trying the next one")
                continue
            }
            log.i("source ${file.fileId} fmt=${file.format} segs=${table.count} " +
                    "len=${"%.3f".format(table.totalDuration.toDouble() / table.timescale)}s " +
                    "offset=${table.offset} pssh=${table.pssh.length}B"
            )
            return AudioSource(file.fileId, file.format, file.bitrate, file.quality, url, table)
        }
        return null
    }

    private suspend fun mediaManifest(context: Context, spclient: String, uri: String): List<AudioFile> {
        if (uri.isBlank()) {
            log.w("media: empty uri and no file id")
            return emptyList()
        }
        val host = spclient.ifBlank { RESOLVE_HOST }
        val url = "https://$host/track-playback/v1/media/$uri?manifestFileFormat=file_ids_mp4&manifest"
        val response = Net.get(url, PlayerHeaders.of(context), tag = "media")
        val json = response?.takeIf { it.ok }?.json()
        if (json == null) {
            log.w("media failed ${response?.code ?: "no response"} ${response?.body?.take(160).orEmpty()}")
            return emptyList()
        }
        val media = json.optJSONObject("media") ?: return emptyList()
        val entry = media.optJSONObject(uri)
            ?: media.keys().asSequence().mapNotNull { media.optJSONObject(it) }.firstOrNull()
            ?: return emptyList()
        val files = entry.optJSONObject("item")
            ?.optJSONObject("manifest")
            ?.optJSONArray("file_ids_mp4")
            ?: return emptyList()
        val parsed = (0 until files.length()).mapNotNull { index ->
            val node = files.optJSONObject(index) ?: return@mapNotNull null
            val id = node.text("file_id")
            if (id.isBlank()) return@mapNotNull null
            AudioFile(
                fileId = id,
                format = node.text("format").toIntOrNull() ?: node.optInt("format", DEFAULT_FORMAT),
                bitrate = node.optInt("bitrate", 0),
                quality = node.text("audio_quality"),
                gainDb = node.optDouble("gain_db", 0.0),
            )
        }
        val ordered = preferBitrate(context, parsed.filter { it.format in MP4_FORMATS })
        log.i("media $uri files=${parsed.size} " +
                parsed.joinToString(" ") { "f${it.format}/${it.bitrate}" } +
                " -> ${ordered.joinToString(",") { it.format.toString() }}"
        )
        return ordered
    }

    private fun preferBitrate(context: Context, files: List<AudioFile>): List<AudioFile> {
        val premium = EncoreAuth.account(context)?.product.orEmpty().trim().uppercase() == "PREMIUM"
        return if (premium) files.sortedByDescending { it.bitrate }
        else files.sortedBy { kotlin.math.abs(it.bitrate - FREE_BITRATE) }
    }

    private suspend fun seektable(fileId: String): Seektable? {
        val response = Net.get("$SEEKTABLE_HOST$fileId", tag = "seektable")
        val json = response?.takeIf { it.ok }?.json()
        if (json == null) {
            log.w("seektable failed ${response?.code ?: "no response"}")
            return null
        }
        val segments = json.optJSONArray("segments") ?: return null
        val usable = (0 until segments.length())
            .mapNotNull { segments.optJSONObject(it) }
            .filter { it.optInt("size", 0) > 0 }
        val sizes = usable.map { it.optInt("size") }
        val durations = usable.map { it.optLong("duration", 0L) }
        val pssh = json.optJSONObject("pssh")?.optString("widevine").orEmpty()
        if (sizes.isEmpty()) {
            log.w("seektable incomplete segs=${sizes.size}")
            return null
        }
        return Seektable(
            offset = json.optInt("offset", 0),
            sizes = sizes,
            durations = durations,
            timescale = json.optLong("timescale", 44_100L).toInt(),
            pssh = pssh,
        )
    }

    private suspend fun cdnUrl(context: Context, fileId: String, format: Int): String? {
        val path = RESOLVE_PREFIX.replace("{format}", format.toString())
        val url = "https://$RESOLVE_HOST$path$fileId$RESOLVE_QUERY"
        val response = Net.get(url, PlayerHeaders.of(context), tag = "resolve")
        val urls = response?.takeIf { it.ok }?.json()?.optJSONArray("cdnurl")
        val first = urls?.optString(0).orEmpty()
        if (first.isBlank()) {
            log.w("resolve failed ${response?.code ?: "no response"} ${response?.body?.take(200).orEmpty()}")
            return null
        }
        return first
    }
}

internal object LicenseEndpoint {

    @Volatile
    private var cached: String = ""

    @Volatile
    private var expiresAt: Long = 0L

    suspend fun of(context: Context, spclient: String): String {
        val host = spclient.ifBlank { RESOLVE_HOST }
        val now = System.currentTimeMillis() / 1000L
        cached.takeIf { it.isNotBlank() && now < expiresAt }?.let { return it }
        stored(context, host, now)?.let { return it }
        val url = "https://$host/melody/v1/license_url?$LICENSE_QUERY"
        val response = Net.get(url, PlayerHeaders.of(context), tag = "license-url")
        val json = response?.takeIf { it.ok }?.json()
        val uri = json?.optString("uri").orEmpty().trimStart('/')
        val resolved = when {
            uri.startsWith("http") -> uri
            uri.isNotBlank() -> "https://$host/$uri"
            else -> "https://$host/${LICENSE_PATH.trimStart('/')}"
        }
        val expires = json?.optLong("expires", 0L) ?: 0L
        expiresAt = if (expires > now) expires else now + ENDPOINT_FALLBACK_SECONDS
        log.i("license endpoint ${response?.code ?: "-"} -> $resolved for ${expiresAt - now}s")
        cached = resolved
        if (json != null) persist(context, host, resolved, expiresAt)
        return resolved
    }

    fun reset() {
        cached = ""
        expiresAt = 0L
    }

    /** Drops the resolved endpoint, in memory and on disk, after a licence
     *  request failed: the next attempt resolves a fresh one. */
    fun invalidate(context: Context) {
        reset()
        runCatching {
            SecureStore.prefs(context).edit()
                .remove(KEY_URL)
                .remove(KEY_EXP)
                .apply()
        }
    }

    private fun stored(context: Context, host: String, now: Long): String? {
        val prefs = SecureStore.prefs(context)
        if (host.isNotBlank() && prefs.getString(KEY_HOST, "") != host) return null
        val url = prefs.getString(KEY_URL, "").orEmpty()
        val expires = prefs.getLong(KEY_EXP, 0L)
        if (url.isBlank() || expires <= now) return null
        cached = url
        expiresAt = expires
        log.i("license endpoint $url kept from the last session for ${expires - now}s")
        return url
    }

    private fun persist(context: Context, host: String, url: String, expires: Long) {
        SecureStore.prefs(context).edit()
            .putString(KEY_HOST, host)
            .putString(KEY_URL, url)
            .putLong(KEY_EXP, expires)
            .apply()
    }
}

internal fun JSONObject.text(name: String): String = if (isNull(name)) "" else optString(name, "")
