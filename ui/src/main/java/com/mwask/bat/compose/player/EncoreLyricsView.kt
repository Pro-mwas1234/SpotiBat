package com.mwask.bat.compose.player

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Karaoke lyrics in Yuma's style.
 *
 * Every line is a wrapping row of words. A word that playback has reached fills in
 * left-to-right with a gradient driven by its own timestamps, words already sung stay
 * solid, and words still to come sit dimmed. Lines away from the active one are blurred
 * and the active line is kept centred; tapping a line seeks to it.
 *
 * Word timings come from the provider when it shipped them (TTML / enhanced LRC); when
 * only line timings exist the line's span is shared between its words by length, so the
 * fill still walks word by word.
 */
private const val LYRICS_TEXT_SIZE_SP = 26f
private const val LYRICS_LINE_SPACING = 1.3f

/** Blur grows with the line's distance from the active one, capped so far lines stay readable. */
private const val LYRICS_BLUR_MAX_DP = 6f
private const val LYRICS_BLUR_STEP_DP = 1.6f
private const val LYRICS_BLUR_MAX_LINES = 4

/** Fallback duration for the last line, or one with no following timestamp. */
private const val LYRICS_LINE_FALLBACK_MS = 3500L

/** Words not reached yet, and the un-filled half of the word being sung. */
private const val LYRICS_DIM_ALPHA = 0.35f

/** Snapshot intervals arrive at most ~500 ms apart; never extrapolate past one. */
private const val LYRICS_SMOOTH_MAX_DRIFT_MS = 520L

/** ~20 Hz: enough for a smooth wipe, and only the active line listens to it. */
private const val LYRICS_SMOOTH_TICK_MS = 48L

/**
 * Fills the gaps between the engine's position snapshots.
 *
 * The player reports its position every half second, so painting straight from it makes
 * the karaoke fill jump in 500 ms steps and land late. This anchors a monotonic clock to
 * each snapshot and lets wall time carry the position forward, capped at one snapshot
 * interval so a pause or a stall cannot run away with it.
 *
 * Only [positionMs] reads [tick], and only the active line calls it, so a tick costs one
 * row of recomposition rather than the whole list.
 */
@Stable
private class LyricsClock(initialSnapshotMs: Long) {
    private var snapshotMs = initialSnapshotMs
    private var anchorMs = SystemClock.elapsedRealtime()

    /** Bumped by the ticker; reading it is what schedules the next recomposition. */
    var tick by mutableLongStateOf(0L)

    fun anchor(snapshot: Long) {
        if (snapshot == snapshotMs) return
        snapshotMs = snapshot
        anchorMs = SystemClock.elapsedRealtime()
    }

