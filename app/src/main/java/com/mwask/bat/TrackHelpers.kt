package com.mwask.bat

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mwask.bat.search.decorateSearchTracks
import com.mwask.bat.backend.api.Browse
import com.mwask.bat.backend.api.HomeFeed
import com.mwask.bat.backend.api.Library
import com.mwask.bat.backend.api.Podcasts
import com.mwask.bat.backend.api.Profile
import com.mwask.bat.backend.api.Search
import com.mwask.bat.backend.api.Settings
import com.mwask.bat.backend.api.WhatsNew
import com.mwask.bat.backend.core.auth.EncoreAuth
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.net.Net
import com.mwask.bat.backend.core.net.Pathfinder
import com.mwask.bat.backend.core.webview.SilentTokenRefresh
import com.mwask.bat.backend.library.Krespot
import com.mwask.bat.backend.library.PlaylistSource
import com.mwask.bat.backend.library.TrackSource
import com.mwask.bat.backend.model.AlbumDetail
import com.mwask.bat.backend.model.ArtistDetail
import com.mwask.bat.backend.model.BrowseCard
import com.mwask.bat.backend.model.BrowsePage
import com.mwask.bat.backend.model.CollectionDetail
import com.mwask.bat.backend.model.Credits
import com.mwask.bat.backend.model.EpisodeDetail
import com.mwask.bat.backend.model.HomeChip
import com.mwask.bat.backend.model.HomeSection
import com.mwask.bat.backend.model.PlaylistChoice
import com.mwask.bat.backend.model.ProfileContext
import com.mwask.bat.backend.model.ProfileTopContent
import com.mwask.bat.backend.model.ProfileUser
import com.mwask.bat.backend.model.SearchHit
import com.mwask.bat.backend.model.SearchKind
import com.mwask.bat.backend.model.SearchResults
import com.mwask.bat.backend.model.ShelfKind
import com.mwask.bat.backend.model.ShowDetail
import com.mwask.bat.backend.model.TrackLyrics
import com.mwask.bat.backend.model.TrackRow
import com.mwask.bat.backend.model.UserProfile
import com.mwask.bat.compose.components.UpdateDialog
import com.mwask.bat.compose.components.UpdateInstallPhase
import com.mwask.bat.compose.format.formatCount
import com.mwask.bat.compose.format.parseHexColor
import com.mwask.bat.compose.home.toArtistRelease
import com.mwask.bat.compose.home.toFavoriteArtistsShelf
import com.mwask.bat.compose.home.toQuickAccess
import com.mwask.bat.compose.home.toQuickAccessFallback
import com.mwask.bat.compose.home.toRecentlyPlayedShelf
import com.mwask.bat.compose.home.toShelves
import com.mwask.bat.compose.home.toTrackItem
import com.mwask.bat.compose.library.LibraryItem
import com.mwask.bat.compose.library.LibraryItemType
import com.mwask.bat.compose.player.TimeSyncedLyric
import com.mwask.bat.compose.playlist.TrackItem
import com.mwask.bat.compose.screens.AppUpdate
import com.mwask.bat.compose.screens.ArtistRelease
import com.mwask.bat.compose.screens.CategoryRecommendationItem
import com.mwask.bat.compose.screens.CategoryShelf
import com.mwask.bat.compose.screens.EncoreNotification
import com.mwask.bat.compose.screens.HistoryStats
import com.mwask.bat.compose.screens.LoginScreen
import com.mwask.bat.compose.screens.PathfinderCaptureScreen
import com.mwask.bat.compose.screens.PopularTrack
import com.mwask.bat.compose.screens.ProfileContextCard
import com.mwask.bat.compose.screens.SettingsLocks
import com.mwask.bat.compose.screens.SettingsSwitches
import com.mwask.bat.compose.search.SearchHistoryItem
import com.mwask.bat.compose.shell.EncoreAppShell
import com.mwask.bat.compose.shell.PlaylistEditDraft
import com.mwask.bat.compose.shell.ShellDeepLink
import com.mwask.bat.compose.theme.EncoreAppTheme
import com.mwask.bat.compose.util.rememberIsOffline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar



