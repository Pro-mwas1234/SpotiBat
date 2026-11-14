package com.quackify.compose.shell

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import com.quackify.backend.api.Collection2
import com.quackify.backend.api.Library
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.net.Pathfinder
import com.quackify.backend.model.AlbumDetail
import com.quackify.backend.model.ArtistDetail
import com.quackify.backend.model.BrowseCard
import com.quackify.backend.model.BrowsePage
import com.quackify.backend.model.CollectionDetail
import com.quackify.backend.model.Credits
import com.quackify.backend.model.EpisodeDetail
import com.quackify.backend.model.HomeChip
import com.quackify.backend.model.PlaylistChoice
import com.quackify.backend.model.PlaylistPermissions
import com.quackify.backend.model.ProfileTopContent
import com.quackify.backend.model.ProfileUser
import com.quackify.backend.model.SearchHit
import com.quackify.backend.model.SearchKind
import com.quackify.backend.model.SearchResults
import com.quackify.backend.model.ShowDetail
import com.quackify.backend.model.UserProfile
import com.quackify.compose.R
import com.quackify.compose.components.SpotifyToastHost
import com.quackify.compose.components.SpotifyToastState
import com.quackify.compose.home.HomeShelf
import com.quackify.compose.home.QuickAccessItem
import com.quackify.compose.home.RecommendationCard
import com.quackify.compose.library.LibraryItem
import com.quackify.compose.library.LibraryItemType
import com.quackify.compose.player.TimeSyncedLyric
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.screens.AppUpdate
import com.quackify.compose.screens.ArtistRelease
import com.quackify.compose.screens.CategoryShelf
import com.quackify.compose.screens.EncoreNotification
import com.quackify.compose.screens.HistoryStats
import com.quackify.compose.screens.PopularTrack
import com.quackify.compose.screens.ProfileContextCard
import com.quackify.compose.screens.SettingsLocks
import com.quackify.compose.screens.SettingsSwitches
import com.quackify.compose.search.SearchHistoryItem
import com.quackify.compose.theme.EncoreAnimation
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.rememberBottomPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun ShellScreenSlot(
    screen: ShellScreen,
    content: @Composable (ShellScreen) -> Unit
) {
    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            (
                fadeIn(tween(EncoreAnimation.ScreenEnterDuration, easing = EncoreAnimation.BoxInterpolator)) +
                    slideInVertically(
                        tween(EncoreAnimation.ScreenEnterDuration, easing = EncoreAnimation.BoxInterpolator)
                    ) { it / 32 }
                ) togetherWith fadeOut(tween(EncoreAnimation.ScreenExitDuration))
        },
        label = "ShellScreenTransition"
    ) { target ->
        content(target)
    }
}

@Composable
internal fun ShellOverlaySlot(content: @Composable () -> Unit) {
    content()
}


/**
 * - 3-tab Bottom Navigation (Home, Search, Your Library)
 * - Persistent floating Mini-Player
 * - Animated Full Now Playing Player expansion
 * - Profile side sheet, Track context options sheet, Library create sheet, and Sort sheet
 * - Sub-screens: Settings, What's New notifications, Listening History, and Playlist view
 */