    fun positionMs(): Long {
        tick
        val elapsed = (SystemClock.elapsedRealtime() - anchorMs)
            .coerceIn(0L, LYRICS_SMOOTH_MAX_DRIFT_MS)
        return snapshotMs + elapsed
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EncoreKaraokeLyrics(
    lyrics: List<TimeSyncedLyric>,
    currentPositionMs: Long,
    textColor: Color,
    listState: LazyListState,
    onSeekMs: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
) {
    if (lyrics.isEmpty()) return

    // Every timestamp at zero means plain text: show it without karaoke.
    val synced = lyrics.any { it.startTimeMs > 0L }
    val lineWords = remember(lyrics) { lyrics.indices.map { index -> wordsForLine(lyrics, index) } }
    // Provider times land slightly before the phone's audio, and the offset is tunable in the
    // fullscreen lyrics so a lagging or rushing highlight can be corrected by ear.
    val syncOffsetMs = LyricsOptions.syncOffsetMs.toLong()
    val playheadMs = currentPositionMs + syncOffsetMs

    val activeIdx = if (synced) {
        lyrics.indexOfLast { playheadMs >= it.startTimeMs }.coerceAtLeast(0)
    } else {
        -1
    }

    val clock = remember { LyricsClock(currentPositionMs) }
    LaunchedEffect(currentPositionMs) { clock.anchor(currentPositionMs) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            delay(LYRICS_SMOOTH_TICK_MS)
            clock.tick = SystemClock.elapsedRealtime()
        }
    }

    LaunchedEffect(activeIdx) {
        if (activeIdx < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        if (info.visibleItemsInfo.none { it.index == activeIdx }) {
            listState.animateScrollToItem(activeIdx)
        }
        val centered = listState.layoutInfo
        val item = centered.visibleItemsInfo.firstOrNull { it.index == activeIdx } ?: return@LaunchedEffect
        val viewportCenter = (centered.viewportStartOffset + centered.viewportEndOffset) / 2f
        val itemCenter = item.offset + item.size / 2f
        val delta = itemCenter - viewportCenter
        if (abs(delta) > 1f) listState.animateScrollBy(delta)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 24.dp)
    ) {
        itemsIndexed(lyrics) { index, line ->
            val isActive = synced && index == activeIdx
            val distance = abs(index - activeIdx)
            val blurRadius = (distance.coerceAtMost(LYRICS_BLUR_MAX_LINES) * LYRICS_BLUR_STEP_DP)
                .coerceAtMost(LYRICS_BLUR_MAX_DP)
                .dp
            val words = lineWords[index]
            // Only the line being sung follows the interpolated clock; the rest are either
            // already lit or still dim, so a snapshot is plenty for them.
            val positionMs = if (isActive) clock.positionMs() + syncOffsetMs else playheadMs

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .then(if (synced) Modifier.clickable { onSeekMs(line.startTimeMs) } else Modifier)
                    .then(
                        if (!synced || isActive) Modifier
                        else Modifier.blur(radius = blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (words.isEmpty()) {
                    Text(
                        text = line.text,
                        style = lyricsStyle(if (synced && !isActive) textColor.copy(alpha = 0.7f) else textColor)
                    )
                } else {
                    words.forEach { word ->
                        Text(
                            text = word.text,
                            style = lyricsStyle(
                                brush = wordBrush(
                                    word = word,
                                    positionMs = positionMs,
                                    textColor = textColor,
                                    enabled = synced
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun lyricsStyle(color: Color): TextStyle = TextStyle(
    fontSize = LYRICS_TEXT_SIZE_SP.sp,
    lineHeight = (LYRICS_TEXT_SIZE_SP * LYRICS_LINE_SPACING).sp,
    fontWeight = FontWeight.Bold,
    color = color
)

private fun lyricsStyle(brush: Brush): TextStyle = TextStyle(
    fontSize = LYRICS_TEXT_SIZE_SP.sp,
    lineHeight = (LYRICS_TEXT_SIZE_SP * LYRICS_LINE_SPACING).sp,
    fontWeight = FontWeight.Bold,
    brush = brush
)

/** Solid once sung, a left-to-right wipe while the word is being sung, dim before it. */
private fun wordBrush(
    word: TimeSyncedWord,
    positionMs: Long,
    textColor: Color,
    enabled: Boolean
): Brush {
    if (!enabled) return SolidColor(textColor.copy(alpha = 0.9f))
    val dim = textColor.copy(alpha = LYRICS_DIM_ALPHA)
    return when {
        positionMs >= word.endTimeMs -> SolidColor(textColor)
        positionMs <= word.startTimeMs -> SolidColor(dim)
        else -> {
            val span = (word.endTimeMs - word.startTimeMs).coerceAtLeast(1L)
            val fraction = ((positionMs - word.startTimeMs).toFloat() / span).coerceIn(0f, 1f)
            Brush.linearGradient(
                0f to textColor,
                fraction to textColor,
                fraction to dim,
                1f to dim
            )
        }
    }
}

/** Real word timings when the provider has them, otherwise a length-weighted split of the line. */
private fun wordsForLine(lyrics: List<TimeSyncedLyric>, index: Int): List<TimeSyncedWord> {
    val line = lyrics[index]
    if (line.words.isNotEmpty()) {
        return line.words.mapNotNull { word ->
            val text = word.text.trim()
            if (text.isEmpty()) null else word.copy(text = text)
        }
    }

    val tokens = line.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return emptyList()

    val start = line.startTimeMs
    val nextStart = lyrics.getOrNull(index + 1)?.startTimeMs
        ?.takeIf { it > start }
        ?: (start + LYRICS_LINE_FALLBACK_MS)
    val span = (nextStart - start).coerceAtLeast(1L)
    val weights = tokens.map { it.length.coerceAtLeast(1) }
    val totalWeight = weights.sum().coerceAtLeast(1)

    var cursor = start.toDouble()
    return tokens.mapIndexed { wordIndex, token ->
        val duration = span * (weights[wordIndex].toDouble() / totalWeight)
        val word = TimeSyncedWord(
            startTimeMs = cursor.toLong(),
            endTimeMs = (cursor + duration).toLong(),
            text = token
        )
        cursor += duration
        word
    }
}
