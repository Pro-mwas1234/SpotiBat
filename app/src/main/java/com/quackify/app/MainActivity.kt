package com.quackify.app

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
import androidx.activity.SystemBarStyle
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
import com.quackify.app.search.decorateSearchTracks
import com.quackify.backend.api.Browse
import com.quackify.backend.api.ArtistBanStore
import com.quackify.backend.api.Collection2
import com.quackify.backend.api.HomeFeed
import com.quackify.backend.api.Library
import com.quackify.backend.api.Podcasts
import com.quackify.backend.api.Profile
import com.quackify.backend.api.Search
import com.quackify.backend.api.Settings
import com.quackify.backend.api.WhatsNew
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.Net
import com.quackify.backend.core.net.Pathfinder
import com.quackify.backend.core.webview.SilentTokenRefresh
import com.quackify.backend.library.Krespot
import com.quackify.backend.library.PlaylistSource
import com.quackify.backend.library.TrackSource
import com.quackify.backend.model.AlbumDetail
import com.quackify.backend.model.ArtistDetail
import com.quackify.backend.model.BrowseCard
import com.quackify.backend.model.BrowsePage
import com.quackify.backend.model.CollectionDetail
import com.quackify.backend.model.Credits
import com.quackify.backend.model.EpisodeDetail
import com.quackify.backend.model.HomeChip
import com.quackify.backend.model.HomeSection
import com.quackify.backend.model.PlaylistChoice
import com.quackify.backend.model.ProfileContext
import com.quackify.backend.model.ProfileTopContent
import com.quackify.backend.model.ProfileUser
import com.quackify.backend.model.SearchHit
import com.quackify.backend.model.SearchKind
import com.quackify.backend.model.SearchResults
import com.quackify.backend.model.ShelfKind
import com.quackify.backend.model.ShowDetail
import com.quackify.backend.model.TrackLyrics
import com.quackify.backend.model.UserProfile
import com.quackify.compose.components.UpdateDialog
import com.quackify.compose.components.UpdateInstallPhase
import com.quackify.compose.format.formatCount
import com.quackify.compose.format.parseHexColor
import com.quackify.app.widget.WidgetSync
import com.quackify.compose.home.toArtistRelease
import com.quackify.compose.home.toFavoriteArtistsShelf
import com.quackify.compose.home.toQuickAccess
import com.quackify.compose.home.toQuickAccessFallback
import com.quackify.compose.home.toRecentlyPlayedShelf
import com.quackify.compose.home.toShelves
import com.quackify.compose.home.toTrackItem
import com.quackify.compose.library.LibraryItem
import com.quackify.compose.library.LibraryItemType
import com.quackify.compose.player.TimeSyncedLyric
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.screens.AppUpdate
import com.quackify.compose.screens.ArtistRelease
import com.quackify.compose.screens.CategoryRecommendationItem
import com.quackify.compose.screens.CategoryShelf
import com.quackify.compose.screens.EncoreNotification
import com.quackify.compose.screens.HistoryStats
import com.quackify.compose.screens.LoginScreen
import com.quackify.compose.screens.PathfinderCaptureScreen
import com.quackify.compose.screens.PopularTrack
import com.quackify.compose.screens.ProfileContextCard
import com.quackify.compose.screens.SettingsLocks
import com.quackify.compose.screens.SettingsSwitches
import com.quackify.compose.search.SearchHistoryItem
import com.quackify.compose.shell.EncoreAppShell
import com.quackify.compose.shell.ActivityFeedState
import com.quackify.compose.shell.PlaylistEditDraft
import com.quackify.compose.shell.ShellDeepLink
import com.quackify.compose.theme.EncoreAppTheme
import com.quackify.compose.util.rememberIsOffline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import java.util.Calendar

private val log = Logs.of(Logs.Module.MAIN)

class MainActivity : ComponentActivity() {

    internal var canvasResolution by mutableStateOf(1080)
    private val clientPrefs by lazy {
        com.quackify.backend.core.store.AppPrefs.prefs(applicationContext, CLIENT_PREFS)
    }
    private var resumeSignal by mutableStateOf(0)
    private var pendingLink by mutableStateOf<SpotLink?>(null)
    private var videoFullscreen by mutableStateOf(false)

    /** Bumped every time the playback notification's body is tapped; the
     * shell watches it and slides the Now Playing view over whatever is
     * on screen. */
    private var openPlayerSignal by mutableStateOf(0)
    private var pausedAt = 0L
    private var sawStop = false

    private var homeGreeting by mutableStateOf("")
    private var homeFacet by mutableStateOf("")
    internal var loading by mutableStateOf(true)
    internal var playHistory by mutableStateOf<List<PlayedTrack>>(emptyList())
    internal var playStats by mutableStateOf(PlayStatsSnapshot())
    private var searchTerm by mutableStateOf("")
    private var searchLoading by mutableStateOf(false)
    private var searchFailed by mutableStateOf(false)
    private var searchFetch by mutableStateOf(0)
    private var searchDataFor by mutableStateOf("")
    private var recentSearches by mutableStateOf<List<RecentSearches.Entry>>(emptyList())
    private var showUri by mutableStateOf("")
    private var showLoading by mutableStateOf(false)
    private var showFetch by mutableStateOf(0)
    private var episodeUri by mutableStateOf("")
    private var episodeLoading by mutableStateOf(false)
    private var episodeFetch by mutableStateOf(0)
    private var browseUri by mutableStateOf("")
    private var browseFetch by mutableStateOf(0)
    private var categoryUri by mutableStateOf("")
    private var folderUri by mutableStateOf("")
    private var folderFetch by mutableStateOf(0)
    private var settingsSwitches by mutableStateOf(SettingsSwitches())
    private var settingsLocks by mutableStateOf(SettingsLocks())
    private var settingsProduct by mutableStateOf("")
    private var settingsCountry by mutableStateOf("")
    private var settingsLoading by mutableStateOf(false)
    private var whatsNewLoading by mutableStateOf(false)
    private var whatsNewFailed by mutableStateOf(false)
    private var whatsNewFetch by mutableStateOf(0)
    private var savedAccountsList by mutableStateOf<List<EncoreAuth.Account>>(emptyList())
    private var addingAccount by mutableStateOf(false)
    private var userProfileColorHex by mutableStateOf("")
    private var profileLoading by mutableStateOf(false)
    private var profileFailed by mutableStateOf(false)
    private var profileFetch by mutableStateOf(0)
    private var profileUserId by mutableStateOf("")
    private var profileFollowLoading by mutableStateOf(false)
    private var profileFollowFetch by mutableStateOf(0)
    private var playlistUri by mutableStateOf("")
    private var playlistLoading by mutableStateOf(false)
    private var playlistHasMore by mutableStateOf(false)
    private var playlistLoadingMore by mutableStateOf(false)
    private var playlistFetch by mutableStateOf(0)
    private var recommendationsLoading by mutableStateOf(false)
    private var recommendationsNonce by mutableStateOf(0)
    private var playlistCanRemoveCover by mutableStateOf(false)
    private var albumUri by mutableStateOf("")
    private var albumLoading by mutableStateOf(false)
    private var albumFailed by mutableStateOf(false)
    private var albumRetry by mutableStateOf(0)
    private var albumHasMore by mutableStateOf(false)
    private var albumLoadingMore by mutableStateOf(false)
    private var artistUri by mutableStateOf("")
    private var artistLoading by mutableStateOf(false)
    private var artistFailed by mutableStateOf(false)
    private var artistRetry by mutableStateOf(0)
    private var addTracksLoading by mutableStateOf(false)
    private var addTracksFetch by mutableStateOf(0)
    private var sawOffline by mutableStateOf(false)
    private var canvasEnabled by mutableStateOf(true)

