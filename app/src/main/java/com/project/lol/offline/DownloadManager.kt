package com.project.lol.offline

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.project.lol.innertube.YouTube
import com.project.lol.innertube.models.SongItem
import com.project.lol.yt.AudioQuality
import com.project.lol.yt.CandidateScorer
import com.project.lol.yt.CandidateScorer.isAcceptableMatch
import com.project.lol.yt.YTPlayerUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class DownloadedTrack(
    val success: Boolean,
    val title: String,
    val artist: String,
    val album: String,
)

private data class ResolvedStream(
    val url: String,
    val chosen: SongItem,
)

object DownloadManager {
    private const val TAG = "Spl-DL"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var onStatus: ((String) -> Unit)? = null

    @Volatile
    var onProgress: ((Int, String) -> Unit)? = null

    @Volatile
    private var activeTrackId: String? = null

    fun isDownloading(): Boolean = activeTrackId != null

    fun currentTrackId(): String? = activeTrackId

    fun downloadCurrentTrack(
        context: Context,
        payload: String,
    ) {
        val appContext = context.applicationContext
        val parsed = runCatching { JSONObject(payload) }.getOrNull() ?: run {
            onStatus?.invoke("Invalid download request")
            return
        }
        val trackId = parsed.optString("trackId").trim()
        if (trackId.isBlank()) {
            Log.e(TAG, "downloadCurrentTrack: empty trackId in payload")
            onStatus?.invoke("Could not identify the current track")
            return
        }
        if (activeTrackId != null) {
            Log.w(TAG, "downloadCurrentTrack: download already in progress ($activeTrackId), rejecting $trackId")
            onStatus?.invoke("Please wait — a download is already in progress")
            return
        }
        activeTrackId = trackId

        val title = parsed.optString("title")
        val artist = parsed.optString("artist")
        val album = parsed.optString("album")
        val cover = parsed.optString("cover")
        Log.d(TAG, "downloadCurrentTrack: start id=$trackId title=$title artist=$artist")
        onProgress?.invoke(0, "Resolving audio...")

        scope.launch {
            val result = runCatching { downloadToFile(appContext, trackId, title, artist, album) }
                .onFailure { Log.e(TAG, "downloadCurrentTrack: exception: ${it.message}", it) }
                .getOrDefault(DownloadedTrack(false, title, artist, album))
            activeTrackId = null
            Log.i(TAG, "downloadCurrentTrack: finished id=$trackId success=${result.success} error=${lastDownloadError}")
            if (result.success) {
                OfflineStore.saveMetadata(
                    appContext,
                    trackId,
                    result.title,
                    result.artist,
                    result.album,
                    cover.ifBlank { null }
                )
                onProgress?.invoke(100, "Saved to Music/Spotilol")
            } else {
                onProgress?.invoke(-1, "Download failed: ${lastDownloadError ?: "unknown error"}")
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onStatus?.invoke(
                    if (result.success) "Saved to Music/Spotilol"
                    else "Download failed: ${lastDownloadError ?: "unknown error"}"
                )
            }
        }
    }

    private suspend fun downloadToFile(
        context: Context,
        trackId: String,
        title: String,
        artist: String,
        album: String,
    ): DownloadedTrack {
        val resolved = resolveStream(context, trackId, title, artist, album) ?: run {
            Log.w(TAG, "downloadToFile: no stream source for $trackId")
            lastDownloadError = "Download source not available yet"
            return DownloadedTrack(false, title, artist, album)
        }

        val effectiveTitle = title.ifBlank { resolved.chosen.title }
        val effectiveArtist = artist.ifBlank {
            resolved.chosen.artists.firstOrNull()?.name.orEmpty()
        }
        val effectiveAlbum = album.ifBlank { resolved.chosen.album?.name.orEmpty() }

        val dir = java.io.File(context.filesDir, "downloads").apply { mkdirs() }
        val tmpFile = java.io.File(dir, "$trackId.part")
        onProgress?.invoke(1, "Downloading audio...")
        val downloaded = httpDownloadRanged(resolved.url, tmpFile) { pct ->
            onProgress?.invoke(pct, "Downloading audio...")
        }
        if (!downloaded) {
            Log.e(TAG, "downloadToFile: audio download failed: ${lastDownloadError}")
            runCatching { tmpFile.delete() }
            return DownloadedTrack(false, effectiveTitle, effectiveArtist, effectiveAlbum)
        }
        Log.d(TAG, "downloadToFile: audio downloaded size=${tmpFile.length()}")
        onProgress?.invoke(99, "Saving...")

        val uri = saveToPublicMusic(context, trackId, effectiveTitle, effectiveArtist, tmpFile, "m4a", "audio/mp4")
        tmpFile.delete()
        if (uri != null) {
            Log.d(TAG, "downloadToFile: saved to Music/Spotilol uri=$uri")
            return DownloadedTrack(true, effectiveTitle, effectiveArtist, effectiveAlbum)
        }
        Log.w(TAG, "downloadToFile: MediaStore save failed")
        lastDownloadError = "Couldn't save file"
        return DownloadedTrack(false, effectiveTitle, effectiveArtist, effectiveAlbum)
    }

