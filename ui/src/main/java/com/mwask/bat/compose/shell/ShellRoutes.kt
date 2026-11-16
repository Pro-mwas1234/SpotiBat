package com.mwask.bat.compose.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import com.mwask.bat.backend.model.ActivityNotification
import com.mwask.bat.backend.model.AlbumDetail
import com.mwask.bat.backend.model.ArtistDetail
import com.mwask.bat.backend.model.BrowseCard
import com.mwask.bat.backend.model.BrowsePage
import com.mwask.bat.backend.model.CollectionDetail
import com.mwask.bat.backend.model.EpisodeDetail
import com.mwask.bat.backend.model.HomeChip
import com.mwask.bat.backend.model.PlaylistPermissions
import com.mwask.bat.backend.model.ProfileTopContent
import com.mwask.bat.backend.model.SearchHit
import com.mwask.bat.backend.model.SearchKind
import com.mwask.bat.backend.model.SearchResults
import com.mwask.bat.backend.model.SelfProfile
import com.mwask.bat.backend.model.ShowDetail
import com.mwask.bat.backend.model.UserProfile
import com.mwask.bat.compose.home.HomeShelf
import com.mwask.bat.compose.home.QuickAccessItem
import com.mwask.bat.compose.home.RecommendationCard
import com.mwask.bat.compose.library.DownloadedTrack
import com.mwask.bat.compose.library.EncoreLibraryScreen
import com.mwask.bat.compose.library.LibraryItem
import com.mwask.bat.compose.library.LibraryItemType
import com.mwask.bat.compose.playlist.TrackItem
import com.mwask.bat.compose.screens.AppUpdate
import com.mwask.bat.compose.screens.ArtistRelease
import com.mwask.bat.compose.screens.CategoryShelf
import com.mwask.bat.compose.screens.EncoreCategoryScreen
import com.mwask.bat.compose.screens.EncoreNotification
import com.mwask.bat.compose.screens.HistoryStats
import com.mwask.bat.compose.screens.PopularTrack
import com.mwask.bat.compose.screens.ProfileContextCard
import com.mwask.bat.compose.screens.SettingsLocks
import com.mwask.bat.compose.screens.SettingsSwitches
import com.mwask.bat.compose.screens.StorageOption
import com.mwask.bat.compose.screens.getCategoryColor
import com.mwask.bat.compose.screens.toTrackItem
import com.mwask.bat.compose.search.SearchHistoryItem
import com.mwask.bat.compose.sheets.LibrarySortOption
import com.mwask.bat.compose.sheets.SortDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.mwask.bat.compose.R

internal class ShellRouteEnv(
    val active: ShellActiveEntity,
    val flags: ShellUiFlags,
    val local: ShellLocalState,
    val navController: ShellNavController,
    val actions: ShellActions,
    val shellScope: CoroutineScope,
    val playbackState: ComposePlaybackState?,
    val playerListener: ComposePlayerListener?,
    val currentTrackTitle: String,
    val currentArtist: String,
    val isPlaying: Boolean,
    val currentSortOption: LibrarySortOption,
    val currentSortDirection: SortDirection,
    val searchFieldFocusRequester: FocusRequester,
    val userDisplayName: String,
    val userAvatarUrl: String,
    val userColorHex: String,
    val handleBack: () -> Unit,
    val handlePlayPause: () -> Unit,
    val handleTrackPlay: (TrackItem) -> Unit,
    val handleTrackOptions: (TrackItem) -> Unit,
    val handleTrackAddToQueue: (TrackItem) -> Unit,
    val handleOpenSettings: () -> Unit,
    val handlePodcastOpen: (String, String) -> Unit,
    val openArtist: (String, String) -> Unit,
    val openAlbum: (String, String, String) -> Unit,
    val openPlaylist: (String, String) -> Unit,
    val openShow: (String) -> Unit,
    val openEpisode: (String) -> Unit,
    val openUserProfile: (String, String) -> Unit,
    val openProfileDrawer: () -> Unit,
    val handleLibraryLongPress: (LibraryItem) -> Unit,
    val downloadedTracks: List<DownloadedTrack>,
    val downloadedUris: Set<String>,
    val handleDownloadedPlay: (DownloadedTrack) -> Unit,
    val handleDownloadedRemove: (DownloadedTrack) -> Unit,
    val handleDownloadedDeleteAll: () -> Unit,
    val handleFolderClick: (LibraryItem) -> Unit,
    val shareEntity: (String) -> Unit,
)

