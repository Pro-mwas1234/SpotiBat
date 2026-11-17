package com.mwask.bat.lyrics.parsing

/**
 * Port of YumaPlayer's `QRCParser` (QQ Music karaoke format), which BetterLyrics'
 * `/qq/getLyrics` endpoint returns.
 *
 * ```
 * [0,2380]Shape(0,476) (476,95)of(571,190)You(856,286)
 * ```
 *
 * The line carries `[startMs,durationMs]`; every word carries its absolute
 * `(startMs,durationMs)`. The payload may be wrapped in `<QrcInfos>` XML with the
 * body inside a `LyricContent="..."` attribute.
 */
internal object QrcParser {

    private val LINE_REGEX = Regex("""^\[(\d{1,8}),(\d{1,8})](.*)$""")
    private val WORD_REGEX = Regex("""(.*?)\((\d{1,8}),(\d{1,8})(?:,\d{1,8})?\)""")
    private val METADATA_REGEX = Regex("""^\[([A-Za-z]+):(.*)]$""")
    private val LYRIC_CONTENT_REGEX = Regex("""LyricContent\s*=\s*"""", RegexOption.IGNORE_CASE)

    fun isQrc(raw: String): Boolean =
        raw.contains("<QrcInfos", ignoreCase = true) ||
            raw.contains("LyricContent=", ignoreCase = true)

    fun hasWordTimings(raw: String): Boolean =
        extractLyricContent(raw).lineSequence().any { line ->
            val match = LINE_REGEX.matchEntire(line.trim()) ?: return@any false
            WORD_REGEX.containsMatchIn(match.groupValues[3])
        }

    fun parse(raw: String): List<LyricsEntry> {
        val content = extractLyricContent(raw)
        val entries = mutableListOf<LyricsEntry>()

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || METADATA_REGEX.matches(line)) return@forEach

            val match = LINE_REGEX.matchEntire(line) ?: return@forEach
            val lineStartMs = match.groupValues[1].toLongOrNull() ?: return@forEach
            val lineDurationMs = match.groupValues[2].toLongOrNull() ?: 0L

            val words = WORD_REGEX.findAll(match.groupValues[3])
                .mapNotNull { word ->
                    val text = word.groupValues[1]
                    val startMs = word.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                    val durationMs = word.groupValues[3].toLongOrNull() ?: 0L
                    if (text.isEmpty()) null
                    else WordTimestamp(
                        text = text,
                        startTime = startMs / 1000.0,
                        endTime = (startMs + durationMs) / 1000.0,
                    )
                }
                .toList()

            if (words.isEmpty()) return@forEach

            val text = words.joinToString(separator = "") { it.text }.trim()
            if (text.isEmpty()) return@forEach

            entries.add(
                LyricsEntry(
                    time = lineStartMs,
                    text = text,
                    words = words,
                    durationMs = lineDurationMs,
                )
            )
        }

        return entries.sorted()
    }

    private fun extractLyricContent(raw: String): String {
        val attribute = LYRIC_CONTENT_REGEX.find(raw) ?: return raw
        val valueStart = attribute.range.last + 1
        val valueEnd = raw.indexOf('"', startIndex = valueStart)
        if (valueEnd < 0) return raw
        return unescapeXml(raw.substring(valueStart, valueEnd))
    }

    private fun unescapeXml(value: String): String = value
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&#10;", "\n")
        .replace("&#13;", "\r")
        .replace("&amp;", "&")
}
