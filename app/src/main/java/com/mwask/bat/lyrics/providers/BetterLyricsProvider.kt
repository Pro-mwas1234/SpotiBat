package com.mwask.bat.lyrics.providers

import com.mwask.bat.lyrics.LyricsHttp
import com.mwask.bat.lyrics.usableLyrics
import org.json.JSONArray
import org.json.JSONObject

/**
 * Port of YumaPlayer's `BetterLyrics` client (lyrics-api.boidu.dev).
 *
 * Endpoints are asked in the order that yields the most precise timing:
 * `ttml/getLyrics` (syllable-level TTML), then `getLyrics` (default TTML),
 * then `qq/getLyrics` (QRC word timings). The legacy/kugou endpoints now
 * require an API key for uncached songs and only answer with line-level LRC,
 * so they are skipped. Payloads may be raw XML or a JSON envelope
 * (`{ttml|lyrics|data|result|response: ...}`) nested up to two levels deep.
 */
internal object BetterLyricsProvider {

    private const val API_BASE_URL = "https://lyrics-api.boidu.dev/"
    private const val SYLLABLE_TTML_PATH = "ttml/getLyrics"
    private const val TTML_LYRICS_PATH = "getLyrics"
    private const val QRC_LYRICS_PATH = "qq/getLyrics"
    private const val MAX_RESPONSE_UNWRAP_DEPTH = 2
    private const val MAX_TTML_ROOT_SCAN_LENGTH = 4096

    private val ttmlRootRegex =
        Regex("""<(?:[A-Za-z_][\w.-]*:)?tt(?:\s|>)""", RegexOption.IGNORE_CASE)

    private data class Decoded(val content: String, val score: Double?)

    fun fetch(title: String, artist: String, album: String?, durationSeconds: Int): String? {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return null

        for (endpoint in listOf(SYLLABLE_TTML_PATH, TTML_LYRICS_PATH, QRC_LYRICS_PATH)) {
            fetchFromEndpoint(endpoint, cleanTitle, cleanArtist, album?.trim().orEmpty(), durationSeconds)
                ?.let { return it }
        }
        return null
    }

    private fun fetchFromEndpoint(
        endpoint: String,
        title: String,
        artist: String,
        album: String,
        durationSeconds: Int,
    ): String? {
        val response = LyricsHttp.get(
            resolveUrl(endpoint),
            params = buildMap {
                put("s", title)
                put("a", artist)
                if (album.isNotBlank()) put("al", album)
                if (durationSeconds > 0) put("d", durationSeconds.toString())
            },
        ) ?: return null
        if (!response.ok) return null

        val decoded = runCatching { decode(response.body) }.getOrNull() ?: return null
        return decoded.content.usableLyrics()
    }

    private fun resolveUrl(endpoint: String): String =
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            endpoint
        } else {
            API_BASE_URL + endpoint.removePrefix("/")
        }

    private fun decode(responseText: String): Decoded? {
        val raw = responseText.removePrefix("\uFEFF")
        if (isSupportedPayload(raw)) return Decoded(raw, null)

        val root = runCatching { JSONObject(responseText) }.getOrNull()
            ?: runCatching { JSONArray(responseText) }.getOrNull()
            ?: return null

        val envelope = root as? JSONObject
        val score = envelope?.optDouble("score")?.takeIf { !it.isNaN() }
        envelope?.optString("ttml")?.takeIf { isSupportedPayload(it) }?.let { return Decoded(it, score) }
        envelope?.optString("lyrics")?.takeIf { isSupportedPayload(it) }?.let { return Decoded(it, score) }

        val nested = decodeElement(root, 0) ?: return null
        return nested.copy(score = score ?: nested.score)
    }

    private fun decodeElement(element: Any?, depth: Int): Decoded? {
        if (depth > MAX_RESPONSE_UNWRAP_DEPTH) return null
        return when (element) {
            is JSONObject -> {
                val score = element.optDouble("score").takeIf { !it.isNaN() }
                val payload = listOf("ttml", "lyrics", "data", "result", "response")
                    .firstNotNullOfOrNull { key ->
                        if (element.has(key) && !element.isNull(key)) element.opt(key) else null
                    } ?: return null
                val decoded = decodeElement(payload, depth + 1) ?: return null
                decoded.copy(score = score ?: decoded.score)
            }

            is String -> when {
                isSupportedPayload(element) -> Decoded(element, null)
                depth < MAX_RESPONSE_UNWRAP_DEPTH -> {
                    val nested = runCatching { JSONObject(element) }.getOrNull()
                        ?: runCatching { JSONArray(element) }.getOrNull()
                        ?: return null
                    decodeElement(nested, depth + 1)
                }

                else -> null
            }

            is JSONArray -> {
                for (index in 0 until element.length()) {
                    val decoded = decodeElement(element.opt(index), depth + 1)
                    if (decoded != null) return decoded
                }
                null
            }

            else -> null
        }
    }

    private fun isTtmlPayload(value: String): Boolean =
        ttmlRootRegex.containsMatchIn(value.take(MAX_TTML_ROOT_SCAN_LENGTH))

    /** TTML (syllable timings) or QRC (QQ karaoke word timings). */
    private fun isSupportedPayload(value: String): Boolean =
        isTtmlPayload(value) || value.contains("<QrcInfos", ignoreCase = true) ||
            value.contains("LyricContent=", ignoreCase = true)
}
