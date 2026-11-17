package com.mwask.bat.lyrics.providers

import android.util.Base64
import com.mwask.bat.lyrics.LyricsHttp
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.min

/**
 * Port of YumaPlayer's `KuGou` lyrics client (itself derived from ViMusic).
 *
 * KuGou needs two hops - find the song's hash, ask for its lyric candidates,
 * then download the base64 LRC - and then trims the credit lines ("作词:", "作曲:")
 * that the service prefixes/suffixes onto its payloads.
 */
internal object KuGouProvider {

    private const val PAGE_SIZE = 8
    private const val HEAD_CUT_LIMIT = 30
    private const val DURATION_TOLERANCE = 8

    private val ACCEPTED_REGEX = Regex("""\[(\d\d):(\d\d)\.(\d{2,3})].*""")
    private val BANNED_REGEX = Regex(""".+].+[:：].+""")

    private class Candidate(val id: Long, val accessKey: String)

    fun fetch(title: String, artist: String, durationSeconds: Int): String? {
        val keywordTitle = normalizeTitle(title)
        val keywordArtist = normalizeArtist(artist)
        val keyword = "$keywordTitle - $keywordArtist"

        val candidate = findCandidate(keyword, durationSeconds) ?: return null
        return download(candidate)
    }

    private fun findCandidate(keyword: String, durationSeconds: Int): Candidate? {
        val songs = searchSongs(keyword)
        for (song in songs) {
            if (durationSeconds == -1 || abs(song.first - durationSeconds) <= DURATION_TOLERANCE) {
                val candidate = searchLyrics(mapOf("hash" to song.second)).firstOrNull()
                if (candidate != null) return candidate
            }
        }
        return searchLyrics(
            buildMap {
                put("duration", durationSeconds.takeIf { it != -1 }?.times(1000)?.toString().orEmpty())
                put("keyword", keyword)
            }.filterValues { it.isNotEmpty() },
        ).firstOrNull()
    }

    private fun searchSongs(keyword: String): List<Pair<Int, String>> {
        val response = LyricsHttp.get(
            "https://mobileservice.kugou.com/api/v3/search/song",
            params = mapOf(
                "version" to "9108",
                "plat" to "0",
                "pagesize" to PAGE_SIZE.toString(),
                "showtype" to "0",
                "keyword" to keyword,
            ),
        ) ?: return emptyList()
        if (!response.ok) return emptyList()

        val info = runCatching {
            JSONObject(response.body).getJSONObject("data").getJSONArray("info")
        }.getOrNull() ?: return emptyList()

        return (0 until info.length()).mapNotNull { index ->
            val node = info.optJSONObject(index) ?: return@mapNotNull null
            val duration = node.optInt("duration", -1)
            val hash = node.optString("hash").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            duration to hash
        }
    }

    private fun searchLyrics(params: Map<String, String>): List<Candidate> {
        val response = LyricsHttp.get(
            "https://lyrics.kugou.com/search",
            params = mapOf(
                "ver" to "1",
                "man" to "yes",
                "client" to "pc",
            ) + params,
        ) ?: return emptyList()
        if (!response.ok) return emptyList()

        val candidates = runCatching { JSONObject(response.body).getJSONArray("candidates") }
            .getOrNull() ?: return emptyList()

        return (0 until candidates.length()).mapNotNull { index ->
            val node = candidates.optJSONObject(index) ?: return@mapNotNull null
            val id = node.optLong("id", -1L)
            val accessKey = node.optString("accesskey")
            if (id <= 0L || accessKey.isBlank()) null else Candidate(id, accessKey)
        }
    }

    private fun download(candidate: Candidate): String? {
        val response = LyricsHttp.get(
            "https://lyrics.kugou.com/download",
            params = mapOf(
                "fmt" to "lrc",
                "charset" to "utf8",
                "client" to "pc",
                "ver" to "1",
                "id" to candidate.id.toString(),
                "accesskey" to candidate.accessKey,
            ),
        ) ?: return null
        if (!response.ok) return null

        val content = runCatching { JSONObject(response.body).optString("content") }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: return null

        val decoded = runCatching {
            String(Base64.decode(content, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull() ?: return null

        return decoded.normalize().takeIf { it.isNotBlank() }
    }

    private fun normalizeTitle(title: String): String = title
        .replace("\\(.*\\)".toRegex(), "")
        .replace("（.*）".toRegex(), "")
        .replace("「.*」".toRegex(), "")
        .replace("『.*』".toRegex(), "")
        .replace("<.*>".toRegex(), "")
        .replace("《.*》".toRegex(), "")
        .replace("〈.*〉".toRegex(), "")
        .replace("＜.*＞".toRegex(), "")

    private fun normalizeArtist(artist: String): String = artist
        .replace(", ", "、")
        .replace(" & ", "、")
        .replace(".", "")
        .replace("和", "、")
        .replace("\\(.*\\)".toRegex(), "")
        .replace("（.*）".toRegex(), "")

    private fun String.normalize(): String = replace("&apos;", "'")
        .lines()
        .filter { it.matches(ACCEPTED_REGEX) }
        .let { lines ->
            if (lines.isEmpty()) return ""

            var headCutLine = 0
            for (index in min(HEAD_CUT_LIMIT, lines.lastIndex) downTo 0) {
                if (lines[index].matches(BANNED_REGEX)) {
                    headCutLine = index + 1
                    break
                }
            }
            val filteredLines = lines.drop(headCutLine)

            var tailCutLine = 0
            for (index in min(lines.size - HEAD_CUT_LIMIT, lines.lastIndex) downTo 0) {
                if (lines[lines.lastIndex - index].matches(BANNED_REGEX)) {
                    tailCutLine = index + 1
                    break
                }
            }

            filteredLines.dropLast(tailCutLine).joinToString("\n")
        }
}
