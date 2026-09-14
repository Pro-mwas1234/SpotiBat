package com.mwask.bat.offline

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class OfflineSong(
    val id: String,
    val title: String,
    val artist: String,
    val uri: Uri,
    val coverFile: File? = null,
    val album: String = "",
    val durationSec: Int? = null,
    val explicit: Boolean = false,
    val videoId: String? = null,
    val ytTitle: String = "",
    val ytArtist: String = "",
    val ytAlbum: String = "",
    val ytThumbnail: String? = null,
    val shareLink: String? = null,
)

object OfflineStore {
    private const val TAG = "Spl-DL"
    private const val FOLDER = "SpotiBat"

    private val TrackIdRegex = Regex("\\[([^\\]]+)\\]\\.[^.]+$")
    private val FileNameRegex = Regex("^(.*) - (.*) \\[([^\\]]+)\\]\\.[^.]+$")

    private fun isUnknown(value: String?): Boolean =
        value.isNullOrBlank() || value.equals("<unknown>", ignoreCase = true)

    private fun metaFile(context: Context) = File(context.filesDir, "offline_meta.json")

    private fun coverDir(context: Context) = File(context.filesDir, "covers").apply { mkdirs() }

    fun coverFile(context: Context, trackId: String): File? =
        File(coverDir(context), "$trackId.jpg").takeIf { it.exists() && it.length() > 0 }