@Composable
fun EncoreAppShell(
    modifier: Modifier = Modifier,
    playbackState: ComposePlaybackState? = null,
    progressState: PlaybackProgress? = null,
    playerListener: ComposePlayerListener? = null,
    expandPlayerSignal: Int = 0,
    isComposeUiEnabled: Boolean = true,
    onToggleComposeUi: (Boolean) -> Unit = {},
    onOpenQuackifySettings: (() -> Unit)? = null,
    homeQuickAccessItems: List<QuickAccessItem>? = null,
    homeRecommendations: List<RecommendationCard>? = null,
    homeShelves: List<HomeShelf>? = null,
    homeChips: List<HomeChip> = emptyList(),
    homeFacet: String = "",
    homeGreeting: String = "",
    onHomeFacetSelected: (String) -> Unit = {},
    libraryItems: List<LibraryItem>? = null,
    libraryLoading: Boolean = false,
    searchResults: List<TrackItem>? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    onSearchSubmit: ((String) -> Unit)? = null,
    searchSuggestions: List<TrackItem> = emptyList(),
    searchSuggestionHits: List<SearchHit> = emptyList(),
    searchRecent: List<SearchHistoryItem> = emptyList(),
    browseCategories: List<BrowseCard>? = null,
    searchLoading: Boolean = false,
    searchFailed: Boolean = false,
    onRetrySearch: (() -> Unit)? = null,
    searchQuery: String = "",
    searchData: SearchResults? = null,
    onClearRecentSearches: (() -> Unit)? = null,
    onRemoveRecentSearch: ((SearchHistoryItem) -> Unit)? = null,
    onRecordRecentSearch: ((SearchHit) -> Unit)? = null,
    onLoadSearchSection: (suspend (SearchKind, Int) -> List<SearchHit>)? = null,
    onToggleSaved: ((uri: String, currentlySaved: Boolean) -> Unit)? = null,
    searchSavedOverrides: Map<String, Boolean> = emptyMap(),
    homeLoading: Boolean = false,
    onLogout: (() -> Unit)? = null,
    settingsSwitches: SettingsSwitches = SettingsSwitches(),
    settingsLocks: SettingsLocks = SettingsLocks(),
    settingsProduct: String = "",
    settingsCountry: String = "",
    settingsLoading: Boolean = false,
    onToggleSetting: ((String, Boolean) -> Unit)? = null,
    videoFullscreen: Boolean = false,
    onToggleVideoFullscreen: ((Boolean) -> Unit)? = null,
    onAppIconChange: ((String) -> Unit)? = null,
    appUpdate: AppUpdate? = null,
    updateChecking: Boolean = false,
    updateChecked: Boolean = false,
    onCheckUpdates: (() -> Unit)? = null,
    onHomeEntered: () -> Unit = {},
    browsePage: BrowsePage? = null,
    categoryShelves: List<CategoryShelf>? = null,
    onOpenCategory: ((String) -> Unit)? = null,
    showDetail: ShowDetail? = null,
    showLoading: Boolean = false,
    episodeDetail: EpisodeDetail? = null,
    episodeLoading: Boolean = false,
    notifications: List<EncoreNotification> = emptyList(),
    notificationsLoading: Boolean = false,
    notificationsFailed: Boolean = false,
    onRefreshWhatsNew: (() -> Unit)? = null,
    activityState: ActivityFeedState? = null,
    historyToday: List<TrackItem> = emptyList(),
    historyYesterday: List<TrackItem> = emptyList(),
    historyEarlier: List<TrackItem> = emptyList(),
    historyStats: HistoryStats? = null,
    historyLoading: Boolean = false,
    onOpenHistory: (() -> Unit)? = null,
    savedAccounts: List<com.quackify.backend.core.auth.EncoreAuth.Account> = emptyList(),
    activeAccountId: String = "",
    onSwitchAccount: ((String) -> Unit)? = null,
    onAddAccount: (() -> Unit)? = null,
    onRemoveAccount: ((String) -> Unit)? = null,
    folderItems: List<LibraryItem>? = null,
    folderLoading: Boolean = false,
    onOpenFolder: ((String) -> Unit)? = null,
    onLoadPlaylist: ((String) -> Unit)? = null,
    onLoadAlbum: ((String, String) -> Unit)? = null,
    onLoadArtist: ((String, String) -> Unit)? = null,
    onOpenShow: ((String) -> Unit)? = null,
    onOpenEpisode: ((String) -> Unit)? = null,
    deeplink: ShellDeepLink? = null,
    onDeeplinkConsumed: () -> Unit = {},
    onDeeplinkSearch: ((String) -> Unit)? = null,
    onOpenBrowse: ((String) -> Unit)? = null,
    onLoadMoreBrowse: (() -> Unit)? = null,
    onLoadMoreEpisodes: (() -> Unit)? = null,
    playlistTracks: List<TrackItem>? = null,
    playlistDetail: CollectionDetail? = null,
    playlistLoading: Boolean = false,
    playlistHasMore: Boolean = false,
    playlistLoadingMore: Boolean = false,
    onLoadMorePlaylistTracks: (() -> Unit)? = null,
    onRetryPlaylist: (() -> Unit)? = null,
    playlistRecommendations: List<TrackItem> = emptyList(),
    recommendationsLoading: Boolean = false,
    onRefreshRecommendations: (() -> Unit)? = null,
    onAddRecommended: ((TrackItem) -> Unit)? = null,
    onSavePlaylistOrder: ((String, List<TrackItem>) -> Unit)? = null,
    albumTracks: List<TrackItem>? = null,
    albumDetail: AlbumDetail? = null,
    albumLoading: Boolean = false,
    albumFailed: Boolean = false,
    albumHasMore: Boolean = false,
    albumLoadingMore: Boolean = false,
    onLoadMoreAlbumTracks: (() -> Unit)? = null,
    onRetryAlbum: (() -> Unit)? = null,
    nowPlayingArtistBio: String = "",
    nowPlayingMonthlyListeners: String = "",
    nowPlayingArtistImageUrl: String? = null,
    nowPlayingArtistFollowed: Boolean? = null,
    nowPlayingLyrics: List<TimeSyncedLyric> = emptyList(),
    canvasFilePath: String? = null,
    isCanvasAllowed: Boolean = true,
    isCanvasEnabled: Boolean = false,
    onCanvasToggle: ((Boolean) -> Unit)? = null,
    canvasResolution: Int = 1080,
    onCanvasResolution: ((Int) -> Unit)? = null,
    artistDetail: ArtistDetail? = null,
    artistLoading: Boolean = false,
    artistFailed: Boolean = false,
    onRetryArtist: (() -> Unit)? = null,
    trackCredits: Credits? = null,
    addToPlaylistChoices: List<PlaylistChoice>? = null,
    addTracksSongs: List<TrackItem> = emptyList(),
    addTracksEpisodes: List<TrackItem> = emptyList(),
    addTracksLiked: List<TrackItem> = emptyList(),
    addTracksRecent: List<TrackItem> = emptyList(),
    addTracksLoading: Boolean = false,
    onAddTracksOpen: (() -> Unit)? = null,
    onAddTrack: (suspend (String) -> Boolean)? = null,
    onShowCredits: ((String) -> Unit)? = null,
    onAddToPlaylist: ((TrackItem) -> Unit)? = null,
    onPickPlaylist: ((PlaylistChoice, TrackItem) -> Unit)? = null,
    onTogglePlaylist: ((PlaylistChoice, TrackItem, Boolean) -> Unit)? = null,
    onCreatePlaylistWith: ((TrackItem) -> Unit)? = null,
    onLoadPlaylistFolder: (suspend (trackUri: String, folderUri: String) -> List<PlaylistChoice>)? = null,
    onLoadTrackCount: (suspend (playlistUri: String) -> Int)? = null,
    onPlaylistDefaultName: (suspend () -> String)? = null,
    onCreatePlaylist: (suspend (name: String, description: String, cover: ByteArray?) -> String?)? = null,
    playlistSaved: Boolean? = null,
    playlistPermissions: PlaylistPermissions? = null,
    contributorsLoading: Boolean = false,
    contributorBusyIds: Set<String> = emptySet(),
    onLoadPlaylistContributors: (() -> Unit)? = null,
    onSearchUsers: (suspend (String) -> List<SearchHit>)? = null,
    onAddPlaylistContributor: ((String) -> Unit)? = null,
    onRemovePlaylistContributor: ((String) -> Unit)? = null,
    onRemovePlaylistMember: ((String) -> Unit)? = null,
    playlistEditDraft: PlaylistEditDraft? = null,
    editablePlaylistUris: Set<String> = emptySet(),
    onEditPlaylist: ((uri: String) -> Unit)? = null,
    onEditPlaylistDetails: (suspend (uri: String, name: String, description: String, cover: ByteArray?, clearCover: Boolean) -> Boolean)? = null,
    onDeletePlaylist: (suspend (uri: String) -> Boolean)? = null,
    onDismissEditPlaylist: () -> Unit = {},
    onTogglePlaylistSaved: ((uri: String, saved: Boolean) -> Unit)? = null,
    onRemoveTrackFromPlaylist: ((playlistUri: String, trackUri: String) -> Unit)? = null,
    onRemoveLibraryItem: ((uri: String) -> Unit)? = null,
    onSetLibraryItemPinned: ((uri: String, pinned: Boolean) -> Unit)? = null,
    onCreateFolder: (suspend (name: String) -> String?)? = null,
    onRenameFolder: ((uri: String, name: String) -> Unit)? = null,
    onDeleteFolder: ((uri: String) -> Unit)? = null,
    onMoveToFolder: ((playlistUri: String, folderUri: String) -> Unit)? = null,
    artistTracks: List<PopularTrack>? = null,
    artistReleases: List<ArtistRelease>? = null,
    artistMonthlyListeners: String? = null,
    artistFollowed: Boolean? = null,
    onArtistFollowToggle: ((artistUri: String, follow: Boolean) -> Unit)? = null,
    onArtistTrackLikeToggle: ((TrackItem) -> Unit)? = null,
    onAlbumTrackLikeToggle: ((TrackItem) -> Unit)? = null,
    onPlaylistTrackLikeToggle: ((TrackItem) -> Unit)? = null,
    onArtistRadio: (suspend (artistUri: String) -> String?)? = null,
    profile: UserProfile? = null,
    profileTop: ProfileTopContent? = null,
    profileTopUnavailable: Boolean = false,
    profileContexts: List<ProfileContextCard> = emptyList(),
    profileLoading: Boolean = false,
    profileFailed: Boolean = false,
    onLoadProfile: (() -> Unit)? = null,
    onRetryProfile: (() -> Unit)? = null,
    onOpenUserProfile: ((String, String) -> Unit)? = null,
    profileFollowers: List<ProfileUser> = emptyList(),
    profileFollowing: List<ProfileUser> = emptyList(),
    profileFollowLoading: Boolean = false,
    onLoadProfileFollows: (() -> Unit)? = null,
    userDisplayName: String = "Duck User",
    userAvatarUrl: String = "",
    userProfileColorHex: String = ""
) {
    val flags = remember { ShellUiFlags() }
    val active = remember { ShellActiveEntity() }
    val navState = remember { ShellNavState() }
    val local = remember { ShellLocalState() }
    val activityFeed = activityState?.feed.orEmpty()
    val activityLoading = activityState?.loading == true
    val activityFailed = activityState?.failed == true
    val activitySelf = activityState?.self
    val activityUnread = activityState?.unread == true
    val onOpenActivity = activityState?.onOpen
    val onRetryActivity = activityState?.onRetry
    val onLeaveActivity = activityState?.onLeave
    val onProfileDrawerOpened = activityState?.onDrawerOpened
    val searchQueryNow = searchQuery
    val navController = remember(
        searchQueryNow,
        onSearchQueryChange,
        playerListener,
        onLoadPlaylist,
        onLoadAlbum,
        onLoadArtist,
        onOpenShow,
        onOpenEpisode,
        onOpenUserProfile,
        onRetryPlaylist,
        libraryItems,
        searchData,
        nowPlayingArtistImageUrl
    ) {
        ShellNavController(
            nav = navState,
            active = active,
            flags = flags,
            searchQuery = { searchQueryNow },
            onSearchQueryChange = onSearchQueryChange,
            playerListener = playerListener,
            onLoadPlaylist = onLoadPlaylist,
            onLoadAlbum = onLoadAlbum,
            onLoadArtist = onLoadArtist,
            onOpenShow = onOpenShow,
            onOpenEpisode = onOpenEpisode,
            onOpenUserProfile = onOpenUserProfile,
            onAddTracksClosed = { onRetryPlaylist?.invoke() },
            libraryItems = libraryItems,
            searchData = searchData,
            nowPlayingArtistImageUrl = nowPlayingArtistImageUrl,
            currentArtist = { playbackState?.artist ?: local.artist },
        )
    }
    var currentScreen by navController.nav::currentScreen
    var selectedNavIndex by navController.nav::selectedNavIndex
    var isPlayerExpanded by navController.nav::isPlayerExpanded

    LaunchedEffect(currentScreen) {
        if (currentScreen == ShellScreen.HOME) onHomeEntered()
    }

    LaunchedEffect(expandPlayerSignal) {
        if (expandPlayerSignal > 0) isPlayerExpanded = true
    }

    var trackTasteExcluded by local::trackTasteExcluded
    var trackOptionsSaved by local::trackOptionsSaved
    var trackOptionsPlays by local::trackOptionsPlays
    val tasteExclusionCache = remember { mutableMapOf<String, Boolean>() }
    var currentSortOption by local::sortOption
    var currentSortDirection by local::sortDirection
    var playbackOrigin by local::playbackOrigin
    var libraryItemOptions by local::libraryItemOptions
    var activeFolderOptionsItem by local::activeFolderOptionsItem
    var folderDraft by local::folderDraft
    var playlistCreateName by local::playlistCreateName
    var creditsTrack by local::creditsTrack
    var addTrackTarget by local::addTrackTarget
    val selectedTrackForOptions = local.trackForOptions

    val shellScope = rememberCoroutineScope()
    val navBackStack = navController.nav.backStack
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    LaunchedEffect(deeplink) {
        val link = deeplink ?: return@LaunchedEffect
        onDeeplinkConsumed()
        when (link.kind) {
            "artist" -> navController.openArtist(context, "", link.uri)
            "album" -> navController.openAlbum(context, "", "", link.uri)
            "playlist" -> navController.openPlaylist(context, "", link.uri)
            "show" -> navController.openShow(link.uri)
            "episode" -> navController.openEpisode(link.uri)
            "search" -> {
                selectedNavIndex = 1
                currentScreen = ShellScreen.SEARCH
                onDeeplinkSearch?.invoke(link.query)
            }
        }
    }

    val actions = ShellActions(
        flags = flags,
        local = local,
        navController = navController,
        playerListener = playerListener,
        playlistTracks = playlistTracks,
        albumTracks = albumTracks,
        artistTracks = artistTracks,
        searchQuery = { searchQueryNow },
        clipboard = clipboard,
    )

    val pushCurrentState: () -> Unit = { navController.pushCurrentState() }
    val handleBack: () -> Unit = { navController.handleBack() }

    LaunchedEffect(flags.isTrackOptionsOpen, selectedTrackForOptions?.uri) {
        val uri = selectedTrackForOptions?.uri.orEmpty()
        if (!flags.isTrackOptionsOpen || !uri.startsWith("spotify:track:")) {
            trackTasteExcluded = null
            trackOptionsSaved = null
            trackOptionsPlays = null
            return@LaunchedEffect
        }
        trackTasteExcluded = tasteExclusionCache[uri]
        trackOptionsSaved = null
        trackOptionsPlays = selectedTrackForOptions?.plays?.takeIf { it > 0L }
        if (trackOptionsPlays == null) {
            val variables = org.json.JSONObject()
                .put("uri", uri)
                .put("includeVideoAssociationItems", true)
                .put("includeMusicVideoPreviews", false)
                .toString()
            val plays = Pathfinder.queryPost(context, EncoreAuth.token(context), "getTrack", variables)
                ?.optJSONObject("data")?.optJSONObject("trackUnion")
                ?.optString("playcount")?.toLongOrNull()
            if (uri == selectedTrackForOptions?.uri) trackOptionsPlays = plays
        }
        if (trackTasteExcluded == null) {
            val excluded = Collection2.excludedFromTasteProfile(context, uri)
            if (uri == selectedTrackForOptions?.uri) {
                tasteExclusionCache[uri] = excluded
                trackTasteExcluded = excluded
            }
        }
        if (uri == playbackState?.trackUri) {
            trackOptionsSaved = playbackState?.isLiked
        } else {
            val saved = Library.saved(context, listOf(uri))
            if (uri == selectedTrackForOptions?.uri) {
                trackOptionsSaved = uri in saved
            }
        }
    }

    val handleOpenSettings: () -> Unit = {
        if (onOpenQuackifySettings != null) {
            onOpenQuackifySettings()
        } else {
            pushCurrentState()
            currentScreen = ShellScreen.SETTINGS
        }
    }

    var localIsPlaying by local::isPlaying
    var localTrackTitle by local::trackTitle
    var localArtist by local::artist
    var localIsLiked by local::isLiked
    var localShuffleMode by local::shuffleMode
    var localRepeatMode by local::repeatMode
    var localDevice by local::device

    val currentTrackTitle = playbackState?.title ?: localTrackTitle
    val currentArtist = playbackState?.artist ?: localArtist
    val isPlaying = playbackState?.isPlaying ?: localIsPlaying
    val isBuffering = playbackState?.isBuffering ?: false
    val currentProgress = progressState?.progress ?: local.progress
    val currentPositionMs = progressState?.positionMs ?: local.positionMs
    val durationMs = progressState?.durationMs ?: 0L
    val isLiked = playbackState?.isLiked ?: localIsLiked
    val currentShuffleMode = playbackState?.shuffleMode ?: localShuffleMode
    val currentRepeatMode = playbackState?.repeatMode ?: localRepeatMode
    val connectedDevice = playbackState?.connectedDevice ?: localDevice
    val currentCoverUrl = playbackState?.coverUrl ?: ""

    val playerIdentity = playerIdentityOf(
        title = currentTrackTitle,
        artist = currentArtist,
        trackUri = playbackState?.trackUri,
        localTitle = localTrackTitle,
    )

    val currentQueueRow =
        remember(playbackState?.trackUri, playbackState?.queue, playbackState?.queueFromContext) {
            (playbackState?.queue.orEmpty() + playbackState?.queueFromContext.orEmpty())
                .firstOrNull { it.uri == playbackState?.trackUri }
        }
    val resolvedAlbumUri = playbackState?.albumUri?.takeIf { it.isNotBlank() }
        ?: currentQueueRow?.albumUri.orEmpty()
    val resolvedArtistUri = playbackState?.artistUri?.takeIf { it.isNotBlank() }
        ?: currentQueueRow?.artistUri.orEmpty()

    val openAlbum: (String, String, String) -> Unit = { title, artist, uri ->
        navController.openAlbum(context, title, artist, uri)
    }

    val openShow: (String) -> Unit = { uri -> navController.openShow(uri) }

    val openPlaylist: (String, String) -> Unit = { title, uri ->
        navController.openPlaylist(context, title, uri)
    }

    val openEpisode: (String) -> Unit = { uri -> navController.openEpisode(uri) }

    val handleTrackPlay: (TrackItem) -> Unit = { track -> actions.onTrackPlay(track) }

    val handlePlayPause: () -> Unit = { actions.playPause() }

    val handleTrackOptions: (TrackItem) -> Unit = { track -> actions.openTrackOptions(track) }

    /** Single funnel for every "add to queue" action so they all fire the
     * Spotify-style confirmation toast from one place. */
    val toast = remember { SpotifyToastState() }
    val handleQueueAdd: (String) -> Unit = { uri ->
        playerListener?.onQueueAdd(uri)
        toast.show("Added to queue", "Queue") {
            isPlayerExpanded = true
            shellScope.launch {
                // Let the player's reveal animation finish before the queue
                // sheet slides in on top of it.
                delay(440L)
                if (isPlayerExpanded) flags.isQueueSheetOpen = true
            }
        }
    }
    val handleTrackAddToQueue: (TrackItem) -> Unit = { track -> handleQueueAdd(track.uri) }

    val handlePodcastOpen: (String, String) -> Unit = { _, uri ->
        if (uri.isNotEmpty()) openShow(uri)
    }

    val openProfileDrawer: () -> Unit = {
        navController.openProfileDrawer()
        onProfileDrawerOpened?.invoke()
    }

    val openUserProfile: (String, String) -> Unit = { uri, name ->
        navController.openUserProfile(context, uri, name)
    }

    val openArtist: (String, String) -> Unit = { artist, uri ->
        navController.openArtist(context, artist, uri)
    }

    val handleSkipNext: () -> Unit = { actions.skipNext() }

    val handleSkipPrevious: () -> Unit = { actions.skipPrevious() }

    val navItems = remember {
        listOf(
            NavItem("Home", R.drawable.encore_icon_home_24, R.drawable.encore_icon_home_active_24),
            NavItem(
                "Search",
                R.drawable.encore_icon_search_24,
                R.drawable.encore_icon_search_active_24
            ),
            NavItem(
                "Your Library",
                R.drawable.encore_icon_your_library_24,
                R.drawable.encore_icon_your_library_active_24
            )
        )
    }

    val searchFieldFocusRequester = remember { FocusRequester() }

    BackHandler(
        enabled = isPlayerExpanded ||
                flags.isProfileDrawerOpen ||
                flags.isProfileFollowOpen ||
                flags.isAccountsSheetOpen ||
                flags.isTrackOptionsOpen ||
                flags.isPlaylistOptionsOpen ||
                flags.isAlbumOptionsOpen ||
                flags.isArtistOptionsOpen ||
                flags.isSortSheetOpen ||
                flags.isCreateSheetOpen ||
                flags.isCreatePlaylistOpen ||
                playlistEditDraft != null ||
                libraryItemOptions != null ||
                folderDraft != null ||
                navBackStack.isNotEmpty() ||
                currentScreen != ShellScreen.HOME
    ) {

        if (isPlayerExpanded) {
            isPlayerExpanded = false
        } else if (flags.isProfileDrawerOpen) {
            flags.isProfileDrawerOpen = false
        } else if (flags.isProfileFollowOpen) {
            flags.isProfileFollowOpen = false
        } else if (flags.isAccountsSheetOpen) {
            flags.isAccountsSheetOpen = false
        } else if (flags.isTrackOptionsOpen) {
            flags.isTrackOptionsOpen = false
        } else if (flags.isPlaylistOptionsOpen) {
            flags.isPlaylistOptionsOpen = false
        } else if (flags.isAlbumOptionsOpen) {
            flags.isAlbumOptionsOpen = false
        } else if (flags.isArtistOptionsOpen) {
            flags.isArtistOptionsOpen = false
        } else if (flags.isSortSheetOpen) {
            flags.isSortSheetOpen = false
        } else if (flags.isCreateSheetOpen) {
            flags.isCreateSheetOpen = false
        } else if (flags.isCreatePlaylistOpen) {
            flags.isCreatePlaylistOpen = false
        } else if (playlistEditDraft != null) {
            onDismissEditPlaylist()
        } else if (libraryItemOptions != null) {
            libraryItemOptions = null
        } else if (folderDraft != null) {
            folderDraft = null
        } else {
            handleBack()
        }
    }

    val copyEntityLink: (String) -> Unit = { uri -> actions.copyEntityLink(uri) }

    val shareEntity: (String) -> Unit = { uri -> actions.shareEntity(context, uri) }

    val libraryFolderChoices = remember(libraryItems) {
        libraryItems.orEmpty()
            .filter { it.type == LibraryItemType.FOLDER }
            .map { it.uri to it.title }
    }

    val handleLibraryLongPress: (LibraryItem) -> Unit = { item -> actions.libraryLongPress(item) }

    val handleFolderClick: (LibraryItem) -> Unit = { item ->
        navController.openFolder(item, onOpenFolder)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreColors.PureBlack)
    ) {

        val hosts = ShellHosts(
            active = active,
            flags = flags,
            local = local,
            navController = navController,
            actions = actions,
            shellScope = shellScope,
            playbackState = playbackState,
            playerListener = playerListener,
            currentTrackTitle = currentTrackTitle,
            currentArtist = currentArtist,
            isPlaying = isPlaying,
            currentSortOption = currentSortOption,
            currentSortDirection = currentSortDirection,
            searchFieldFocusRequester = searchFieldFocusRequester,
            userDisplayName = userDisplayName,
            userAvatarUrl = userAvatarUrl,
            userColorHex = userProfileColorHex,
            handleBack = handleBack,
            handlePlayPause = handlePlayPause,
            handleTrackPlay = handleTrackPlay,
            handleTrackOptions = handleTrackOptions,
            handleTrackAddToQueue = handleTrackAddToQueue,
            handleOpenSettings = handleOpenSettings,
            handlePodcastOpen = handlePodcastOpen,
            openArtist = openArtist,
            openAlbum = openAlbum,
            openPlaylist = openPlaylist,
            openShow = openShow,
            openEpisode = openEpisode,
            openUserProfile = openUserProfile,
            openProfileDrawer = openProfileDrawer,
            handleLibraryLongPress = handleLibraryLongPress,
            handleFolderClick = handleFolderClick,
            shareEntity = shareEntity,
            progressState = progressState,
            currentScreen = currentScreen,
            isPlayerExpanded = isPlayerExpanded,
            setIsPlayerExpanded = { isPlayerExpanded = it },
            setCurrentScreen = { currentScreen = it },
            pushCurrentState = pushCurrentState,
            navBackStack = navBackStack,
            selectedNavIndex = selectedNavIndex,
            setSelectedNavIndex = { selectedNavIndex = it },
            navItems = navItems,
            playerIdentity = playerIdentity,
            isBuffering = isBuffering,
            isLiked = isLiked,
            currentCoverUrl = currentCoverUrl,
            currentShuffleMode = currentShuffleMode,
            currentRepeatMode = currentRepeatMode,
            connectedDevice = connectedDevice,
            resolvedAlbumUri = resolvedAlbumUri,
            resolvedArtistUri = resolvedArtistUri,
            nowPlayingMonthlyListeners = nowPlayingMonthlyListeners,
            nowPlayingArtistBio = nowPlayingArtistBio,
            nowPlayingArtistImageUrl = nowPlayingArtistImageUrl,
            nowPlayingArtistFollowed = nowPlayingArtistFollowed,
            nowPlayingLyrics = nowPlayingLyrics,
            canvasFilePath = canvasFilePath,
            isCanvasAllowed = isCanvasAllowed,
            isCanvasEnabled = isCanvasEnabled,
            videoFullscreen = videoFullscreen,
            playbackOrigin = playbackOrigin,
            handleSkipNext = handleSkipNext,
            handleSkipPrevious = handleSkipPrevious,
            onCanvasToggle = onCanvasToggle,
            onToggleVideoFullscreen = onToggleVideoFullscreen,
            onArtistFollowToggle = onArtistFollowToggle,
            onAddToPlaylist = onAddToPlaylist,
            context = context,
            handleQueueAdd = handleQueueAdd,
            copyEntityLink = copyEntityLink,
            savedAccounts = savedAccounts,
            activeAccountId = activeAccountId,
            playlistDetail = playlistDetail,
            playlistSaved = playlistSaved,
            albumDetail = albumDetail,
            artistDetail = artistDetail,
            artistFollowed = artistFollowed,
            trackTasteExcluded = trackTasteExcluded,
            setTrackTasteExcluded = { trackTasteExcluded = it },
            tasteExclusionCache = tasteExclusionCache,
            canvasResolution = canvasResolution,
            setLocalDevice = { localDevice = it },
            onLoadProfile = onLoadProfile,
            onOpenHistory = onOpenHistory,
            onOpenActivity = onOpenActivity,
            activityUnread = activityUnread,
            onShowCredits = onShowCredits,
            onEditPlaylist = onEditPlaylist,
            onTogglePlaylistSaved = onTogglePlaylistSaved,
            onDeletePlaylist = onDeletePlaylist,
            onSwitchAccount = onSwitchAccount,
            onAddAccount = onAddAccount,
            onRemoveAccount = onRemoveAccount,
            onLogout = onLogout,
            onArtistRadio = onArtistRadio,
            onCanvasResolution = onCanvasResolution,
            onPlaylistDefaultName = onPlaylistDefaultName,
            onRemoveTrackFromPlaylist = onRemoveTrackFromPlaylist,
            profileFollowers = profileFollowers,
            profileFollowing = profileFollowing,
            profileFollowLoading = profileFollowLoading,
            playlistPermissions = playlistPermissions,
            contributorsLoading = contributorsLoading,
            contributorBusyIds = contributorBusyIds,
            onLoadPlaylistContributors = onLoadPlaylistContributors,
            onSearchUsers = onSearchUsers,
            onAddPlaylistContributor = onAddPlaylistContributor,
            onRemovePlaylistContributor = onRemovePlaylistContributor,
            onRemovePlaylistMember = onRemovePlaylistMember,
            trackCredits = trackCredits,
            addToPlaylistChoices = addToPlaylistChoices,
            albumTracks = albumTracks,
            onPickPlaylist = onPickPlaylist,
            onTogglePlaylist = onTogglePlaylist,
            onLoadPlaylistFolder = onLoadPlaylistFolder,
            onLoadTrackCount = onLoadTrackCount,
            onCreatePlaylistWith = onCreatePlaylistWith,
            playlistEditDraft = playlistEditDraft,
            onEditPlaylistDetails = onEditPlaylistDetails,
            onDismissEditPlaylist = onDismissEditPlaylist,
            libraryFolderChoices = libraryFolderChoices,
            editablePlaylistUris = editablePlaylistUris,
            folderItems = folderItems,
            onRemoveLibraryItem = onRemoveLibraryItem,
            onSetLibraryItemPinned = onSetLibraryItemPinned,
            onMoveToFolder = onMoveToFolder,
            onCreateFolder = onCreateFolder,
            onRenameFolder = onRenameFolder,
            onDeleteFolder = onDeleteFolder,
            onQueueAdd = handleQueueAdd,
            onLoadPlaylist = onLoadPlaylist,
            onCreatePlaylist = onCreatePlaylist,
            playlistDescription = playlistDetail?.description.orEmpty(),
        )
        ShellScreenSlot(currentScreen) { screen ->
            when (screen) {
                ShellScreen.HOME -> ShellHomeRoute(
                    env = hosts.route(),
                    quickAccessItems = homeQuickAccessItems,
                    recommendations = homeRecommendations,
                    shelves = homeShelves,
                    chips = homeChips,
                    selectedFacet = homeFacet,
                    greeting = homeGreeting,
                    loading = homeLoading,
                    libraryItems = libraryItems,
                    notifications = notifications,
                    onHomeFacetSelected = onHomeFacetSelected,
                    onOpenHistory = onOpenHistory,
                )

                ShellScreen.SEARCH -> ShellSearchRoute(
                    env = hosts.route(),
                    searchResults = searchResults,
                    searchData = searchData,
                    searchRecent = searchRecent,
                    searchSuggestions = searchSuggestions,
                    searchSuggestionHits = searchSuggestionHits,
                    searchLoading = searchLoading,
                    searchFailed = searchFailed,
                    searchSavedOverrides = searchSavedOverrides,
                    browseCategories = browseCategories,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onSearchSubmit = onSearchSubmit,
                    onRetrySearch = onRetrySearch,
                    onClearRecentSearches = onClearRecentSearches,
                    onRemoveRecentSearch = onRemoveRecentSearch,
                    onRecordRecentSearch = onRecordRecentSearch,
                    onToggleSaved = onToggleSaved,
                    onLoadSearchSection = onLoadSearchSection,
                    onOpenBrowse = onOpenBrowse,
                    onOpenCategory = onOpenCategory,
                )

                ShellScreen.LIBRARY -> ShellLibraryRoute(
                    env = hosts.route(),
                    libraryItems = libraryItems,
                    libraryLoading = libraryLoading,
                )

                ShellScreen.PLAYLIST -> ShellPlaylistRoute(
                    env = hosts.route(),
                    playlistDetail = playlistDetail,
                    playlistTracks = playlistTracks,
                    playlistLoading = playlistLoading,
                    playlistHasMore = playlistHasMore,
                    playlistLoadingMore = playlistLoadingMore,
                    onLoadMorePlaylistTracks = onLoadMorePlaylistTracks,
                    onRetryPlaylist = onRetryPlaylist,
                    playlistSaved = playlistSaved,
                    playlistRecommendations = playlistRecommendations,
                    recommendationsLoading = recommendationsLoading,
                    onRefreshRecommendations = onRefreshRecommendations,
                    onAddRecommended = onAddRecommended,
                    onSavePlaylistOrder = onSavePlaylistOrder,
                    onEditPlaylistDetails = onEditPlaylistDetails,
                    onDeletePlaylist = onDeletePlaylist,
                    onTogglePlaylistSaved = onTogglePlaylistSaved,
                    playlistPermissions = playlistPermissions,
                    onLoadPlaylistContributors = onLoadPlaylistContributors,
                    onPlaylistTrackLikeToggle = onPlaylistTrackLikeToggle,
                    onEditPlaylist = onEditPlaylist,
                    onAddTracksOpen = onAddTracksOpen,
                )

                ShellScreen.ALBUM -> ShellAlbumRoute(
                    env = hosts.route(),
                    albumDetail = albumDetail,
                    albumTracks = albumTracks,
                    albumLoading = albumLoading,
                    albumFailed = albumFailed,
                    albumHasMore = albumHasMore,
                    albumLoadingMore = albumLoadingMore,
                    onLoadMoreAlbumTracks = onLoadMoreAlbumTracks,
                    onRetryAlbum = onRetryAlbum,
                    onAlbumTrackLikeToggle = onAlbumTrackLikeToggle,
                )

                ShellScreen.ARTIST -> ShellArtistRoute(
                    env = hosts.route(),
                    artistDetail = artistDetail,
                    artistTracks = artistTracks,
                    artistReleases = artistReleases,
                    artistMonthlyListeners = artistMonthlyListeners,
                    artistLoading = artistLoading,
                    artistFailed = artistFailed,
                    onRetryArtist = onRetryArtist,
                    artistFollowed = artistFollowed,
                    onArtistFollowToggle = onArtistFollowToggle,
                    onArtistTrackLikeToggle = onArtistTrackLikeToggle,
                )

                ShellScreen.CATEGORY -> ShellCategoryRoute(
                    env = hosts.route(),
                    categoryShelves = categoryShelves,
                    homeQuickAccessItems = homeQuickAccessItems,
                    homeRecommendations = homeRecommendations,
                    libraryItems = libraryItems,
                )

                ShellScreen.SETTINGS -> ShellSettingsRoute(
                    env = hosts.route(),
                    switches = settingsSwitches,
                    locks = settingsLocks,
                    product = settingsProduct,
                    country = settingsCountry,
                    loading = settingsLoading,
                    onToggleSetting = onToggleSetting,
                    onAppIconChange = onAppIconChange,
                    update = appUpdate,
                    updateChecking = updateChecking,
                    updateChecked = updateChecked,
                    onCheckUpdates = onCheckUpdates,
                    onLogout = onLogout,
                )

                ShellScreen.NOTIFICATIONS -> ShellNotificationsRoute(
                    env = hosts.route(),
                    notifications = notifications,
                    loading = notificationsLoading,
                    loadFailed = notificationsFailed,
                    onRefresh = onRefreshWhatsNew,
                    onLoadPlaylist = onLoadPlaylist,
                    onOpenEpisode = onOpenEpisode,
                )

                ShellScreen.ACTIVITY -> ShellActivityRoute(
                    env = hosts.route(),
                    notifications = activityFeed,
                    loading = activityLoading,
                    loadFailed = activityFailed,
                    self = activitySelf,
                    onRetry = onRetryActivity,
                    onLeave = onLeaveActivity,
                )

                ShellScreen.HISTORY -> ShellHistoryRoute(
                    env = hosts.route(),
                    todayTracks = historyToday,
                    yesterdayTracks = historyYesterday,
                    earlierTracks = historyEarlier,
                    stats = historyStats,
                    loading = historyLoading,
                )

                ShellScreen.ADD_TRACKS -> ShellAddTracksRoute(
                    env = hosts.route(),
                    songs = addTracksSongs,
                    episodes = addTracksEpisodes,
                    recent = addTracksRecent,
                    liked = addTracksLiked,
                    loading = addTracksLoading,
                    onAddTrack = onAddTrack,
                    searchData = searchData,
                    searchLoading = searchLoading,
                    onSearchQueryChange = onSearchQueryChange,
                )

                ShellScreen.BROWSE -> ShellBrowseRoute(
                    env = hosts.route(),
                    page = browsePage,
                    onOpenBrowse = onOpenBrowse,
                    onOpenShow = onOpenShow,
                    onLoadMore = onLoadMoreBrowse,
                )

                ShellScreen.SHOW -> ShellShowRoute(
                    env = hosts.route(),
                    show = showDetail,
                    loading = showLoading,
                    onOpenShow = onOpenShow,
                    onLoadMore = onLoadMoreEpisodes,
                )

                ShellScreen.EPISODE -> ShellEpisodeRoute(
                    env = hosts.route(),
                    episode = episodeDetail,
                    loading = episodeLoading,
                    onOpenEpisode = onOpenEpisode,
                )

                ShellScreen.FOLDER -> ShellFolderRoute(
                    env = hosts.route(),
                    items = folderItems,
                    loading = folderLoading,
                    onLoadPlaylist = onLoadPlaylist,
                )

                ShellScreen.PROFILE -> ShellProfileRoute(
                    env = hosts.route(),
                    profile = profile,
                    topContent = profileTop,
                    topContentUnavailable = profileTopUnavailable,
                    contexts = profileContexts,
                    loading = profileLoading,
                    loadFailed = profileFailed,
                    onRetry = onRetryProfile,
                    onLoadProfileFollows = onLoadProfileFollows,
                )
            }
        }


        ShellPlayerLayer(env = hosts.player())
        ShellSheetsHost(env = hosts.sheets())
        ShellOverlaysHost(env = hosts.overlays())

        SpotifyToastHost(
            state = toast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = rememberBottomPadding())
        )

    }
}