@Composable
internal fun ShellHomeRoute(
    env: ShellRouteEnv,
    quickAccessItems: List<QuickAccessItem>?,
    recommendations: List<RecommendationCard>?,
    shelves: List<HomeShelf>?,
    chips: List<HomeChip>,
    selectedFacet: String,
    greeting: String,
    loading: Boolean,
    libraryItems: List<LibraryItem>?,
    notifications: List<EncoreNotification>,
    onHomeFacetSelected: (String) -> Unit,
    onOpenHistory: (() -> Unit)?,
) {
    ShellHomePane(
        quickAccessItems = quickAccessItems,
        recommendations = recommendations,
        shelves = shelves,
        chips = chips,
        selectedFacet = selectedFacet,
        greeting = greeting,
        loading = loading,
        libraryItems = libraryItems,
        notifications = notifications,
        userDisplayName = env.userDisplayName,
        userAvatarUrl = env.userAvatarUrl,
        userColorHex = env.userColorHex,
        onFacetSelected = onHomeFacetSelected,
        onOpenNotifications = {
            env.navController.pushCurrentState()
            env.navController.currentScreen = ShellScreen.NOTIFICATIONS
        },
        onOpenHistory = {
            env.navController.pushCurrentState()
            env.navController.currentScreen = ShellScreen.HISTORY
            onOpenHistory?.invoke()
        },
        onOpenSettings = env.handleOpenSettings,
        onOpenProfile = env.openProfileDrawer,
        onArtistClick = env.openArtist,
        onCoverResolved = { cover ->
            env.active.playlistCoverUrl = cover
            env.active.albumCoverUrl = cover
        },
        onPlayTrackShortcut = { uri, title -> env.actions.playUriShortcut(uri, title) },
        onOpenAlbum = env.openAlbum,
        onOpenShow = env.openShow,
        onOpenEpisode = env.openEpisode,
        onOpenPlaylist = env.openPlaylist,
    )
}

@Composable
internal fun ShellSearchRoute(
    env: ShellRouteEnv,
    searchResults: List<TrackItem>?,
    searchData: SearchResults?,
    searchRecent: List<SearchHistoryItem>,
    searchSuggestions: List<TrackItem>,
    searchSuggestionHits: List<SearchHit>,
    searchLoading: Boolean,
    searchFailed: Boolean,
    searchSavedOverrides: Map<String, Boolean>,
    browseCategories: List<BrowseCard>?,
    searchQuery: String,
    onSearchQueryChange: ((String) -> Unit)?,
    onSearchSubmit: ((String) -> Unit)?,
    onRetrySearch: (() -> Unit)?,
    onClearRecentSearches: (() -> Unit)?,
    onRemoveRecentSearch: ((SearchHistoryItem) -> Unit)?,
    onRecordRecentSearch: ((SearchHit) -> Unit)?,
    onToggleSaved: ((String, Boolean) -> Unit)?,
    onLoadSearchSection: (suspend (SearchKind, Int) -> List<SearchHit>)?,
    onOpenBrowse: ((String) -> Unit)?,
    onOpenCategory: ((String) -> Unit)?,
) {
    ShellSearchPane(
        searchResults = searchResults,
        searchData = searchData,
        searchRecent = searchRecent,
        searchSuggestions = searchSuggestions,
        searchSuggestionHits = searchSuggestionHits,
        searchLoading = searchLoading,
        searchFailed = searchFailed,
        searchSavedOverrides = searchSavedOverrides,
        browseCategories = browseCategories,
        userDisplayName = env.userDisplayName,
        userAvatarUrl = env.userAvatarUrl,
        userColorHex = env.userColorHex,
        currentTrackTitle = env.currentTrackTitle,
        currentTrackUri = env.playbackState?.trackUri.orEmpty(),
        isPlaying = env.isPlaying,
        searchFieldFocusRequester = env.searchFieldFocusRequester,
        initialQuery = searchQuery,
        playerListener = env.playerListener,
        onSearchQueryChange = onSearchQueryChange,
        onSearchSubmit = onSearchSubmit,
        onRetrySearch = { onRetrySearch?.invoke() },
        onClearRecentSearches = { onClearRecentSearches?.invoke() },
        onRemoveRecentSearch = { item -> onRemoveRecentSearch?.invoke(item) },
        onRecordRecentSearch = { hit -> onRecordRecentSearch?.invoke(hit) },
        onToggleSaved = { uri, currentlySaved -> onToggleSaved?.invoke(uri, currentlySaved) },
        onLoadSearchSection = onLoadSearchSection,
        onCategoryClick = { category ->
            env.navController.pushCurrentState()
            env.active.categoryTitle = category.title
            env.active.categoryColor = category.color
            if (category.uri.startsWith("spotify:page:")) {
                env.navController.currentScreen = ShellScreen.BROWSE
                onOpenBrowse?.invoke(category.uri)
            } else {
                env.navController.currentScreen = ShellScreen.CATEGORY
                onOpenCategory?.invoke(category.title)
            }
        },
        onAlbumClick = env.openAlbum,
        onArtistClick = env.openArtist,
        onPodcastClick = env.handlePodcastOpen,
        onOpenEpisode = env.openEpisode,
        onOpenShow = env.openShow,
        onOpenPlaylist = env.openPlaylist,
        onUserClick = env.openUserProfile,
        onTrackSelect = env.handleTrackPlay,
        onTrackOptionsClick = env.handleTrackOptions,
        onTrackAddToQueue = env.handleTrackAddToQueue,
        onProfileClick = env.openProfileDrawer,
    )
}

