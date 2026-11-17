package com.mwask.bat.lyrics.providers

import com.mwask.bat.lyrics.LyricsHttp
import com.mwask.bat.lyrics.usableLyrics
import org.json.JSONArray
import kotlin.math.abs

/**
 * Port of YumaPlayer's `LrcLib` client (lrclib.net/api/search).
 *
 * Yuma picks the sync/plain payload of the track whose duration is within two
 * seconds of the song; when the duration is unknown it prefers a synced entry.
 */
internal object LrcLibProvider {

    private const val BASE_URL = "https://lrclib.net/api/search"
    private const val MAX_DURATION_DELTA_SECONDS = 2

    private class Track(
        val trackName: String,
        val artistName: String,
        val duration: Int,
        val syncedLyrics: String?,
        val plainLyrics: String?,
    )

    fun fetch(title: String, artist: String, album: String?, durationSeconds: Int): String? {
        val tracks = query(title, artist, album).filter {
            it.syncedLyrics.usableLyrics() != null || it.plainLyrics.usableLyrics() != null
        }
        if (tracks.isEmpty()) return null

        val best = if (durationSeconds == -1) {
            tracks.firstOrNull { it.syncedLyrics.usableLyrics() != null } ?: tracks.first()
        } else {
            tracks.minByOrNull { abs(it.duration - durationSeconds) }
                ?.takeIf { abs(it.duration - durationSeconds) <= MAX_DURATION_DELTA_SECONDS }
        } ?: return null

        return best.syncedLyrics.usableLyrics() ?: best.plainLyrics.usableLyrics()
    }

    private fun query(title: String, artist: String, album: String?): List<Track> {
        val response = LyricsHttp.get(
            BASE_URL,
            params = buildMap {
                put("track_name", title)
                put("artist_name", artist)
                if (!album.isNullOrBlank()) put("album_name", album)
            },
        ) ?: return emptyList()
        if (!response.ok) return emptyList()

        val array = runCatching { JSONArray(response.body) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val node = array.optJSONObject(index) ?: return@mapNotNull null
            Track(
                trackName = node.optString("trackName"),
                artistName = node.optString("artistName"),
                duration = node.optDouble("duration", -1.0).takeIf { it > 0 }?.toInt() ?: -1,
                syncedLyrics = node.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" },
                plainLyrics = node.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }
}
