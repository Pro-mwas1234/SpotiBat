package com.quackify.compose.shell

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.quackify.backend.api.ArtistBanStore
import com.quackify.backend.api.Collection2
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.model.AlbumDetail
import com.quackify.backend.model.ArtistDetail
import com.quackify.backend.model.CollectionDetail
import com.quackify.backend.model.ShowDetail
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.sheets.EntityOptions
import com.quackify.compose.sheets.SleepTimerOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.quackify.compose.R

internal class ShellSheetsEnv(
    val flags: ShellUiFlags,
    val local: ShellLocalState,
    val active: ShellActiveEntity,
    val navController: ShellNavController,
    val playbackState: ComposePlaybackState?,
    val playerListener: ComposePlayerListener?,
    val shellScope: CoroutineScope,
    val context: Context,
    val currentScreen: ShellScreen,
    val isPlayerExpanded: Boolean,
    val setIsPlayerExpanded: (Boolean) -> Unit,
    val pushCurrentState: () -> Unit,
    val setCurrentScreen: (ShellScreen) -> Unit,
    val currentTrackTitle: String,
    val currentArtist: String,
    val isLiked: Boolean,
    val currentCoverUrl: String,
    val connectedDevice: String,
    val handleBack: () -> Unit,
    val handleQueueAdd: (String) -> Unit,
    val shareEntity: (String) -> Unit,
    val copyEntityLink: (String) -> Unit,
    val openPlaylist: (String, String) -> Unit,
    val openArtist: (String, String) -> Unit,
    val openAlbum: (String, String, String) -> Unit,
    val userDisplayName: String,
    val userAvatarUrl: String,
    val userColorHex: String,
    val savedAccounts: List<EncoreAuth.Account>,
    val activeAccountId: String,
    val playlistDetail: CollectionDetail?,
    val playlistSaved: Boolean?,
    val albumDetail: AlbumDetail?,
    val albumSaved: Boolean?,
    val onAlbumSavedToggle: ((String, Boolean) -> Unit)?,
    val showDetail: ShowDetail?,
    val onShowFollowToggle: ((String, Boolean) -> Unit)?,
    val artistDetail: ArtistDetail?,
    val artistFollowed: Boolean?,
    val artistBanned: Boolean?,
    val onArtistBanChanged: ((String, Boolean) -> Unit)?,
    val trackTasteExcluded: Boolean?,
    val setTrackTasteExcluded: (Boolean?) -> Unit,
    val tasteExclusionCache: MutableMap<String, Boolean>,
    val canvasFilePath: String?,
    val isCanvasAllowed: Boolean,
    val isCanvasEnabled: Boolean,
    val canvasResolution: Int,
    val setLocalDevice: (String) -> Unit,
    val handleOpenSettings: () -> Unit,
    val onLoadProfile: (() -> Unit)?,
    val onOpenHistory: (() -> Unit)?,
    val onOpenActivity: (() -> Unit)?,
    val activityUnread: Boolean,
    val onAddToPlaylist: ((TrackItem) -> Unit)?,
    val onShowCredits: ((String) -> Unit)?,
    val onEditPlaylist: ((String) -> Unit)?,
    val onTogglePlaylistSaved: ((String, Boolean) -> Unit)?,
    val onDeletePlaylist: (suspend (String) -> Boolean)?,
    val onSwitchAccount: ((String) -> Unit)?,
    val onAddAccount: (() -> Unit)?,
    val onRemoveAccount: ((String) -> Unit)?,
    val onLogout: (() -> Unit)?,
    val onArtistFollowToggle: ((String, Boolean) -> Unit)?,
    val onArtistRadio: (suspend (String) -> String?)?,
    val onCanvasToggle: ((Boolean) -> Unit)?,
    val onCanvasResolution: ((Int) -> Unit)?,
    val onPlaylistDefaultName: (suspend () -> String)?,
    val onRemoveTrackFromPlaylist: ((String, String) -> Unit)?,
)

/**
 * The persistent bottom sheets (track options, entity options, sort, create,
 * devices, queue, sleep timer and the profile drawer), extracted from
 * EncoreAppShell so that composable stays under the JIT method-size limit.
 */