private val log = Logs.of(Logs.Module.MAIN)

internal suspend fun decorateSearchTracks(
    context: android.content.Context,
    results: com.mwask.bat.backend.model.SearchResults,
): com.mwask.bat.backend.model.SearchResults {
    fun thin(hit: com.mwask.bat.backend.model.SearchHit) =
        hit.kind == com.mwask.bat.backend.model.SearchKind.TRACK &&
                (hit.imageUrl.isBlank() || hit.subtitle.isBlank())
    val missing = (listOfNotNull(results.top) + results.tracks)
        .filter(::thin)
        .map { it.uri }
        .distinct()
    if (missing.isEmpty()) return results
    val rows = runCatching {
        com.mwask.bat.backend.library.TrackSource.decorate(context, missing)
    }.getOrDefault(emptyMap())
    if (rows.isEmpty()) return results
    fun fill(hit: com.mwask.bat.backend.model.SearchHit): com.mwask.bat.backend.model.SearchHit {
        val row = rows[hit.uri] ?: return hit
        return hit.copy(
            subtitle = hit.subtitle.ifBlank { row.artists },
            imageUrl = hit.imageUrl.ifBlank { row.imageUrl },
            durationMs = row.durationMs.takeIf { it > 0L } ?: hit.durationMs,
            explicit = hit.explicit || row.explicit,
        )
    }
    return results.copy(
        top = results.top?.let(::fill),
        tracks = results.tracks.map(::fill),
    )
}

internal const val FAVORITE_ARTIST_LIMIT = 10

internal const val PLAYLIST_PREFETCH_ROWS = 8

internal data class ArtistStanding(
    val plays: Int,
    val lastPlayedAtMs: Long,
    val latestTrack: TrackItem,
)

/**
 * The history's favorite artists: every artist uri ranked by total plays,
 * the most recent play breaking ties. Each artist is represented by its
 * newest track so the shelf card carries a cover.
 */
internal fun favoriteArtistsOf(history: List<PlayedTrack>): List<TrackItem> =
    history
        .filter { it.item.artistUri.startsWith("spotify:artist:") && it.item.artist.isNotBlank() }
        .groupBy { it.item.artistUri }
        .values
        .map { plays ->
            ArtistStanding(
                plays = plays.sumOf { it.plays },
                lastPlayedAtMs = plays.maxOf { it.playedAtMs },
                latestTrack = plays.maxByOrNull { it.playedAtMs }!!.item,
            )
        }
        .sortedWith(compareByDescending<ArtistStanding> { it.plays }.thenByDescending { it.lastPlayedAtMs })
        .take(FAVORITE_ARTIST_LIMIT)
        .map { it.latestTrack }

internal const val PROFILE_CONTEXT_LIMIT = 12

/**
 * The recently-played feed only carries context uris, so each entry is turned
 * into a displayable card: artists come straight from the profile response,
 * playlists and albums are resolved through their existing sources.
 */
internal suspend fun resolveProfileContexts(
    context: android.content.Context,
    profile: UserProfile?,
    contexts: List<ProfileContext>?,
): List<ProfileContextCard> {
    if (contexts.isNullOrEmpty()) return emptyList()
    val artistByUri = profile?.recentlyPlayedArtists?.associateBy { it.uri }.orEmpty()
    return contexts.take(PROFILE_CONTEXT_LIMIT).mapNotNull { entry ->
        when {
            entry.uri.startsWith("spotify:artist:") -> {
                val artist = artistByUri[entry.uri] ?: return@mapNotNull null
                ProfileContextCard(entry.uri, artist.name, "Artist", artist.imageUrl)
            }
            entry.uri.startsWith("spotify:playlist:") -> {
                val detail = runCatching {
                    Krespot.collections.collection(context, entry.uri)
                }.getOrNull() ?: return@mapNotNull null
                ProfileContextCard(entry.uri, detail.title, "Playlist", detail.imageUrl)
            }
            entry.uri.startsWith("spotify:album:") -> {
                val detail = runCatching {
                    Krespot.albums.album(context, entry.uri)
                }.getOrNull() ?: return@mapNotNull null
                ProfileContextCard(entry.uri, detail.title, detail.artists, detail.imageUrl)
            }
            else -> null
        }
    }
}

