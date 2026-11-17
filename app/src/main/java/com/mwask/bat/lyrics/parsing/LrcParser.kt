package com.mwask.bat.lyrics.parsing

/**
 * Port of YumaPlayer's `LrcParser` restricted to what SpotiBat's line-based
 * player needs: LRC (`[mm:ss.xx]`), YRC (`[start,duration]`), enhanced LRC word
 * timing (`<mm:ss.xx>`), and the instrumental-break insertion Yuma renders as
 * "..." gaps. TTML goes through [TtmlParser].
 */
object LrcParser {

    private const val MINUTE_MS = 60_000L
    private const val SECOND_MS = 1_000L

    val LINE_REGEX = Regex("""((\[\d{1,3}:\d{2}(?:[.:]\d{2,3})?\]\s*)+)(.*)""")
    private val TIME_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{2,3}))?]""")
    private val WHITESPACE_REGEX = Regex("""\s+""")
    private val ENHANCED_LRC_WORD_TIME_REGEX = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{2,3})?>""")
    private val ENHANCED_LRC_WORD_TIME_CAPTURING_REGEX = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{2,3}))?>""")
    private val INLINE_MILLISECONDS_TIME_REGEX = Regex("""<\d{1,8}(?:,\d{1,8})?>""")
    private val YRC_LINE_REGEX = Regex("""\[(\d{1,8}),\d{1,8}](.*)""")
    private val YRC_WORD_TIME_REGEX = Regex("""\(\d{1,8},\d{1,8}(?:,\d{1,8})?\)""")
    private val INVISIBLE_CHARS_REGEX = Regex("""[\u200B\u200C\u200D\u2060\u00AD]""")

    private const val NBSP = '\u00A0'
    private const val INSTRUMENTAL_GAP_THRESHOLD_MS = 5000L
    private const val INSTRUMENTAL_INTRO_START_MS = 1000L
    private const val INSTRUMENTAL_OUTRO_VOCAL_TAIL_MS = 2500L

    fun isTtml(lyrics: String): Boolean {
        val trimmed = normalizeLyricsText(lyrics)
        if (!trimmed.startsWith("<")) return false
        return trimmed.contains("<tt", ignoreCase = true) ||
            trimmed.contains("http://www.w3.org/ns/ttml", ignoreCase = true)
    }

    fun isLineSyncedLrc(lyrics: String): Boolean = lyrics.lineSequence().any { line ->
        val trimmed = line.trim()
        LINE_REGEX.matches(trimmed) || YRC_LINE_REGEX.matches(trimmed)
    }

    fun hasWordSyncedLyrics(lyrics: String): Boolean {
        val normalized = normalizeLyricsText(lyrics)
        if (isTtml(normalized)) return TtmlParser.hasWordTimings(normalized)
        return normalized.lineSequence().any(::hasEnhancedLrcWordTimings)
    }

    private fun hasEnhancedLrcWordTimings(line: String): Boolean {
        val match = LINE_REGEX.matchEntire(line.trim()) ?: return false
        val content = match.groupValues[3]
        val timings = ENHANCED_LRC_WORD_TIME_CAPTURING_REGEX.findAll(content).toList()
        return timings.indices.any { index ->
            val start = timings[index].range.last + 1
            val end = timings.getOrNull(index + 1)?.range?.first ?: content.length
            start < end && content.substring(start, end).isNotEmpty()
        }
    }

    fun normalizeLyricsText(lyrics: String): String {
        val raw = lyrics
            .replace("\uFEFF", "")
            .replace(INVISIBLE_CHARS_REGEX, "")
            .trim { it.isWhitespace() || it == NBSP }
        val unwrapped = stripCodeFence(raw)
        val normalized =
            if (isEscapedTtml(unwrapped)) {
                unwrapped
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&apos;", "'")
            } else {
                unwrapped
            }
        return normalized.trim { it.isWhitespace() || it == NBSP }
    }

    private fun stripCodeFence(lyrics: String): String {
        if (!lyrics.startsWith("```")) return lyrics
        val lines = lyrics.lines()
        if (lines.size <= 1) return lyrics
        val body = lines.drop(1).let { remaining ->
            if (remaining.lastOrNull()?.trim() == "```") remaining.dropLast(1) else remaining
        }
        return body.joinToString("\n").trim { it.isWhitespace() || it == NBSP }
    }

    private fun isEscapedTtml(lyrics: String): Boolean {
        val trimmed = lyrics.trimStart()
        return trimmed.startsWith("&lt;tt", ignoreCase = true) ||
            trimmed.contains("&lt;tt", ignoreCase = true) ||
            trimmed.contains("http://www.w3.org/ns/ttml", ignoreCase = true) &&
            trimmed.contains("&lt;", ignoreCase = true)
    }

    /** Parses everything Yuma's parser handles: TTML, LRC, YRC, plain text. */
    fun parse(lyrics: String, songDurationMs: Long = 0L): ParsedLyrics {
        val normalized = normalizeLyricsText(lyrics)
        if (normalized.isEmpty()) return ParsedLyrics(emptyList(), synced = false, wordSynced = false)

        if (isTtml(normalized)) {
            val entries = TtmlParser.parse(normalized)
            if (entries.isNotEmpty()) {
                return ParsedLyrics(
                    entries = insertInstrumentalBreaks(entries, songDurationMs),
                    synced = true,
                    wordSynced = entries.any { !it.words.isNullOrEmpty() },
                )
            }
        }

        // QQ karaoke (QRC) carries absolute word timings, so it goes before plain LRC.
        if (QrcParser.isQrc(normalized)) {
            val entries = QrcParser.parse(normalized)
            if (entries.isNotEmpty()) {
                return ParsedLyrics(
                    entries = insertInstrumentalBreaks(entries, songDurationMs),
                    synced = true,
                    wordSynced = entries.any { !it.words.isNullOrEmpty() },
                )
            }
        }

        if (isLineSyncedLrc(normalized)) {
            val entries = insertInstrumentalBreaks(parseLineSynced(normalized), songDurationMs)
            return ParsedLyrics(
                entries = entries,
                synced = true,
                wordSynced = hasWordSyncedLyrics(normalized),
            )
        }

        // Plain lyrics: no timing at all, shown as a static block.
        val plain = normalized.lines()
            .map { cleanInlineWordTimingText(it) }
            .filter { it.isNotBlank() }
            .mapIndexed { index, text -> LyricsEntry(time = (index * 4_000L), text = text) }
        return ParsedLyrics(entries = plain, synced = false, wordSynced = false)
    }

    fun parseLineSynced(lyrics: String): List<LyricsEntry> {
        val result = mutableListOf<LyricsEntry>()
        for (line in lyrics.lines()) {
            val entries = parseLineSyncedLrcLine(line) ?: parseMillisecondsSyncedLine(line)
            if (entries != null) result.addAll(entries)
        }
        return mergeLineSyncedTranslations(result).sorted()
    }

    private fun parseLineSyncedLrcLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) return null
        val match = LINE_REGEX.matchEntire(line.trim()) ?: return null
        val times = match.groupValues[1]
        val text = cleanInlineWordTimingText(match.groupValues[3])
        return TIME_REGEX.findAll(times).map { timeMatch ->
            val minutes = timeMatch.groupValues[1].toLong()
            val seconds = timeMatch.groupValues[2].toLong()
            val millisString = timeMatch.groupValues[3]
            var millis = millisString.toLongOrNull() ?: 0L
            when (millisString.length) {
                1 -> millis *= 100
                2 -> millis *= 10
            }
            LyricsEntry(minutes * MINUTE_MS + seconds * SECOND_MS + millis, text)
        }.toList()
    }

    private fun parseMillisecondsSyncedLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) return null
        val match = YRC_LINE_REGEX.matchEntire(line.trim()) ?: return null
        val time = match.groupValues[1].toLongOrNull() ?: return null
        return listOf(LyricsEntry(time, cleanInlineWordTimingText(match.groupValues[2])))
    }

    private fun mergeLineSyncedTranslations(entries: List<LyricsEntry>): List<LyricsEntry> {
        val merged = linkedMapOf<Long, LyricsEntry>()
        entries.forEach { entry ->
            val existing = merged[entry.time]
            if (existing == null) {
                merged[entry.time] = entry
                return@forEach
            }
            val translated = entry.text
                .replace(WHITESPACE_REGEX, " ")
                .trim()
                .takeIf { it.isNotEmpty() && !it.equals(existing.text.trim(), ignoreCase = true) }
            if (translated != null && existing.translation == null) {
                merged[entry.time] = existing.copy(translation = translated)
            }
        }
        return merged.values.toList()
    }

    private fun cleanInlineWordTimingText(text: String): String = text
        .replace(ENHANCED_LRC_WORD_TIME_REGEX, "")
        .replace(INLINE_MILLISECONDS_TIME_REGEX, "")
        .replace(YRC_WORD_TIME_REGEX, "")
        .replace(WHITESPACE_REGEX, " ")
        .trim { it.isWhitespace() || it == NBSP }

    fun insertInstrumentalBreaks(entries: List<LyricsEntry>, songDurationMs: Long = 0L): List<LyricsEntry> {
        if (entries.isEmpty()) return entries
        val result = mutableListOf<LyricsEntry>()

        val firstVocal = entries.firstOrNull { it.time >= 0L && it.text.isNotBlank() }
        if (firstVocal != null) {
            val introGap = firstVocal.time - INSTRUMENTAL_INTRO_START_MS
            if (introGap >= INSTRUMENTAL_GAP_THRESHOLD_MS) {
                result.add(
                    LyricsEntry(
                        time = INSTRUMENTAL_INTRO_START_MS,
                        text = "",
                        isInstrumental = true,
                        durationMs = introGap,
                    )
                )
            }
        }

        result.addAll(entries)

        if (songDurationMs > 0L) {
            val lastVocal = entries.lastOrNull { it.text.isNotBlank() }
            if (lastVocal != null) {
                val outroStart = lastVocal.time + INSTRUMENTAL_OUTRO_VOCAL_TAIL_MS
                val outroDuration = songDurationMs - outroStart
                if (outroDuration >= INSTRUMENTAL_GAP_THRESHOLD_MS) {
                    result.add(
                        LyricsEntry(
                            time = outroStart,
                            text = "",
                            isInstrumental = true,
                            durationMs = outroDuration,
                        )
                    )
                }
            }
        }

        return result
    }

    /** Plain-text view of a payload, used by share sheets and the mini player. */
    fun displayLyricsText(lyrics: String): String {
        val parsed = parse(lyrics)
        return parsed.entries
            .filter { it.text.isNotBlank() }
            .joinToString("\n") { it.text.replace(WHITESPACE_REGEX, " ").trim() }
    }

    fun findCurrentLineIndex(lines: List<LyricsEntry>, positionMs: Long, leadMs: Long = 300L): Int {
        if (lines.isEmpty()) return -1
        val target = positionMs + leadMs
        var low = 0
        var high = lines.lastIndex
        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (lines[mid].time < target) low = mid + 1 else high = mid - 1
        }
        return high.coerceIn(0, lines.lastIndex)
    }
}
