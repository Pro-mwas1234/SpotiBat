package com.mwask.bat.lyrics.providers

import com.mwask.bat.lyrics.LyricsHttp
import com.mwask.bat.lyrics.usableLyrics
import org.json.JSONObject
import kotlin.math.abs

/**
 * Port of YumaPlayer's `SimpMusic` client (api-lyrics.simpmusic.org/v1/{videoId}).
 *
 * Keys on YouTube video ids: Yuma prefers the entry whose duration is closest to
 * the song and takes its synced payload first, then plain.
 */
internal object SimpMusicProvider {

    private const val BASE_URL = "https://api-lyrics.simpmusic.org/v1/"

    private class Data(val duration: Int, val syncedLyrics: String?, val plainLyrics: String?)

    fun fetch(videoId: String, durationSeconds: Int): String? {
        val tracks = fetchAll(videoId)
        if (tracks.isEmpty()) return null

        val best = if (durationSeconds > 0 && tracks.size > 1) {
            tracks.minByOrNull { abs(it.duration - durationSeconds) }
        } else {
            tracks.firstOrNull()
        } ?: return null

        return (best.syncedLyrics ?: best.plainLyrics).usableLyrics()
    }

    private fun fetchAll(videoId: String): List<Data> {
        val response = LyricsHttp.get(
            BASE_URL + videoId,
            headers = mapOf(
                "Accept" to "application/json",
                "Content-Type" to "application/json",
            ),
        ) ?: return emptyList()
        if (!response.ok) return emptyList()

        val root = runCatching { JSONObject(response.body) }.getOrNull() ?: return emptyList()
        if (!root.optBoolean("success")) return emptyList()
        val data = root.optJSONArray("data") ?: return emptyList()

        return (0 until data.length()).mapNotNull { index ->
            val node = data.optJSONObject(index) ?: return@mapNotNull null
            Data(
                duration = node.optInt("duration", 0),
                syncedLyrics = node.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" },
                plainLyrics = node.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }
}