@Composable
internal fun ShellLibraryRoute(
    env: ShellRouteEnv,
    libraryItems: List<LibraryItem>?,
    libraryLoading: Boolean,
    downloadsSignal: Int = 0,
) {
    EncoreLibraryScreen(
        onItemClick = { itemTitle, itemUri ->
            val foundItem = libraryItems?.find { it.uri == itemUri || it.title == itemTitle }
            env.active.playlistCoverUrl = foundItem?.imageUrl
            env.openPlaylist(itemTitle, itemUri)
        },
        onItemLongPress = env.handleLibraryLongPress,
        onFolderClick = env.handleFolderClick,
        onAlbumClick = env.openAlbum,
        onPodcastClick = env.handlePodcastOpen,
        onArtistClick = env.openArtist,
        items = libraryItems,
        loading = libraryLoading,
        userDisplayName = env.userDisplayName,
        userAvatarUrl = env.userAvatarUrl,
        userColorHex = env.userColorHex,
        onProfileClick = env.openProfileDrawer,
        onPlusClick = { env.flags.isCreateSheetOpen = true },
        onSortClick = { env.flags.isSortSheetOpen = true },
        currentSort = env.currentSortOption,
        sortDirection = env.currentSortDirection,
        downloadedTracks = env.downloadedTracks,
        onDownloadedPlay = env.handleDownloadedPlay,
        onDownloadedRemove = env.handleDownloadedRemove,
        onDownloadedDeleteAll = env.handleDownloadedDeleteAll,
        currentTrackUri = env.playbackState?.trackUri.orEmpty(),
        isPlaying = env.isPlaying,
        downloadsSignal = downloadsSignal
    )
}