    /**
     * Asks the media scanner to index the download folder. Self-heal for files
     * that exist on disk but have no MediaStore row (copied via USB, restored
     * by backup, or written by the old com.project.lol package) - loadSongs()
     * only sees files MediaStore knows about.
     */
    fun rescanFolder(context: Context) {
        runCatching {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                FOLDER,
            )
            val files = dir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in setOf("m4a", "mp3", "ogg", "flac", "wav", "opus") }
                ?.map { it.absolutePath }
                ?.toTypedArray()
            if (files.isNullOrEmpty()) return
            MediaScannerConnection.scanFile(context.applicationContext, files, null, null)
            Log.i(TAG, "rescanFolder: scanning ${files.size} file(s) in Music/$FOLDER")
        }
    }

    /**
     * One-time self-heal: re-fetches cover art for songs whose cover file is
     * missing - e.g. after the app's private storage was cleared or the app
     * reinstalled while the Music/SpotiBat files survived. Cover urls are not
     * stored on disk, so covers are resolved via Spotify's unauthenticated
     * oEmbed endpoint using the Spotify track id embedded in the file names.
     * Returns the number of covers restored; onRestored(done, total) fires
     * after each cover so the caller can refresh progressively.
     */
    fun restoreMissingCovers(
        context: Context,
        trackIds: List<String>,
        onRestored: (Int, Int) -> Unit = { _, _ -> },
    ): Int {
        val missing = trackIds.filter { coverFile(context, it) == null }.distinct()
        val total = missing.size
        if (total == 0) return 0
        var restored = 0
        for (id in missing) {
            runCatching {
                val meta = URL("https://open.spotify.com/oembed?url=spotify:track:$id")
                    .openConnection() as HttpURLConnection
                meta.connectTimeout = 10000
                meta.readTimeout = 10000
                meta.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                val thumb = runCatching {
                    JSONObject(meta.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
                        .optString("thumbnail_url")
                }.getOrNull().orEmpty()
                if (thumb.isNotBlank()) {
                    val img = URL(thumb).openConnection() as HttpURLConnection
                    img.connectTimeout = 10000
                    img.readTimeout = 10000
                    img.instanceFollowRedirects = true
                    img.inputStream.use { input ->
                        File(coverDir(context), "$id.jpg").outputStream().use { input.copyTo(it) }
                    }
                    restored++
                    onRestored(restored, total)
                }
            }.onFailure { Log.w(TAG, "restoreMissingCovers: $id failed: ${it.message}") }
            try { Thread.sleep(120) } catch (_: InterruptedException) { break }
        }
        Log.i(TAG, "restoreMissingCovers: restored $restored/$total")
        return restored
    }

    fun saveMetadata(
        context: Context,
        trackId: String,
        title: String,
        artist: String,
        album: String,
        coverUrl: String?,
        videoId: String? = null,
        ytTitle: String = "",
        ytArtist: String = "",
        ytAlbum: String = "",
        ytThumbnail: String? = null,
        durationSec: Int? = null,
        explicit: Boolean = false,
        shareLink: String? = null,
    ) {
        runCatching {
            val file = metaFile(context)
            val root = if (file.exists()) {
                runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
            } else {
                JSONObject()
            }
            root.put(
                trackId,
                JSONObject().apply {
                    put("title", title)
                    put("artist", artist)
                    put("album", album)
                    put("coverUrl", coverUrl ?: "")
                    if (videoId != null) put("videoId", videoId)
                    if (ytTitle.isNotBlank()) put("ytTitle", ytTitle)
                    if (ytArtist.isNotBlank()) put("ytArtist", ytArtist)
                    if (ytAlbum.isNotBlank()) put("ytAlbum", ytAlbum)
                    if (ytThumbnail != null) put("ytThumb", ytThumbnail)
                    if (durationSec != null) put("durationSec", durationSec)
                    if (explicit) put("explicit", true)
                    if (shareLink != null) put("shareLink", shareLink)
                }
            )
            file.writeText(root.toString())
        }.onFailure { Log.w(TAG, "saveMetadata: failed to write manifest: ${it.message}") }

        if (coverUrl.isNullOrBlank()) return
        if (coverFile(context, trackId) != null) return
        runCatching {
            val conn = URL(coverUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true
            conn.inputStream.use { input ->
                File(coverDir(context), "$trackId.jpg").outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }.onFailure { Log.w(TAG, "saveMetadata: cover fetch failed: ${it.message}") }
    }

    fun removeMetadata(context: Context, trackId: String) {
        runCatching {
            val file = metaFile(context)
            if (!file.exists()) return
            val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
            root.remove(trackId)
            file.writeText(root.toString())
        }
        runCatching { File(coverDir(context), "$trackId.jpg").delete() }
    }

    fun loadSongs(context: Context): List<OfflineSong> {
        val songs = mutableListOf<OfflineSong>()
        val manifest = runCatching {
            val file = metaFile(context)
            if (file.exists()) JSONObject(file.readText()) else JSONObject()
        }.getOrDefault(JSONObject())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.IS_PENDING,
            )
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Audio.Media.IS_PENDING}=0",
                arrayOf("%Music/$FOLDER%"),
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    runCatching {
                        if (c.getInt(4) != 0) return@runCatching
                        val displayName = c.getString(3) ?: return@runCatching
                        val nameMatch = FileNameRegex.find(displayName)
                        val rawTitle = c.getString(1) ?: ""
                        val rawArtist = c.getString(2) ?: ""
                        val title = if (isUnknown(rawTitle)) {
                            nameMatch?.groupValues?.get(2)?.trim()?.ifBlank { rawTitle } ?: rawTitle
                        } else {
                            rawTitle
                        }
                        val artist = if (isUnknown(rawArtist)) {
                            nameMatch?.groupValues?.get(1)?.trim()?.ifBlank { rawArtist } ?: rawArtist
                        } else {
                            rawArtist
                        }
                        val trackId = TrackIdRegex.find(displayName)?.groupValues?.get(1)
                            ?.takeIf { it.isNotBlank() }
                            ?: "ms${c.getLong(0)}"
                        val uri = ContentUris.withAppendedId(collection, c.getLong(0))
                        val extras = manifest.optJSONObject(trackId)
                        songs.add(
                            OfflineSong(
                                id = trackId,
                                title = title,
                                artist = artist,
                                uri = uri,
                                coverFile = coverFile(context, trackId),
                                album = extras?.optString("album", "") ?: "",
                                durationSec = extras?.optInt("durationSec", 0)?.takeIf { it > 0 },
                                explicit = extras?.optBoolean("explicit", false) ?: false,
                                videoId = extras?.optString("videoId", null)?.ifBlank { null },
                                ytTitle = extras?.optString("ytTitle", "") ?: "",
                                ytArtist = extras?.optString("ytArtist", "") ?: "",
                                ytAlbum = extras?.optString("ytAlbum", "") ?: "",
                                ytThumbnail = extras?.optString("ytThumb", null)?.ifBlank { null },
                                shareLink = extras?.optString("shareLink", null)?.ifBlank { null },
                            )
                        )
                    }
                }
            }
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                FOLDER,
            )
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.forEach { f ->
                if (!f.isFile) return@forEach
                val match = FileNameRegex.find(f.name)
                val artist = match?.groupValues?.get(1)?.trim().orEmpty()
                val title = match?.groupValues?.get(2)?.trim() ?: f.nameWithoutExtension
                val trackId = match?.groupValues?.get(3) ?: f.nameWithoutExtension
                val extras = manifest.optJSONObject(trackId)
                songs.add(
                    OfflineSong(
                        id = trackId,
                        title = title,
                        artist = artist,
                        uri = Uri.fromFile(f),
                        coverFile = coverFile(context, trackId),
                        album = extras?.optString("album", "") ?: "",
                        durationSec = extras?.optInt("durationSec", 0)?.takeIf { it > 0 },
                        explicit = extras?.optBoolean("explicit", false) ?: false,
                        videoId = extras?.optString("videoId", null)?.ifBlank { null },
                        ytTitle = extras?.optString("ytTitle", "") ?: "",
                        ytArtist = extras?.optString("ytArtist", "") ?: "",
                        ytAlbum = extras?.optString("ytAlbum", "") ?: "",
                        ytThumbnail = extras?.optString("ytThumb", null)?.ifBlank { null },
                        shareLink = extras?.optString("shareLink", null)?.ifBlank { null },
                    )
                )
            }
        }
        return songs
    }

    fun deleteSong(context: Context, song: OfflineSong): Boolean {
        val ok = runCatching {
            if (song.uri.scheme == "content") {
                context.contentResolver.delete(song.uri, null, null) > 0
            } else {
                File(song.uri.path ?: return@runCatching false).delete()
            }
        }.getOrElse {
            Log.w(TAG, "deleteSong: failed to delete ${song.uri}: ${it.message}")
            false
        }
        removeMetadata(context, song.id)
        return ok
    }

    /**
     * TRUE if a track with this Spotify ID is already saved in Music/SpotiBat.
     * Used to de-duplicate album/playlist batch downloads. MediaStore is the
     * source of truth on Q+; the folder listing on older devices.
     */
    fun isTrackSaved(context: Context, trackId: String): Boolean {
        if (trackId.isBlank()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val escaped = trackId
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            runCatching {
                context.contentResolver.query(
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                    arrayOf(MediaStore.Audio.Media._ID),
                    "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? AND " +
                            "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ? ESCAPE '\\'",
                    arrayOf("%Music/$FOLDER%", "%[$escaped]%"),
                    null,
                )?.use { it.count > 0 } ?: false
            }.getOrDefault(false)
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                FOLDER,
            )
            val marker = "[$trackId]."
            runCatching {
                dir.listFiles()?.any { it.isFile && it.name.contains(marker) } == true
            }.getOrDefault(false)
        }
    }
}