    internal var feed by mutableStateOf<List<HomeSection>>(emptyList())
    private var homeChips by mutableStateOf<List<HomeChip>>(emptyList())
    private var searchData by mutableStateOf<SearchResults?>(null)
    private var searchHits by mutableStateOf<List<SearchHit>>(emptyList())
    private val searchSavedOverrides = mutableStateMapOf<String, Boolean>()
    private var submittedSearchQuery by mutableStateOf<String?>(null)
    private var showDetail by mutableStateOf<ShowDetail?>(null)
    private var episodeDetail by mutableStateOf<EpisodeDetail?>(null)
    private var browsePage by mutableStateOf<BrowsePage?>(null)
    private var categoryShelves by mutableStateOf<List<CategoryShelf>?>(null)
    private var browseCategories by mutableStateOf<List<BrowseCard>>(emptyList())
    internal val homeArtistImages = mutableStateMapOf<String, String>()
    internal val artistCache = mutableMapOf<String, ArtistDetail>()
    private var folderItems by mutableStateOf<List<LibraryItem>?>(null)
    private var libraryItems by mutableStateOf<List<LibraryItem>?>(null)
    private var whatsNew by mutableStateOf<List<EncoreNotification>>(emptyList())
    private var profileDetail by mutableStateOf<UserProfile?>(null)
    private var profileTop by mutableStateOf<ProfileTopContent?>(null)
    private var profileTopUnavailable by mutableStateOf(false)
    private var profileContexts by mutableStateOf<List<ProfileContextCard>>(emptyList())
    private var profileFollowers by mutableStateOf<List<ProfileUser>>(emptyList())
    private var profileFollowing by mutableStateOf<List<ProfileUser>>(emptyList())
    private var playlistDetail by mutableStateOf<CollectionDetail?>(null)
    private var playlistTracks by mutableStateOf<List<TrackItem>?>(null)
    private var playlistRecommendations by mutableStateOf<List<TrackItem>>(emptyList())
    private var playlistSaved by mutableStateOf<Boolean?>(null)
    private var playlistEditDraft by mutableStateOf<PlaylistEditDraft?>(null)
    private var editableUris by mutableStateOf<Set<String>>(emptySet())
    private var albumDetail by mutableStateOf<AlbumDetail?>(null)
    private var albumTracks by mutableStateOf<List<TrackItem>?>(null)
    private var artistDetail by mutableStateOf<ArtistDetail?>(null)
    private var trackCredits by mutableStateOf<Credits?>(null)
    private var addToPlaylistChoices by mutableStateOf<List<PlaylistChoice>?>(null)
    private var addTracksSongs by mutableStateOf<List<TrackItem>>(emptyList())
    private var addTracksEpisodes by mutableStateOf<List<TrackItem>>(emptyList())
    private var addTracksLiked by mutableStateOf<List<TrackItem>>(emptyList())
    private var addTracksRecent by mutableStateOf<List<TrackItem>>(emptyList())
    private var artistTracks by mutableStateOf<List<PopularTrack>?>(null)
    private var artistReleases by mutableStateOf<List<ArtistRelease>?>(null)
    private var artistListeners by mutableStateOf<String?>(null)
    private var artistFollowed by mutableStateOf<Boolean?>(null)
    private var artistBanned by mutableStateOf<Boolean?>(null)
    internal var nowPlayingArtist by mutableStateOf<ArtistDetail?>(null)
    internal var nowPlayingArtistFollowed by mutableStateOf<Boolean?>(null)
    internal var nowPlayingLyrics by mutableStateOf<TrackLyrics?>(null)
    internal var canvasFile by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false) == true) {
            openPlayerSignal++
        }
        pendingLink = SpotLinks.parse(intent?.data)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        Pathfinder.silentRefresh = { context, reason -> SilentTokenRefresh.refresh(context, reason) }
        playHistory = PlayHistory.all(applicationContext)
        playStats = PlayStats.snapshot(applicationContext)
        recentSearches = RecentSearches.all(applicationContext)
        savedAccountsList = EncoreAuth.savedAccounts(applicationContext)
        userProfileColorHex = EncoreAuth.account(applicationContext)?.colorHex.orEmpty()
        canvasEnabled = clientPrefs.getBoolean("canvasNpvEnabled", true)
        syncLauncherIcon(applicationContext)
        canvasResolution = clientPrefs.getInt("canvasResolution", 1080)
        val appContext = applicationContext
        Net.onUnauthorized = { sent, reason ->
            val current = EncoreAuth.token(appContext)
            val left = EncoreAuth.tokenRemainingMs(appContext)
            when {
                current.isNotBlank() && current != sent -> {
                    log.i("$reason 401, a newer token is already in the store")
                    current
                }
                left > TOKEN_STILL_GOOD_MS -> {
                    log.w("$reason 401 with ${left / 1000}s left, not spending a refresh on it")
                    null
                }
                SilentTokenRefresh.refresh(appContext, reason) -> {
                    EncoreAuth.token(appContext).takeIf { it.isNotBlank() && it != sent }
                }
                else -> {
                    log.w("$reason 401 and the silent refresh failed, giving up on this call")
                    null
                }
            }
        }
        Library.onLibraryChanged = { Krespot.library.invalidate() }
        setContent {
            EncoreAppTheme {
                val context = LocalContext.current
                var loggedIn by remember { mutableStateOf(EncoreAuth.isLoggedIn(context)) }


                if (loggedIn) {
                    var capturing by remember {
                        val tokenUsable = EncoreAuth.token(context).isNotBlank() &&
                            !EncoreAuth.tokenExpired(context)
                        mutableStateOf(Pathfinder.shouldCapture(context, tokenUsable))
                    }
                    remember(loggedIn, capturing) {
                        val ttl = EncoreAuth.tokenRemainingMs(context)
                            .let { if (it < 0L) "unknown" else "${it / 1000}s" }
                        log.v("composition loggedIn=$loggedIn capturing=$capturing " +
                                "ops=${Pathfinder.count()} token=$ttl")
                        true
                    }
                    if (capturing) {
                        PathfinderCaptureScreen(onDone = {
                            log.i("capture finished ops=${Pathfinder.count()}")
                            capturing = false
                        })
                    } else {
                        LaunchedEffect(Unit) {
                            playStats = PlayStats.recordLogin(context)
                        }
                        LaunchedEffect(resumeSignal, homeFacet) {
                            if (resumeSignal == 0) loading = true
                            val payload = HomeFeed.load(context, homeFacet)
                            if (payload.sections.isNotEmpty() || resumeSignal == 0) {
                                feed = payload.sections
                                homeGreeting = payload.greeting
                            }
                            if (payload.chips.isNotEmpty()) homeChips = payload.chips
                            loading = false
                            log.i("home feed loaded facet='$homeFacet' sections=${feed.size} " +
                                    "chips=${homeChips.size} signal=$resumeSignal"
                            )
                        }
                        LaunchedEffect(resumeSignal) {
                            if (resumeSignal > 0) SilentTokenRefresh.maybeRefresh(context)
                        }
                        val account = remember(loggedIn) { EncoreAuth.account(context) }
                        LaunchedEffect(feed) {
                            feed.forEach { section ->
                                section.items.forEach { item ->
                                    if (item.uri.startsWith("spotify:artist:") && item.imageUrl.isNotBlank()) {
                                        homeArtistImages[item.uri] = item.imageUrl
                                    }
                                }
                            }
                        }
                        LaunchedEffect(playHistory, loading) {
                            if (loading) return@LaunchedEffect
                            favoriteArtistsOf(playHistory).forEach { artist ->
                                val uri = artist.artistUri
                                if (homeArtistImages.containsKey(uri)) return@forEach
                                val fetched = runCatching {
                                    Search.section(context, com.quackify.backend.model.SearchKind.ARTIST, artist.artist.substringBefore(", "))
                                        .firstOrNull { it.uri == uri }
                                }.getOrNull()?.imageUrl?.takeIf { it.isNotBlank() }
                                if (fetched != null) homeArtistImages[uri] = fetched
                            }
                        }

                        val playlistNames = remember { java.util.concurrent.ConcurrentHashMap<String, String>() }
                        val scope = rememberCoroutineScope()


                        LaunchedEffect(Unit) {
                            browseCategories = Browse.all(context)
                            log.i("browse categories: ${browseCategories.size}")
                        }

                        LaunchedEffect(categoryUri) {
                            if (categoryUri.isBlank()) return@LaunchedEffect
                            val page = Browse.page(context, categoryUri)
                            categoryShelves = page?.sections?.map { section ->
                                CategoryShelf(
                                    title = section.title,
                                    items = section.cards.map { card ->
                                        CategoryRecommendationItem(
                                            title = card.title,
                                            subtitle = card.subtitle,
                                            imageUrl = card.imageUrl.takeIf { it.isNotBlank() },
                                            coverColor = parseHexColor(card.colorHex) ?: Color(0xFF282828),
                                            uri = card.uri,
                                            isAlbum = card.uri.startsWith("spotify:album:"),
                                        )
                                    },
                                )
                            }
                        }


                        LaunchedEffect(Unit) {
                            settingsLoading = true
                            val attrs = Settings.attributes(context)
                            if (attrs != null) {
                                settingsSwitches = SettingsSwitches(
                                    musicVideos = !attrs.musicVideoDisabled,
                                    canvas = !attrs.canvasDisabled,
                                    otherVideos = !attrs.otherVideoDisabled,
                                )
                                settingsLocks = SettingsLocks(
                                    musicVideos = attrs.musicVideoLocked,
                                    canvas = attrs.canvasLocked,
                                    otherVideos = attrs.otherVideoLocked,
                                )
                                settingsProduct = attrs.product
                                settingsCountry = attrs.country
                            }
                            val privacy = Settings.privacy(context, account?.id.orEmpty())
                            if (privacy != null) {
                                settingsSwitches = settingsSwitches.copy(
                                    showFollows = privacy.showFollows,
                                    showPlaylists = privacy.showPublishedPlaylists,
                                )
                            }
                            settingsLoading = false
                        }

                        val activeAccountId = remember { EncoreAuth.activeAccountId(context) }
                        LaunchedEffect(Unit) {
                            if (EncoreAuth.activeAccountId(context).isBlank()) {
                                EncoreAuth.userId(context)
                                EncoreAuth.storeCurrentAccount(context)
                                savedAccountsList = EncoreAuth.savedAccounts(context)
                            }
                            val missing = savedAccountsList.filter { it.colorHex.isBlank() }
                            if (missing.isNotEmpty()) {
                                log.i("backfilling profile colors for ${missing.size} account(s)")
                                missing.forEach { saved ->
                                    val color = runCatching {
                                        Profile.profile(context, saved.id)
                                    }.getOrNull()?.colorHex.orEmpty()
                                    if (color.isNotBlank()) {
                                        EncoreAuth.saveAccountColor(context, saved.id, color)
                                        log.i("profile color backfilled for '${saved.name}' -> $color")
                                    }
                                }
                                savedAccountsList = EncoreAuth.savedAccounts(context)
                            }
                        }

                        LaunchedEffect(whatsNewFetch) {
                            whatsNewLoading = true
                            val feed = WhatsNew.feed(context)
                            whatsNewFailed = feed == null
                            whatsNew = feed?.items.orEmpty().map { item ->
                                EncoreNotification(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    timestamp = item.timestamp,
                                    isMusic = item.isMusic,
                                    id = item.id,
                                    isNew = item.isNew,
                                    imageUrl = item.imageUrl,
                                    uri = item.uri,
                                    kind = item.kind,
                                )
                            }
                            whatsNewLoading = false
                        }

                        LaunchedEffect(profileFetch) {
                            if (profileFetch == 0) return@LaunchedEffect
                            profileLoading = true
                            profileFailed = false
                            coroutineScope {
                                val target = profileUserId
                                val profile = async { runCatching { Profile.profile(context, target) }.getOrNull() }.await()
                                val isSelf = target.isBlank() || profile?.isCurrentUser == true
                                val (top, recent) = if (isSelf) {
                                    val topJob = async { runCatching { Profile.topContent(context) }.getOrNull() }
                                    val recentJob = async { runCatching { Profile.recentlyPlayed(context) }.getOrNull() }
                                    topJob.await() to recentJob.await()
                                } else {
                                    val topJob = async {
                                        runCatching { Profile.topContent(context, target) }.getOrNull()
                                    }
                                    topJob.await() to null
                                }
                                profileDetail = profile
                                if (isSelf && !profile?.colorHex.isNullOrBlank()) {
                                    userProfileColorHex = profile!!.colorHex
                                    EncoreAuth.saveProfileColor(context, profile!!.colorHex)
                                }
                                profileTop = top
                                profileTopUnavailable = !isSelf && top == null
                                profileContexts = if (isSelf) {
                                    resolveProfileContexts(context, profile, recent)
                                } else {
                                    profile?.publicPlaylists.orEmpty().map {
                                        ProfileContextCard(
                                            uri = it.uri,
                                            title = it.name,
                                            subtitle = "Playlist",
                                            imageUrl = it.imageUrl,
                                        )
                                    }
                                }
                                profileFailed = profile == null && top == null
                                log.i("profile loaded self=$isSelf failed=${profile == null} " +
                                        "topArtists=${top?.topArtists?.size ?: 0} " +
                                        "topUnavailable=${profileTopUnavailable} " +
                                        "contexts=${profileContexts.size}"
                                )
                            }
                            profileLoading = false
                        }

                        LaunchedEffect(profileFollowFetch) {
                            if (profileFollowFetch == 0) return@LaunchedEffect
                            profileFollowLoading = true
                            coroutineScope {
                                val id = profileUserId
                                val followersJob = async { runCatching { Profile.followers(context, id) }.getOrNull() }
                                val followingJob = async { runCatching { Profile.following(context, id) }.getOrNull() }
                                profileFollowers = followersJob.await().orEmpty()
                                profileFollowing = followingJob.await().orEmpty()
                            }
                            profileFollowLoading = false
                        }

                        val offlineNow = rememberIsOffline()
                        LaunchedEffect(offlineNow) {
                            if (offlineNow) {
                                sawOffline = true
                            } else if (sawOffline) {
                                sawOffline = false
                                log.i("network is back, retrying the screens that came back empty")
                                if (feed.isEmpty()) resumeSignal++
                                if (libraryItems.isNullOrEmpty()) Krespot.library.invalidate()
                                if (whatsNew.isEmpty()) whatsNewFetch++
                            }
                        }

                        LaunchedEffect(addTracksFetch) {
                            // keyed on the sheet opening only, so opening a playlist does not fetch every library playlist
                            val uri = playlistUri
                            if (uri.isBlank()) return@LaunchedEffect
                            addTracksLoading = true
                            try {
                                coroutineScope {
                                    val likedDeferred = async {
                                        Krespot.collections.collection(context, "spotify:collection:tracks")
                                    }
                                    val pageDeferred = async { Krespot.library.page(context) }
                                    addTracksLiked = likedDeferred.await()
                                        ?.tracks?.map { it.toTrackItem() }
                                        .orEmpty()
                                    val page = pageDeferred.await()
                                    addTracksEpisodes = run {
                                        val libraryEpisodes = page?.items
                                            ?.filter { it.uri.startsWith("spotify:episode:") }
                                            ?.map {
                                                TrackItem(
                                                    title = it.title,
                                                    artist = it.subtitle,
                                                    uri = it.uri,
                                                    imageUrl = it.imageUrl.takeIf { src -> src.isNotBlank() },
                                                )
                                            }
                                            .orEmpty()
                                        val seenEpisodes = libraryEpisodes.map { it.uri }.toHashSet()
                                        libraryEpisodes + PlayHistory.all(context)
                                            .filter {
                                                it.item.uri.startsWith("spotify:episode:") &&
                                                        seenEpisodes.add(it.item.uri)
                                            }
                                            .map { it.item }
                                    }
                                    val sourceUris = page?.items
                                        ?.filter { it.kind == ShelfKind.PLAYLIST && it.uri != uri }
                                        ?.map { it.uri }
                                        .orEmpty()
                                    val seen = HashSet<String>()
                                    addTracksSongs = sourceUris
                                        .map { async { Krespot.collections.collection(context, it) } }
                                        .awaitAll()
                                        .asSequence()
                                        .flatMap { it?.tracks.orEmpty().asSequence() }
                                        .filter { seen.add(it.uri) }
                                        .map { it.toTrackItem() }
                                        .toList()
                                    addTracksRecent = PlayHistory.all(context).map { it.item }
                                }
                            } finally {
                                addTracksLoading = false
                            }
                        }

                        LaunchedEffect(playlistUri, playlistFetch) {
                            if (playlistUri.isBlank()) return@LaunchedEffect
                            val uri = playlistUri
                            playlistLoading = true
                            val detail = Krespot.collections.collection(context, uri)
                            if (uri != playlistUri) return@LaunchedEffect
                            playlistDetail = detail
                            playlistTracks = detail?.tracks?.map { it.toTrackItem() }
                            playlistHasMore = detail?.hasMore == true
                            playlistLoading = false
                            playlistSaved = Krespot.rootlist.saved(context, uri)
                            playlistCanRemoveCover = false
                            val editable = detail?.editable == true
                            coroutineScope {
                                val saved = async { Krespot.rootlist.saved(context, uri) }
                                val cover = if (editable) {
                                    async { Krespot.playlists.hasCustomCover(context, uri) }
                                } else null
                                playlistSaved = saved.await()
                                playlistCanRemoveCover = cover?.await() ?: false
                            }
                        }

                        LaunchedEffect(playlistUri, playlistTracks, recommendationsNonce) {
                            val tracks = playlistTracks
                            if (!playlistUri.startsWith("spotify:playlist:") || tracks == null) {
                                playlistRecommendations = emptyList()
                                return@LaunchedEffect
                            }
                            val inPlaylist = tracks.map { it.uri }
                                .filter { it.startsWith("spotify:track:") }
                                .toHashSet()
                            recommendationsLoading = true
                            try {
                                playlistRecommendations = buildPlaylistRecommendations(context, tracks, inPlaylist)
                            } finally {
                                recommendationsLoading = false
                            }
                        }

                        LaunchedEffect(albumUri, albumRetry) {
                            if (albumUri.isBlank()) {
                                albumDetail = null
                                albumTracks = null
                                albumLoading = false
                                albumFailed = false
                                return@LaunchedEffect
                            }
                            val uri = albumUri
                            albumDetail = null
                            albumTracks = null
                            albumFailed = false
                            albumHasMore = false
                            albumLoading = true
                            try {
                                val detail = Krespot.albums.album(context, uri)
                                if (uri != albumUri) return@LaunchedEffect
                                albumDetail = detail
                                albumTracks = detail?.tracks?.map { it.toTrackItem() }
                                    ?.let { withSavedStates(context, it) }
                                albumHasMore = detail != null &&
                                    detail.fetched < detail.total && detail.tracks.isNotEmpty()
                                albumFailed = detail == null
                            } finally {
                                if (uri == albumUri) albumLoading = false
                            }
                        }

                        LaunchedEffect(artistUri, artistRetry) {
                            if (artistUri.isBlank()) {
                                artistDetail = null
                                artistTracks = null
                                artistReleases = null
                                artistListeners = null
                                artistFollowed = null
                                artistBanned = null
                                artistLoading = false
                                artistFailed = false
                                return@LaunchedEffect
                            }
                            val uri = artistUri
                            artistDetail = null
                            artistTracks = null
                            artistReleases = null
                            artistListeners = null
                            artistFollowed = null
                            artistBanned = null
                            artistFailed = false
                            artistLoading = true
                            try {
                                val load = loadArtistDetail(context, uri)
                                if (uri != artistUri) return@LaunchedEffect
                                artistDetail = load.detail
                                artistFailed = load.detail == null
                                artistTracks = load.tracks
                                artistReleases = load.releases
                                artistListeners = load.listeners
                                artistFollowed = load.followed
                                artistBanned = runCatching { Collection2.artistBanned(context, uri) }.getOrNull()
                            } finally {
                                artistLoading = false
                            }
                        }

        val libraryRevision by Krespot.library.revision.collectAsState()

        LaunchedEffect(libraryRevision) {
            val page = Krespot.library.page(context)
            libraryItems = page?.items?.map { item ->
                LibraryItem(
                    title = item.title,
                    subtitle = item.subtitle,
                    type = when (item.kind) {
                        ShelfKind.ARTIST -> LibraryItemType.ARTIST
                        ShelfKind.ALBUM -> LibraryItemType.ALBUM
                        ShelfKind.FOLDER -> LibraryItemType.FOLDER
                        ShelfKind.SHOW, ShelfKind.EPISODE -> LibraryItemType.PODCAST
                        else -> LibraryItemType.PLAYLIST
                    },
                    uri = item.uri,
                    imageUrl = item.imageUrl.takeIf { it.isNotBlank() },
                    isPinned = item.pinned,
                    isPinnable = item.pinnable,
                )
            }
            editableUris = Krespot.playlists.editable(context, "").map { it.uri }.toSet()
        }

                        LaunchedEffect(artistUri, artistTracks?.size, libraryRevision) {
                            val rows = artistTracks ?: return@LaunchedEffect
                            if (artistUri.isBlank()) return@LaunchedEffect
                            artistTracks = withSavedPopularTracks(context, rows)
                        }

                        LaunchedEffect(folderUri, folderFetch) {
                            if (folderUri.isBlank()) return@LaunchedEffect
                            val page = Krespot.library.folder(context, folderUri)
                            folderItems = page?.items?.map { item ->
                                LibraryItem(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    type = when {
                                        item.uri.startsWith("spotify:user:") -> LibraryItemType.FOLDER
                                        item.uri.startsWith("spotify:artist:") -> LibraryItemType.ARTIST
                                        item.uri.startsWith("spotify:album:") -> LibraryItemType.ALBUM
                                        item.uri.startsWith("spotify:show:") -> LibraryItemType.PODCAST
                                        else -> LibraryItemType.PLAYLIST
                                    },
                                    uri = item.uri,
                                    imageUrl = item.imageUrl.takeIf { it.isNotBlank() },
                                    isPinned = item.pinned,
                                    isPinnable = item.pinnable,
                                )
                            }
                        }

                        LaunchedEffect(showUri, showFetch) {
                            if (showUri.isBlank()) return@LaunchedEffect
                            val uri = showUri
                            showLoading = true
                            val detail = Podcasts.show(context, uri)
                            if (uri != showUri) return@LaunchedEffect
                            showDetail = detail
                            showLoading = false
                        }

                        LaunchedEffect(episodeUri, episodeFetch) {
                            if (episodeUri.isBlank()) return@LaunchedEffect
                            val uri = episodeUri
                            episodeLoading = true
                            val detail = Podcasts.episode(context, uri)
                            if (uri != episodeUri) return@LaunchedEffect
                            episodeDetail = detail
                            episodeLoading = false
                        }

                        LaunchedEffect(browseUri, browseFetch) {
                            if (browseUri.isBlank()) return@LaunchedEffect
                            browsePage = Browse.page(context, browseUri)
                        }

                        LaunchedEffect(loggedIn, pendingLink) {
                            val link = pendingLink ?: return@LaunchedEffect
                            if (link.kind != "track" && link.kind != "station") return@LaunchedEffect
                            pendingLink = null
                            PlaybackHost.bridge?.listener?.onPlayUri(link.uri)
                        }

                        val hostGeneration by PlaybackHost.generation
                        val bridge = remember(hostGeneration) { PlaybackHost.ensure(context) }
                        val playback = bridge.state

                        LaunchedEffect(playback.videoActive) {
                            if (!playback.videoActive && videoFullscreen) {
                                videoFullscreen = false
                                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }

                        LaunchedEffect(videoFullscreen) {
                            val controller = WindowCompat.getInsetsController(window, window.decorView)
                            if (videoFullscreen) {
                                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                controller.hide(WindowInsetsCompat.Type.systemBars())
                            } else {
                                controller.show(WindowInsetsCompat.Type.systemBars())
                            }
                        }
                        LaunchedEffect(settingsSwitches.musicVideos, settingsSwitches.otherVideos) {
                            bridge.setVideoPolicy(settingsSwitches.musicVideos, settingsSwitches.otherVideos)
                        }
                        LaunchedEffect(playlistTracks) {
                            val rows = playlistTracks.orEmpty()
                                .take(PLAYLIST_PREFETCH_ROWS)
                                .mapNotNull { it.uri.takeIf { uri -> uri.startsWith("spotify:track:") } }
                            if (rows.isNotEmpty()) bridge.prefetchUris(rows)
                        }
                        LaunchedEffect(bridge) {
                            log.i("starting playback bridge device=${bridge.deviceId}")
                            PlaybackHost.startService(context)
                        }

                        LaunchedEffect(playback.trackUri, playback.title, playback.artistUri) {
                            val now = playback
                            val addable = now.trackUri.startsWith("spotify:track:") ||
                                    now.trackUri.startsWith("spotify:episode:")
                            if (!addable || now.title.isBlank()) {
                                return@LaunchedEffect
                            }
                            val cover = now.coverUrl.takeIf { it.isNotBlank() }
                            val head = playHistory.firstOrNull()?.item
                            if (head?.uri == now.trackUri && head.title == now.title &&
                                head.artistUri == now.artistUri && head.imageUrl == cover
                            ) {
                                return@LaunchedEffect
                            }
                            playHistory = recordNowPlaying(
                                now.trackUri, now.title, now.artist, now.artistUri,
                                now.albumUri, cover.orEmpty(), now.contextUri, now.contextLabel,
                                now.explicit, playlistNames,
                            )
                            playStats = PlayStats.snapshot(context)
                        }

                        val historyAlbumAttempts = remember { mutableSetOf<String>() }
                        LaunchedEffect(playHistory) {
                            val missing = playHistory
                                .map { it.item }
                                .filter {
                                    it.albumUri.isBlank() &&
                                        it.uri.startsWith("spotify:track:") &&
                                        it.uri !in historyAlbumAttempts
                                }
                                .map { it.uri }
                                .distinct()
                                .take(50)
                            if (missing.isEmpty()) return@LaunchedEffect
                            historyAlbumAttempts += missing
                            val rows = runCatching {
                                TrackSource.decorate(context, missing)
                            }.getOrNull().orEmpty()
                            if (rows.isEmpty()) return@LaunchedEffect
                            playHistory = PlayHistory.applyAlbums(context, rows)
                        }

                        LaunchedEffect(playback.isPlaying) {
                            if (!playback.isPlaying) return@LaunchedEffect
                            while (true) {
                                delay(10_000L)
                                PlayStats.addPlayedMs(context, 10_000L)
                                playStats = PlayStats.snapshot(context)
                            }
                        }


                        LaunchedEffect(searchTerm, searchFetch, offlineNow) {
                            val term = searchTerm.trim()
                            if (term.isEmpty()) {
                                searchData = null
                                searchHits = emptyList()
                                searchLoading = false
                                searchFailed = false
                                searchSavedOverrides.clear()
                                return@LaunchedEffect
                            }
                            if (offlineNow) {
                                searchLoading = false
                                searchFailed = false
                                if (searchDataFor != term) {
                                    searchHits = emptyList()
                                    searchData = null
                                }
                                return@LaunchedEffect
                            }
                            searchLoading = true
                            searchFailed = false
                            val searched = runSearch(term)
                            searchHits = searched.second
                            searchData = searched.first
                            searchDataFor = term
                            searchSavedOverrides.clear()
                            searchFailed = searched.first == null
                            searchLoading = false
                            if (searched.first != null) {
                                log.i("searched '$term'")
                            }
                        }

                        val updates = rememberUpdateController()

                        UpdateEffects(updates)

                        ShellSection(
                            bridge = bridge,
                            account = account,
                            context = context,
                            loggedIn = loggedIn,
                            activeAccountId = activeAccountId,
                            onSignedOut = { loggedIn = false },
                            updates = updates,
                        )

                        if (addingAccount) {
                            LoginScreen(
                                onLoggedIn = {
                                    log.i("add account completed")
                                    finishAddAccount()
                                },
                                isDuplicateAccount = { candidate ->
                                    candidate.id.isNotBlank() &&
                                        savedAccountsList.any { it.id == candidate.id }
                                },
                                onDuplicateLogin = { duplicate ->
                                    refuseDuplicateAccount(duplicate.id, duplicate.name, activeAccountId)
                                },
                                onBackExhausted = {
                                    log.i("add account cancelled, restoring the active session")
                                    EncoreAuth.applySession(context)
                                    addingAccount = false
                                }
                            )
                        }
                    }
                } else {
                    LoginScreen(
                        onLoggedIn = {
                            log.i("login completed")
                            finishLogin()
                            loggedIn = true
                        },
                        onBackExhausted = {
                            if (handleLoginBack()) {
                                loggedIn = true
                            } else {
                                (context as? Activity)?.finish()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        WidgetSync.start(applicationContext)
    }

    override fun onPause() {
        super.onPause()
        pausedAt = System.currentTimeMillis()
        sawStop = false
    }

    override fun onStop() {
        super.onStop()
        sawStop = true
        lifecycleScope.launch {
            WidgetSync.refresh(applicationContext)
        }
    }

    override fun onDestroy() {
        if (isChangingConfigurations) {
            super.onDestroy()
            return
        }
        if (sawStop && PlaybackHost.started && isFinishing) {
            log.i("activity finished by the user, handing the stream back and stopping the service")
            PlaybackHost.teardown(this)
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        val away = if (pausedAt == 0L) 0L else System.currentTimeMillis() - pausedAt
        pausedAt = 0L
        if (away > RESUME_REFRESH_AFTER_MS) {
            log.i("back after ${away / 1000}s, refreshing")
            resumeSignal++
        }
    }


    private suspend fun loadMorePlaylistTracks(currentSize: Int): Pair<List<TrackItem>, Boolean>? {
        val page = Krespot.collections.collectionMore(applicationContext, playlistUri, currentSize)
        if (page == null) return null
        log.i("playlist more '$playlistUri' -> ${page.rows.size} rows hasMore=${page.hasMore}")
        return page.rows.map { it.toTrackItem() } to page.hasMore
    }

    private suspend fun loadMoreAlbumTracks(currentSize: Int, coverUrl: String): Pair<List<TrackItem>, Boolean>? {
        val page = Krespot.albums.albumMore(applicationContext, albumUri, currentSize, albumDetail?.imageUrl.orEmpty())
        if (page == null) return null
        val rows = page.rows.map { it.toTrackItem() }
        log.i("album more '$albumUri' -> ${rows.size} rows hasMore=${page.hasMore}")
        return withSavedStates(applicationContext, rows) to page.hasMore
    }

    private suspend fun addRecommendedToPlaylist(trackUri: String, trackTitle: String): CollectionDetail? {
        val ok = Krespot.playlists.addTrack(applicationContext, playlistUri, trackUri)
        log.i("recommend '$trackTitle' into playlist ok=$ok")
        if (!ok) return null
        return Krespot.collections.collection(applicationContext, playlistUri)
    }

    private suspend fun savePlaylistOrderMoves(
        before: List<TrackItem>,
        uri: String,
        target: List<String>,
    ): Int {
        if (target.any { it.isBlank() }) {
            log.w("playlist order save '$uri': missing uids, skipping")
            return -1
        }
        val current = before.map { it.uid }.toMutableList()
        var moved = 0
        for (i in target.indices) {
            if (current.getOrNull(i) == target[i]) continue
            val anchor = current.getOrNull(i)
            if (anchor.isNullOrBlank()) continue
            if (!Krespot.playlists.moveItems(
                    applicationContext, uri, listOf(target[i]),
                    PlaylistSource.Position.BeforeUid(anchor),
                )
            ) continue
            current.remove(target[i])
            current.add(i, target[i])
            moved++
        }
        log.i("playlist order save '$uri' -> $moved move(s) of ${target.size}")
        return moved
    }

    private suspend fun fetchCredits(uri: String): Credits? =
        if (uri.isBlank()) null else Krespot.albums.credits(applicationContext, uri)

    private suspend fun fetchEditablePlaylists(trackUri: String): List<PlaylistChoice> =
        if (trackUri.isBlank()) emptyList() else Krespot.playlists.editable(applicationContext, trackUri)

    private suspend fun addToPlaylistTarget(choice: PlaylistChoice, trackUri: String, trackTitle: String) {
        if (choice.uri == PlaylistChoice.LIKED_SONGS_URI) {
            val saved = Library.setSaved(applicationContext, listOf(trackUri), true)
            log.i("add '$trackTitle' to Liked Songs ok=$saved")
        } else {
            val added = Krespot.playlists.addTrack(applicationContext, choice.uri, trackUri)
            log.i("add '$trackTitle' to '${choice.name}' ok=$added")
        }
    }

    private suspend fun togglePlaylistMembership(
        choice: PlaylistChoice,
        trackUri: String,
        trackTitle: String,
        shouldAdd: Boolean,
    ) {
        if (choice.uri == PlaylistChoice.LIKED_SONGS_URI) {
            val host = PlaybackHost.bridge
            if (trackUri == host?.state?.trackUri && host.state.isLiked != shouldAdd) {
                host.listener.onLikeToggle()
            } else {
                val saved = Library.setSaved(applicationContext, listOf(trackUri), shouldAdd)
                log.i("${if (shouldAdd) "add" else "remove"} '$trackTitle' to/from Liked Songs ok=$saved")
            }
        } else {
            val res = if (shouldAdd) {
                Krespot.playlists.addTrack(applicationContext, choice.uri, trackUri)
            } else {
                Krespot.playlists.removeTrack(applicationContext, choice.uri, trackUri)
            }
            log.i("${if (shouldAdd) "add" else "remove"} '$trackTitle' to/from '${choice.name}' ok=$res")
        }
    }

    private suspend fun createPlaylistWithTrack(trackUri: String) {
        val name = Krespot.playlists.defaultName(applicationContext)
        val uri = Krespot.playlists.create(applicationContext, name, trackUri)
        log.i("create playlist '$name' ok=${uri != null}")
        if (uri != null) Krespot.library.invalidate()
    }

    private suspend fun setPlaylistSaved(uri: String, saved: Boolean): Boolean {
        val ok = Krespot.rootlist.setSaved(applicationContext, uri, saved)
        log.i("playlist '$uri' in library -> $saved ok=$ok")
        if (ok) Krespot.library.invalidate()
        return ok
    }

    private suspend fun removeTrackFromPlaylist(pUri: String, tUri: String): Boolean {
        val ok = Krespot.playlists.removeTrack(applicationContext, pUri, tUri)
        log.i("remove track '$tUri' from playlist '$pUri' ok=$ok")
        return ok
    }

    private suspend fun removeFromLibrary(uri: String) {
        val ok = if (uri.startsWith("spotify:playlist:")) {
            Krespot.rootlist.remove(applicationContext, uri)
        } else {
            Library.setSaved(applicationContext, listOf(uri), false)
        }
        log.i("remove '$uri' from library ok=$ok")
    }

    private suspend fun setLibraryItemPinned(uri: String, pinned: Boolean) {
        val ok = Krespot.library.setPinned(applicationContext, uri, pinned)
        log.i("pin '$uri' -> $pinned ok=$ok")
        if (!ok) {
            Toast.makeText(
                applicationContext,
                "Couldn't update the pin",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private suspend fun renameFolder(uri: String, name: String) {
        val ok = Krespot.folders.renameFolder(applicationContext, uri, name)
        log.i("rename folder '$uri' -> '$name' ok=$ok")
    }

    private suspend fun deleteFolder(uri: String) {
        val ok = Krespot.folders.deleteFolder(applicationContext, uri)
        log.i("delete folder '$uri' ok=$ok")
    }

    private suspend fun moveTrackToFolder(playlistUri: String, targetFolderUri: String): Boolean {
        val ok = if (targetFolderUri.isBlank()) {
            Krespot.folders.removeFromFolder(applicationContext, playlistUri)
        } else {
            Krespot.folders.moveIntoFolder(applicationContext, playlistUri, targetFolderUri)
        }
        log.i("move '$playlistUri' into '$targetFolderUri' ok=$ok")
        return ok
    }

    private suspend fun setArtistFollow(uri: String, follow: Boolean): Boolean {
        val ok = Library.setSaved(applicationContext, listOf(uri), follow)
        log.i("artist follow '$uri' -> $follow ok=$ok")
        return ok
    }

    private suspend fun toggleSearchSaved(uri: String, next: Boolean): Boolean {
        val ok = Library.setSaved(applicationContext, listOf(uri), next)
        log.i("search heart '$uri' -> $next ok=$ok")
        return ok
    }

    private suspend fun recordNowPlaying(
        trackUri: String,
        title: String,
        artist: String,
        artistUri: String,
        albumUri: String,
        coverUrl: String,
        contextUri: String,
        contextLabel: String,
        explicit: Boolean,
        playlistNames: java.util.concurrent.ConcurrentHashMap<String, String>,
    ): List<PlayedTrack> = withContext(Dispatchers.Default) {
        PlayStats.recordListen(applicationContext)
        PlayStats.tallyArtist(applicationContext, artistUri, artist)
        if (contextUri.startsWith("spotify:playlist:")) {
            val resolved = contextLabel.ifBlank {
                playlistNames[contextUri] ?: runCatching {
                    Krespot.collections.collection(applicationContext, contextUri)?.title
                }.getOrNull()?.trim().orEmpty().also { name ->
                    if (name.isNotBlank()) playlistNames[contextUri] = name
                }
            }
            PlayStats.tallyPlaylist(applicationContext, contextUri, resolved.ifBlank { "Playlist" })
        }
        PlayHistory.record(
            applicationContext,
            TrackItem(
                title = title,
                artist = artist,
                uri = trackUri,
                imageUrl = coverUrl,
                artistUri = artistUri,
                albumUri = albumUri,
                isExplicit = explicit,
            ),
            System.currentTimeMillis(),
        )
    }

    private suspend fun runSearch(term: String): Pair<com.quackify.backend.model.SearchResults?, List<SearchHit>> {
        delay(280)
        val hits = Search.suggest(applicationContext, term)
        val results = Search.query(applicationContext, term)
        return results?.let { decorateSearchTracks(applicationContext, it) } to hits
    }

    internal fun buildHistoryStats(stats: PlayStatsSnapshot, history: List<PlayedTrack>): HistoryStats {
        val replayed = history.filter { it.plays > 1 }.maxByOrNull { it.plays }
        return HistoryStats(
            totalPlayedMs = stats.totalMs,
            listenStreakDays = stats.listenStreak,
            loginStreakDays = stats.loginStreak,
            topArtistName = stats.topArtistName,
            topArtistPlays = stats.topArtistPlays,
            topPlaylistName = stats.topPlaylistName,
            topPlaylistPlays = stats.topPlaylistPlays,
            topReplayTitle = replayed?.item?.title.orEmpty(),
            topReplayArtist = replayed?.item?.artist.orEmpty(),
            topReplayPlays = replayed?.plays ?: 0,
        )
    }

    private suspend fun buildPlaylistEditDraft(
        uri: String,
        loaded: CollectionDetail?,
        canRemoveCover: Boolean,
    ): PlaylistEditDraft? {
        return loaded?.let {
            PlaylistEditDraft(
                uri = uri,
                name = it.title,
                description = it.description,
                coverUrl = it.imageUrl.takeIf { src -> src.isNotBlank() },
                canRemoveCover = canRemoveCover,
            )
        } ?: Krespot.collections.collection(applicationContext, uri)?.let {
            PlaylistEditDraft(
                uri = uri,
                name = it.title,
                description = it.description,
                coverUrl = it.imageUrl.takeIf { src -> src.isNotBlank() },
                canRemoveCover = if (it.editable) Krespot.playlists.hasCustomCover(applicationContext, uri) else false,
            )
        }
    }

    private suspend fun editPlaylistDetails(
        uri: String,
        name: String,
        description: String,
        cover: ByteArray?,
        clearCover: Boolean,
    ): Boolean {
        val picture = cover?.let { Krespot.playlists.uploadCover(applicationContext, uri, it) }
        val effective = when {
            picture != null -> picture
            clearCover -> ""
            else -> null
        }
        val ok = Krespot.playlists.edit(applicationContext, uri, name, description, effective)
        log.i("edit playlist '$uri' cover=${picture != null} clear=$clearCover ok=$ok")
        if (ok) Krespot.library.invalidate()
        return ok
    }

    private suspend fun applySettingChange(field: String, value: Boolean, accountId: String): Boolean =
        when (field) {
            "showFollows" -> Settings.setPrivacy(applicationContext, accountId, showFollows = !value)
            "showPublishedPlaylists" -> Settings.setPrivacy(applicationContext, accountId, showPublishedPlaylists = !value)
            else -> Settings.setAttribute(applicationContext, field, value)
        }

    private fun switchAccountNow(id: String) {
        log.i("switch account tapped id=$id")
        PlaybackHost.reset(applicationContext)
        if (EncoreAuth.switchAccount(applicationContext, id)) {
            Krespot.library.invalidate()
            recreate()
        }
    }

    private suspend fun beginAddAccountFlow(): List<EncoreAuth.Account> {
        // Snapshot the current account first so cancelling the flow can
        // restore it, then empty the WebView cookie jar: the live sp_dc
        // otherwise makes the login page auto-redirect into the player,
        // which the poll resolves as the already-saved (duplicate) account
        // and refuses, bouncing the user back without a login form.
        EncoreAuth.userId(applicationContext, force = true)
        EncoreAuth.storeCurrentAccount(applicationContext)
        EncoreAuth.clearCookies()
        return EncoreAuth.savedAccounts(applicationContext)
    }

    private fun forgetAccountAndSwitch(id: String): Boolean =
        EncoreAuth.forgetAccount(applicationContext, id)

    private fun finishAddAccount() {
        EncoreAuth.storeCurrentAccount(applicationContext)
        PlaybackHost.reset(applicationContext)
        Krespot.library.invalidate()
        addingAccount = false
        recreate()
    }

    private fun refuseDuplicateAccount(id: String, name: String, activeId: String) {
        log.w("add account refused: '$name' ($id) is already saved")
        if (activeId.isBlank() || !EncoreAuth.switchAccount(applicationContext, activeId)) {
            EncoreAuth.invalidateToken(applicationContext)
            EncoreAuth.applySession(applicationContext)
        }
        addingAccount = false
        Toast.makeText(
            this,
            "'${name.ifBlank { "This account" }}' is already added",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun finishLogin() {
        EncoreAuth.storeCurrentAccount(applicationContext)
        PlaybackHost.reset(applicationContext)
        Krespot.library.invalidate()
    }

    private fun handleLoginBack(): Boolean {
        if (EncoreAuth.savedAccounts(applicationContext).isEmpty()) {
            log.i("login backed out, leaving the app")
            return false
        }
        log.i("login backed out with a saved account, restoring its session")
        EncoreAuth.applySession(applicationContext)
        if (EncoreAuth.token(applicationContext).isBlank()) {
            finish()
            return false
        }
        return true
    }

    @Composable
    private fun ShellSection(
        bridge: PlaybackBridge,
        account: EncoreAuth.Account?,
        context: Context,
        loggedIn: Boolean,
        activeAccountId: String,
        onSignedOut: () -> Unit,
        updates: UpdateController,
    ) {
        val scope = rememberCoroutineScope()
        val playback = bridge.state
        val contributors = rememberPlaylistContributorController(context, playlistUri)
        val activity = remember { ActivityFeedController(context.applicationContext, scope) }
        LaunchedEffect(activity.fetchTick) {
            if (activity.fetchTick > 0) activity.load()
        }
        LaunchedEffect(Unit) {
            activity.refreshUnread()
        }
        val home = rememberHomeFeedData()
        val nowPlaying = rememberNowPlayingData(canvasAllowed = settingsSwitches.canvas)
        EncoreAppShell(
            deeplink = pendingLink?.let { ShellDeepLink(it.kind, it.uri, it.query) },
            onDeeplinkConsumed = { pendingLink = null },
            onDeeplinkSearch = { q -> if (searchTerm == q) searchFetch++ else searchTerm = q },
            appUpdate = updates.appUpdate,
            updateChecking = updates.checking,
            updateChecked = updates.checked,
            onCheckUpdates = updates::requestManualCheck,
            onHomeEntered = updates::onHomeEntered,
            userDisplayName = account?.name?.takeIf { it.isNotBlank() } ?: "Quackify",
            userAvatarUrl = account?.imageUrl.orEmpty(),
            userProfileColorHex = userProfileColorHex,
        playbackState = bridge.state,
        progressState = bridge.progress,
        playerListener = bridge.listener,
        expandPlayerSignal = if (loggedIn) openPlayerSignal else 0,
            homeQuickAccessItems = home.quick,
        homeShelves = if (homeFacet.isBlank() && !loading) home.historyShelves + home.shelves else home.shelves,
        homeChips = homeChips,
        homeFacet = homeFacet,
        homeGreeting = homeGreeting,
        onHomeFacetSelected = { facet -> homeFacet = facet },
        libraryItems = libraryItems,
        libraryLoading = libraryItems == null,
        playlistTracks = playlistTracks,
        playlistDetail = playlistDetail,
        playlistLoading = playlistLoading,
        playlistHasMore = playlistHasMore,
        playlistLoadingMore = playlistLoadingMore,
        onLoadMorePlaylistTracks = {
            val current = playlistTracks
            if (!playlistLoadingMore && playlistHasMore && current != null) {
                scope.launch {
                    playlistLoadingMore = true
                    val page = loadMorePlaylistTracks(current.size)
                    if (page != null) {
                        playlistTracks = current + page.first
                        playlistHasMore = page.second
                    }
                    playlistLoadingMore = false
                }
            }
        },
        playlistRecommendations = playlistRecommendations,
        recommendationsLoading = recommendationsLoading,
        onRefreshRecommendations = { recommendationsNonce++ },
        onAddRecommended = { track ->
            scope.launch {
                val detail = addRecommendedToPlaylist(track.uri, track.title)
                if (detail != null) {
                    playlistDetail = detail
                    playlistTracks = detail.tracks.map { it.toTrackItem() }
                }
            }
        },
        onSavePlaylistOrder = { uri, order ->
            scope.launch {
                val moved = savePlaylistOrderMoves(playlistTracks.orEmpty(), uri, order.map { it.uid })
                if (moved > 0) {
                    val detail = Krespot.collections.collection(context, uri)
                    playlistDetail = detail
                    playlistTracks = detail?.tracks?.map { it.toTrackItem() }
                }
            }
        },
        albumTracks = albumTracks,
        albumDetail = albumDetail,
        albumLoading = albumLoading,
        albumFailed = albumFailed,
        albumHasMore = albumHasMore,
        albumLoadingMore = albumLoadingMore,
        onLoadMoreAlbumTracks = {
            val current = albumTracks
            if (!albumLoadingMore && albumHasMore && current != null) {
                scope.launch {
                    albumLoadingMore = true
                    val page = loadMoreAlbumTracks(current.size, albumDetail?.imageUrl.orEmpty())
                    if (page != null) {
                        albumTracks = current + page.first
                        albumHasMore = page.second
                    }
                    albumLoadingMore = false
                }
            }
        },
        onRetryAlbum = { albumRetry++ },
        nowPlayingArtistBio = nowPlayingArtist?.biography.orEmpty(),
        nowPlayingMonthlyListeners = nowPlayingArtist?.monthlyListeners
            ?.takeIf { it > 0L }?.let { formatCount(it) + " monthly listeners" }.orEmpty(),
        nowPlayingArtistImageUrl = nowPlayingArtist?.imageUrl?.takeIf { it.isNotBlank() },
        nowPlayingArtistFollowed = nowPlayingArtistFollowed,
        nowPlayingLyrics = remember(nowPlayingLyrics) {
            nowPlayingLyrics?.lines?.map {
                TimeSyncedLyric(it.startTimeMs, it.text)
            }.orEmpty()
        },
        canvasFilePath = canvasFile,
        isCanvasAllowed = nowPlaying.canvasAllowed,
        isCanvasEnabled = canvasEnabled,
        onCanvasToggle = { on ->
            canvasEnabled = on
            clientPrefs.edit().putBoolean("canvasNpvEnabled", on).apply()
            bridge.setCanvas(on)
        },
        canvasResolution = canvasResolution,
        onCanvasResolution = { height ->
            canvasResolution = height
            clientPrefs.edit().putInt("canvasResolution", height).apply()
        },
        artistDetail = artistDetail,
        artistLoading = artistLoading,
        artistFailed = artistFailed,
        onRetryArtist = { artistRetry++ },
        trackCredits = trackCredits,
        addToPlaylistChoices = addToPlaylistChoices,
        addTracksSongs = addTracksSongs,
        addTracksEpisodes = addTracksEpisodes,
        addTracksLiked = addTracksLiked,
        addTracksRecent = addTracksRecent,
        addTracksLoading = addTracksLoading,
        onAddTracksOpen = { addTracksFetch++ },
        onAddTrack = { trackUri ->
            val ok = if (playlistUri == "spotify:collection:tracks") {
                Library.setSaved(context, listOf(trackUri), true)
            } else {
                Krespot.playlists.addTrack(context, playlistUri, trackUri)
            }
            log.i("add '$trackUri' -> '$playlistUri' ok=$ok")
            ok
        },
        onShowCredits = { uri ->
            trackCredits = null
            scope.launch {
                trackCredits = fetchCredits(uri)
            }
        },
        onAddToPlaylist = { track ->
            addToPlaylistChoices = null
            scope.launch {
                addToPlaylistChoices = fetchEditablePlaylists(track.uri)
            }
        },
        onPickPlaylist = { choice, track ->
            scope.launch { addToPlaylistTarget(choice, track.uri, track.title) }
        },
        onTogglePlaylist = { choice, track, shouldAdd ->
            scope.launch {
                togglePlaylistMembership(choice, track.uri, track.title, shouldAdd)
            }
        },
        onLoadPlaylistFolder = { trackUri, folderUri ->
            Krespot.playlists.editable(context, trackUri, folderUri)
        },
        onLoadTrackCount = { playlistUri ->
            Krespot.playlists.trackCount(context, playlistUri)
        },
        onCreatePlaylistWith = { track ->
            scope.launch { createPlaylistWithTrack(track.uri) }
        },
        onPlaylistDefaultName = { Krespot.playlists.defaultName(context) },
        onCreatePlaylist = { name, description, cover ->
            val uri = Krespot.playlists.create(context, name, "")
            log.i("create playlist '$name' ok=${uri != null}")
            if (uri != null) {
                val picture = cover?.let { Krespot.playlists.uploadCover(context, uri, it) }
                if (description.isNotBlank() || picture != null) {
                    Krespot.playlists.edit(context, uri, name, description, picture)
                }
                Krespot.library.invalidate()
            }
            uri
        },
        playlistSaved = playlistSaved,
        playlistPermissions = contributors.permissions,
        contributorsLoading = contributors.loading,
        contributorBusyIds = contributors.busyIds,
        onLoadPlaylistContributors = contributors::refresh,
        onSearchUsers = { term ->
            Search.section(context, SearchKind.USER, term)
        },
        onAddPlaylistContributor = contributors::add,
        onRemovePlaylistContributor = contributors::removeAsContributor,
        onRemovePlaylistMember = contributors::removeFromPlaylist,
        playlistEditDraft = playlistEditDraft,
        editablePlaylistUris = editableUris,
        onEditPlaylist = { uri ->
            playlistEditDraft = null
            scope.launch {
                val loaded = if (uri == playlistUri) playlistDetail else null
                playlistEditDraft = buildPlaylistEditDraft(
                    uri, loaded, playlistCanRemoveCover,
                )
            }
        },
        onDismissEditPlaylist = { playlistEditDraft = null },
        onEditPlaylistDetails = { uri, name, description, cover, clearCover ->
            val ok = editPlaylistDetails(uri, name, description, cover, clearCover)
            if (ok) playlistDetail = Krespot.collections.collection(context, uri)
            ok
        },
        onDeletePlaylist = { uri ->
            val ok = Krespot.playlists.delete(context, uri)
            log.i("delete playlist '$uri' ok=$ok")
            if (ok) {
                playlistUri = ""
                playlistDetail = null
                playlistTracks = null
                Krespot.library.invalidate()
            }
            ok
        },
        onTogglePlaylistSaved = { uri, saved ->
            scope.launch {
                if (setPlaylistSaved(uri, saved)) playlistSaved = saved
            }
        },
        onRemoveTrackFromPlaylist = { pUri, tUri ->
            scope.launch {
                val ok = removeTrackFromPlaylist(pUri, tUri)
                if (ok && pUri == playlistUri) {
                    playlistTracks = playlistTracks?.filterNot { it.uri == tUri }
                }
            }
        },
        onRemoveLibraryItem = { uri ->
            scope.launch { removeFromLibrary(uri) }
        },
        onSetLibraryItemPinned = { uri, pinned ->
            scope.launch { setLibraryItemPinned(uri, pinned) }
        },
        onCreateFolder = { name ->
            val uri = Krespot.folders.createFolder(context, name)
            log.i("create folder '$name' ok=${uri != null}")
            uri
        },
        onRenameFolder = { uri, name ->
            scope.launch { renameFolder(uri, name) }
        },
        onDeleteFolder = { uri ->
            scope.launch { deleteFolder(uri) }
        },
        onMoveToFolder = { playlistUri, targetFolderUri ->
            scope.launch {
                val ok = moveTrackToFolder(playlistUri, targetFolderUri)
                if (ok && folderUri.isNotBlank()) {
                    folderItems = null
                    folderFetch++
                }
            }
        },
        artistTracks = artistTracks,
        artistReleases = artistReleases,
        artistMonthlyListeners = artistListeners,
        artistFollowed = artistFollowed,
        artistBanned = artistBanned,
        onArtistUnban = { uri ->
            scope.launch {
                val ok = Collection2.setArtistBanned(context, uri, false)
                if (ok && uri == artistUri) artistBanned = false
                if (ok) {
                    ArtistBanStore.invalidate()
                    com.quackify.compose.bans.ArtistBans.setBanned(uri, false)
                }
            }
        },
        onArtistBanChanged = { uri, banned ->
            if (uri == artistUri) artistBanned = banned
        },
        onArtistFollowToggle = { uri, follow ->
            scope.launch {
                val ok = setArtistFollow(uri, follow)
                if (!ok) return@launch
                if (uri == artistUri) artistFollowed = follow
                if (uri == nowPlaying.artistUri) nowPlayingArtistFollowed = follow
            }
        },
        onArtistTrackLikeToggle = { track ->
            toggleArtistTrackLike(context, scope, track, { artistTracks }) { artistTracks = it }
        },
        onAlbumTrackLikeToggle = { track ->
            toggleAlbumTrackLike(context, scope, track, { albumTracks }) { albumTracks = it }
        },
        onPlaylistTrackLikeToggle = { track ->
            togglePlaylistTrackLike(
                context, scope, playlistUri, track,
                getTracks = { playlistTracks }, setTracks = { playlistTracks = it },
                getDetail = { playlistDetail }, setDetail = { playlistDetail = it },
            )
        },
        onArtistRadio = { uri ->
            val radio = Krespot.artists.artistRadio(context, uri)
            log.i("artist radio '$uri' -> '$radio'")
            if (radio == null) {
                Toast.makeText(
                    context,
                    "Couldn't load the radio",
                    Toast.LENGTH_SHORT
                ).show()
            }
            radio
        },
        profile = profileDetail,
        profileTop = profileTop,
        profileTopUnavailable = profileTopUnavailable,
        profileContexts = profileContexts,
        profileLoading = profileLoading,
        profileFailed = profileFailed,
        onLoadProfile = {
            if (profileUserId.isNotBlank()) {
                profileUserId = ""
                profileDetail = null
                profileTop = null
                profileTopUnavailable = false
                profileContexts = emptyList()
            }
            profileFetch++
        },
        onRetryProfile = {
            profileDetail = null
            profileTop = null
            profileTopUnavailable = false
            profileContexts = emptyList()
            profileFetch++
        },
        onOpenUserProfile = { uri, _ ->
            if (profileUserId != uri) {
                profileUserId = uri
                profileDetail = null
                profileTop = null
                profileTopUnavailable = false
                profileContexts = emptyList()
            }
            profileFetch++
        },
        profileFollowers = profileFollowers,
        profileFollowing = profileFollowing,
        profileFollowLoading = profileFollowLoading,
        onLoadProfileFollows = { profileFollowFetch++ },
        homeLoading = loading,
        searchResults = remember(searchHits) {
            searchHits.map { hit ->
                com.quackify.compose.playlist.TrackItem(
                    title = hit.title,
                    artist = hit.subtitle,
                    uri = hit.uri,
                    imageUrl = hit.imageUrl,
                )
            }
        },
        onSearchQueryChange = { query -> searchTerm = query },
        onSearchSubmit = { query ->
            RecentSearches.recordQuery(context, query)
            submittedSearchQuery = query
            recentSearches = RecentSearches.all(context)
        },
        searchSuggestionHits = searchHits,
        browseCategories = browseCategories,
        searchSuggestions = remember(searchHits) {
            searchHits.map { hit ->
                TrackItem(
                    title = hit.title,
                    artist = hit.subtitle,
                    uri = hit.uri,
                    imageUrl = hit.imageUrl,
                )
            }
        },
        searchRecent = remember(recentSearches) {
            recentSearches.map { entry ->
                SearchHistoryItem(
                    title = entry.title,
                    subtitle = entry.subtitle,
                    kind = runCatching { SearchKind.valueOf(entry.kind) }
                        .getOrDefault(SearchKind.QUERY),
                    colorHex = entry.colorHex,
                    uri = entry.uri,
                    imageUrl = entry.imageUrl,
                )
            }
        },
        searchLoading = searchLoading,
        searchFailed = searchFailed,
        onRetrySearch = { searchFetch++ },
        searchQuery = searchTerm,
        searchData = searchData,
        onClearRecentSearches = {
            RecentSearches.clear(context)
            recentSearches = emptyList()
            log.i("cleared the recent searches on this device")
        },
        onRemoveRecentSearch = { item ->
            RecentSearches.remove(context, item.uri.ifBlank { item.title })
            recentSearches = RecentSearches.all(context)
            log.i("removed '${item.title}' from the recent searches")
        },
        onRecordRecentSearch = { hit ->
            RecentSearches.record(context, hit)
            val submitted = submittedSearchQuery
            if (submitted != null && searchTerm.trim() == submitted) {
                RecentSearches.remove(context, submitted)
            }
            submittedSearchQuery = null
            recentSearches = RecentSearches.all(context)
        },
        onLoadSearchSection = { kind, offset ->
            Search.section(context, kind, searchTerm, offset)
        },
        onToggleSaved = { uri, saved ->
            scope.launch {
                val next = !saved
                val ok = toggleSearchSaved(uri, next)
                if (ok) {
                    searchSavedOverrides[uri] = next
                    searchData = searchData?.withSaved(uri, next)
                }
            }
        },
        searchSavedOverrides = searchSavedOverrides.toMap(),
        browsePage = browsePage,
        categoryShelves = categoryShelves,
        videoFullscreen = videoFullscreen,
        onToggleVideoFullscreen = { on ->
            videoFullscreen = on
            requestedOrientation = if (on) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        },
        onAppIconChange = { option ->
            applyLauncherIcon(context, option)
        },
        onOpenCategory = { category ->
            categoryUri = "spotify:genre:" + category.lowercase().replace(" ", "-")
            categoryShelves = null
        },
        showDetail = showDetail,
        showLoading = showLoading,
        episodeDetail = episodeDetail,
        episodeLoading = episodeLoading,
        onOpenShow = { uri ->
            showUri = uri
            showFetch++
            showDetail = null
        },
        onOpenEpisode = { uri ->
            episodeUri = uri
            episodeFetch++
            episodeDetail = null
        },
        onOpenBrowse = { uri ->
            browseUri = uri
            browseFetch++
            browsePage = null
        },
        folderItems = folderItems,
        folderLoading = folderItems == null,
        onOpenFolder = { uri ->
            folderUri = uri
            folderFetch++
            folderItems = null
        },
        onLoadPlaylist = { uri ->
            if (uri != playlistUri) {
                playlistUri = uri
                playlistDetail = null
                playlistTracks = null
                playlistHasMore = false
                playlistLoading = true
                playlistRecommendations = emptyList()
                playlistSaved = null
            }
        },
        onRetryPlaylist = {
            playlistDetail = null
            playlistTracks = null
            playlistLoading = true
            playlistFetch++
        },
        onLoadAlbum = { uri, _ -> albumUri = uri },
        onLoadArtist = { uri, _ -> artistUri = uri },
        settingsSwitches = settingsSwitches,
        settingsLocks = settingsLocks,
        settingsProduct = settingsProduct,
        settingsCountry = settingsCountry,
        settingsLoading = settingsLoading,
        onToggleSetting = { field, value ->
            val before = settingsSwitches
            settingsSwitches = when (field) {
                "canvasDisabled" -> before.copy(canvas = !value)
                "musicVideoDisabled" -> before.copy(musicVideos = !value)
                "otherVideoDisabled" -> before.copy(otherVideos = !value)
                "showFollows" -> before.copy(showFollows = !value)
                "showPublishedPlaylists" -> before.copy(showPlaylists = !value)
                else -> before
            }
            scope.launch {
                val ok = applySettingChange(field, value, account?.id.orEmpty())
                log.i("settings $field -> $value ok=$ok")
                if (!ok) settingsSwitches = before
            }
        },
        notifications = whatsNew,
        notificationsLoading = whatsNewLoading,
        notificationsFailed = whatsNewFailed,
        onRefreshWhatsNew = { whatsNewFetch++ },
        activityState = ActivityFeedState(
            feed = activity.feed,
            loading = activity.loading,
            failed = activity.failed,
            self = activity.self,
            unread = activity.unread,
            onOpen = activity::open,
            onRetry = activity::open,
            onLeave = activity::leave,
            onDrawerOpened = activity::refreshUnread,
        ),
        historyToday = home.historyToday,
        historyYesterday = home.historyYesterday,
        historyEarlier = home.historyEarlier,
        historyStats = home.historyStats,
        historyLoading = false,
        onLogout = {
            log.i("logout tapped")
            PlaybackHost.reset(context)
            EncoreAuth.logout(context)
            Krespot.library.invalidate()
            val remaining = EncoreAuth.savedAccounts(context)
            if (remaining.isNotEmpty()) {
                EncoreAuth.switchAccount(context, remaining.last().id)
                (context as? Activity)?.recreate()
            } else {
                (context as? Activity)?.recreate()
            }
        },
        savedAccounts = savedAccountsList,
        activeAccountId = activeAccountId,
        onSwitchAccount = { id -> switchAccountNow(id) },
        onAddAccount = {
            log.i("add account tapped")
            scope.launch {
                savedAccountsList = beginAddAccountFlow()
                addingAccount = true
            }
        },
        onRemoveAccount = { id ->
            log.i("remove account tapped id=$id")
            if (forgetAccountAndSwitch(id)) {
                PlaybackHost.reset(context)
                val remaining = EncoreAuth.savedAccounts(context)
                if (remaining.isNotEmpty()) {
                    EncoreAuth.switchAccount(context, remaining.last().id)
                    Krespot.library.invalidate()
                    recreate()
                } else {
                    EncoreAuth.logout(context)
                    Krespot.library.invalidate()
                    onSignedOut()
                }
            } else {
                savedAccountsList = EncoreAuth.savedAccounts(context)
            }
        }
    )
        }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false)) {
            openPlayerSignal++
        }
        SpotLinks.parse(intent.data)?.let { pendingLink = it }
    }

    private companion object {
        const val CLIENT_PREFS = "quackify.client"
        const val RESUME_REFRESH_AFTER_MS = 60_000L
        const val TOKEN_STILL_GOOD_MS = 60_000L
    }
}

/**
 * The ranked search answer decorates only its first row fully: every track
 * below the top result can arrive with a bare name and uri, which left the
 * Songs list without covers or artist lines. One decorateContextTracks
 * batch fills exactly those rows before the list reaches the screen.
 */
