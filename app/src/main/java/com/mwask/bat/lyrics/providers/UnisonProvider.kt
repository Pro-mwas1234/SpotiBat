package com.mwask.bat.lyrics.providers

import com.mwask.bat.lyrics.LyricsHttp
import com.mwask.bat.lyrics.usableLyrics
import org.json.JSONObject

/**
 * Port of YumaPlayer's `Unison` client (unison.boidu.dev).
 *
 * Yuma tries the video id first and falls back to a metadata lookup
 * (`song`/`artist`/`album`/`duration`); both answers come back wrapped in
 * `{success, data: {lyrics, format, syncType}}`.
 */
internal object UnisonProvider {

    private const val API_BASE_URL = "https://unison.boidu.dev/"

    fun fetch(
        videoId: String?,
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int,
    ): String? {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return null

        if (!videoId.isNullOrBlank()) {
            fetchById(videoId)?.let { return it }
        }
        return fetchByMetadata(cleanTitle, cleanArtist, album?.trim(), durationSeconds)
    }

    private fun fetchById(videoId: String): String? {
        val response = LyricsHttp.get(API_BASE_URL + "lyrics", params = mapOf("v" to videoId))
            ?: return null
        return extract(response)
    }

    private fun fetchByMetadata(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int,
    ): String? {
        val response = LyricsHttp.get(
            API_BASE_URL + "lyrics",
            params = buildMap {
                put("song", title)
                put("artist", artist)
                if (!album.isNullOrBlank()) put("album", album)
                if (durationSeconds > 0) put("duration", durationSeconds.toString())
            },
        ) ?: return null
        return extract(response)
    }

    private fun extract(response: com.mwask.bat.lyrics.LyricsHttp.Response): String? {
        if (!response.ok) return null
        val root = runCatching { JSONObject(response.body) }.getOrNull() ?: return null
        if (!root.optBoolean("success")) return null
        val data = root.optJSONObject("data") ?: return null
        return data.optString("lyrics").usableLyrics()
    }
}