@Composable
internal fun ShellPlaylistRoute(
    env: ShellRouteEnv,
    playlistDetail: CollectionDetail?,
    playlistTracks: List<TrackItem>?,
    playlistLoading: Boolean,
    playlistHasMore: Boolean,
    playlistLoadingMore: Boolean,
    onLoadMorePlaylistTracks: (() -> Unit)?,
    onRetryPlaylist: (() -> Unit)?,
    playlistSaved: Boolean?,
    playlistRecommendations: List<TrackItem>,
    recommendationsLoading: Boolean,
    onRefreshRecommendations: (() -> Unit)?,
    onAddRecommended: ((TrackItem) -> Unit)?,
    onSavePlaylistOrder: ((String, List<TrackItem>) -> Unit)?,
    onEditPlaylistDetails: (suspend (String, String, String, ByteArray?, Boolean) -> Boolean)?,
    onDeletePlaylist: (suspend (String) -> Boolean)?,
    onTogglePlaylistSaved: ((String, Boolean) -> Unit)?,
    playlistPermissions: PlaylistPermissions?,
    onLoadPlaylistContributors: (() -> Unit)?,
    onPlaylistTrackLikeToggle: ((TrackItem) -> Unit)?,
    onEditPlaylist: ((String) -> Unit)?,
    onAddTracksOpen: (() -> Unit)?,
) {
    val playlistLoadFailed = !playlistLoading && playlistTracks == null
    ShellPlaylistPane(
        activePlaylistTitle = env.active.playlistTitle,
        activePlaylistUri = env.active.playlistUri,
        activePlaylistCoverUrl = env.active.playlistCoverUrl,
        playlistDetail = playlistDetail,
        playlistTracks = playlistTracks,
        playlistLoading = playlistLoading,
        playlistHasMore = playlistHasMore,
        playlistLoadingMore = playlistLoadingMore,
        onLoadMorePlaylistTracks = onLoadMorePlaylistTracks,
        playlistLoadFailed = playlistLoadFailed,
        onRetryPlaylist = onRetryPlaylist,
        currentTrackTitle = env.currentTrackTitle,
        currentTrackUri = env.playbackState?.trackUri.orEmpty(),
        isPlaying = env.isPlaying,
        userDisplayName = env.userDisplayName,
        playerListener = env.playerListener,
        playlistSaved = playlistSaved,
        playlistRecommendations = playlistRecommendations,
        recommendationsLoading = recommendationsLoading,
        onRefreshRecommendations = onRefreshRecommendations,
        onAddRecommended = onAddRecommended,
        onSavePlaylistOrder = onSavePlaylistOrder,
        onEditPlaylistDetails = onEditPlaylistDetails,
        onDeletePlaylist = { uri ->
            env.shellScope.launch {
                val ok = onDeletePlaylist?.invoke(uri) ?: false
                if (ok) env.handleBack()
            }
        },
        onTogglePlaylistSaved = onTogglePlaylistSaved,
        downloadedCount = playlistTracks.orEmpty().count { it.uri in env.downloadedUris },
        downloadedUris = env.downloadedUris,
        onDownloadClick = { env.actions.downloadTracks(playlistTracks.orEmpty().map { it.uri }) },
        playlistMembers = playlistPermissions?.members ?: playlistDetail?.members.orEmpty(),
        onOpenContributors = {
            env.flags.isContributorsOpen = true
            onLoadPlaylistContributors?.invoke()
        },
        onTrackLikeToggle = { onPlaylistTrackLikeToggle?.invoke(it) },
        onTrackAddToQueue = env.handleTrackAddToQueue,
        handleBack = env.handleBack,
        handleTrackOptions = env.handleTrackOptions,
        shareEntity = env.shareEntity,
        onLocalPlayPause = { env.local.isPlaying = !env.local.isPlaying },
        onLocalTrack = { track -> env.actions.localPlay(track) },
        onPlaylistOptions = { env.flags.isPlaylistOptionsOpen = true },
        onEditModeChange = { isEditing -> env.flags.isPlaylistEditMode = isEditing },
        onChangeTitle = { env.flags.isRenamePlaylistDialogOpen = true },
        onAddTracksClick = {
            env.navController.openAddTracks()
            onAddTracksOpen?.invoke()
        },
        onEditCoverClick = {
            val uri = env.active.playlistUri
            if (uri.startsWith("spotify:playlist:") && playlistDetail?.editable == true) {
                onEditPlaylist?.invoke(uri)
            } else {
                env.flags.isPlaylistOptionsOpen = true
            }
        },
        onPlaybackOrigin = { env.local.playbackOrigin = it }
    )
}

@Composable
internal fun ShellAlbumRoute(
    env: ShellRouteEnv,
    albumDetail: AlbumDetail?,
    albumTracks: List<TrackItem>?,
    albumLoading: Boolean,
    albumFailed: Boolean,
    albumHasMore: Boolean,
    albumLoadingMore: Boolean,
    onLoadMoreAlbumTracks: (() -> Unit)?,
    onRetryAlbum: (() -> Unit)?,
    onAlbumTrackLikeToggle: ((TrackItem) -> Unit)?,
    albumSaved: Boolean? = null,
    onAlbumSavedToggle: ((String, Boolean) -> Unit)? = null,
) {
    ShellAlbumPane(
        activeAlbumTitle = env.active.albumTitle,
        activeAlbumArtist = env.active.albumArtist,
        activeAlbumUri = env.active.albumUri,
        activeAlbumCoverUrl = env.active.albumCoverUrl,
        activeAlbumYear = env.active.albumYear,
        activeAlbumReleaseDate = env.active.albumReleaseDate,
        activeAlbumColor = env.active.albumColor,
        albumDetail = albumDetail,
        albumTracks = albumTracks,
        albumLoading = albumLoading,
        albumFailed = albumFailed,
        albumHasMore = albumHasMore,
        albumLoadingMore = albumLoadingMore,
        onLoadMoreAlbumTracks = onLoadMoreAlbumTracks,
        onRetryAlbum = onRetryAlbum,
        currentTrackTitle = env.currentTrackTitle,
        currentTrackUri = env.playbackState?.trackUri.orEmpty(),
        isPlaying = env.isPlaying,
        playerListener = env.playerListener,
        handleBack = env.handleBack,
        handlePlayPause = env.handlePlayPause,
        handleTrackOptions = env.handleTrackOptions,
        onTrackLikeToggle = { onAlbumTrackLikeToggle?.invoke(it) },
        onTrackAddToQueue = env.handleTrackAddToQueue,
        openArtist = env.openArtist,
        openAlbum = env.openAlbum,
        onAlbumOptions = { env.flags.isAlbumOptionsOpen = true },
        albumSaved = albumSaved == true,
        downloadedCount = albumTracks.orEmpty().count { it.uri in env.downloadedUris },
        downloadedUris = env.downloadedUris,
        onDownloadClick = { env.actions.downloadTracks(albumTracks.orEmpty().map { it.uri }) },
        onAlbumLikeToggle = {
            val uri = env.active.albumUri.ifBlank { albumDetail?.uri.orEmpty() }
            if (uri.startsWith("spotify:album:")) {
                onAlbumSavedToggle?.invoke(uri, albumSaved != true)
            }
        },
        onPlaybackOrigin = { env.local.playbackOrigin = it }
    )
}

