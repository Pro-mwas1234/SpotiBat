package com.mwask.bat.lyrics.parsing

/**
 * Line/word extraction for TTML lyrics (Apple-style and QQ/KuGou style), which
 * BetterLyrics and YouLyPlus return.
 *
 * Yuma's full TTML pipeline (TtmlSpanHandler/TtmlTimeParser, ~1.2k lines) also
 * carries romanization, translations and per-agent alignment. SpotiBat's player
 * renders lines with optional word highlighting, so this port keeps the subset
 * that matters: `<p>` lines with `begin`/`end`/`dur`, nested `<span>` word
 * timings, `<br/>` splits, and XML entity unescaping.
 */
internal object TtmlParser {

    private val REGEX_OPTIONS = RegexOption.IGNORE_CASE

    private val PARAGRAPH_REGEX =
        Regex("""<p\b([^>]*)>(.*?)</p>""", setOf(REGEX_OPTIONS, RegexOption.DOT_MATCHES_ALL))
    private val SPAN_REGEX =
        Regex("""<span\b([^>]*)>(.*?)</span>""", setOf(REGEX_OPTIONS, RegexOption.DOT_MATCHES_ALL))
    private val BRACKETED_SPAN_REGEX =
        Regex("""<span\b[^>]*>""", setOf(REGEX_OPTIONS, RegexOption.DOT_MATCHES_ALL))
    private val ANY_TAG_REGEX = Regex("""<[^>]+>""", REGEX_OPTIONS)
    private val BREAK_REGEX = Regex("""<br\s*/?>""", REGEX_OPTIONS)

    private val BEGIN_ATTR = Regex("""\bbegin\s*=\s*["']([^"']*)["']""", REGEX_OPTIONS)
    private val END_ATTR = Regex("""\bend\s*=\s*["']([^"']*)["']""", REGEX_OPTIONS)
    private val DUR_ATTR = Regex("""\bdur\s*=\s*["']([^"']*)["']""", REGEX_OPTIONS)
    private val AGENT_ATTR = Regex("""\b(?:agent|ttm:agent|role)\s*=\s*["']([^"']*)["']""", REGEX_OPTIONS)

    fun hasWordTimings(ttml: String): Boolean = SPAN_REGEX.findAll(ttml).any { match ->
        match.groupValues[1].isNotBlank() && BEGIN_ATTR.containsMatchIn(match.groupValues[1])
    }

    fun parse(ttml: String): List<LyricsEntry> {
        val paragraphs = PARAGRAPH_REGEX.findAll(ttml).toList()
        if (paragraphs.isEmpty()) {
            // Div-only TTML: keep the text so the user still sees the lyrics.
            return parseTextFallback(ttml)
        }

        val entries = mutableListOf<LyricsEntry>()
        paragraphs.forEach { match ->
            val attributes = match.groupValues[1]
            val body = match.groupValues[2]
            val begin = parseTime(BEGIN_ATTR.find(attributes)?.groupValues?.get(1)) ?: return@forEach
            val end = parseTime(END_ATTR.find(attributes)?.groupValues?.get(1))
                ?: parseTime(DUR_ATTR.find(attributes)?.groupValues?.get(1))?.let { begin + it }

            // Two passes over the spans: a word that carries only `begin` would otherwise
            // collapse to zero duration and never fill, so its end comes from the next
            // word's begin, then the paragraph's end.
            val spans = SPAN_REGEX.findAll(body).toList()
            val words = spans.mapIndexedNotNull { index, span ->
                val spanAttributes = span.groupValues[1]
                val spanBegin = parseTime(BEGIN_ATTR.find(spanAttributes)?.groupValues?.get(1))
                    ?: return@mapIndexedNotNull null
                val nextBegin = spans.getOrNull(index + 1)?.let { next ->
                    parseTime(BEGIN_ATTR.find(next.groupValues[1])?.groupValues?.get(1))
                }
                val spanEnd = parseTime(END_ATTR.find(spanAttributes)?.groupValues?.get(1))
                    ?: parseTime(DUR_ATTR.find(spanAttributes)?.groupValues?.get(1))?.let { spanBegin + it }
                    ?: nextBegin?.takeIf { it > spanBegin }
                    ?: end?.takeIf { it > spanBegin }
                    ?: spanBegin
                WordTimestamp(
                    text = plainText(span.groupValues[2]),
                    startTime = spanBegin,
                    endTime = spanEnd,
                )
            }.filter { it.text.isNotEmpty() }

            val text = plainText(body)
            if (text.isBlank() && words.isEmpty()) return@forEach

            entries.add(
                LyricsEntry(
                    time = (begin * 1000.0).toLong(),
                    text = text,
                    words = words.takeIf { it.isNotEmpty() },
                    durationMs = end?.let { ((it - begin) * 1000.0).toLong() } ?: 0L,
                )
            )
        }

        return entries.sorted()
    }

    private fun parseTextFallback(ttml: String): List<LyricsEntry> {
        val withoutSpans = ANY_TAG_REGEX.replace(BREAK_REGEX.replace(ttml, "\n"), "")
        return unescape(withoutSpans)
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, text -> LyricsEntry(time = index * 4_000L, text = text) }
    }

    /** Tries `hh:mm:ss.mmm`, `mm:ss.mmm`, `ss.mmm`, each with optional `s`/`ms`/frames suffix. */
    fun parseTime(raw: String?): Double? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val clock = value.removeSuffix("t").trim()
        if (clock.contains(":")) {
            val parts = clock.split(":")
            val seconds = if (parts.size >= 3) {
                parts[0].toDoubleOrNull()?.times(3600.0)
                    ?.plus((parts[1].toDoubleOrNull() ?: return null) * 60.0)
                    ?.plus(parts[2].toDoubleOrNull() ?: return null)
            } else {
                parts[0].toDoubleOrNull()?.times(60.0)
                    ?.plus(parts[1].toDoubleOrNull() ?: return null)
            }
            return seconds
        }
        val numeric = clock.removeSuffix("ms").removeSuffix("s").trim().toDoubleOrNull() ?: return null
        return if (clock.endsWith("ms")) numeric / 1000.0 else numeric
    }

    private fun plainText(fragment: String): String = unescape(
        ANY_TAG_REGEX.replace(fragment, "")
    )
        .replace(BRACKETED_SPAN_REGEX, "")
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun unescape(text: String): String = HtmlEntities.decode(text)
}
