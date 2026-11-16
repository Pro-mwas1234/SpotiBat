package com.mwask.bat

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.mwask.bat.backend.api.Library
import com.mwask.bat.backend.library.Krespot
import com.mwask.bat.backend.model.ArtistDetail
import com.mwask.bat.backend.model.TrackLyrics
import com.mwask.bat.compose.home.HomeShelf
import com.mwask.bat.compose.home.QuickAccessItem
import com.mwask.bat.compose.home.toFavoriteArtistsShelf
import com.mwask.bat.compose.home.toQuickAccess
import com.mwask.bat.compose.home.toQuickAccessFallback
import com.mwask.bat.compose.home.toRecentlyPlayedShelf
import com.mwask.bat.compose.home.toShelves
import com.mwask.bat.compose.playlist.TrackItem
import com.mwask.bat.compose.screens.HistoryStats
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.lyrics.LyricsRepository
import java.util.Calendar

private val log = Logs.of(Logs.Module.MAIN)

internal class ShellHomeData(
    val quick: List<QuickAccessItem>,
    val shelves: List<HomeShelf>,
    val historyShelves: List<HomeShelf>,
    val historyToday: List<TrackItem>,
    val historyYesterday: List<TrackItem>,
    val historyEarlier: List<TrackItem>,
    val historyStats: HistoryStats,
)

internal class ShellNowPlayingData(
    val artistUri: String,
    val canvasAllowed: Boolean,
)

private class NowPlayingWriters(
    val setArtist: (ArtistDetail?) -> Unit,
    val setArtistFollowed: (Boolean?) -> Unit,
    val setLyrics: (TrackLyrics?) -> Unit,
    val setCanvasFile: (String?) -> Unit,
)

@Composable
internal fun MainActivity.rememberHomeFeedData(): ShellHomeData {
    val quick = remember(feed, playHistory, loading) {
        val fromFeed = feed.toQuickAccess()
        when {
            fromFeed.isNotEmpty() -> fromFeed
            loading -> emptyList()
            else -> {
                log.i("feed has no shorts section, deriving the shortcut grid from history")
                playHistory.map { it.item }.toQuickAccessFallback()
            }
        }
    }
    LaunchedEffect(quick) {
        log.i("home quick access tiles=${quick.size}")
    }
    val shelves = remember(feed, homeFacet) {
        feed.toShelves(includePodcasts = homeFacet.contains("podcast", ignoreCase = true))
    }
    val historyShelves = remember(playHistory, homeArtistImages.size) {
        listOfNotNull(
            playHistory.map { it.item }.toRecentlyPlayedShelf(),
            favoriteArtistsOf(playHistory).toFavoriteArtistsShelf(homeArtistImages),
        )
    }
    LaunchedEffect(shelves) {
        log.i("home shelves=${shelves.size} " +
                "grids=${shelves.count { it.grid }} " +
                "cards=${shelves.sumOf { it.cards.size }} " +
                "titles=${shelves.take(8).joinToString(" | ") { it.title.ifBlank { "-" } }}"
        )
    }
    val startOfToday = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val startOfYesterday = remember { startOfToday - 24L * 60 * 60 * 1000 }
    val historyToday = remember(playHistory, startOfToday) {
        playHistory.filter { it.playedAtMs >= startOfToday }.map { it.item }
    }
    val historyYesterday = remember(playHistory, startOfToday, startOfYesterday) {
        playHistory
            .filter { it.playedAtMs >= startOfYesterday && it.playedAtMs < startOfToday }
            .map { it.item }
    }
    val historyEarlier = remember(playHistory, startOfYesterday) {
        playHistory.filter { it.playedAtMs in 1 until startOfYesterday }.map { it.item }
    }
    val historyStats = remember(playStats, playHistory) {
        buildHistoryStats(playStats, playHistory)
    }
    return ShellHomeData(
        quick = quick,
        shelves = shelves,
        historyShelves = historyShelves,
        historyToday = historyToday,
        historyYesterday = historyYesterday,
        historyEarlier = historyEarlier,
        historyStats = historyStats,
    )
}