@Composable
internal fun ShellArtistRoute(
    env: ShellRouteEnv,
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
    onArtistFollowToggle: ((String, Boolean) -> Unit)?,
    onArtistTrackLikeToggle: ((TrackItem) -> Unit)?,
) {
    ShellArtistPane(
        artistName = env.active.artistName,
        artistAvatarUrl = env.active.artistAvatarUrl,
        artistUri = env.active.artistUri,
        artistDetail = artistDetail,
        artistTracks = artistTracks,
        artistReleases = artistReleases,
        artistMonthlyListeners = artistMonthlyListeners,
        artistLoading = artistLoading,
        artistFailed = artistFailed,
        onRetryArtist = onRetryArtist,
        artistFollowed = artistFollowed,
        artistBanned = artistBanned,
        onArtistUnban = onArtistUnban,
        currentTrackTitle = env.currentTrackTitle,
        currentTrackUri = env.playbackState?.trackUri.orEmpty(),
        isPlaying = env.isPlaying,
        onBackClick = env.handleBack,
        onPlayPauseClick = env.handlePlayPause,
        onTrackSelect = env.handleTrackPlay,
        onTrackOptionsClick = env.handleTrackOptions,
        onTrackLikeToggle = { onArtistTrackLikeToggle?.invoke(it) },
        onTrackAddToQueue = env.handleTrackAddToQueue,
        onFollowToggle = { uri, follow ->
            onArtistFollowToggle?.invoke(uri, follow)
        },
        onReleaseClick = { release ->
            env.openAlbum(release.title, env.active.artistName, release.uri)
        },
        onArtistClick = { name, uri -> env.openArtist(name, uri) },
        onShelfClick = { title, uri ->
            if (uri.startsWith("spotify:album:")) {
                env.openAlbum(title, "", uri)
            } else {
                env.openPlaylist(title, uri)
            }
        },
        onAboutClick = { env.flags.isArtistAboutOpen = true },
        onDiscographyClick = { env.flags.isDiscographyOpen = true },
        onOptionsClick = { env.flags.isArtistOptionsOpen = true }
    )
}

@Composable
internal fun ShellCategoryRoute(
    env: ShellRouteEnv,
    categoryShelves: List<CategoryShelf>?,
    homeQuickAccessItems: List<QuickAccessItem>?,
    homeRecommendations: List<RecommendationCard>?,
    libraryItems: List<LibraryItem>?,
) {
    EncoreCategoryScreen(
        categoryTitle = env.active.categoryTitle,
        categoryColor = env.active.categoryColor,
        onBackClick = env.handleBack,
        onPlaylistClick = { title, uri ->
            if (uri.startsWith("spotify:show:")) {
                env.openShow(uri)
                return@EncoreCategoryScreen
            }
            val foundItem = homeQuickAccessItems?.find { it.uri == uri || it.title == title }
            val foundRec = homeRecommendations?.find { it.uri == uri || it.title == title }
            val foundLib = libraryItems?.find { it.uri == uri || it.title == title }
            val cover = foundItem?.imageUrl ?: foundRec?.imageUrl ?: foundLib?.imageUrl
            env.active.playlistCoverUrl = cover
            env.openPlaylist(title, uri)
        },
        onAlbumClick = { title, artist, uri ->
            env.openAlbum(title, artist, uri)
        },
        onArtistClick = { artist, uri ->
            env.openArtist(artist, uri)
        },
        onSubcategoryClick = { subcategory ->
            env.navController.pushCurrentState()
            env.active.categoryTitle = subcategory
            env.active.categoryColor = getCategoryColor(subcategory)
            env.navController.currentScreen = ShellScreen.CATEGORY
        },
        onTrackSelect = env.handleTrackPlay,
        shelves = categoryShelves
    )
}