internal suspend fun withSavedStates(context: Context, rows: List<TrackItem>): List<TrackItem> {
    val uris = rows.map { it.uri }.filter { it.isNotBlank() }
    if (uris.isEmpty()) return rows
    val liked = Library.saved(context, uris)
    return rows.map { it.copy(saved = it.uri in liked) }
}

internal suspend fun withSavedPopularTracks(context: Context, rows: List<PopularTrack>): List<PopularTrack> {
    val uris = rows.map { it.track.uri }.filter { it.isNotBlank() }
    if (uris.isEmpty()) return rows
    val liked = Library.saved(context, uris)
    return rows.map { popTrack ->
        popTrack.copy(track = popTrack.track.copy(saved = popTrack.track.uri in liked))
    }
}

internal fun toggleArtistTrackLike(
    context: Context,
    scope: CoroutineScope,
    track: TrackItem,
    getTracks: () -> List<PopularTrack>?,
    setTracks: (List<PopularTrack>?) -> Unit,
) {
    val next = track.saved != true
    val swap = { liked: Boolean ->
        setTracks(
            getTracks()?.map { popTrack ->
                if (popTrack.track.uri == track.uri) {
                    popTrack.copy(track = popTrack.track.copy(saved = liked))
                } else {
                    popTrack
                }
            }
        )
    }
    swap(next)
    scope.launch {
        val ok = Library.setSaved(context, listOf(track.uri), next)
        log.i("artist heart '${track.title}' -> $next ok=$ok")
        if (!ok) swap(!next)
    }
}

internal fun toggleAlbumTrackLike(
    context: Context,
    scope: CoroutineScope,
    track: TrackItem,
    getTracks: () -> List<TrackItem>?,
    setTracks: (List<TrackItem>?) -> Unit,
) {
    val next = track.saved != true
    val swap = { liked: Boolean ->
        setTracks(getTracks()?.map { if (it.uri == track.uri) it.copy(saved = liked) else it })
    }
    swap(next)
    scope.launch {
        val ok = Library.setSaved(context, listOf(track.uri), next)
        log.i("album heart '${track.title}' -> $next ok=$ok")
        if (!ok) swap(!next)
    }
}

internal fun togglePlaylistTrackLike(
    context: Context,
    scope: CoroutineScope,
    playlistUri: String,
    track: TrackItem,
    getTracks: () -> List<TrackItem>?,
    setTracks: (List<TrackItem>?) -> Unit,
    getDetail: () -> CollectionDetail?,
    setDetail: (CollectionDetail?) -> Unit,
) {
    val next = track.saved != true
    val isLikedSongs = playlistUri == "spotify:collection:tracks"
    if (!next && isLikedSongs) {
        setTracks(getTracks()?.filterNot { it.uri == track.uri })
        setDetail(getDetail()?.let { it.copy(total = (it.total - 1).coerceAtLeast(0)) })
    } else {
        setTracks(getTracks()?.map { if (it.uri == track.uri) it.copy(saved = next) else it })
    }
    scope.launch {
        val ok = Library.setSaved(context, listOf(track.uri), next)
        log.i("playlist heart '${track.title}' -> $next ok=$ok")
        if (!ok) {
            val fresh = Krespot.collections.collection(context, playlistUri)
            setDetail(fresh)
            setTracks(fresh?.tracks?.map { it.toTrackItem() })
        }
    }
}

