package com.quackify.compose.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.quackify.backend.model.ConnectDevice
import com.quackify.compose.playlist.TrackItem
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.compose.sheets.AccountsSheet
import com.quackify.compose.sheets.AlbumOptionsSheet
import com.quackify.compose.sheets.ArtistOptionsSheet
import com.quackify.compose.sheets.EntityOptions
import com.quackify.compose.sheets.EntityOptionsSheet
import com.quackify.compose.sheets.LibraryCreateSheet
import com.quackify.compose.sheets.LibrarySortOption
import com.quackify.compose.sheets.LibrarySortSheet
import com.quackify.compose.sheets.SortDirection
import com.quackify.compose.sheets.ProfileDrawerSheet
import com.quackify.compose.sheets.SleepTimerOption
import com.quackify.compose.sheets.SleepTimerSheet
import com.quackify.compose.sheets.CanvasResolutionSheet
import com.quackify.compose.sheets.EncoreConnectDeviceSheet
import com.quackify.compose.sheets.EncoreQueueSheet
import com.quackify.compose.sheets.TrackOptionsSheet
import com.quackify.compose.sheets.toEncoreDevice

internal data class SheetFlags(
    val profileDrawer: Boolean = false,
    val accounts: Boolean = false,
    val trackOptions: Boolean = false,
    val playlistOptions: Boolean = false,
    val albumOptions: Boolean = false,
    val artistOptions: Boolean = false,
    val sort: Boolean = false,
    val create: Boolean = false,
    val device: Boolean = false,
    val queue: Boolean = false,
    val sleepTimer: Boolean = false,
    val canvasResolutionSheet: Boolean = false,
)