@Composable
internal fun MainActivity.rememberNowPlayingData(
    canvasAllowed: Boolean,
): ShellNowPlayingData {
    val writers = NowPlayingWriters(
        setArtist = { nowPlayingArtist = it },
        setArtistFollowed = { nowPlayingArtistFollowed = it },
        setLyrics = { nowPlayingLyrics = it },
        setCanvasFile = { canvasFile = it },
    )
    val playback = PlaybackHost.bridge?.state
    val nowPlayingArtistUri = remember(playback?.trackUri, playback?.artistUri, playback?.queue, playback?.queueFromContext) {
        playback?.artistUri.orEmpty().ifBlank {
            ((playback?.queue.orEmpty()) + (playback?.queueFromContext.orEmpty()))
                .firstOrNull { it.uri == playback?.trackUri }?.artistUri.orEmpty()
        }
    }
    LaunchedEffect(nowPlayingArtistUri) {
        if (nowPlayingArtistUri.isBlank()) {
            writers.setArtist(null)
            writers.setArtistFollowed(null)
            return@LaunchedEffect
        }
        val cached = artistCache[nowPlayingArtistUri]
        if (cached != null) {
            writers.setArtist(cached)
        } else {
            val fetched = Krespot.artists.artist(this@rememberNowPlayingData, nowPlayingArtistUri)
                ?.also {
                    if (artistCache.size >= 24) artistCache.clear()
                    artistCache[it.uri] = it
                }
            writers.setArtist(fetched)
        }
        val inLibrary = Library.saved(this@rememberNowPlayingData, listOf(nowPlayingArtistUri))
        writers.setArtistFollowed(if (inLibrary.isEmpty()) {
            nowPlayingArtist?.saved == true
        } else {
            nowPlayingArtistUri in inLibrary
        })
    }
    LaunchedEffect(playback?.trackUri) {
        val uri = playback?.trackUri.orEmpty()
        if (!uri.startsWith("spotify:track:")) {
            writers.setLyrics(null)
            return@LaunchedEffect
        }
        // Spotify's own lyrics first; the Yuma-derived providers fill the gaps.
        val spotify = Krespot.lyrics.lyrics(this@rememberNowPlayingData, uri)
        if (spotify != null && spotify.lines.isNotEmpty()) {
            writers.setLyrics(spotify)
            return@LaunchedEffect
        }
        val track = ((playback?.queue.orEmpty()) + (playback?.queueFromContext.orEmpty()))
            .firstOrNull { it.uri == uri }
        val durationMs = PlaybackHost.bridge?.progress?.durationMs?.takeIf { it > 0L }
            ?: track?.durationMs ?: 0L
        val external = LyricsRepository.load(
            context = this@rememberNowPlayingData,
            trackId = uri.substringAfterLast(":"),
            title = playback?.title.orEmpty().ifBlank { track?.title.orEmpty() },
            artist = playback?.artist.orEmpty().ifBlank { track?.artist.orEmpty() },
            album = track?.albumName?.takeIf { it.isNotBlank() },
            durationMs = durationMs,
        )
        log.i("lyrics for $uri came from ${external?.provider ?: "no provider"}")
        writers.setLyrics(external)
    }
    LaunchedEffect(playback?.trackUri, canvasAllowed, canvasResolution) {
        writers.setCanvasFile(null)
        if (!canvasAllowed) return@LaunchedEffect
        val uri = playback?.trackUri.orEmpty()
        if (!uri.startsWith("spotify:track:")) return@LaunchedEffect
        val file = CanvasKit.download(this@rememberNowPlayingData, uri, canvasResolution)
        if (uri == playback?.trackUri) writers.setCanvasFile(file)
    }
    return ShellNowPlayingData(
        artistUri = nowPlayingArtistUri,
        canvasAllowed = canvasAllowed,
    )
}