    private suspend fun resolveStream(
        context: Context,
        trackId: String,
        title: String,
        artist: String,
        album: String,
    ): ResolvedStream? {
        val searchText = buildString {
            append(title)
            if (artist.isNotBlank()) append(" $artist")
        }
        Log.d(TAG, "resolveStream: id=$trackId searching '$searchText'")

        val searchResult = runCatching {
            YouTube.search(searchText, YouTube.SearchFilter.FILTER_SONG).getOrNull()
        }.onFailure { Log.e(TAG, "resolveStream: search failed: ${it.message}", it) }
            .getOrNull()
        if (searchResult == null || searchResult.items.isEmpty()) {
            Log.w(TAG, "resolveStream: no results for '$searchText'")
            return null
        }

        val songItems = searchResult.items.filterIsInstance<SongItem>()
        if (songItems.isEmpty()) {
            Log.w(TAG, "resolveStream: no song items for '$searchText'")
            return null
        }

        val metadata = CandidateScorer.TrackMatchMetadata(
            title = title,
            artist = artist,
            album = album,
        )
        val scored = songItems.mapNotNull { song ->
            CandidateScorer.ytmusicTransferScore(song, metadata, expectedDurationMs = 0)
                .takeIf { it.isAcceptableMatch() }
        }.sortedByDescending { it.score }

        val chosen = scored.firstOrNull()?.item ?: run {
            Log.w(TAG, "resolveStream: no acceptable match for '$searchText'")
            return null
        }
        Log.d(TAG, "resolveStream: chosen '${chosen.title}' by ${chosen.artists.joinToString { it.name }} (videoId=${chosen.id})")

        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val playback = runCatching {
            YTPlayerUtils.playerResponseForPlayback(
                videoId = chosen.id,
                playlistId = null,
                audioQuality = AudioQuality.HIGH,
                connectivityManager = connectivityManager,
                skipValidation = true,
            ).getOrNull()
        }.onFailure { Log.e(TAG, "resolveStream: playback resolve failed: ${it.message}", it) }
            .getOrNull()

        val streamUrl = playback?.streamUrl
        if (streamUrl != null) {
            Log.d(TAG, "resolveStream: resolved url=${streamUrl.take(80)}")
        } else {
            Log.w(TAG, "resolveStream: no stream url for ${chosen.id}")
        }
        return streamUrl?.let { ResolvedStream(it, chosen) }
    }

    @Volatile
    private var lastDownloadError: String? = null

    private fun httpDownloadRanged(url: String, tmpFile: java.io.File, onProgress: ((Int) -> Unit)? = null): Boolean {
        val chunk = 8L * 1024 * 1024
        var total = -1L
        var position = 0L
        return try {
            java.io.BufferedOutputStream(tmpFile.outputStream()).use { output ->
                outer@ while (true) {
                    val end = if (total > 0) minOf(position + chunk - 1, total - 1) else position + chunk - 1
                    var attempt = 0
                    var fullBody = false
                    while (true) {
                        attempt++
                        val conn = openDownloadConn(url)
                        conn.setRequestProperty("Range", "bytes=$position-$end")
                        try {
                            val code = conn.responseCode
                            if (code !in 200..299) {
                                Log.e(TAG, "httpDownloadRanged: HTTP $code at $position (attempt $attempt)")
                                lastDownloadError = "Stream returned HTTP $code"
                                return false
                            }
                            if (total < 0) {
                                total = conn.getHeaderField("Content-Range")
                                    ?.substringAfter('/')?.toLongOrNull()
                                    ?: conn.contentLengthLong
                                Log.d(TAG, "httpDownloadRanged: total=$total bytes")
                            }
                            fullBody = code == 200
                            conn.inputStream.use { input ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val r = input.read(buf)
                                    if (r < 0) break
                                    output.write(buf, 0, r)
                                    position += r
                                    if (total > 0) {
                                        val pct = ((position * 100) / total).toInt().coerceIn(0, 100)
                                        onProgress?.invoke(pct)
                                    }
                                }
                            }
                            if (total > 0) Log.d(TAG, "httpDownloadRanged: chunk done $position/$total")
                            break
                        } catch (e: Exception) {
                            Log.w(TAG, "httpDownloadRanged: chunk @$position failed attempt $attempt: ${e.message}")
                            if (attempt >= 4) {
                                lastDownloadError = e.message ?: "Connection reset"
                                return false
                            }
                        } finally {
                            conn.disconnect()
                        }
                    }
                    if (fullBody) { total = position; break@outer }
                    if (total in 1..position) break@outer
                    if (total < 0) break@outer
                }
            }
            val ok = total <= 0 || position >= total
            Log.d(TAG, "httpDownloadRanged: done ok=$ok position=$position total=$total")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "httpDownloadRanged: exception: ${e.message}", e)
            lastDownloadError = e.message ?: "Download error"
            false
        }
    }

    private fun openDownloadConn(url: String): java.net.HttpURLConnection =
        (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            )
        }

    private fun saveToPublicMusic(
        context: Context,
        trackId: String,
        title: String,
        artist: String,
        tmpFile: java.io.File,
        ext: String,
        mime: String,
    ): String? {
        val folderName = "Spotilol"
        val fileName = "$artist - $title [$trackId]"
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .let { if (it.length > 200) it.take(200) else it }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val displayName = "$fileName.$ext"
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$folderName")
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
            ) ?: run {
                Log.w(TAG, "saveToPublicMusic: MediaStore insert returned null")
                return null
            }
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    tmpFile.inputStream().use { it.copyTo(out) }
                } ?: run {
                    Log.w(TAG, "saveToPublicMusic: openOutputStream returned null")
                    context.contentResolver.delete(uri, null, null)
                    return null
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "saveToPublicMusic: MediaStore write failed: ${e.message}")
                runCatching { context.contentResolver.delete(uri, null, null) }
                return null
            }
            return uri.toString()
        } else {
            val dir = java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                folderName,
            ).apply { mkdirs() }
            val outFile = java.io.File(dir, "$fileName.$ext")
            if (!tmpFile.renameTo(outFile)) {
                Log.w(TAG, "saveToPublicMusic: rename to public dir failed (API < 29)")
                return null
            }
            return outFile.absolutePath
        }
    }
}