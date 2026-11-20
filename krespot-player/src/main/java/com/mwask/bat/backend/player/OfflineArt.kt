package com.mwask.bat.backend.player

import android.content.Context
import com.mwask.bat.backend.core.log.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private val log = Logs.of(Logs.Module.PLAYER)

private val artClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}

/** The cover kept beside the songs, named after the track's uri. */
private fun artFile(context: Context, uri: String): File =
    File(OfflineStorage.artDir(context), "${uri.replace(Regex("[^A-Za-z0-9._-]"), "_")}.jpg")

/**
 * Spotify does not always send a cover for a track, and the YouTube resolver only answers with
 * the audio, so a downloaded song could be stored with a blank imageUrl and show as an empty
 * tile in Downloads. SpotiBat old took the artwork of the video it had already resolved the
 * audio from and, failing that, asked Spotify's unauthenticated oEmbed endpoint for the cover of
 * the bare track id. The bytes are kept on disk so the row still draws its art with no network.
 *
 * Returns the local file, or null when neither source produced a picture.
 */
internal suspend fun cacheArtwork(
    context: Context,
    uri: String,
    thumbnailUrl: String?,
): String? = withContext(Dispatchers.IO) {
    val file = artFile(context, uri)
    if (file.isFile && file.length() > 0L) return@withContext file.absolutePath

    val url = thumbnailUrl?.takeIf { it.isNotBlank() } ?: spotifyCoverUrl(uri)
    if (url.isNullOrBlank()) return@withContext null

    val bytes = runCatching {
        artClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body?.bytes() else null
        }
    }.getOrNull()
    if (bytes == null || bytes.isEmpty()) {
        log.w("offline art: no bytes for '${uri.takeLast(12)}'")
        return@withContext null
    }

    val saved = runCatching {
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }.isSuccess
    if (!saved) {
        log.w("offline art: could not save cover for '${uri.takeLast(12)}'")
        return@withContext null
    }
    log.i("offline art saved '${uri.takeLast(12)}' ${bytes.size}B")
    file.absolutePath
}

/** Spotify's public oEmbed endpoint answers with the album cover for a bare track id. */
private fun spotifyCoverUrl(uri: String): String? {
    val id = uri.substringAfterLast(':').takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        val request = Request.Builder()
            .url("https://open.spotify.com/oembed?url=spotify:track:$id")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()
        artClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return@use null
            JSONObject(body).optString("thumbnail_url").takeIf { it.isNotBlank() }
        }
    }.getOrNull()
}