@Composable
internal fun ShellSheetsHost(env: ShellSheetsEnv) {
    val playbackState = env.playbackState
    val active = env.active
    val flags = env.flags
    val local = env.local
    val currentTrackUri = playbackState?.trackUri.orEmpty()
    val artistBlockedMsg = stringResource(R.string.artist_blocked)
    val artistUnblockedMsg = stringResource(R.string.artist_unblocked)
    val artistUpdateFailedMsg = stringResource(R.string.couldnt_update_this_artist)
    val tasteExcludedMsg = stringResource(R.string.excluded_from_taste_profile)
    val tasteIncludedMsg = stringResource(R.string.included_in_taste_profile)
    val tasteUpdateFailedMsg = stringResource(R.string.couldnt_update_taste_profile)
    val trackRadioTitle = stringResource(R.string.radio_title, local.trackForOptions?.title.orEmpty())
    val albumRadioTitle = stringResource(R.string.radio_title, active.albumTitle)

    val sheetArtistFromPlayer = flags.isArtistOptionsFromPlayer
    val sheetArtistName = if (sheetArtistFromPlayer) local.artistOptionsName else active.artistName
    val sheetArtistUri = if (sheetArtistFromPlayer) local.artistOptionsUri else active.artistUri
    val sheetArtistAvatarUrl = if (sheetArtistFromPlayer) {
        local.artistOptionsAvatarUrl
    } else {
        active.artistAvatarUrl
            ?: env.artistDetail?.imageUrl?.takeIf { it.isNotBlank() }
            ?: env.artistDetail?.headerImageUrl?.takeIf { it.isNotBlank() }
    }
    val artistRadioTitle = stringResource(R.string.radio_title, sheetArtistName)
    var artistBannedState by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(flags.isArtistOptionsOpen, sheetArtistUri) {
        if (!flags.isArtistOptionsOpen) return@LaunchedEffect
        if (!sheetArtistUri.startsWith("spotify:artist:")) return@LaunchedEffect
        artistBannedState = runCatching { Collection2.artistBanned(env.context, sheetArtistUri) }.getOrNull()
    }

    val playlistEntity = if (active.playlistUri.isBlank() && active.playlistTitle.isBlank()) null
    else EntityOptions(
        title = env.playlistDetail?.title?.takeIf { it.isNotBlank() } ?: active.playlistTitle,
        subtitle = if (active.playlistUri.startsWith("spotify:playlist:"))
            stringResource(R.string.playlist_by, env.playlistDetail?.creator?.takeIf { it.isNotBlank() } ?: env.userDisplayName)
        else "",
        imageUrl = active.playlistCoverUrl ?: env.playlistDetail?.imageUrl?.takeIf { it.isNotBlank() },
        uri = active.playlistUri,
    )

    ShellSheets(
        flags = SheetFlags(
            profileDrawer = flags.isProfileDrawerOpen,
            accounts = flags.isAccountsSheetOpen,
            trackOptions = flags.isTrackOptionsOpen,
            playlistOptions = flags.isPlaylistOptionsOpen,
            albumOptions = flags.isAlbumOptionsOpen,
            showOptions = flags.isShowOptionsOpen,
            artistOptions = flags.isArtistOptionsOpen,
            sort = flags.isSortSheetOpen,
            create = flags.isCreateSheetOpen,
            device = flags.isDeviceSheetOpen,
            queue = flags.isQueueSheetOpen,
            sleepTimer = flags.isSleepTimerOpen,
            canvasResolutionSheet = flags.isCanvasResolutionSheetOpen
        ),
        selectedTrack = local.trackForOptions,
        devices = playbackState?.devices.orEmpty(),
        connectedDevice = env.connectedDevice,
        queueFromContext = playbackState?.queueFromContext.orEmpty(),
        canAddToQueue = local.trackForOptions?.uri
            ?.takeIf { it.isNotBlank() }
            ?.let { uri ->
                uri != currentTrackUri &&
                    playbackState?.queue.orEmpty().none { it.uri == uri } &&
                    !com.quackify.compose.bans.ArtistBans.isBanned(local.trackForOptions?.artistUri.orEmpty())
            } ?: false,
        currentTrackTitle = env.currentTrackTitle,
        currentTrackArtist = env.currentArtist,
        currentTrackUri = currentTrackUri,
        currentTrackCoverUrl = env.currentCoverUrl.takeIf { it.isNotBlank() },
        playlistEntity = playlistEntity,
        playlistEditable = active.playlistUri.startsWith("spotify:playlist:") && env.playlistDetail?.editable == true,
        playlistSaved = if (active.playlistUri.startsWith("spotify:playlist:")) env.playlistSaved else null,
        albumTitle = active.albumTitle,
        albumArtist = active.albumArtist,
        albumCoverUrl = active.albumCoverUrl,
        albumUri = active.albumUri.ifBlank { env.albumDetail?.uri.orEmpty() },
        albumSaved = env.albumSaved,
        onToggleAlbumSaved = {
            val uri = active.albumUri.ifBlank { env.albumDetail?.uri.orEmpty() }
            if (uri.startsWith("spotify:album:")) {
                env.onAlbumSavedToggle?.invoke(uri, env.albumSaved != true)
            }
        },
        showName = env.showDetail?.name.orEmpty(),
        showPublisher = env.showDetail?.publisher.orEmpty(),
        showCoverUrl = env.showDetail?.imageUrl?.takeIf { it.isNotBlank() },
        showUri = active.showUri.ifBlank { env.showDetail?.uri.orEmpty() },
        showFollowing = env.showDetail?.saved == true,
        onShowFollowToggle = {
            val uri = active.showUri.ifBlank { env.showDetail?.uri.orEmpty() }
            if (uri.startsWith("spotify:show:")) {
                env.onShowFollowToggle?.invoke(uri, env.showDetail?.saved != true)
            }
        },
        onDismissShowOptions = { flags.isShowOptionsOpen = false },
        artistName = sheetArtistName,
        artistAvatarUrl = sheetArtistAvatarUrl,
        artistUri = sheetArtistUri,
        artistFollowing = env.artistFollowed == true,
        artistBanned = artistBannedState == true,
        onArtistFollowToggle = {
            if (sheetArtistUri.startsWith("spotify:artist:")) {
                env.onArtistFollowToggle?.invoke(sheetArtistUri, env.artistFollowed != true)
            }
        },
        onDontPlayArtist = toggle@{
            if (!sheetArtistUri.startsWith("spotify:artist:")) return@toggle
            val previous = artistBannedState
            val next = previous != true
            artistBannedState = next
            env.shellScope.launch {
                val ok = Collection2.setArtistBanned(env.context, sheetArtistUri, next)
                if (!ok) {
                    artistBannedState = previous
                } else {
                    env.onArtistBanChanged?.invoke(sheetArtistUri, next)
                    ArtistBanStore.invalidate()
                    com.quackify.compose.bans.ArtistBans.setBanned(sheetArtistUri, next)
                }
                Toast.makeText(
                    env.context,
                    when {
                        ok && next -> artistBlockedMsg
                        ok -> artistUnblockedMsg
                        else -> artistUpdateFailedMsg
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        onArtistRadio = {
            flags.isArtistOptionsOpen = false
            val artistUriNow = sheetArtistUri
            if (artistUriNow.startsWith("spotify:artist:")) {
                env.shellScope.launch {
                    val radio = env.onArtistRadio?.invoke(artistUriNow)
                    if (radio != null && radio.startsWith("spotify:playlist:")) {
                        env.openPlaylist(artistRadioTitle, radio)
                    }
                }
            }
        },
        sortOption = local.sortOption,
        sortDirection = local.sortDirection,
        sleepTimer = if (env.playerListener != null) playbackState?.sleepTimer else local.sleepTimer,
        userDisplayName = env.userDisplayName,
        userAvatarUrl = env.userAvatarUrl,
        userColorHex = env.userColorHex,
        savedAccounts = env.savedAccounts,
        activeAccountId = env.activeAccountId,
        isFolder = env.currentScreen == ShellScreen.FOLDER,
        canvasAvailable = flags.isTrackOptionsFromPlayer && env.isCanvasAllowed && env.canvasFilePath != null,
        isCanvasEnabled = env.isCanvasEnabled,
        onCanvasToggle = { env.onCanvasToggle?.invoke(!env.isCanvasEnabled) },
        canvasResolution = env.canvasResolution,
        onCanvasResolution = { res -> env.onCanvasResolution?.invoke(res) },
        onDismissProfile = { flags.isProfileDrawerOpen = false },
        onDismissAccounts = { flags.isAccountsSheetOpen = false },
        onSwitchAccount = { id -> env.onSwitchAccount?.invoke(id) },
        onAddAccount = { env.onAddAccount?.invoke() },
        onRemoveAccount = { id -> env.onRemoveAccount?.invoke(id) },
        onOpenAccounts = { flags.isAccountsSheetOpen = true },
        onDismissTrackOptions = { flags.isTrackOptionsOpen = false },
        onDismissPlaylistOptions = { flags.isPlaylistOptionsOpen = false },
        onDismissAlbumOptions = { flags.isAlbumOptionsOpen = false },
        onDismissArtistOptions = {
            flags.isArtistOptionsOpen = false
            flags.isArtistOptionsFromPlayer = false
        },
        onDismissSort = { flags.isSortSheetOpen = false },
        onDismissCreate = { flags.isCreateSheetOpen = false },
        onDismissDevice = { flags.isDeviceSheetOpen = false },
        onDismissQueue = { flags.isQueueSheetOpen = false },
        onDismissSleepTimer = { flags.isSleepTimerOpen = false },
        onCanvasResolutionClick = { flags.isCanvasResolutionSheetOpen = true },
        onDismissCanvasResolution = { flags.isCanvasResolutionSheetOpen = false },
        onShowCredits = {
            local.trackForOptions?.let { track ->
                flags.isCreditsOpen = true
                local.creditsTrack = track
                env.onShowCredits?.invoke(track.uri)
            }
        },
        onEditPlaylist = {
            if (active.playlistUri.startsWith("spotify:playlist:") && active.playlistUri.isNotEmpty()) env.onEditPlaylist?.invoke(active.playlistUri)
        },
        onTogglePlaylistSaved = {
            if (active.playlistUri.startsWith("spotify:playlist:") && active.playlistUri.isNotEmpty()) {
                env.onTogglePlaylistSaved?.invoke(active.playlistUri, !(env.playlistSaved ?: false))
            }
        },
        onDeletePlaylist = {
            env.shellScope.launch {
                val ok = env.onDeletePlaylist?.invoke(active.playlistUri) ?: false
                if (ok) env.handleBack()
            }
        },
        onCopyLink = { uri -> env.copyEntityLink(uri) },
        onShare = { uri -> env.shareEntity(uri) },
        onWhatsNew = { env.pushCurrentState(); env.setCurrentScreen(ShellScreen.NOTIFICATIONS) },
        onNotifications = {
            env.navController.openActivity()
            env.onOpenActivity?.invoke()
        },
        notificationsUnread = env.activityUnread,
        onProfile = {
            env.pushCurrentState()
            env.setCurrentScreen(ShellScreen.PROFILE)
            env.onLoadProfile?.invoke()
        },
        onHistory = {
            env.pushCurrentState()
            env.setCurrentScreen(ShellScreen.HISTORY)
            env.onOpenHistory?.invoke()
        },
        onSettings = env.handleOpenSettings,
        onLogout = { env.onLogout?.invoke() },
        onAddToQueue = { track -> env.handleQueueAdd(track.uri) },
        onViewArtist = { artist, uri ->
            flags.isTrackOptionsOpen = false
            env.setIsPlayerExpanded(false)
            env.openArtist(artist, uri)
        },
        onViewAlbum = { album, uri ->
            flags.isTrackOptionsOpen = false
            env.setIsPlayerExpanded(false)
            env.openAlbum(album, local.trackForOptions?.artist ?: "", uri)
        },
        onSortSelected = { selected, direction -> local.sortOption = selected; local.sortDirection = direction },
        onGoToArtist = { artist ->
            flags.isAlbumOptionsOpen = false
            env.setIsPlayerExpanded(false)
            env.openArtist(artist, env.albumDetail?.artistUri.orEmpty())
        },
        onCreatePlaylist = {
            env.shellScope.launch {
                local.playlistCreateName = env.onPlaylistDefaultName?.invoke() ?: "My Playlist"
                flags.isCreatePlaylistOpen = true
            }
        },
        onCreateFolder = { local.folderDraft = FolderDraft(mode = FolderMode.CREATE) },
        onSelectDevice = { id -> env.setLocalDevice(id); flags.isDeviceSheetOpen = false; env.playerListener?.onSelectDevice(id) },
        onDeviceVisible = { visible -> env.playerListener?.onDeviceSheetVisible(visible) },
        userQueue = playbackState?.queue.orEmpty(),
        trackTasteExcluded = env.trackTasteExcluded,
        trackPlaysOverride = local.trackOptionsPlays,
        onToggleTasteExclusion = toggle@{ exclude ->
            val track = local.trackForOptions
            val uri = track?.uri.orEmpty()
            if (!uri.startsWith("spotify:track:")) return@toggle
            val previous = env.trackTasteExcluded
            env.setTrackTasteExcluded(exclude)
            env.tasteExclusionCache[uri] = exclude
            env.shellScope.launch {
                val ok = Collection2.setExcludedFromTasteProfile(env.context, uri, exclude)
                if (uri == local.trackForOptions?.uri) {
                    if (!ok) {
                        env.setTrackTasteExcluded(previous)
                        if (previous != null) env.tasteExclusionCache[uri] = previous
                    }
                    Toast.makeText(
                        env.context,
                        when {
                            ok && exclude -> tasteExcludedMsg
                            ok -> tasteIncludedMsg
                            else -> tasteUpdateFailedMsg
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        },
        queueSourceContext = playbackState?.queueContextLabel?.takeIf { it.isNotBlank() }
            ?: playbackState?.contextLabel?.takeIf { it.isNotBlank() } ?: "",
        onQueueTrackSelect = { track ->
            if (env.playerListener != null) {
                if (track.uri.isNotEmpty()) {
                    env.playerListener.onPlayUri(track.uri)
                } else {
                    env.playerListener.onTrackSelect(track.title, track.artist)
                }
            }
        },
        onRemoveFromQueue = { track ->
            val queue = playbackState?.queue.orEmpty()
            val context = playbackState?.queueFromContext.orEmpty()
            val remaining = queue.filterNot { it.uri == track.uri }.map { it.uri }
            if (remaining.size != queue.size) {
                env.playerListener?.onQueueSet(remaining + context.map { it.uri })
            }
        },
        onClearQueue = {
            val context = playbackState?.queueFromContext.orEmpty()
            env.playerListener?.onQueueSet(context.map { it.uri })
        },
        onReorderQueue = { reordered ->
            val queue = playbackState?.queue.orEmpty()
            val context = playbackState?.queueFromContext.orEmpty()
            val hidden = queue.filter { it.uri.isBlank() || it.title.isBlank() }.map { it.uri }
            env.playerListener?.onQueueSet(reordered.map { it.uri } + hidden + context.map { it.uri })
        },
        onSleepTimerSelected = { option ->
            local.sleepTimer = if (option == SleepTimerOption.OFF) null else option
            env.playerListener?.onSleepTimer(option)
        },
        onRemoveTrackFromPlaylist = if (env.currentScreen == ShellScreen.PLAYLIST && active.playlistUri.startsWith("spotify:playlist:") && env.playlistDetail?.editable == true && active.playlistUri.isNotBlank()) {
            {
                val trackUri = local.trackForOptions?.uri.orEmpty()
                val playlistUri = active.playlistUri
                flags.isTrackOptionsOpen = false
                if (trackUri.isNotBlank() && playlistUri.isNotBlank()) {
                    env.onRemoveTrackFromPlaylist?.invoke(playlistUri, trackUri)
                }
            }
        } else null,
        onSongRadio = {
            val trackNow = local.trackForOptions
            flags.isTrackOptionsOpen = false
            env.setIsPlayerExpanded(false)
            if (trackNow != null && trackNow.uri.startsWith("spotify:track:")) {
                env.shellScope.launch {
                    val radio = env.onArtistRadio?.invoke(trackNow.uri)
                    if (radio != null && radio.startsWith("spotify:playlist:")) {
                        env.openPlaylist(trackRadioTitle, radio)
                    }
                }
            }
        },
        onAlbumRadio = {
            flags.isAlbumOptionsOpen = false
            env.setIsPlayerExpanded(false)
            val albumUriNow = active.albumUri.ifBlank { env.albumDetail?.uri.orEmpty() }
            if (albumUriNow.startsWith("spotify:album:")) {
                env.shellScope.launch {
                    val radio = env.onArtistRadio?.invoke(albumUriNow)
                    if (radio != null && radio.startsWith("spotify:playlist:")) {
                        env.openPlaylist(albumRadioTitle, radio)
                    }
                }
            }
        },
        onAddToPlaylistFromAlbum = {
            flags.isAlbumOptionsOpen = false
            local.addTrackTarget = TrackItem(
                title = active.albumTitle,
                artist = active.albumArtist,
                uri = active.albumUri.ifBlank { env.albumDetail?.uri.orEmpty() }
            )
            flags.isAddToPlaylistOpen = true
        }
    )
}