@Composable
internal fun ShellSheets(
    flags: SheetFlags,
    selectedTrack: TrackItem?,
    canAddToQueue: Boolean = true,
    devices: List<ConnectDevice>,
    connectedDevice: String,
    userQueue: List<TrackItem>,
    queueFromContext: List<TrackItem>,
    trackTasteExcluded: Boolean? = null,
    trackPlaysOverride: Long? = null,
    onToggleTasteExclusion: (Boolean) -> Unit = {},
    queueSourceContext: String,
    currentTrackTitle: String,
    currentTrackArtist: String,
    currentTrackUri: String,
    currentTrackCoverUrl: String?,
    playlistEntity: EntityOptions?,
    playlistEditable: Boolean,
    playlistSaved: Boolean?,
    albumTitle: String,
    albumArtist: String,
    albumCoverUrl: String?,
    albumUri: String = "",
    artistName: String,
    artistAvatarUrl: String?,
    artistUri: String = "",
    artistFollowing: Boolean = false,
    artistBanned: Boolean = false,
    onArtistFollowToggle: () -> Unit = {},
    onArtistRadio: () -> Unit = {},
    onDontPlayArtist: () -> Unit = {},
    sortOption: LibrarySortOption,
    sortDirection: SortDirection = SortDirection.DESCENDING,
    sleepTimer: SleepTimerOption?,
    userDisplayName: String,
    userAvatarUrl: String,
    userColorHex: String = "",
    savedAccounts: List<EncoreAuth.Account>,
    activeAccountId: String,
    isFolder: Boolean = false,
    canvasAvailable: Boolean = false,
    isCanvasEnabled: Boolean = false,
    onCanvasToggle: () -> Unit = {},
    canvasResolution: Int = 1080,
    onCanvasResolution: (Int) -> Unit = {},
    onCanvasResolutionClick: () -> Unit = {},
    onDismissCanvasResolution: () -> Unit = {},
    onDismissProfile: () -> Unit,
    onDismissAccounts: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (String) -> Unit,
    onDismissTrackOptions: () -> Unit,
    onDismissPlaylistOptions: () -> Unit,
    onDismissAlbumOptions: () -> Unit,
    onDismissArtistOptions: () -> Unit,
    onDismissSort: () -> Unit,
    onDismissCreate: () -> Unit,
    onDismissDevice: () -> Unit,
    onDismissQueue: () -> Unit,
    onDismissSleepTimer: () -> Unit,
    onShowCredits: () -> Unit,
    onEditPlaylist: () -> Unit,
    onTogglePlaylistSaved: () -> Unit,
    onDeletePlaylist: () -> Unit,
    onCopyLink: (String) -> Unit,
    onShare: (String) -> Unit,
    onWhatsNew: () -> Unit,
    onNotifications: (() -> Unit)? = null,
    notificationsUnread: Boolean = false,
    onProfile: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
    onOpenAccounts: () -> Unit,
    onAddToQueue: (TrackItem) -> Unit,
    onViewArtist: (String, String) -> Unit,
    onViewAlbum: (String, String) -> Unit,
    onGoToArtist: (String) -> Unit,
    onSortSelected: (LibrarySortOption, SortDirection) -> Unit,
    onCreatePlaylist: () -> Unit,
    onCreateFolder: () -> Unit = {},
    onSelectDevice: (String) -> Unit,
    onDeviceVisible: (Boolean) -> Unit,
    onQueueTrackSelect: (TrackItem) -> Unit,
    onRemoveFromQueue: (TrackItem) -> Unit,
    onClearQueue: () -> Unit,
    onReorderQueue: (List<TrackItem>) -> Unit,
    onSleepTimerSelected: (SleepTimerOption) -> Unit,
    onRemoveTrackFromPlaylist: (() -> Unit)? = null,
    onSongRadio: (() -> Unit)? = null,
    onAlbumRadio: (() -> Unit)? = null,
    onAddToPlaylistFromAlbum: (() -> Unit)? = null,
) {
    ShellProfileSheets(
        flags = flags,
        userDisplayName = userDisplayName,
        userAvatarUrl = userAvatarUrl,
        userColorHex = userColorHex,
        savedAccounts = savedAccounts,
        activeAccountId = activeAccountId,
        notificationsUnread = notificationsUnread,
        onDismissProfile = onDismissProfile,
        onDismissAccounts = onDismissAccounts,
        onSwitchAccount = onSwitchAccount,
        onAddAccount = onAddAccount,
        onRemoveAccount = onRemoveAccount,
        onOpenAccounts = onOpenAccounts,
        onWhatsNew = onWhatsNew,
        onNotifications = onNotifications,
        onProfile = onProfile,
        onHistory = onHistory,
        onSettings = onSettings,
        onLogout = onLogout,
    )

    ShellEntityOptionsSheets(
        flags = flags,
        selectedTrack = selectedTrack,
        canAddToQueue = canAddToQueue,
        trackTasteExcluded = trackTasteExcluded,
        trackPlaysOverride = trackPlaysOverride,
        onToggleTasteExclusion = onToggleTasteExclusion,
        playlistEntity = playlistEntity,
        playlistEditable = playlistEditable,
        playlistSaved = playlistSaved,
        albumTitle = albumTitle,
        albumArtist = albumArtist,
        albumCoverUrl = albumCoverUrl,
        albumUri = albumUri,
        artistName = artistName,
        artistAvatarUrl = artistAvatarUrl,
        artistUri = artistUri,
        artistFollowing = artistFollowing,
        artistBanned = artistBanned,
        onArtistFollowToggle = onArtistFollowToggle,
        onArtistRadio = onArtistRadio,
        onDontPlayArtist = onDontPlayArtist,
        currentTrackTitle = currentTrackTitle,
        currentTrackArtist = currentTrackArtist,
        currentTrackUri = currentTrackUri,
        currentTrackCoverUrl = currentTrackCoverUrl,
        onDismissTrackOptions = onDismissTrackOptions,
        onDismissPlaylistOptions = onDismissPlaylistOptions,
        onDismissAlbumOptions = onDismissAlbumOptions,
        onDismissArtistOptions = onDismissArtistOptions,
        onShowCredits = onShowCredits,
        onEditPlaylist = onEditPlaylist,
        onTogglePlaylistSaved = onTogglePlaylistSaved,
        onDeletePlaylist = onDeletePlaylist,
        onCopyLink = onCopyLink,
        onShare = onShare,
        onAddToQueue = onAddToQueue,
        onViewArtist = onViewArtist,
        onViewAlbum = onViewAlbum,
        onGoToArtist = onGoToArtist,
        canvasAvailable = canvasAvailable,
        isCanvasEnabled = isCanvasEnabled,
        onCanvasToggle = onCanvasToggle,
        canvasResolution = canvasResolution,
        onCanvasResolutionClick = onCanvasResolutionClick,
        onRemoveTrackFromPlaylist = onRemoveTrackFromPlaylist,
        onSongRadio = onSongRadio,
        onAlbumRadio = onAlbumRadio,
        onAddToPlaylistFromAlbum = onAddToPlaylistFromAlbum,
    )

    ShellLibrarySheets(
        flags = flags,
        isFolder = isFolder,
        sortOption = sortOption,
        sortDirection = sortDirection,
        sleepTimer = sleepTimer,
        devices = devices,
        connectedDevice = connectedDevice,
        userQueue = userQueue,
        queueFromContext = queueFromContext,
        queueSourceContext = queueSourceContext,
        currentTrackUri = currentTrackUri,
        currentTrackTitle = currentTrackTitle,
        currentTrackArtist = currentTrackArtist,
        currentTrackCoverUrl = currentTrackCoverUrl,
        canvasResolution = canvasResolution,
        onDismissSort = onDismissSort,
        onDismissCreate = onDismissCreate,
        onDismissDevice = onDismissDevice,
        onDismissQueue = onDismissQueue,
        onDismissSleepTimer = onDismissSleepTimer,
        onCanvasResolution = onCanvasResolution,
        onDismissCanvasResolution = onDismissCanvasResolution,
        onCreatePlaylist = onCreatePlaylist,
        onCreateFolder = onCreateFolder,
        onSortSelected = onSortSelected,
        onSelectDevice = onSelectDevice,
        onDeviceVisible = onDeviceVisible,
        onQueueTrackSelect = onQueueTrackSelect,
        onRemoveFromQueue = onRemoveFromQueue,
        onClearQueue = onClearQueue,
        onReorderQueue = onReorderQueue,
        onSleepTimerSelected = onSleepTimerSelected,
    )
}

