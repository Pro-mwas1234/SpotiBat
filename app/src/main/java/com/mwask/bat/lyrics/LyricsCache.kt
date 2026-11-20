package com.mwask.bat.lyrics

import android.content.Context
import com.mwask.bat.backend.model.LyricLine
import com.mwask.bat.backend.model.LyricWord
import com.mwask.bat.backend.model.TrackLyrics
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lyrics kept next to the downloaded songs.
 *
 * "Download lyrics" in Personalization controls this: while it is on, whatever lyrics end up
 * on screen (Spotify's or a provider's) are written to disk, so the same track still shows
 * timed lyrics with no connection. Files are keyed by track uri and read before any network
 * lookup, which is also what makes a cold offline start fast.
 */
object LyricsCache {

    private const val DIR = "lyrics_cache"

    /** Files written before the entity decoding are dropped instead of shown with `&#8217;`. */
    private const val VERSION = 2

    fun save(context: Context, uri: String, lyrics: TrackLyrics) {
        if (uri.isBlank() || lyrics.lines.isEmpty()) return
        val file = fileFor(context, uri) ?: return
        runCatching {
            val array = JSONArray()
            lyrics.lines.forEach { line ->
                val words = JSONArray()
                line.words.forEach { word ->
                    words.put(
                        JSONArray()
                            .put(word.startTimeMs)
                            .put(word.endTimeMs)
                            .put(word.text)
                    )
                }
                array.put(
                    JSONObject()
                        .put("start", line.startTimeMs)
                        .put("text", line.text)
                        .put("words", words)
                )
            }
            val root = JSONObject()
                .put("version", VERSION)
                .put("provider", lyrics.provider)
                .put("synced", lyrics.synced)
                .put("lines", array)
            file.writeText(root.toString())
        }
    }

    fun load(context: Context, uri: String): TrackLyrics? {
        val file = fileFor(context, uri) ?: return null
        if (!file.isFile || file.length() <= 0L) return null
        return runCatching {
            val root = JSONObject(file.readText())
            if (root.optInt("version") < VERSION) {
                file.delete()
                return null
            }
            val array = root.optJSONArray("lines") ?: return null
            val lines = ArrayList<LyricLine>(array.length())
            for (index in 0 until array.length()) {
                val node = array.optJSONObject(index) ?: continue
                val wordsNode = node.optJSONArray("words")
                val words = ArrayList<LyricWord>(wordsNode?.length() ?: 0)
                if (wordsNode != null) {
                    for (wordIndex in 0 until wordsNode.length()) {
                        val word = wordsNode.optJSONArray(wordIndex) ?: continue
                        words += LyricWord(
                            startTimeMs = word.optLong(0),
                            endTimeMs = word.optLong(1),
                            text = word.optString(2),
                        )
                    }
                }
                lines += LyricLine(
                    startTimeMs = node.optLong("start"),
                    text = node.optString("text"),
                    words = words,
                )
            }
            if (lines.isEmpty()) null else TrackLyrics(
                lines = lines,
                provider = root.optString("provider"),
                synced = root.optBoolean("synced"),
            )
        }.getOrNull()
    }

    fun remove(context: Context, uri: String) {
        fileFor(context, uri)?.delete()
    }

    fun clear(context: Context) {
        val dir = File(context.applicationContext.filesDir, DIR)
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun fileFor(context: Context, uri: String): File? {
        if (uri.isBlank()) return null
        val dir = File(context.applicationContext.filesDir, DIR)
        if (!dir.isDirectory) dir.mkdirs()
        val name = uri.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, "$name.json")
    }
}
