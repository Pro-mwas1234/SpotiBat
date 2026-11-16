package com.mwask.bat.compose.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.backend.model.AlbumDetail
import com.mwask.bat.backend.model.ArtistDetail
import com.mwask.bat.backend.model.BrowseCard
import com.mwask.bat.backend.model.BrowsePage
import com.mwask.bat.backend.model.CollectionDetail
import com.mwask.bat.backend.model.EpisodeDetail
import com.mwask.bat.backend.model.EpisodeRow
import com.mwask.bat.backend.model.HomeChip
import com.mwask.bat.backend.model.PlaylistMember
import com.mwask.bat.backend.model.ProfileTopContent
import com.mwask.bat.backend.model.ProfileTopTrack
import com.mwask.bat.backend.model.ActivityNotification
import com.mwask.bat.backend.model.SelfProfile
import com.mwask.bat.backend.model.UserProfile
import com.mwask.bat.backend.model.SearchHit
import com.mwask.bat.backend.model.SearchKind
import com.mwask.bat.backend.model.SearchResults
import com.mwask.bat.backend.model.ShowDetail
import com.mwask.bat.backend.model.collectionDuration
import com.mwask.bat.backend.model.collectionDurationIsEstimate
import com.mwask.bat.compose.R
import com.mwask.bat.compose.components.DetailPageSkeleton
import com.mwask.bat.compose.components.EncoreChip
import com.mwask.bat.compose.components.EncoreIconButton
import com.mwask.bat.compose.components.EncoreArtworkImage
import com.mwask.bat.compose.components.TrackListSkeleton
import com.mwask.bat.compose.format.parseHexColor
import com.mwask.bat.compose.home.HomeShelf
import com.mwask.bat.compose.home.QuickAccessItem
import com.mwask.bat.compose.home.RecommendationCard
import com.mwask.bat.compose.home.EncoreHomeScreen
import com.mwask.bat.compose.home.toArtistRelease
import com.mwask.bat.compose.library.LibraryItem
import com.mwask.bat.compose.library.EncoreLibraryScreen
import com.mwask.bat.compose.playlist.EncorePlaylistScreen
import com.mwask.bat.compose.playlist.TrackItem
import com.mwask.bat.compose.screens.ActivityScreen
import com.mwask.bat.compose.screens.ArtistRelease
import com.mwask.bat.compose.screens.AppUpdate
import com.mwask.bat.compose.screens.CategoryShelf
import com.mwask.bat.compose.screens.HistoryStats
import com.mwask.bat.compose.screens.HistoryScreen
import com.mwask.bat.compose.screens.NotificationsScreen
import com.mwask.bat.compose.screens.PopularTrack
import com.mwask.bat.compose.screens.SettingsLocks
import com.mwask.bat.compose.screens.SettingsScreen
import com.mwask.bat.compose.screens.SettingsSwitches
import com.mwask.bat.compose.screens.StorageOption
import com.mwask.bat.compose.screens.EncoreAlbumScreen
import com.mwask.bat.compose.screens.EncoreArtistScreen
import com.mwask.bat.compose.screens.EncoreBrowseScreen
import com.mwask.bat.compose.screens.EncoreEpisodeScreen
import com.mwask.bat.compose.screens.EncoreNotification
import com.mwask.bat.compose.screens.EncoreProfileScreen
import com.mwask.bat.compose.screens.EncoreShowScreen
import com.mwask.bat.compose.screens.ProfileContextCard
import com.mwask.bat.compose.screens.getCategoryColor
import com.mwask.bat.compose.search.CategoryCard
import com.mwask.bat.compose.search.SearchHistoryItem
import com.mwask.bat.compose.search.EncoreSearchScreen
import com.mwask.bat.compose.sheets.EditPlaylistSheet
import com.mwask.bat.compose.sheets.LibrarySortOption
import com.mwask.bat.compose.sheets.SortDirection
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreAppTheme
import com.mwask.bat.compose.theme.EncoreTheme
import com.mwask.bat.compose.util.rememberDominantColor
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

internal data class PlayerIdentity(
    val trackLoaded: Boolean,
    val title: String,
    val artist: String,
)

@Composable
internal fun playerIdentityOf(
    title: String,
    artist: String,
    trackUri: String?,
    localTitle: String,
): PlayerIdentity = PlayerIdentity(
    trackLoaded = title.isNotBlank() ||
        trackUri?.isNotBlank() == true ||
        (trackUri == null && localTitle.isNotBlank()),
    title = title.ifBlank { stringResource(R.string.no_track) },
    artist = artist.ifBlank { stringResource(R.string.no_artist) },
)

@Composable
internal fun nowPlayingContext(
    trackLoaded: Boolean,
    origin: PlaybackOrigin?,
    contextUri: String?,
    contextLabel: String,
): Pair<String, String> {
    @Composable
    fun searchSub(query: String): String {
        val q = query.trim()
        return if (q.isBlank()) stringResource(R.string.search) else "$q in Search"
    }
    @Composable
    fun playlistContext(label: String): Pair<String, String> =
        if (label.isBlank()) stringResource(R.string.playing_from_queue) to stringResource(R.string.next_up)
        else stringResource(R.string.playing_from_playlist) to label
    val uri = contextUri.orEmpty()
    return when {
        !trackLoaded -> stringResource(R.string.nothing_playing) to stringResource(R.string.pick_a_song_to_get_started)
        origin != null -> when (origin.screen) {
            ShellScreen.SEARCH -> stringResource(R.string.playing_from_search) to searchSub(origin.label)
            ShellScreen.ALBUM -> stringResource(R.string.playing_from_album) to origin.label
            ShellScreen.ARTIST -> stringResource(R.string.playing_from_artist) to origin.label
            ShellScreen.HOME -> stringResource(R.string.playing_from_home) to origin.label
            else -> playlistContext(origin.label)
        }
        uri.startsWith("spotify:album:") -> stringResource(R.string.playing_from_album) to contextLabel
        uri.startsWith("spotify:artist:") -> stringResource(R.string.playing_from_artist) to contextLabel
        else -> playlistContext(contextLabel)
    }
}