internal suspend fun buildPlaylistRecommendations(
    context: Context,
    tracks: List<TrackItem>,
    inPlaylist: Set<String>,
): List<TrackItem> {
    val seedArtists = if (inPlaylist.isNotEmpty()) {
        tracks
            .filter { it.artistUri.startsWith("spotify:artist:") }
            .groupingBy { it.artistUri }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(6)
            .map { it.key }
            .shuffled()
            .take(3)
    } else {
        val candidateArtists = LinkedHashSet<String>()
        val likedDetail = Krespot.collections.collection(context, "spotify:user:@me:collection")
        likedDetail?.tracks?.forEach { t ->
            if (t.artistUri.startsWith("spotify:artist:")) candidateArtists.add(t.artistUri)
        }
        if (candidateArtists.size < 5) {
            val libraryPage = Krespot.library.page(context)
            libraryPage?.items?.forEach { item ->
                if (item.uri.startsWith("spotify:artist:")) {
                    candidateArtists.add(item.uri)
                }
            }
        }
        if (candidateArtists.isNotEmpty()) {
            candidateArtists.shuffled().take(4)
        } else {
            listOf(
                "spotify:artist:4YRxDV8wJFPHPTeXepOstw",
                "spotify:artist:3g2kUQ6tHLLbmkV7T4GPdf",
                "spotify:artist:246dkjvS1zLTtiykYqq8Gv"
            )
        }
    }
    val picks = LinkedHashMap<String, TrackItem>()
    val overviews = coroutineScope {
        seedArtists.map { artist ->
            async { Krespot.artists.artist(context, artist) }
        }.awaitAll()
    }
    for (detail in overviews.filterNotNull()) {
        for (row in detail.topTracks) {
            if (row.uri in inPlaylist || picks.containsKey(row.uri)) continue
            picks[row.uri] = row.toTrackItem()
            if (picks.size >= 10) break
        }
        if (picks.size >= 10) break
    }
    log.i("recommended ${picks.size} client track(s) from ${seedArtists.size} artist(s)")
    return picks.values.toList()
}

internal data class ArtistLoad(
    val detail: ArtistDetail?,
    val tracks: List<PopularTrack>,
    val releases: List<ArtistRelease>,
    val listeners: String?,
    val followed: Boolean?,
)

internal suspend fun loadArtistDetail(context: Context, uri: String): ArtistLoad {
    val detail = Krespot.artists.artist(context, uri)
    var releaseRows = detail?.releases.orEmpty()
    if (releaseRows.isEmpty()) {
        releaseRows = Krespot.artists.discography(context, uri, "all")?.rows.orEmpty()
    }
    val inLibrary = Library.saved(context, listOf(uri))
    return ArtistLoad(
        detail = detail,
        tracks = popularTracks(context, uri, detail?.topTracks.orEmpty()),
        releases = releaseRows.map { it.toArtistRelease() },
        listeners = detail?.monthlyListeners?.takeIf { it > 0L }?.let { formatCount(it) },
        followed = if (inLibrary.isEmpty()) detail?.saved == true else uri in inLibrary,
    )
}

private fun TrackRow.toPopularTrack(): PopularTrack {
    val countFormatted = if (plays > 0L) {
        java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(plays)
    } else ""
    return PopularTrack(toTrackItem(), countFormatted)
}

private suspend fun popularTracks(
    context: Context,
    artistUri: String,
    top: List<TrackRow>,
): List<PopularTrack> {
    val topByUri = top.associateBy { it.uri }
    val order = runCatching { Krespot.artists.popularTrackUris(context, artistUri) }
        .getOrDefault(emptyList())
    if (order.isEmpty()) return top.map { it.toPopularTrack() }
    val missing = order.filterNot { topByUri.containsKey(it) }
    val decorated = if (missing.isEmpty()) emptyMap() else {
        runCatching { TrackSource.decorate(context, missing) }.getOrDefault(emptyMap())
    }
    val rows = order.mapNotNull { uri -> topByUri[uri] ?: decorated[uri] }
        .filter { it.title.isNotBlank() }
    if (rows.size < top.size) return top.map { it.toPopularTrack() }
    return rows.map { it.toPopularTrack() }
}
