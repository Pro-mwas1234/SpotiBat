package com.mwask.bat.lyrics.providers

import com.mwask.bat.lyrics.LyricsHttp
import com.mwask.bat.lyrics.usableLyrics
import org.json.JSONObject
import java.util.Locale

/**
 * Port of YumaPlayer's `YouLyPlus` client.
 *
 * Yuma rotates through five LyricsPlus mirrors, asking for TTML first and
 * falling back to the v2 lyrics endpoint, which it converts into LRC using the
 * syllabus (syllable) timings when the response is word-synced.
 */
internal object YouLyPlusProvider {

    private const val TTML_PATH = "v1/ttml/get"
    private const val LYRICS_PATH = "v2/lyrics/get"

    private val baseUrls = listOf(
        "https://lyricsplus.binimum.org/",
        "https://lyricsplus.prjktla.my.id/",
        "https://lyricsplus.prjktla.workers.dev/",
        "https://lyricsplus.atomix.one/",
        "https://lyricsplus-seven.vercel.app/",
    )

    fun fetch(title: String, artist: String, album: String?, durationSeconds: Int): String? {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return null
        val cleanAlbum = album?.trim().orEmpty()

        return fetchTtml(cleanTitle, cleanArtist, cleanAlbum, durationSeconds)
            ?: fetchLyricsAsLrc(cleanTitle, cleanArtist, cleanAlbum, durationSeconds)
    }

    private fun fetchTtml(title: String, artist: String, album: String, durationSeconds: Int): String? =
        fetchFromMirrors(TTML_PATH, title, artist, album, durationSeconds) { body ->
            val trimmed = body.trim()
            val candidate = if (trimmed.startsWith("<")) {
                trimmed
            } else {
                runCatching { JSONObject(body).optString("ttml").trim() }.getOrNull()
            }
            candidate?.takeIf { it.isNotBlank() && it.startsWith("<") }
        }

    private fun fetchLyricsAsLrc(title: String, artist: String, album: String, durationSeconds: Int): String? =
        fetchFromMirrors(LYRICS_PATH, title, artist, album, durationSeconds) { body ->
            toLyricsText(body)
        }

    private fun fetchFromMirrors(
        path: String,
        title: String,
        artist: String,
        album: String,
        durationSeconds: Int,
        decode: (String) -> String?,
    ): String? {
        for (baseUrl in baseUrls) {
            val response = LyricsHttp.get(
                baseUrl + path,
                params = buildMap {
                    put("title", title)
                    put("artist", artist)
                    if (album.isNotBlank()) put("album", album)
                    if (durationSeconds > 0) put("duration", durationSeconds.toString())
                },
                headers = mapOf("Accept" to "application/json"),
            ) ?: continue
            if (!response.ok) continue
            val lyrics = runCatching { decode(response.body) }.getOrNull() ?: continue
            lyrics.usableLyrics()?.let { return it }
        }
        return null
    }

    private fun toLyricsText(body: String): String? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val type = root.optString("type")
        val lyrics = root.optJSONArray("lyrics") ?: return null
        if (lyrics.length() == 0) return null

        val timed = (0 until lyrics.length()).mapNotNull { lyrics.optJSONObject(it) }
            .filter { it.has("time") && !it.isNull("time") }

        if (timed.isNotEmpty()) {
            val text = timed.joinToString("\n") { line ->
                buildString {
                    append(formatTimestamp(line.optLong("time"), bracketed = true))
                    val syllables = line.optJSONArray("syllabus")?.let { array ->
                        (0 until array.length()).mapNotNull { array.optJSONObject(it) }
                            .filter { it.optString("text").isNotBlank() && it.has("time") }
                    }.orEmpty()
                    if (type.equals("Word", ignoreCase = true) && syllables.isNotEmpty()) {
                        syllables.forEach { syllable ->
                            append(formatTimestamp(syllable.optLong("time"), bracketed = false))
                            append(syllable.optString("text"))
                        }
                    } else {
                        append(line.optString("text"))
                    }
                }
            }
            if (text.isNotBlank()) return text
        }

        val plain = (0 until lyrics.length())
            .mapNotNull { lyrics.optJSONObject(it)?.optString("text")?.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        return plain.takeIf { it.isNotBlank() }
    }

    private fun formatTimestamp(timeMs: Long, bracketed: Boolean): String {
        val safe = timeMs.coerceAtLeast(0L)
        val minutes = safe / 60_000L
        val seconds = (safe % 60_000L) / 1_000L
        val millis = safe % 1_000L
        val timestamp = String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
        return if (bracketed) "[$timestamp]" else "<$timestamp>"
    }
}