@Composable
private fun ShellProfileSheets(
    flags: SheetFlags,
    userDisplayName: String,
    userAvatarUrl: String,
    userColorHex: String,
    savedAccounts: List<EncoreAuth.Account>,
    activeAccountId: String,
    notificationsUnread: Boolean,
    onDismissProfile: () -> Unit,
    onDismissAccounts: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (String) -> Unit,
    onOpenAccounts: () -> Unit,
    onWhatsNew: () -> Unit,
    onNotifications: (() -> Unit)?,
    onProfile: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    ProfileDrawerSheet(
        isOpen = flags.profileDrawer,
        onDismiss = onDismissProfile,
        onAccountsClick = onOpenAccounts,
        onProfileClick = onProfile,
        onWhatsNewClick = onWhatsNew,
        onListeningHistoryClick = onHistory,
        onSettingsClick = onSettings,
        onNotificationsClick = onNotifications,
        notificationsUnread = notificationsUnread,
        onLogoutClick = onLogout,
        userDisplayName = userDisplayName,
        userAvatarUrl = userAvatarUrl,
        userColorHex = userColorHex
    )

    AccountsSheet(
        isOpen = flags.accounts,
        onDismiss = onDismissAccounts,
        accounts = savedAccounts,
        activeAccountId = activeAccountId,
        onSwitchAccount = onSwitchAccount,
        onAddAccount = onAddAccount,
        onRemoveAccount = onRemoveAccount
    )
}