@Composable
internal fun ShellLoadingState(loading: Boolean, onRetry: () -> Unit) {
    if (loading) {
        DetailPageSkeleton(
            modifier = Modifier
                .fillMaxSize()
                .background(EncoreColors.Background)
                .statusBarsPadding()
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EncoreColors.Background)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.couldnt_load),
                style = EncoreTheme.typography.titleMedium,
                color = EncoreColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text(text = stringResource(R.string.try_again), color = EncoreColors.Green)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewEncoreAppShell() {
    EncoreAppTheme {
        EncoreAppShell()
    }
}

@Composable
internal fun ShellSettingsPane(
    onBackClick: () -> Unit,
    onLogout: () -> Unit,
    switches: SettingsSwitches,
    locks: SettingsLocks,
    product: String,
    country: String,
    loading: Boolean,
    onToggleSetting: ((String, Boolean) -> Unit)?,
    onAppIconChange: (String) -> Unit = {},
    update: AppUpdate? = null,
    updateChecking: Boolean = false,
    updateChecked: Boolean = false,
    onCheckUpdates: () -> Unit = {},
    storageOptions: List<StorageOption> = emptyList(),
    selectedStorageId: String = "",
    onSelectStorage: ((StorageOption) -> Unit)? = null,
    themeMode: String = "system",
    themeAccent: String = "green",
    onSelectThemeMode: ((String) -> Unit)? = null,
    onSelectThemeAccent: ((String) -> Unit)? = null,
) {
    SettingsScreen(
        onBackClick = onBackClick,
        onLogout = onLogout,
        switches = switches,
        locks = locks,
        product = product,
        country = country,
        loading = loading,
        onToggleMusicVideos = { onToggleSetting?.invoke("musicVideoDisabled", !it) },
        onToggleCanvas = { onToggleSetting?.invoke("canvasDisabled", !it) },
        onToggleOtherVideos = { onToggleSetting?.invoke("otherVideoDisabled", !it) },
        onToggleShowFollows = { onToggleSetting?.invoke("showFollows", !it) },
        onToggleShowPlaylists = { onToggleSetting?.invoke("showPublishedPlaylists", !it) },
        onAppIconChange = onAppIconChange,
        update = update,
        updateChecking = updateChecking,
        updateChecked = updateChecked,
        onCheckUpdates = onCheckUpdates,
        storageOptions = storageOptions,
        selectedStorageId = selectedStorageId,
        onSelectStorage = onSelectStorage,
        themeMode = themeMode,
        themeAccent = themeAccent,
        onSelectThemeMode = onSelectThemeMode,
        onSelectThemeAccent = onSelectThemeAccent
    )
}

@Composable
internal fun ShellNotificationsPane(
    onBackClick: () -> Unit,
    notifications: List<EncoreNotification>,
    loading: Boolean,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onPlay: (EncoreNotification) -> Unit
) {
    NotificationsScreen(
        onBackClick = onBackClick,
        notifications = notifications,
        loading = loading,
        loadFailed = loadFailed,
        onRetry = onRetry,
        onPlayClick = onPlay
    )
}

@Composable
internal fun ShellActivityPane(
    onBackClick: () -> Unit,
    notifications: List<ActivityNotification>,
    loading: Boolean,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    self: SelfProfile?,
    fallbackName: String,
    fallbackAvatarUrl: String,
    onOpenNotification: (ActivityNotification) -> Unit,
    onLeave: () -> Unit = {}
) {
    /** Leaving the screen — back, row tap, anything — resets the server-side
     * read cursor the way the web player does when the panel closes. */
    val leave by rememberUpdatedState(onLeave)
    DisposableEffect(Unit) {
        onDispose { leave() }
    }
    ActivityScreen(
        onBackClick = onBackClick,
        notifications = notifications,
        loading = loading,
        loadFailed = loadFailed,
        onRetry = onRetry,
        selfName = self?.name?.takeIf { it.isNotBlank() } ?: fallbackName,
        selfAvatarUrl = self?.avatarUrl?.takeIf { it.isNotBlank() } ?: fallbackAvatarUrl,
        selfAvatarColor = self?.avatarBackgroundColor ?: 0,
        onOpenNotification = onOpenNotification
    )
}

@Composable
internal fun ShellProfilePane(
    profile: UserProfile?,
    topContent: ProfileTopContent?,
    topContentUnavailable: Boolean,
    contexts: List<ProfileContextCard>,
    loading: Boolean,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    currentTrackUri: String,
    isPlaying: Boolean,
    onFollowStatsClick: () -> Unit,
    onArtistClick: (String, String) -> Unit,
    onTrackSelect: (ProfileTopTrack) -> Unit,
    onTrackOptionsClick: (ProfileTopTrack) -> Unit,
    onTrackAddToQueue: ((ProfileTopTrack) -> Unit)? = null,
    onContextClick: (ProfileContextCard) -> Unit,
    contextsTitle: String,
    fallbackName: String,
    avatarUrl: String,
) {
    EncoreProfileScreen(
        profile = profile,
        topContent = topContent,
        topContentUnavailable = topContentUnavailable,
        contexts = contexts,
        loading = loading,
        loadFailed = loadFailed,
        onRetry = onRetry,
        onBackClick = onBack,
        currentTrackUri = currentTrackUri,
        isPlaying = isPlaying,
        onFollowStatsClick = onFollowStatsClick,
        onArtistClick = onArtistClick,
        onTrackSelect = onTrackSelect,
        onTrackOptionsClick = onTrackOptionsClick,
        onTrackAddToQueue = onTrackAddToQueue,
        onContextClick = onContextClick,
        contextsTitle = contextsTitle,
        fallbackName = fallbackName,
        avatarUrl = avatarUrl
    )
}

@Composable
internal fun ShellAlbumPane(
    activeAlbumTitle: String,
    activeAlbumArtist: String,
    activeAlbumUri: String,
    activeAlbumCoverUrl: String?,
    activeAlbumYear: String,
    activeAlbumReleaseDate: String,
    activeAlbumColor: Color,
    albumDetail: AlbumDetail?,
    albumTracks: List<TrackItem>?,
    albumLoading: Boolean,
    albumFailed: Boolean,
    albumHasMore: Boolean,
    albumLoadingMore: Boolean,
    onLoadMoreAlbumTracks: (() -> Unit)?,
    onRetryAlbum: (() -> Unit)?,
    currentTrackTitle: String,
    currentTrackUri: String,
    isPlaying: Boolean,
    playerListener: ComposePlayerListener?,
    handleBack: () -> Unit,
    handlePlayPause: () -> Unit,
    handleTrackOptions: (TrackItem) -> Unit,
    onTrackLikeToggle: ((TrackItem) -> Unit)? = null,
    onTrackAddToQueue: ((TrackItem) -> Unit)? = null,
    openArtist: (String, String) -> Unit,
    openAlbum: (String, String, String) -> Unit,
    onAlbumOptions: () -> Unit,
    albumSaved: Boolean = false,
    onAlbumLikeToggle: () -> Unit = {},
    downloadedCount: Int = 0,
    downloadedUris: Set<String> = emptySet(),
    onDownloadClick: () -> Unit = {},
    onPlaybackOrigin: (PlaybackOrigin?) -> Unit = {}
) {
    androidx.compose.runtime.key(activeAlbumTitle, activeAlbumArtist) {
        val albumDominantColor by rememberDominantColor(
            imageUrl = activeAlbumCoverUrl ?: albumDetail?.imageUrl?.takeIf { it.isNotBlank() },
            fallbackSeed = activeAlbumTitle,
            defaultColor = activeAlbumColor
        )
        LaunchedEffect(albumTracks, activeAlbumUri) {
            if (!albumTracks.isNullOrEmpty()) {
                playerListener?.onListRendered(albumTracks.map { it.uri })
            }
        }
        EncoreAlbumScreen(
            albumTitle = albumDetail?.title?.takeIf { it.isNotBlank() } ?: activeAlbumTitle,
            artistName = albumDetail?.artists?.takeIf { it.isNotBlank() } ?: activeAlbumArtist,
            releaseYear = albumDetail?.year ?: activeAlbumYear,
            releaseDate = albumDetail?.releaseDate ?: activeAlbumReleaseDate,
            coverColor = albumDetail?.colorHex?.let { parseHexColor(it) } ?: albumDominantColor,
            tracks = albumTracks ?: emptyList(),
            copyrightNotice = albumDetail?.copyright.orEmpty(),
            phonographicNotice = albumDetail?.phonographic.orEmpty(),
            currentTrackTitle = currentTrackTitle,
            currentTrackUri = currentTrackUri,
            isPlaying = isPlaying,
            isLoading = albumLoading,
            loadFailed = albumFailed,
            hasMore = albumHasMore,
            loadingMore = albumLoadingMore,
            onLoadMore = { onLoadMoreAlbumTracks?.invoke() },
            onRetry = { onRetryAlbum?.invoke() },
            downloadedCount = downloadedCount,
            downloadedUris = downloadedUris,
            onDownloadClick = onDownloadClick,
            coverUrl = activeAlbumCoverUrl ?: albumDetail?.imageUrl?.takeIf { it.isNotBlank() },
            artistImageUrl = albumDetail?.artistImageUrl?.takeIf { it.isNotBlank() },
            moreByArtist = remember(albumDetail) {
                albumDetail?.moreByArtist?.map { it.toArtistRelease() }.orEmpty()
            },
            onBackClick = handleBack,
            onPlayPauseClick = handlePlayPause,
            onTrackSelect = { track ->
                onPlaybackOrigin(PlaybackOrigin(ShellScreen.ALBUM, activeAlbumTitle))
                if (playerListener != null) {
                    if (track.uri.isNotEmpty()) {
                        val index = albumTracks.orEmpty().indexOfFirst { it.uri == track.uri }
                        if (index >= 0 && activeAlbumUri.startsWith("spotify:album:")) {
                            playerListener.onPlayTrack(track.uri, activeAlbumUri, index)
                        } else {
                            playerListener.onPlayUri(track.uri)
                        }
                    } else {
                        playerListener.onTrackSelect(track.title, track.artist)
                    }
                }
            },
            onTrackOptionsClick = handleTrackOptions,
            onTrackLikeToggle = { onTrackLikeToggle?.invoke(it) },
            onTrackAddToQueue = onTrackAddToQueue,
            onAlbumOptionsClick = onAlbumOptions,
            isSaved = albumSaved,
            onLikeToggle = onAlbumLikeToggle,
            onArtistClick = { artist -> openArtist(artist, albumDetail?.artistUri.orEmpty()) },
            onAlbumClick = { release -> openAlbum(release.title, activeAlbumArtist, release.uri) }
        )
    }
}

@Composable
internal fun ShellSearchPane(
    searchResults: List<TrackItem>?,
    searchData: SearchResults?,
    searchRecent: List<SearchHistoryItem>,
    searchSuggestions: List<TrackItem>,
    searchSuggestionHits: List<SearchHit>,
    searchLoading: Boolean,
    searchFailed: Boolean,
    searchSavedOverrides: Map<String, Boolean>,
    browseCategories: List<BrowseCard>?,
    userDisplayName: String,
    userAvatarUrl: String,
    userColorHex: String = "",
    currentTrackTitle: String,
    currentTrackUri: String,
    isPlaying: Boolean,
    searchFieldFocusRequester: FocusRequester,
    initialQuery: String,
    playerListener: ComposePlayerListener?,
    onSearchQueryChange: ((String) -> Unit)?,
    onSearchSubmit: ((String) -> Unit)?,
    onRetrySearch: () -> Unit,
    onClearRecentSearches: () -> Unit,
    onRemoveRecentSearch: (SearchHistoryItem) -> Unit,
    onRecordRecentSearch: (SearchHit) -> Unit,
    onToggleSaved: (String, Boolean) -> Unit,
    onLoadSearchSection: (suspend (SearchKind, Int) -> List<SearchHit>)?,
    onCategoryClick: (CategoryCard) -> Unit,
    onAlbumClick: (String, String, String) -> Unit,
    onArtistClick: (String, String) -> Unit,
    onPodcastClick: (String, String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onOpenShow: (String) -> Unit,
    onOpenPlaylist: (String, String) -> Unit,
    onUserClick: (String, String) -> Unit,
    onTrackSelect: (TrackItem) -> Unit,
    onTrackOptionsClick: (TrackItem) -> Unit,
    onTrackAddToQueue: ((TrackItem) -> Unit)? = null,
    onProfileClick: () -> Unit,
) {
    EncoreSearchScreen(
        onCategoryClick = onCategoryClick,
        onAlbumClick = onAlbumClick,
        onArtistClick = onArtistClick,
        onPodcastClick = onPodcastClick,
        onEpisodeClick = { _, uri -> if (uri.isNotEmpty()) onOpenEpisode(uri) },
        onShowClick = { _, uri -> if (uri.isNotEmpty()) onOpenShow(uri) },
        onUserClick = onUserClick,
        onPlaylistClick = onOpenPlaylist,
        onTrackSelect = onTrackSelect,
        onTrackOptionsClick = onTrackOptionsClick,
        onTrackAddToQueue = onTrackAddToQueue,
        onProfileClick = onProfileClick,
        searchFieldFocusRequester = searchFieldFocusRequester,
        initialQuery = initialQuery,
        searchResults = searchResults,
        searchData = searchData,
        onSearchQueryChange = { query ->
            onSearchQueryChange?.invoke(query) ?: playerListener?.onSearch(query)
        },
        onSearchSubmit = { query -> onSearchSubmit?.invoke(query) },
        recentSearches = searchRecent,
        suggestions = searchSuggestions,
        suggestionHits = searchSuggestionHits,
        loading = searchLoading,
        loadFailed = searchFailed,
        onRetry = onRetrySearch,
        onClearRecent = onClearRecentSearches,
        onRemoveRecent = onRemoveRecentSearch,
        onRecordRecent = onRecordRecentSearch,
        onToggleSaved = onToggleSaved,
        savedOverrides = searchSavedOverrides,
        onLoadSection = onLoadSearchSection,
        browseCategories = remember(browseCategories) {
            browseCategories.orEmpty().map { card ->
                CategoryCard(
                    title = card.title,
                    color = parseHexColor(card.colorHex) ?: getCategoryColor(card.title),
                    uri = card.uri,
                    imageUrl = card.imageUrl.takeIf { it.isNotBlank() },
                    subtitle = card.subtitle
                )
            }
        },
        userDisplayName = userDisplayName,
        userAvatarUrl = userAvatarUrl,
        userColorHex = userColorHex,
        currentTrackTitle = currentTrackTitle,
        currentTrackUri = currentTrackUri,
        isPlaying = isPlaying
    )
}

@Composable
internal fun ShellArtistPane(
    artistName: String,
    artistAvatarUrl: String?,
    artistUri: String,
    artistDetail: ArtistDetail?,
    artistTracks: List<PopularTrack>?,
    artistReleases: List<ArtistRelease>?,
    artistMonthlyListeners: String?,
    artistLoading: Boolean,
    artistFailed: Boolean,
    onRetryArtist: (() -> Unit)?,
    artistFollowed: Boolean?,
    artistBanned: Boolean? = null,
    onArtistUnban: ((String) -> Unit)? = null,
    currentTrackTitle: String,
    currentTrackUri: String,
    isPlaying: Boolean,
    onBackClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onTrackSelect: (TrackItem) -> Unit,
    onTrackOptionsClick: (TrackItem) -> Unit,
    onTrackLikeToggle: (TrackItem) -> Unit,
    onTrackAddToQueue: ((TrackItem) -> Unit)? = null,
    onFollowToggle: (String, Boolean) -> Unit,
    onReleaseClick: (ArtistRelease) -> Unit,
    onArtistClick: (String, String) -> Unit = { _, _ -> },
    onShelfClick: (String, String) -> Unit = { _, _ -> },
    onAboutClick: () -> Unit,
    onDiscographyClick: () -> Unit,
    onOptionsClick: () -> Unit,
) {
    androidx.compose.runtime.key(artistName) {
        EncoreArtistScreen(
            artistName = artistName,
            monthlyListeners = artistMonthlyListeners?.takeIf { it.isNotBlank() }.orEmpty(),
            heroColor = artistDetail?.colorHex?.let { parseHexColor(it) } ?: EncoreColors.Card,
            headerImageUrl = artistDetail?.headerImageUrl?.takeIf { it.isNotBlank() }
                ?: artistDetail?.imageUrl?.takeIf { it.isNotBlank() }
                ?: artistAvatarUrl,
            popularTracks = artistTracks.orEmpty(),
            releases = artistReleases.orEmpty(),
            albums = artistDetail?.albums.orEmpty().map { it.toArtistRelease() },
            singles = artistDetail?.singles.orEmpty().map { it.toArtistRelease() },
            relatedArtists = artistDetail?.relatedArtists.orEmpty(),
            appearsOn = artistDetail?.appearsOn.orEmpty(),
            featuring = artistDetail?.featuring.orEmpty(),
            discoveredOn = artistDetail?.discoveredOn.orEmpty(),
            currentTrackTitle = currentTrackTitle,
            currentTrackUri = currentTrackUri,
            isPlaying = isPlaying,
            biography = artistDetail?.biography.orEmpty(),
            verified = artistDetail?.verified == true,
            followers = artistDetail?.followers ?: 0L,
            worldRank = artistDetail?.worldRank ?: 0,
            topCities = artistDetail?.topCities.orEmpty(),
            gallery = artistDetail?.gallery.orEmpty(),
            isLoading = artistLoading,
            loadFailed = artistFailed,
            onRetry = { onRetryArtist?.invoke() },
            onBackClick = onBackClick,
            onPlayPauseClick = onPlayPauseClick,
            onTrackSelect = onTrackSelect,
            onTrackOptionsClick = onTrackOptionsClick,
            onTrackLikeToggle = onTrackLikeToggle,
            onTrackAddToQueue = onTrackAddToQueue,
            onArtistOptionsClick = onOptionsClick,
            isFollowing = artistFollowed == true,
            isBanned = artistBanned == true,
            onUnbanClick = {
                if (artistUri.startsWith("spotify:artist:")) onArtistUnban?.invoke(artistUri)
            },
            onFollowToggle = { follow ->
                if (artistUri.startsWith("spotify:artist:")) {
                    onFollowToggle(artistUri, follow)
                }
            },
            onReleaseClick = onReleaseClick,
            onArtistClick = onArtistClick,
            onShelfClick = onShelfClick,
            onAboutClick = onAboutClick,
            onDiscographyClick = onDiscographyClick
        )
    }
}

@Composable
internal fun ShellHomePane(
    quickAccessItems: List<QuickAccessItem>?,
    recommendations: List<RecommendationCard>?,
    shelves: List<HomeShelf>?,
    chips: List<HomeChip>,
    selectedFacet: String,
    greeting: String,
    loading: Boolean,
    libraryItems: List<LibraryItem>?,
    notifications: List<EncoreNotification>,
    userDisplayName: String,
    userAvatarUrl: String,
    userColorHex: String = "",
    onFacetSelected: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onArtistClick: (String, String) -> Unit,
    onCoverResolved: (String?) -> Unit,
    onPlayTrackShortcut: (String, String) -> Unit,
    onOpenAlbum: (String, String, String) -> Unit,
    onOpenShow: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onOpenPlaylist: (String, String) -> Unit,
) {
    EncoreHomeScreen(
        onPlaylistClick = { playlist, uri ->
            val foundItem = quickAccessItems?.find { it.uri == uri || it.title == playlist }
            val foundRec = recommendations?.find { it.uri == uri || it.title == playlist }
            val foundLib = libraryItems?.find { it.uri == uri || it.title == playlist }
            onCoverResolved(foundItem?.imageUrl ?: foundRec?.imageUrl ?: foundLib?.imageUrl)
            if (uri.startsWith("spotify:album:")) {
                onOpenAlbum(playlist, "", uri)
            } else if (uri.startsWith("spotify:show:")) {
                onOpenShow(uri)
            } else if (uri.startsWith("spotify:episode:")) {
                onOpenEpisode(uri)
            } else if (uri.startsWith("spotify:artist:")) {
                onArtistClick(foundItem?.title ?: playlist, uri)
            } else if (uri.startsWith("spotify:track:")) {
                onPlayTrackShortcut(uri, foundItem?.title ?: playlist)
            } else {
                onOpenPlaylist(playlist, uri)
            }
        },
        onProfileClick = onOpenProfile,
        onNotificationsClick = onOpenNotifications,
        onHistoryClick = onOpenHistory,
        onSettingsClick = onOpenSettings,
        quickAccessItems = quickAccessItems,
        recommendations = recommendations,
        shelves = shelves,
        loading = loading,
        chips = chips,
        selectedFacet = selectedFacet,
        onFacetSelected = onFacetSelected,
        greeting = greeting,
        whatsNewCount = notifications.count { it.isNew },
        onArtistClick = onArtistClick,
        userDisplayName = userDisplayName,
        userAvatarUrl = userAvatarUrl,
        userColorHex = userColorHex
    )
}

@Composable
internal fun ShellPlaylistPane(
    activePlaylistTitle: String,
    activePlaylistUri: String,
    activePlaylistCoverUrl: String?,
    playlistDetail: CollectionDetail?,
    playlistTracks: List<TrackItem>?,
    playlistLoading: Boolean,
    playlistHasMore: Boolean,
    playlistLoadingMore: Boolean,
    onLoadMorePlaylistTracks: (() -> Unit)?,
    playlistLoadFailed: Boolean,
    onRetryPlaylist: (() -> Unit)?,
    currentTrackTitle: String,
    currentTrackUri: String,
    isPlaying: Boolean,
    userDisplayName: String,
    playerListener: ComposePlayerListener?,
    playlistSaved: Boolean?,
    playlistMembers: List<PlaylistMember> = emptyList(),
    onOpenContributors: (() -> Unit)? = null,
    playlistRecommendations: List<TrackItem>,
    recommendationsLoading: Boolean,
    onRefreshRecommendations: (() -> Unit)?,
    onAddRecommended: ((TrackItem) -> Unit)?,
    onSavePlaylistOrder: ((String, List<TrackItem>) -> Unit)?,
    onEditPlaylistDetails: (suspend (String, String, String, ByteArray?, Boolean) -> Boolean)?,
    onDeletePlaylist: ((String) -> Unit)?,
    onTogglePlaylistSaved: ((String, Boolean) -> Unit)?,
    onTrackLikeToggle: ((TrackItem) -> Unit)? = null,
    onTrackAddToQueue: ((TrackItem) -> Unit)? = null,
    handleBack: () -> Unit,
    handleTrackOptions: (TrackItem) -> Unit,
    shareEntity: (String) -> Unit,
    onLocalPlayPause: () -> Unit,
    onLocalTrack: (TrackItem) -> Unit,
    onPlaylistOptions: () -> Unit,
    onEditModeChange: (Boolean) -> Unit = {},
    onChangeTitle: (() -> Unit)? = null,
    onAddTracksClick: (() -> Unit)? = null,
    onEditCoverClick: (() -> Unit)? = null,
    downloadedCount: Int = 0,
    downloadedUris: Set<String> = emptySet(),
    onDownloadClick: () -> Unit = {},
    onPlaybackOrigin: (PlaybackOrigin?) -> Unit = {}
) {
    androidx.compose.runtime.key(activePlaylistUri.ifEmpty { activePlaylistTitle }) {
        LaunchedEffect(playlistTracks, activePlaylistUri) {
            if (!playlistTracks.isNullOrEmpty()) {
                playerListener?.onListRendered(playlistTracks.map { it.uri })
            }
        }
        val resolvedPlaylistCover = activePlaylistCoverUrl ?: playlistTracks?.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl
        val playlistTotal = playlistDetail?.total ?: 0
        val playlistDurations = remember(playlistTracks) {
            playlistTracks.orEmpty().map { it.durationMs }
        }
        val playlistDurationMs = remember(playlistDurations, playlistTotal) {
            collectionDuration(playlistDurations, playlistTotal)
        }
        val playlistDurationIsEstimate = remember(playlistDurations, playlistTotal) {
            collectionDurationIsEstimate(playlistDurations, playlistTotal)
        }
        val playlistDominantColor by rememberDominantColor(
            imageUrl = resolvedPlaylistCover,
            fallbackSeed = activePlaylistTitle,
            defaultColor = Color(0xFF144D3F)
        )
        EncorePlaylistScreen(
            title = playlistDetail?.title?.takeIf { it.isNotBlank() } ?: activePlaylistTitle,
            description = playlistDetail?.description.orEmpty(),
            creator = playlistDetail?.creator?.takeIf { it.isNotBlank() } ?: if (activePlaylistUri.startsWith("spotiduck:local:playlist:") || activePlaylistUri.isEmpty()) userDisplayName else "",
            creatorAvatarUrl = playlistDetail?.creatorAvatarUrl.orEmpty(),
            saveCount = playlistDetail?.saveCount.orEmpty(),
            totalDurationMs = playlistDurationMs,
            totalIsEstimate = playlistDurationIsEstimate,
            tracks = playlistTracks ?: emptyList(),
            loading = playlistLoading,
            hasMore = playlistHasMore,
            loadingMore = playlistLoadingMore,
            onLoadMore = { onLoadMorePlaylistTracks?.invoke() },
            loadFailed = playlistLoadFailed,
            onRetry = { onRetryPlaylist?.invoke() },
            downloadedCount = downloadedCount,
            downloadedUris = downloadedUris,
            onDownloadClick = onDownloadClick,
            currentTrackTitle = currentTrackTitle,
            currentTrackUri = currentTrackUri,
            isPlaying = isPlaying,
            headerColor = playlistDetail?.colorHex?.let { parseHexColor(it) } ?: playlistDominantColor,
            coverUrl = activePlaylistCoverUrl
                ?: playlistDetail?.imageUrl?.takeIf { it.isNotBlank() }
                ?: playlistTracks?.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl,
            contributors = playlistMembers,
            // The contributors sheet loads its members from the playlist-permission
            // API, so it only makes sense for real Spotify playlists — local ones
            // keep a static author row instead of a dead tap.
            onContributorsClick = if (activePlaylistUri.startsWith("spotify:playlist:")) {
                { onOpenContributors?.invoke() }
            } else {
                null
            },
            onBackClick = handleBack,
            onPlayPauseClick = {
                if (playerListener != null) {
                    if (activePlaylistUri.isNotEmpty() && !isPlaying) {
                        onPlaybackOrigin(PlaybackOrigin(ShellScreen.PLAYLIST, activePlaylistTitle))
                        val firstTrack = playlistTracks
                            ?.firstOrNull { it.uri.startsWith("spotify:track:") }
                        if (firstTrack != null && activePlaylistUri.startsWith("spotify:playlist:")) {
                            playerListener.onPlayTrack(firstTrack.uri, activePlaylistUri, 0)
                        } else {
                            playerListener.onPlayUri(activePlaylistUri)
                        }
                    } else {
                        playerListener.onPlayPause()
                    }
                } else {
                    onPlaybackOrigin(PlaybackOrigin(ShellScreen.PLAYLIST, activePlaylistTitle))
                    onLocalPlayPause()
                }
            },
            onTrackSelect = { track ->
                onPlaybackOrigin(PlaybackOrigin(ShellScreen.PLAYLIST, activePlaylistTitle))
                if (playerListener != null) {
                    if (track.uri.isNotEmpty()) {
                        val index = playlistTracks.orEmpty().indexOfFirst { it.uri == track.uri }
                        if (index >= 0 && activePlaylistUri.startsWith("spotify:playlist:")) {
                            playerListener.onPlayTrack(track.uri, activePlaylistUri, index)
                        } else {
                            playerListener.onPlayUri(track.uri)
                        }
                    } else {
                        playerListener.onTrackSelect(track.title, track.artist)
                    }
                } else {
                    onLocalTrack(track)
                }
            },
            onTrackOptionsClick = handleTrackOptions,
            onTrackLikeToggle = { onTrackLikeToggle?.invoke(it) },
            onTrackAddToQueue = onTrackAddToQueue,
            onPlaylistOptionsClick = onPlaylistOptions,
            onShareClick = {
                if (activePlaylistUri.isNotEmpty()) shareEntity(activePlaylistUri)
            },
            isSaved = playlistSaved,
            onSaveToggle = {
                if (activePlaylistUri.startsWith("spotify:playlist:") && activePlaylistUri.isNotEmpty()) {
                    onTogglePlaylistSaved?.invoke(activePlaylistUri, !(playlistSaved ?: false))
                }
            },
            recommendedTracks = playlistRecommendations,
            recommendationsLoading = recommendationsLoading,
            onRecommendRefresh = { onRefreshRecommendations?.invoke() },
            onAddRecommended = { track -> onAddRecommended?.invoke(track) },
            onChangeTitle = onChangeTitle,
            onAddTracksClick = onAddTracksClick,
            onEditCoverClick = onEditCoverClick,
            onEditModeChange = onEditModeChange,
            onSaveOrder = { order -> onSavePlaylistOrder?.invoke(activePlaylistUri, order) },
            onEditDetails = { name, description, cover, clearCover ->
                onEditPlaylistDetails?.invoke(activePlaylistUri, name, description, cover, clearCover)
            },
            onDeletePlaylist = { onDeletePlaylist?.invoke(activePlaylistUri) },
            isEditable = playlistDetail?.editable == true,
            canAddTracks = playlistDetail?.editable == true || activePlaylistUri.startsWith("spotify:collection:")
        )
    }
}

@Composable
internal fun ShellHistoryPane(
    onBackClick: () -> Unit,
    onTrackSelect: (TrackItem) -> Unit,
    onTrackOptionsClick: (TrackItem) -> Unit,
    onTrackAddToQueue: (TrackItem) -> Unit = {},
    todayTracks: List<TrackItem>,
    yesterdayTracks: List<TrackItem>,
    earlierTracks: List<TrackItem>,
    stats: HistoryStats? = null,
    loading: Boolean,
    currentTrackTitle: String,
    isPlaying: Boolean
) {
    HistoryScreen(
        onBackClick = onBackClick,
        onTrackSelect = onTrackSelect,
        onTrackOptionsClick = onTrackOptionsClick,
        onTrackAddToQueue = onTrackAddToQueue,
        todayTracks = todayTracks,
        yesterdayTracks = yesterdayTracks,
        earlierTracks = earlierTracks,
        stats = stats,
        loading = loading,
        currentTrackTitle = currentTrackTitle,
        isPlaying = isPlaying
    )
}

@Composable
internal fun ShellBrowsePane(
    page: BrowsePage?,
    fallbackTitle: String,
    onBack: () -> Unit,
    onOpenBrowse: (String, String) -> Unit,
    onOpenShow: (String) -> Unit,
    onOpenAlbum: (String, String) -> Unit,
    onOpenPlaylist: (String, String) -> Unit,
    onLoadMore: () -> Unit
) {
    if (page == null) {
        EncoreBrowseScreen(
            title = fallbackTitle,
            subtitle = "",
            colorHex = "",
            sections = emptyList(),
            isLoading = true,
            isLoadingMore = false,
            hasMore = false,
            onBackClick = onBack,
            onCardClick = { _, _ -> },
            onLoadMore = {}
        )
    } else {
        EncoreBrowseScreen(
            title = page.title.ifBlank { fallbackTitle },
            subtitle = page.subtitle,
            colorHex = page.colorHex,
            sections = page.sections,
            isLoading = false,
            isLoadingMore = false,
            hasMore = page.nextOffset != null,
            onBackClick = onBack,
            onCardClick = { title, uri ->
                when {
                    uri.startsWith("spotify:show:") -> onOpenShow(uri)
                    uri.startsWith("spotify:page:") -> onOpenBrowse(uri, title)
                    uri.startsWith("spotify:album:") -> onOpenAlbum(title, uri)
                    uri.startsWith("spotify:") -> onOpenPlaylist(title, uri)
                    else -> Unit
                }
            },
            onLoadMore = onLoadMore
        )
    }
}

@Composable
internal fun ShellShowPane(
    show: ShowDetail?,
    loading: Boolean,
    retryUri: String,
    isPlaying: Boolean,
    currentTrackTitle: String,
    onBack: () -> Unit,
    onRetry: (String) -> Unit,
    onEpisodeClick: (String) -> Unit,
    onPlayEpisode: (EpisodeRow) -> Unit = {},
    onPlayPause: () -> Unit,
    onLoadMore: () -> Unit,
    onFollowToggle: () -> Unit = {},
    onMoreClick: (() -> Unit)? = null
) {
    if (show == null) {
        ShellLoadingState(
            loading = loading,
            onRetry = { onRetry(retryUri) }
        )
    } else {
        EncoreShowScreen(
            showName = show.name,
            publisher = show.publisher,
            description = show.description,
            imageUrl = show.imageUrl.takeIf { it.isNotBlank() },
            headerColor = parseHexColor(show.colorHex) ?: EncoreColors.Card,
            rating = show.rating,
            ratingCount = show.ratingCount,
            topics = show.topics,
            isFollowing = show.saved,
            onFollowToggle = onFollowToggle,
            episodes = show.episodes,
            total = show.total,
            isPlaying = isPlaying,
            currentTrackTitle = currentTrackTitle,
            onEpisodeClick = { episode -> onEpisodeClick(episode.uri) },
            onPlayEpisode = onPlayEpisode,
            onBackClick = onBack,
            onPlayPauseClick = onPlayPause,
            onLoadMore = onLoadMore,
            onMoreClick = onMoreClick
        )
    }
}

@Composable
internal fun ShellEpisodePane(
    episode: EpisodeDetail?,
    loading: Boolean,
    retryUri: String,
    isPlaying: Boolean,
    isCurrent: Boolean,
    onBack: () -> Unit,
    onRetry: (String) -> Unit,
    onPlayPause: () -> Unit,
    onPlay: () -> Unit = {},
    onShowClick: (String) -> Unit
) {
    if (episode == null) {
        ShellLoadingState(
            loading = loading,
            onRetry = { onRetry(retryUri) }
        )
    } else {
        EncoreEpisodeScreen(
            title = episode.title,
            showName = episode.showName,
            description = episode.description,
            imageUrl = episode.imageUrl.takeIf { it.isNotBlank() },
            wideImageUrl = episode.wideImageUrl.takeIf { it.isNotBlank() },
            headerColor = parseHexColor(episode.colorHex) ?: EncoreColors.Card,
            metaLabel = episode.releaseDate,
            durationMs = episode.durationMs,
            positionMs = episode.positionMs,
            isVideo = episode.video,
            isPlaying = isPlaying,
            isCurrent = isCurrent,
            onBackClick = onBack,
            onPlayPauseClick = onPlayPause,
            onPlayClick = onPlay,
            onShowClick = { onShowClick(episode.showUri) }
        )
    }
}

@Composable
internal fun ShellFolderPane(
    items: List<LibraryItem>?,
    loading: Boolean,
    title: String,
    userDisplayName: String,
    userAvatarUrl: String,
    userColorHex: String = "",
    onItemClick: (String, String) -> Unit,
    onItemLongPress: (LibraryItem) -> Unit,
    onFolderClick: (LibraryItem) -> Unit,
    onProfileClick: () -> Unit,
    onPlusClick: () -> Unit = {},
    onSortClick: () -> Unit = {},
    currentSort: LibrarySortOption = LibrarySortOption.RECENTS,
    sortDirection: SortDirection = SortDirection.DESCENDING,
    onBackClick: () -> Unit = {},
    onFolderOptionsClick: () -> Unit = {},
    onPlayClick: () -> Unit = {}
) {
    EncoreLibraryScreen(
        onItemClick = onItemClick,
        onItemLongPress = onItemLongPress,
        onFolderClick = onFolderClick,
        items = items,
        loading = loading,
        title = title,
        userDisplayName = userDisplayName,
        userAvatarUrl = userAvatarUrl,
        userColorHex = userColorHex,
        onProfileClick = onProfileClick,
        onPlusClick = onPlusClick,
        onSortClick = onSortClick,
        currentSort = currentSort,
        sortDirection = sortDirection,
        isFolder = true,
        onBackClick = onBackClick,
        onFolderOptionsClick = onFolderOptionsClick,
        onPlayClick = onPlayClick
    )
}

@Composable
internal fun ShellPlaylistEditSheets(
    editDraft: PlaylistEditDraft?,
    onEditSave: (suspend (String, String, String, ByteArray?, Boolean) -> Boolean)?,
    onDismissEdit: () -> Unit,
    onDeletePlaylist: (suspend (String) -> Boolean)?,
    onDeleteAsk: (String, String, () -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    val editDeleteTitle = stringResource(R.string.delete_playlist_title)
    val editDeleteSuffix = stringResource(R.string.will_be_deleted_followers_lose_access)
    if (editDraft != null) {
        EditPlaylistSheet(
            isOpen = true,
            initialName = editDraft.name,
            initialDescription = editDraft.description,
            coverUrl = editDraft.coverUrl,
            canRemoveCover = editDraft.canRemoveCover,
            onSave = { name, description, cover, clearCover ->
                val uri = editDraft.uri
                scope.launch {
                    val ok = onEditSave?.invoke(uri, name, description, cover, clearCover) ?: false
                    if (ok) onDismissEdit()
                }
            },
            onDeletePlaylist = {
                onDismissEdit()
                onDeleteAsk(
                    editDeleteTitle,
                    "\"${editDraft.name}\"" + editDeleteSuffix,
                ) { scope.launch { onDeletePlaylist?.invoke(editDraft.uri) } }
            },
            onDismiss = onDismissEdit
        )
    }
}

private enum class AddTracksFilter(val labelRes: Int) {
    SONGS(R.string.songs),
    EPISODES(R.string.episodes),
    RECENT(R.string.recently_played),
    LIKED(R.string.from_your_liked_songs)
}

private enum class AssistedSearchCategory(val labelRes: Int) {
    SONGS(R.string.songs),
    ALBUMS(R.string.albums),
    PLAYLISTS(R.string.playlists),
    ARTISTS(R.string.artists),
    EPISODES(R.string.episodes),
    PODCASTS(R.string.podcasts)
}

@Composable
internal fun ShellAddTracksPane(
    targetTitle: String,
    songs: List<TrackItem>,
    episodes: List<TrackItem>,
    recent: List<TrackItem>,
    liked: List<TrackItem>,
    loading: Boolean,
    onBack: () -> Unit,
    onAdd: suspend (String) -> Boolean,
    searchData: SearchResults? = null,
    searchLoading: Boolean = false,
    onSearchQueryChange: ((String) -> Unit)? = null,
    onOpenAlbum: ((uri: String, title: String, artist: String) -> Unit)? = null,
    onOpenArtist: ((uri: String, name: String) -> Unit)? = null,
    onOpenPlaylist: ((uri: String, title: String) -> Unit)? = null,
    onOpenShow: ((uri: String) -> Unit)? = null,
    onOpenEpisode: ((uri: String) -> Unit)? = null,
) {
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQueryText by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AddTracksFilter.SONGS) }
    var searchCategory by remember { mutableStateOf<AssistedSearchCategory?>(null) }
    val added = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler(enabled = isSearchMode) {
        isSearchMode = false
        searchQueryText = ""
        onSearchQueryChange?.invoke("")
        keyboardController?.hide()
    }

    LaunchedEffect(isSearchMode) {
        if (isSearchMode) {
            searchFocusRequester.requestFocus()
        }
    }

    val defaultItems = when (filter) {
        AddTracksFilter.SONGS -> songs
        AddTracksFilter.EPISODES -> episodes
        AddTracksFilter.RECENT -> recent
        AddTracksFilter.LIKED -> liked
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EncoreColors.Background)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TOOLBAR (56.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                EncoreIconButton(
                    iconRes = R.drawable.encore_icon_arrow_left_24,
                    contentDescription = stringResource(R.string.back),
                    onClick = {
                        if (isSearchMode) {
                            isSearchMode = false
                            searchQueryText = ""
                            onSearchQueryChange?.invoke("")
                            keyboardController?.hide()
                        } else {
                            onBack()
                        }
                    },
                    tint = EncoreColors.TextPrimary
                )
                Text(
                    text = if (isSearchMode) "Find" else stringResource(R.string.add_to_this_playlist),
                    style = EncoreTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = EncoreColors.TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 60.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isSearchMode) {
                // DEFAULT ASSISTED CURATION MODE
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AddTracksFilter.entries.forEach { option ->
                        EncoreChip(
                            text = stringResource(option.labelRes),
                            isSelected = option == filter,
                            onClick = { filter = option }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                when {
                    loading -> TrackListSkeleton(
                        rows = 9,
                        modifier = Modifier.fillMaxSize()
                    )
                    defaultItems.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.nothing_here_yet),
                            color = EncoreColors.TextSecondary
                        )
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(defaultItems, key = { it.uri }) { item ->
                            AddTrackRow(
                                item = item,
                                added = item.uri in added,
                                onAdd = {
                                    scope.launch {
                                        if (onAdd(item.uri)) added.add(item.uri)
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // SEARCH / FIND MODE
                if (searchQueryText.isBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(bottom = 68.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.find_titles_you_ll_love),
                                style = EncoreTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    color = EncoreColors.TextPrimary
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.search_for_anything),
                                style = EncoreTheme.typography.bodyMedium.copy(
                                    color = EncoreColors.TextSecondary,
                                    fontSize = 14.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Category filter chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistedSearchCategory.entries.forEach { category ->
                            EncoreChip(
                                text = stringResource(category.labelRes),
                                isSelected = category == searchCategory,
                                onClick = {
                                    searchCategory = if (searchCategory == category) null else category
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val queryClean = searchQueryText.trim()
                    val cat = searchCategory
                    if (cat == null) {
                        // ALL / MIXED SEARCH RESULTS
                        val remoteTracks = searchData?.tracks ?: emptyList()
                        val remoteAlbums = searchData?.albums ?: emptyList()
                        val remotePlaylists = searchData?.playlists ?: emptyList()
                        val remoteArtists = searchData?.artists ?: emptyList()
                        val remoteEpisodes = searchData?.others?.filter { it.kind == SearchKind.EPISODE } ?: emptyList()
                        val remoteShows = searchData?.others?.filter { it.kind == SearchKind.SHOW } ?: emptyList()

                        val localMatches = (songs + liked + recent)
                            .distinctBy { it.uri }
                            .filter {
                                it.title.contains(queryClean, ignoreCase = true) ||
                                it.artist.contains(queryClean, ignoreCase = true)
                            }
                            .filter { local -> remoteTracks.none { it.uri == local.uri } }

                        val hasAny = remoteTracks.isNotEmpty() || remoteAlbums.isNotEmpty() ||
                            remotePlaylists.isNotEmpty() || remoteArtists.isNotEmpty() ||
                            remoteEpisodes.isNotEmpty() || remoteShows.isNotEmpty() || localMatches.isNotEmpty()

                        if (searchLoading && !hasAny) {
                            TrackListSkeleton(rows = 8, modifier = Modifier.fillMaxSize())
                        } else if (!hasAny) {
                            SearchEmptyState(query = searchQueryText)
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .imePadding(),
                                contentPadding = PaddingValues(bottom = 76.dp)
                            ) {
                                // Interleaved mixed results matching Spotify
                                items(remoteTracks, key = { it.uri }) { hit ->
                                    AssistedSearchTrackHitRow(
                                        hit = hit,
                                        added = hit.uri in added,
                                        onAdd = {
                                            scope.launch {
                                                if (onAdd(hit.uri)) added.add(hit.uri)
                                            }
                                        }
                                    )
                                }
                                items(localMatches, key = { it.uri }) { item ->
                                    AddTrackRow(
                                        item = item,
                                        added = item.uri in added,
                                        onAdd = {
                                            scope.launch {
                                                if (onAdd(item.uri)) added.add(item.uri)
                                            }
                                        }
                                    )
                                }
                                items(remoteAlbums, key = { it.uri }) { hit ->
                                    AssistedSearchNavRow(
                                        title = hit.title,
                                        subtitle = if (hit.subtitle.isNotBlank()) "Album • ${hit.subtitle}" else stringResource(R.string.album),
                                        imageUrl = hit.imageUrl,
                                        isCircular = false,
                                        onClick = { onOpenAlbum?.invoke(hit.uri, hit.title, hit.subtitle) }
                                    )
                                }
                                items(remotePlaylists, key = { it.uri }) { hit ->
                                    AssistedSearchNavRow(
                                        title = hit.title,
                                        subtitle = if (hit.subtitle.isNotBlank()) "Playlist • ${hit.subtitle}" else stringResource(R.string.playlist),
                                        imageUrl = hit.imageUrl,
                                        isCircular = false,
                                        onClick = { onOpenPlaylist?.invoke(hit.uri, hit.title) }
                                    )
                                }
                                items(remoteArtists, key = { it.uri }) { hit ->
                                    AssistedSearchNavRow(
                                        title = hit.title,
                                        subtitle = stringResource(R.string.artist),
                                        imageUrl = hit.imageUrl,
                                        isCircular = true,
                                        onClick = { onOpenArtist?.invoke(hit.uri, hit.title) }
                                    )
                                }
                                items(remoteEpisodes, key = { it.uri }) { hit ->
                                    AssistedSearchTrackHitRow(
                                        hit = hit,
                                        added = hit.uri in added,
                                        onAdd = {
                                            scope.launch {
                                                if (onAdd(hit.uri)) added.add(hit.uri)
                                            }
                                        }
                                    )
                                }
                                items(remoteShows, key = { it.uri }) { hit ->
                                    AssistedSearchNavRow(
                                        title = hit.title,
                                        subtitle = if (hit.subtitle.isNotBlank()) "Podcast • ${hit.subtitle}" else stringResource(R.string.podcast),
                                        imageUrl = hit.imageUrl,
                                        isCircular = false,
                                        onClick = { onOpenShow?.invoke(hit.uri) }
                                    )
                                }
                            }
                        }
                    } else when (cat) {
                        AssistedSearchCategory.SONGS -> {
                            val remoteHits = searchData?.tracks ?: emptyList()
                            val localMatches = (songs + liked + recent)
                                .distinctBy { it.uri }
                                .filter {
                                    it.title.contains(queryClean, ignoreCase = true) ||
                                    it.artist.contains(queryClean, ignoreCase = true)
                                }
                                .filter { local -> remoteHits.none { it.uri == local.uri } }

                            if (searchLoading && remoteHits.isEmpty() && localMatches.isEmpty()) {
                                TrackListSkeleton(rows = 8, modifier = Modifier.fillMaxSize())
                            } else if (remoteHits.isEmpty() && localMatches.isEmpty()) {
                                SearchEmptyState(query = searchQueryText)
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .imePadding(),
                                    contentPadding = PaddingValues(bottom = 76.dp)
                                ) {
                                    items(remoteHits, key = { it.uri }) { hit ->
                                        AssistedSearchTrackHitRow(
                                            hit = hit,
                                            added = hit.uri in added,
                                            onAdd = {
                                                scope.launch {
                                                    if (onAdd(hit.uri)) added.add(hit.uri)
                                                }
                                            }
                                        )
                                    }
                                    items(localMatches, key = { it.uri }) { item ->
                                        AddTrackRow(
                                            item = item,
                                            added = item.uri in added,
                                            onAdd = {
                                                scope.launch {
                                                    if (onAdd(item.uri)) added.add(item.uri)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        AssistedSearchCategory.ALBUMS -> {
                            val albums = searchData?.albums ?: emptyList()
                            if (searchLoading && albums.isEmpty()) {
                                TrackListSkeleton(rows = 8, modifier = Modifier.fillMaxSize())
                            } else if (albums.isEmpty()) {
                                SearchEmptyState(query = searchQueryText)
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .imePadding(),
                                    contentPadding = PaddingValues(bottom = 76.dp)
                                ) {
                                    items(albums, key = { it.uri }) { hit ->
                                        AssistedSearchNavRow(
                                            title = hit.title,
                                            subtitle = if (hit.subtitle.isNotBlank()) "Album • ${hit.subtitle}" else stringResource(R.string.album),
                                            imageUrl = hit.imageUrl,
                                            isCircular = false,
                                            onClick = { onOpenAlbum?.invoke(hit.uri, hit.title, hit.subtitle) }
                                        )
                                    }
                                }
                            }
                        }
                        AssistedSearchCategory.PLAYLISTS -> {
                            val playlists = searchData?.playlists ?: emptyList()
                            if (searchLoading && playlists.isEmpty()) {
                                TrackListSkeleton(rows = 8, modifier = Modifier.fillMaxSize())
                            } else if (playlists.isEmpty()) {
                                SearchEmptyState(query = searchQueryText)
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .imePadding(),
                                    contentPadding = PaddingValues(bottom = 76.dp)
                                ) {
                                    items(playlists, key = { it.uri }) { hit ->
                                        AssistedSearchNavRow(
                                            title = hit.title,
                                            subtitle = if (hit.subtitle.isNotBlank()) "Playlist • ${hit.subtitle}" else stringResource(R.string.playlist),
                                            imageUrl = hit.imageUrl,
                                            isCircular = false,
                                            onClick = { onOpenPlaylist?.invoke(hit.uri, hit.title) }
                                        )
                                    }
                                }
                            }
                        }
                        AssistedSearchCategory.ARTISTS -> {
                            val artists = searchData?.artists ?: emptyList()
                            if (searchLoading && artists.isEmpty()) {
                                TrackListSkeleton(rows = 8, modifier = Modifier.fillMaxSize())
                            } else if (artists.isEmpty()) {
                                SearchEmptyState(query = searchQueryText)
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .imePadding(),
                                    contentPadding = PaddingValues(bottom = 76.dp)
                                ) {
                                    items(artists, key = { it.uri }) { hit ->
                                        AssistedSearchNavRow(
                                            title = hit.title,
                                            subtitle = stringResource(R.string.artist),
                                            imageUrl = hit.imageUrl,
                                            isCircular = true,
                                            onClick = { onOpenArtist?.invoke(hit.uri, hit.title) }
                                        )
                                    }
                                }
                            }
                        }
                        AssistedSearchCategory.EPISODES -> {
                            val remoteEpisodes = searchData?.others?.filter { it.kind == SearchKind.EPISODE } ?: emptyList()
                            val localEpisodes = episodes.filter {
                                it.title.contains(queryClean, ignoreCase = true) ||
                                it.artist.contains(queryClean, ignoreCase = true)
                            }.filter { local -> remoteEpisodes.none { it.uri == local.uri } }

                            if (searchLoading && remoteEpisodes.isEmpty() && localEpisodes.isEmpty()) {
                                TrackListSkeleton(rows = 8, modifier = Modifier.fillMaxSize())
                            } else if (remoteEpisodes.isEmpty() && localEpisodes.isEmpty()) {
                                SearchEmptyState(query = searchQueryText)
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .imePadding(),
                                    contentPadding = PaddingValues(bottom = 76.dp)
                                ) {
                                    items(remoteEpisodes, key = { it.uri }) { hit ->
                                        AssistedSearchTrackHitRow(
                                            hit = hit,
                                            added = hit.uri in added,
                                            onAdd = {
                                                scope.launch {
                                                    if (onAdd(hit.uri)) added.add(hit.uri)
                                                }
                                            }
                                        )
                                    }
                                    items(localEpisodes, key = { it.uri }) { item ->
                                        AddTrackRow(
                                            item = item,
                                            added = item.uri in added,
                                            onAdd = {
                                                scope.launch {
                                                    if (onAdd(item.uri)) added.add(item.uri)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        AssistedSearchCategory.PODCASTS -> {
                            val podcasts = searchData?.others?.filter { it.kind == SearchKind.SHOW } ?: emptyList()
                            if (searchLoading && podcasts.isEmpty()) {
                                TrackListSkeleton(rows = 8, modifier = Modifier.fillMaxSize())
                            } else if (podcasts.isEmpty()) {
                                SearchEmptyState(query = searchQueryText)
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .imePadding(),
                                    contentPadding = PaddingValues(bottom = 76.dp)
                                ) {
                                    items(podcasts, key = { it.uri }) { hit ->
                                        AssistedSearchNavRow(
                                            title = hit.title,
                                            subtitle = if (hit.subtitle.isNotBlank()) "Podcast • ${hit.subtitle}" else stringResource(R.string.podcast),
                                            imageUrl = hit.imageUrl,
                                            isCircular = false,
                                            onClick = { onOpenShow?.invoke(hit.uri) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // FLOATING SEARCH PILL
        if (!isSearchMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF282828))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    .clickable { isSearchMode = true }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_search_24),
                        contentDescription = stringResource(R.string.search),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.what_would_you_like_to_add),
                        style = EncoreTheme.typography.bodyMedium.copy(
                            color = EncoreColors.TextSecondary,
                            fontSize = 14.sp
                        )
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF282828))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_search_24),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (searchQueryText.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search),
                                style = EncoreTheme.typography.bodyMedium.copy(
                                    color = EncoreColors.TextSecondary,
                                    fontSize = 15.sp
                                )
                            )
                        }
                        BasicTextField(
                            value = searchQueryText,
                            onValueChange = {
                                searchQueryText = it
                                onSearchQueryChange?.invoke(it)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                            textStyle = EncoreTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(EncoreColors.Green),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboardController?.hide()
                            })
                        )
                    }
                    if (searchQueryText.isNotEmpty()) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_x_24),
                            contentDescription = stringResource(R.string.clear_search),
                            tint = Color.White,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    searchQueryText = ""
                                    onSearchQueryChange?.invoke("")
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyState(query: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .padding(bottom = 68.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.couldnt_find_query, query),
                style = EncoreTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = EncoreColors.TextPrimary
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.try_searching_again_using_a_different_spelling_o),
                style = EncoreTheme.typography.bodyMedium.copy(
                    color = EncoreColors.TextSecondary,
                    fontSize = 14.sp
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AssistedSearchTrackHitRow(
    hit: SearchHit,
    added: Boolean,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            EncoreArtworkImage(
                imageUrl = hit.imageUrl,
                contentDescription = hit.title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 4.dp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hit.title,
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = EncoreColors.TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = if (hit.subtitle.isNotBlank()) "Song • ${hit.subtitle}" else stringResource(R.string.song)
            Text(
                text = sub,
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .border(
                    1.dp,
                    if (added) EncoreColors.Green else Color.White.copy(alpha = 0.35f),
                    CircleShape
                )
                .clickable { if (!added) onAdd() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = if (added) R.drawable.encore_icon_check_24 else R.drawable.encore_icon_plus_24),
                contentDescription = if (added) "Added" else stringResource(R.string.add),
                tint = if (added) EncoreColors.Green else Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AssistedSearchNavRow(
    title: String,
    subtitle: String,
    imageUrl: String,
    isCircular: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(if (isCircular) CircleShape else RoundedCornerShape(4.dp))
        ) {
            EncoreArtworkImage(
                imageUrl = imageUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = if (isCircular) 24.dp else 4.dp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = EncoreColors.TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            painter = painterResource(id = R.drawable.encore_icon_chevron_right_24),
            contentDescription = null,
            tint = EncoreColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AddTrackRow(
    item: TrackItem,
    added: Boolean,
    onAdd: () -> Unit
) {
    val isBanned = com.mwask.bat.compose.bans.ArtistBans.isBanned(item.artistUri)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .graphicsLayer { alpha = if (isBanned) 0.4f else 1f }
        ) {
            EncoreArtworkImage(
                imageUrl = item.imageUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 4.dp
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_play_16),
                    contentDescription = stringResource(R.string.preview),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (isBanned) EncoreColors.TextSecondary.copy(alpha = 0.55f)
                    else EncoreColors.TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.artist.isNotBlank()) {
                Text(
                    text = item.artist,
                    style = EncoreTheme.typography.bodySmall.copy(
                        color = EncoreColors.TextSecondary.takeIf { !isBanned }
                            ?: EncoreColors.TextSecondary.copy(alpha = 0.55f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .border(
                    1.dp,
                    if (added) EncoreColors.Green else Color.White.copy(alpha = 0.35f),
                    CircleShape
                )
                .clickable { if (!added) onAdd() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = if (added) R.drawable.encore_icon_check_24 else R.drawable.encore_icon_plus_24),
                contentDescription = if (added) "Added" else stringResource(R.string.add),
                tint = if (added) EncoreColors.Green else Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
