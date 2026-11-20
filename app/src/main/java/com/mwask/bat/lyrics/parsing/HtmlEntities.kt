package com.mwask.bat.lyrics.parsing

/**
 * Decodes the entities lyric providers hand back inside their XML, TTML and LRC payloads.
 *
 * Providers escape punctuation numerically (`don&#8217;t`, `&#39;`) as often as they use the
 * named forms (`&apos;`), and the parsers used to know only a handful of named ones, so lines
 * shipped with the raw `&#8217;` text on screen. One pass over the entities is enough; it is
 * deliberately not repeated, because a second pass would turn `&amp;amp;` into `&`.
 */
object HtmlEntities {

    private val ENTITY_REGEX =
        Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,9});")

    private val NAMED = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        "hellip" to "\u2026",
        "mdash" to "\u2014",
        "ndash" to "\u2013",
        "lsquo" to "\u2018",
        "rsquo" to "\u2019",
        "ldquo" to "\u201C",
        "rdquo" to "\u201D",
        "bull" to "\u2022",
        "middot" to "\u00B7",
        "laquo" to "\u00AB",
        "raquo" to "\u00BB",
        "copy" to "\u00A9",
        "reg" to "\u00AE",
        "trade" to "\u2122",
        "deg" to "\u00B0",
        "times" to "\u00D7",
        "eacute" to "\u00E9",
        "egrave" to "\u00E8",
        "agrave" to "\u00E0",
        "ccedil" to "\u00E7",
        "uuml" to "\u00FC",
        "ouml" to "\u00F6",
        "auml" to "\u00E4",
        "ntilde" to "\u00F1",
        "oslash" to "\u00F8",
        "aring" to "\u00E5",
    )

    fun decode(text: String): String {
        if (text.indexOf('&') < 0) return text
        return ENTITY_REGEX.replace(text) { match -> resolve(match.value) ?: match.value }
    }

    private fun resolve(entity: String): String? {
        val body = entity.substring(1, entity.length - 1)
        return when {
            body.startsWith("#x") || body.startsWith("#X") ->
                codepoint(body.substring(2).toIntOrNull(16))
            body.startsWith("#") -> codepoint(body.substring(1).toIntOrNull())
            else -> NAMED[body]
        }
    }

    private fun codepoint(value: Int?): String? {
        if (value == null || value <= 0) return null
        if (value > 0x10FFFF) return null
        return String(Character.toChars(value))
    }
}