@Composable
private fun ShellEntityOptionsSheets(
    flags: SheetFlags,
    selectedTrack: TrackItem?,
    canAddToQueue: Boolean,
    trackTasteExcluded: Boolean?,
    trackPlaysOverride: Long?,
    onToggleTasteExclusion: (Boolean) -> Unit,
    playlistEntity: EntityOptions?,
    playlistEditable: Boolean,
    playlistSaved: Boolean?,
    albumTitle: String,
    albumArtist: String,
    albumCoverUrl: String?,
    albumUri: String,
    artistName: String,
    artistAvatarUrl: String?,
    artistUri: String,
    artistFollowing: Boolean,
    artistBanned: Boolean,
    onArtistFollowToggle: () -> Unit,
    onArtistRadio: () -> Unit,
    onDontPlayArtist: () -> Unit,
    currentTrackTitle: String,
    currentTrackArtist: String,
    currentTrackUri: String,
    currentTrackCoverUrl: String?,
    onDismissTrackOptions: () -> Unit,
    onDismissPlaylistOptions: () -> Unit,
    onDismissAlbumOptions: () -> Unit,
    onDismissArtistOptions: () -> Unit,
    onShowCredits: () -> Unit,
    onEditPlaylist: () -> Unit,
    onTogglePlaylistSaved: () -> Unit,
    onDeletePlaylist: () -> Unit,
    onCopyLink: (String) -> Unit,
    onShare: (String) -> Unit,
    onAddToQueue: (TrackItem) -> Unit,
    onViewArtist: (String, String) -> Unit,
    onViewAlbum: (String, String) -> Unit,
    onGoToArtist: (String) -> Unit,
    canvasAvailable: Boolean,
    isCanvasEnabled: Boolean,
    onCanvasToggle: () -> Unit,
    canvasResolution: Int,
    onCanvasResolutionClick: () -> Unit,
    onRemoveTrackFromPlaylist: (() -> Unit)?,
    onSongRadio: (() -> Unit)?,
    onAlbumRadio: (() -> Unit)?,
    onAddToPlaylistFromAlbum: (() -> Unit)?,
) {
    TrackOptionsSheet(
        track = selectedTrack,
        isOpen = flags.trackOptions,
        onDismiss = onDismissTrackOptions,
        streamCount = trackPlaysOverride ?: selectedTrack?.plays?.takeIf { it > 0L },
        onAddToQueue = { selectedTrack?.let(onAddToQueue) },
        canAddToQueue = canAddToQueue,
        onViewArtist = onViewArtist,
        onViewAlbum = onViewAlbum,
        onShare = { selectedTrack?.uri?.takeIf { it.isNotBlank() }?.let(onShare) },
        onShowCredits = onShowCredits,
        canvasAvailable = canvasAvailable,
        isCanvasEnabled = isCanvasEnabled,
        onCanvasToggle = onCanvasToggle,
        canvasResolution = canvasResolution,
        onCanvasResolutionClick = onCanvasResolutionClick,
        isTasteExcluded = trackTasteExcluded,
        onToggleTasteExclusion = onToggleTasteExclusion,
        onRemoveFromPlaylist = onRemoveTrackFromPlaylist,
        onSongRadio = onSongRadio
    )

    EntityOptionsSheet(
        entity = playlistEntity,
        isOpen = flags.playlistOptions,
        onDismiss = onDismissPlaylistOptions,
        canEditPlaylist = playlistEditable,
        onEditPlaylist = onEditPlaylist,
        isSaved = playlistSaved,
        onSaveToggle = onTogglePlaylistSaved,
        canDeletePlaylist = playlistEditable,
        onDeletePlaylist = onDeletePlaylist,
        onCopyLink = { playlistEntity?.uri?.let(onCopyLink) },
        onShare = { playlistEntity?.uri?.let(onShare) }
    )

    AlbumOptionsSheet(
        title = albumTitle,
        artist = albumArtist,
        coverUrl = albumCoverUrl,
        isOpen = flags.albumOptions,
        onDismiss = onDismissAlbumOptions,
        onGoToArtist = onGoToArtist,
        onAddToPlaylist = onAddToPlaylistFromAlbum,
        onAlbumRadio = onAlbumRadio,
        onShare = { albumUri.takeIf { it.startsWith("spotify:album:") }?.let(onShare) }
    )

    ArtistOptionsSheet(
        artistName = artistName,
        avatarUrl = artistAvatarUrl,
        isOpen = flags.artistOptions,
        onDismiss = onDismissArtistOptions,
        isFollowing = artistFollowing,
        onFollowToggle = onArtistFollowToggle,
        onGoToRadio = onArtistRadio,
        isBanned = artistBanned,
        onDontPlayArtist = onDontPlayArtist,
        onShare = { artistUri.takeIf { it.startsWith("spotify:artist:") }?.let(onShare) }
    )
}

