package com.mwask.bat.lyrics.parsing

/**
 * One lyric line, mirroring YumaPlayer's `LyricsEntry`: a start time, the text,
 * and - when the provider returned word-level timing (TTML / enhanced LRC) - the
 * per-word timestamps the karaoke view highlights.
 */
data class LyricsEntry(
    val time: Long,
    val text: String,
    val words: List<WordTimestamp>? = null,
    val isInstrumental: Boolean = false,
    val durationMs: Long = 0L,
    val translation: String? = null,
) : Comparable<LyricsEntry> {
    override fun compareTo(other: LyricsEntry): Int = time.compareTo(other.time)
}

data class WordTimestamp(
    val text: String,
    val startTime: Double,
    val endTime: Double,
    val isBackground: Boolean = false,
)

/** A parsed lyrics payload plus the sync quality the providers reported. */
data class ParsedLyrics(
    val entries: List<LyricsEntry>,
    val synced: Boolean,
    val wordSynced: Boolean,
) {
    val isUsable: Boolean get() = entries.any { it.text.isNotBlank() }
}