@Composable
internal fun ShellSettingsRoute(
    env: ShellRouteEnv,
    switches: SettingsSwitches,
    locks: SettingsLocks,
    product: String,
    country: String,
    loading: Boolean,
    onToggleSetting: ((String, Boolean) -> Unit)?,
    onAppIconChange: ((String) -> Unit)?,
    update: AppUpdate?,
    updateChecking: Boolean,
    updateChecked: Boolean,
    onCheckUpdates: (() -> Unit)?,
    onLogout: (() -> Unit)?,
    storageOptions: List<StorageOption> = emptyList(),
    selectedStorageId: String = "",
    onSelectStorage: ((StorageOption) -> Unit)? = null,
    themeMode: String = "system",
    themeAccent: String = "green",
    onSelectThemeMode: ((String) -> Unit)? = null,
    onSelectThemeAccent: ((String) -> Unit)? = null,
) {
    ShellSettingsPane(
        onBackClick = env.handleBack,
        onLogout = { onLogout?.invoke() },
        switches = switches,
        locks = locks,
        product = product,
        country = country,
        loading = loading,
        onToggleSetting = onToggleSetting,
        onAppIconChange = { onAppIconChange?.invoke(it) },
        update = update,
        updateChecking = updateChecking,
        updateChecked = updateChecked,
        onCheckUpdates = { onCheckUpdates?.invoke() },
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
internal fun ShellNotificationsRoute(
    env: ShellRouteEnv,
    notifications: List<EncoreNotification>,
    loading: Boolean,
    loadFailed: Boolean,
    onRefresh: (() -> Unit)?,
    onLoadPlaylist: ((String) -> Unit)?,
    onOpenEpisode: ((String) -> Unit)?,
) {
    ShellNotificationsPane(
        onBackClick = env.handleBack,
        notifications = notifications,
        loading = loading,
        loadFailed = loadFailed,
        onRetry = { onRefresh?.invoke() },
        onPlay = { item ->
            when {
                item.uri.startsWith("spotify:album:") -> env.openAlbum(item.title, "", item.uri)
                item.uri.startsWith("spotify:episode:") -> {
                    env.navController.pushCurrentState()
                    env.active.episodeUri = item.uri
                    env.navController.currentScreen = ShellScreen.EPISODE
                    onOpenEpisode?.invoke(item.uri)
                }
                item.uri.isNotEmpty() -> {
                    env.navController.pushCurrentState()
                    env.active.playlistTitle = item.title
                    env.active.playlistUri = item.uri
                    env.navController.currentScreen = ShellScreen.PLAYLIST
                    if (onLoadPlaylist != null) onLoadPlaylist?.invoke(item.uri) else env.playerListener?.onLoadPlaylist(item.uri)
                }
            }
        }
    )
}

@Composable
internal fun ShellActivityRoute(
    env: ShellRouteEnv,
    notifications: List<ActivityNotification>,
    loading: Boolean,
    loadFailed: Boolean,
    self: SelfProfile?,
    onRetry: (() -> Unit)?,
    onLeave: (() -> Unit)?,
) {
    ShellActivityPane(
        onBackClick = env.handleBack,
        notifications = notifications,
        loading = loading,
        loadFailed = loadFailed,
        onRetry = { onRetry?.invoke() },
        self = self,
        fallbackName = env.userDisplayName,
        fallbackAvatarUrl = env.userAvatarUrl,
        onOpenNotification = { item ->
            when {
                item.actionUri.startsWith("spotify:playlist:") ->
                    env.openPlaylist(item.title, item.actionUri)
                item.actionUri.startsWith("spotify:user:") ->
                    env.openUserProfile(item.actionUri, item.primaryActor.ifBlank { item.title })
                item.actionUri.startsWith("spotify:artist:") ->
                    env.openArtist(item.primaryActor.ifBlank { item.title }, item.actionUri)
                item.actionUri.startsWith("spotify:album:") ->
                    env.openAlbum(item.title, "", item.actionUri)
            }
        },
        onLeave = { onLeave?.invoke() }
    )
}

@Composable
internal fun ShellHistoryRoute(
    env: ShellRouteEnv,
    todayTracks: List<TrackItem>,
    yesterdayTracks: List<TrackItem>,
    earlierTracks: List<TrackItem>,
    stats: HistoryStats?,
    loading: Boolean,
) {
    ShellHistoryPane(
        onBackClick = env.handleBack,
        onTrackSelect = { track -> env.actions.onTrackPlay(track) },
        onTrackOptionsClick = { track -> env.actions.openTrackOptions(track) },
        onTrackAddToQueue = env.handleTrackAddToQueue,
        todayTracks = todayTracks,
        yesterdayTracks = yesterdayTracks,
        earlierTracks = earlierTracks,
        stats = stats,
        loading = loading,
        currentTrackTitle = env.currentTrackTitle,
        isPlaying = env.isPlaying
    )
}

@Composable
internal fun ShellAddTracksRoute(
    env: ShellRouteEnv,
    songs: List<TrackItem>,
    episodes: List<TrackItem>,
    recent: List<TrackItem>,
    liked: List<TrackItem>,
    loading: Boolean,
    onAddTrack: (suspend (String) -> Boolean)?,
    searchData: SearchResults? = null,
    searchLoading: Boolean = false,
    onSearchQueryChange: ((String) -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    ShellAddTracksPane(
        targetTitle = env.active.playlistTitle,
        songs = songs,
        episodes = episodes,
        recent = recent,
        liked = liked,
        loading = loading,
        onBack = env.handleBack,
        onAdd = { uri -> onAddTrack?.invoke(uri) ?: false },
        searchData = searchData,
        searchLoading = searchLoading,
        onSearchQueryChange = onSearchQueryChange,
        onOpenAlbum = { uri, title, artist -> env.navController.openAlbum(context, title, artist, uri) },
        onOpenArtist = { uri, name -> env.navController.openArtist(context, name, uri) },
        onOpenPlaylist = { uri, title -> env.navController.openPlaylist(context, title, uri) },
        onOpenShow = { uri -> env.navController.openShow(uri) },
        onOpenEpisode = { uri -> env.navController.openEpisode(uri) },
    )
}

@Composable
internal fun ShellBrowseRoute(
    env: ShellRouteEnv,
    page: BrowsePage?,
    onOpenBrowse: ((String) -> Unit)?,
    onOpenShow: ((String) -> Unit)?,
    onLoadMore: (() -> Unit)?,
) {
    ShellBrowsePane(
        page = page,
        fallbackTitle = env.active.categoryTitle,
        onBack = env.handleBack,
        onOpenBrowse = { uri, title ->
            env.active.categoryTitle = title
            onOpenBrowse?.invoke(uri)
        },
        onOpenShow = { uri -> onOpenShow?.invoke(uri) },
        onOpenAlbum = { title, uri -> env.openAlbum(title, "", uri) },
        onOpenPlaylist = env.openPlaylist,
        onLoadMore = { onLoadMore?.invoke() }
    )
}

@Composable
internal fun ShellShowRoute(
    env: ShellRouteEnv,
    show: ShowDetail?,
    loading: Boolean,
    onOpenShow: ((String) -> Unit)?,
    onLoadMore: (() -> Unit)?,
    onShowFollowToggle: ((String, Boolean) -> Unit)? = null,
) {
    ShellShowPane(
        show = show,
        loading = loading,
        retryUri = env.active.showUri,
        isPlaying = env.isPlaying,
        currentTrackTitle = env.currentTrackTitle,
        onBack = env.handleBack,
        onRetry = { uri -> onOpenShow?.invoke(uri) },
        onEpisodeClick = env.openEpisode,
        onPlayEpisode = { episode ->
            env.playerListener?.onPlayEpisode(episode.uri, env.active.showUri, episode.video)
        },
        onPlayPause = env.handlePlayPause,
        onLoadMore = { onLoadMore?.invoke() },
        onFollowToggle = {
            val uri = env.active.showUri.ifBlank { show?.uri.orEmpty() }
            if (uri.startsWith("spotify:show:")) {
                onShowFollowToggle?.invoke(uri, show?.saved != true)
            }
        },
        onMoreClick = { env.flags.isShowOptionsOpen = true }
    )
}

@Composable
internal fun ShellEpisodeRoute(
    env: ShellRouteEnv,
    episode: EpisodeDetail?,
    loading: Boolean,
    onOpenEpisode: ((String) -> Unit)?,
) {
    ShellEpisodePane(
        episode = episode,
        loading = loading,
        retryUri = env.active.episodeUri,
        isPlaying = env.isPlaying,
        isCurrent = env.playbackState?.trackUri == env.active.episodeUri,
        onBack = env.handleBack,
        onRetry = { uri -> onOpenEpisode?.invoke(uri) },
        onPlayPause = env.handlePlayPause,
        onPlay = {
            val current = episode
            if (current != null) {
                env.playerListener?.onPlayEpisode(current.uri, current.showUri, current.video)
            }
        },
        onShowClick = { uri -> env.openShow(uri) }
    )
}

@Composable
internal fun ShellFolderRoute(
    env: ShellRouteEnv,
    items: List<LibraryItem>?,
    loading: Boolean,
    onLoadPlaylist: ((String) -> Unit)?,
) {
    ShellFolderPane(
        items = items,
        loading = loading,
        title = env.active.folderTitle.ifBlank { stringResource(R.string.your_library) },
        userDisplayName = env.userDisplayName,
        userAvatarUrl = env.userAvatarUrl,
        userColorHex = env.userColorHex,
        onItemClick = { itemTitle, itemUri ->
            val foundItem = items?.find { it.uri == itemUri || it.title == itemTitle }
            env.active.playlistCoverUrl = foundItem?.imageUrl
            env.navController.pushCurrentState()
            env.active.playlistTitle = itemTitle
            env.active.playlistUri = itemUri
            env.navController.currentScreen = ShellScreen.PLAYLIST
            if (itemUri.isNotEmpty()) if (onLoadPlaylist != null) onLoadPlaylist?.invoke(itemUri) else env.playerListener?.onLoadPlaylist(itemUri)
        },
        onItemLongPress = env.handleLibraryLongPress,
        onFolderClick = env.handleFolderClick,
        onProfileClick = env.openProfileDrawer,
        onPlusClick = { env.flags.isCreateSheetOpen = true },
        onSortClick = { env.flags.isSortSheetOpen = true },
        currentSort = env.currentSortOption,
        sortDirection = env.currentSortDirection,
        onBackClick = { env.handleBack() },
        onFolderOptionsClick = {
            env.local.activeFolderOptionsNested = false
            env.local.activeFolderOptionsItem = LibraryItem(
                title = env.active.folderTitle,
                subtitle = "${items?.size ?: 0} playlists",
                type = LibraryItemType.FOLDER,
                uri = env.active.folderUri
            )
        },
        onPlayClick = {
            val firstItem = items?.firstOrNull()
            if (firstItem != null && firstItem.type == LibraryItemType.PLAYLIST) {
                env.openPlaylist(firstItem.title, firstItem.uri)
            }
        }
    )
}

@Composable
internal fun ShellProfileRoute(
    env: ShellRouteEnv,
    profile: UserProfile?,
    topContent: ProfileTopContent?,
    topContentUnavailable: Boolean,
    contexts: List<ProfileContextCard>,
    loading: Boolean,
    loadFailed: Boolean,
    onRetry: (() -> Unit)?,
    onLoadProfileFollows: (() -> Unit)?,
) {
    ShellProfilePane(
        profile = profile,
        topContent = topContent,
        topContentUnavailable = topContentUnavailable,
        contexts = contexts,
        loading = loading,
        loadFailed = loadFailed,
        onRetry = { onRetry?.invoke() },
        onBack = env.handleBack,
        currentTrackUri = env.playbackState?.trackUri.orEmpty(),
        isPlaying = env.isPlaying,
        onFollowStatsClick = {
            env.flags.isProfileFollowOpen = true
            onLoadProfileFollows?.invoke()
        },
        onArtistClick = env.openArtist,
        onTrackSelect = { track -> env.actions.onTrackPlay(track.toTrackItem()) },
        onTrackOptionsClick = { track -> env.handleTrackOptions(track.toTrackItem()) },
        onTrackAddToQueue = { track -> env.handleTrackAddToQueue(track.toTrackItem()) },
        onContextClick = { card ->
            when {
                card.uri.startsWith("spotify:artist:") -> env.openArtist(card.title, card.uri)
                card.uri.startsWith("spotify:album:") -> env.openAlbum(card.title, "", card.uri)
                card.uri.startsWith("spotify:playlist:") -> env.openPlaylist(card.title, card.uri)
            }
        },
        contextsTitle = if (profile?.isCurrentUser == false) stringResource(R.string.public_playlists) else stringResource(R.string.recently_played),
        fallbackName = if (profile?.isCurrentUser == false) profile.name else env.userDisplayName,
        avatarUrl = when {
            profile == null -> env.userAvatarUrl
            profile.isCurrentUser -> profile.avatarUrl.ifBlank { env.userAvatarUrl }
            else -> profile.avatarUrl
        }
    )
}