@Composable
private fun ShellLibrarySheets(
    flags: SheetFlags,
    isFolder: Boolean,
    sortOption: LibrarySortOption,
    sortDirection: SortDirection,
    sleepTimer: SleepTimerOption?,
    devices: List<ConnectDevice>,
    connectedDevice: String,
    userQueue: List<TrackItem>,
    queueFromContext: List<TrackItem>,
    queueSourceContext: String,
    currentTrackUri: String,
    currentTrackTitle: String,
    currentTrackArtist: String,
    currentTrackCoverUrl: String?,
    canvasResolution: Int,
    onDismissSort: () -> Unit,
    onDismissCreate: () -> Unit,
    onDismissDevice: () -> Unit,
    onDismissQueue: () -> Unit,
    onDismissSleepTimer: () -> Unit,
    onCanvasResolution: (Int) -> Unit,
    onDismissCanvasResolution: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onCreateFolder: () -> Unit,
    onSortSelected: (LibrarySortOption, SortDirection) -> Unit,
    onSelectDevice: (String) -> Unit,
    onDeviceVisible: (Boolean) -> Unit,
    onQueueTrackSelect: (TrackItem) -> Unit,
    onRemoveFromQueue: (TrackItem) -> Unit,
    onClearQueue: () -> Unit,
    onReorderQueue: (List<TrackItem>) -> Unit,
    onSleepTimerSelected: (SleepTimerOption) -> Unit,
) {
    LibrarySortSheet(
        isOpen = flags.sort,
        currentSort = sortOption,
        currentDirection = sortDirection,
        onSortSelected = onSortSelected,
        onDismiss = onDismissSort
    )

    LibraryCreateSheet(
        isOpen = flags.create,
        onCreatePlaylist = onCreatePlaylist,
        onCreateFolder = onCreateFolder,
        showHeader = !isFolder,
        onDismiss = onDismissCreate
    )

    if (flags.device) {
        LaunchedEffect(Unit) { onDeviceVisible(true) }
        val mapped = devices.map { it.toEncoreDevice() }
        EncoreConnectDeviceSheet(
            onDismissRequest = {
                onDismissDevice()
                onDeviceVisible(false)
            },
            currentDeviceName = connectedDevice,
            currentTrackTitle = currentTrackTitle,
            currentDeviceKind = devices.firstOrNull { it.active }?.kind,
            devices = mapped,
            onSelectDevice = { device -> onSelectDevice(device.id) }
        )
    }

    if (flags.queue) {
        val queued = userQueue.filter { it.uri.isNotBlank() && it.title.isNotBlank() }
        val upcoming = queueFromContext
            .filter { it.uri.isNotBlank() }
            .dropWhile { it.uri == currentTrackUri }
            .filter { it.title.isNotBlank() }
        EncoreQueueSheet(
            currentTrackTitle = currentTrackTitle,
            currentTrackArtist = currentTrackArtist,
            currentTrackCoverUrl = currentTrackCoverUrl,
            queueSourceContext = queueSourceContext,
            userQueue = queued,
            nextFromContext = upcoming,
            onDismissRequest = onDismissQueue,
            onTrackSelect = onQueueTrackSelect,
            onRemoveFromQueue = onRemoveFromQueue,
            onClearQueue = onClearQueue,
            onReorderQueue = onReorderQueue
        )
    }

    if (flags.sleepTimer) {
        SleepTimerSheet(
            activeOption = sleepTimer,
            onOptionSelected = onSleepTimerSelected,
            onDismiss = onDismissSleepTimer
        )
    }

    if (flags.canvasResolutionSheet) {
        CanvasResolutionSheet(
            selectedHeight = canvasResolution,
            onHeightSelected = onCanvasResolution,
            onDismiss = onDismissCanvasResolution
        )
    }
}
