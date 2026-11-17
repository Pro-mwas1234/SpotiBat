package com.mwask.bat.lyrics

import android.content.Context
import android.os.SystemClock
import com.mwask.bat.backend.core.store.AppPrefs
import com.mwask.bat.backend.model.LyricLine
import com.mwask.bat.backend.model.LyricWord
import com.mwask.bat.backend.model.TrackLyrics
import com.mwask.bat.constants.DefaultLyricsProviderOrder
import com.mwask.bat.constants.LyricsProviderKeys
import com.mwask.bat.constants.PreferredLyricsProvider
import com.mwask.bat.constants.VideoIdOnlyLyricsProviders
import com.mwask.bat.lyrics.parsing.LrcParser
import com.mwask.bat.lyrics.providers.BetterLyricsProvider
import com.mwask.bat.lyrics.providers.KuGouProvider
import com.mwask.bat.lyrics.providers.LrcLibProvider
import com.mwask.bat.lyrics.providers.SimpMusicProvider
import com.mwask.bat.lyrics.providers.UnisonProvider
import com.mwask.bat.lyrics.providers.YouLyPlusProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * External lyrics lookup, ported from YumaPlayer.
 *
 * SpotiBat asks Spotify's color-lyrics endpoint first; when that returns nothing
 * this repository walks Yuma's provider list in the user's preferred order and
 * returns the first payload that parses into usable lines.
 */
object LyricsRepository {

    private const val PROVIDER_TIMEOUT_MS = 8_000L

    /** Upper bound for the whole provider walk, so a bad network cannot stall lyrics for a minute. */
    private const val TOTAL_BUDGET_MS = 15_000L
    private const val MAX_CACHE_ENTRIES = 24

    /** Higher is better: word timings beat line timings, which beat plain text. */
    private const val WORD_SYNCED_QUALITY = 3
    private const val SYNCED_QUALITY = 2
    private const val PLAIN_QUALITY = 1

    private class CachedLyrics(val trackId: String, val lyrics: TrackLyrics)

    private val cache = object : LinkedHashMap<String, CachedLyrics>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedLyrics>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    /** Providers the user has not switched off, in their saved order. */
    fun providerOrder(context: Context): List<PreferredLyricsProvider> {
        val prefs = AppPrefs.prefs(context, LyricsProviderKeys.PrefsName)
        val saved = prefs.getString(LyricsProviderKeys.ProviderOrder, null)
        val order = saved
            ?.split(',')
            ?.mapNotNull { name -> PreferredLyricsProvider.entries.firstOrNull { it.name == name } }
            ?.takeIf { it.isNotEmpty() }
            ?: DefaultLyricsProviderOrder

        return order.filter { provider ->
            prefs.getBoolean(provider.prefsKey, true)
        }
    }

    fun cached(trackId: String): TrackLyrics? = synchronized(cache) { cache[trackId]?.lyrics }

    /**
     * Fetches lyrics from the external providers. Returns null when every
     * provider is disabled, errors, times out, or has no usable payload.
     */
    suspend fun load(
        context: Context,
        trackId: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        videoId: String? = null,
    ): TrackLyrics? {
        if (trackId.isBlank() || title.isBlank() || artist.isBlank()) return null
        cached(trackId)?.let { return it }

        val durationSeconds = if (durationMs > 0) (durationMs / 1000L).toInt() else -1
        val providers = providerOrder(context)
        if (providers.isEmpty()) return null

        return withContext(Dispatchers.IO) {
            // Yuma walks the providers and keeps the best-synced payload, not simply the
            // first one that answers, so a word-timed TTML beats an earlier line-only LRC.
            var best: TrackLyrics? = null
            var bestScore = 0
            val deadline = SystemClock.elapsedRealtime() + TOTAL_BUDGET_MS

            for (provider in providers) {
                if (provider in VideoIdOnlyLyricsProviders && videoId.isNullOrBlank()) continue

                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0L) break

                val raw = withTimeoutOrNull(minOf(PROVIDER_TIMEOUT_MS, remaining)) {
                    runCatching {
                        fetch(provider, videoId, title, artist, album, durationSeconds)
                    }.getOrNull()
                } ?: continue

                val parsed = LrcParser.parse(raw, songDurationMs = durationMs)
                if (!parsed.isUsable) continue

                val lyrics = TrackLyrics(
                    lines = parsed.entries.map { entry ->
                        LyricLine(
                            startTimeMs = entry.time,
                            text = entry.text,
                            words = entry.words.orEmpty().map { word ->
                                LyricWord(
                                    startTimeMs = (word.startTime * 1000.0).toLong(),
                                    endTimeMs = (word.endTime * 1000.0).toLong(),
                                    text = word.text,
                                )
                            },
                        )
                    },
                    provider = provider.displayName,
                    synced = parsed.synced,
                )

                val score = qualityOf(lyrics)
                if (score > bestScore) {
                    best = lyrics
                    bestScore = score
                }
                // Nothing in the provider list can beat real word timings.
                if (bestScore >= WORD_SYNCED_QUALITY) break
            }

            best?.also { lyrics ->
                synchronized(cache) { cache[trackId] = CachedLyrics(trackId, lyrics) }
            }
        }
    }

    /** Whether a payload has any word-level timing to drive karaoke. */
    fun hasWordTimings(lyrics: TrackLyrics?): Boolean =
        lyrics != null && lyrics.lines.any { it.words.isNotEmpty() }

    private fun qualityOf(lyrics: TrackLyrics): Int = when {
        lyrics.lines.any { it.words.isNotEmpty() } -> WORD_SYNCED_QUALITY
        lyrics.synced -> SYNCED_QUALITY
        else -> PLAIN_QUALITY
    }

    private fun fetch(
        provider: PreferredLyricsProvider,
        videoId: String?,
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int,
    ): String? = when (provider) {
        PreferredLyricsProvider.BETTER_LYRICS ->
            BetterLyricsProvider.fetch(title, artist, album, durationSeconds)

        PreferredLyricsProvider.YOULY_PLUS ->
            YouLyPlusProvider.fetch(title, artist, album, durationSeconds)

        PreferredLyricsProvider.LRCLIB ->
            LrcLibProvider.fetch(title, artist, album, durationSeconds)

        PreferredLyricsProvider.KUGOU ->
            KuGouProvider.fetch(title, artist, durationSeconds)

        PreferredLyricsProvider.SIMPMUSIC ->
            videoId?.let { SimpMusicProvider.fetch(it, durationSeconds) }

        PreferredLyricsProvider.UNISON ->
            UnisonProvider.fetch(videoId, title, artist, album, durationSeconds)
    }
}
