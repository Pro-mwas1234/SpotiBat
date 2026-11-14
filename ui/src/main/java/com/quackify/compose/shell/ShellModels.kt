package com.quackify.compose.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.quackify.backend.model.ActivityNotification
import com.quackify.backend.model.ConnectDevice
import com.quackify.backend.model.SelfProfile
import com.quackify.compose.components.RepeatMode
import com.quackify.compose.components.ShuffleMode
import com.quackify.compose.library.LibraryItem
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.sheets.EntityOptions
import com.quackify.compose.sheets.LibrarySortOption
import com.quackify.compose.sheets.SortDirection
import com.quackify.compose.sheets.SleepTimerOption
import com.quackify.compose.theme.EncoreColors

enum class ShellScreen {
    HOME,
    SEARCH,
    LIBRARY,
    PLAYLIST,
    ALBUM,
    ARTIST,
    CATEGORY,
    SETTINGS,
    NOTIFICATIONS,
    ACTIVITY,
    HISTORY,
    FOLDER,
    SHOW,
    EPISODE,
    BROWSE,
    ADD_TRACKS,
    PROFILE
}

data class PlaylistEditDraft(
    val uri: String,
    val name: String,
    val description: String,
    val coverUrl: String?,
    val canRemoveCover: Boolean,
)

data class NavItem(
    val title: String,
    val iconRes: Int,
    val activeIconRes: Int = iconRes
)

data class ActivityFeedState(
    val feed: List<ActivityNotification> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    val self: SelfProfile? = null,
    val unread: Boolean = false,
    val onOpen: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onLeave: () -> Unit = {},
    val onDrawerOpened: () -> Unit = {},
)

data class BackStackItem(
    val screen: ShellScreen,
    val categoryTitle: String = "",
    val playlistTitle: String = "",
    val playlistUri: String = "",
    val playlistCoverUrl: String? = null,
    val albumTitle: String = "",
    val albumArtist: String = "",
    val albumUri: String = "",
    val albumCoverUrl: String? = null,
    val artistName: String = "",
    val artistUri: String = "",
    val artistAvatarUrl: String? = null,
    val showUri: String = "",
    val searchQuery: String = ""
)

@Stable
class PlaybackProgress {
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    val progress: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    fun set(positionMs: Long, durationMs: Long) {
        this.positionMs = positionMs.coerceIn(0L, durationMs.takeIf { it > 0L } ?: positionMs)
        if (durationMs > 0L) this.durationMs = durationMs
    }

    fun reset() {
        positionMs = 0L
        durationMs = 0L
    }
}

data class ComposePlaybackState(
    val title: String = "",
    val artist: String = "",
    val coverUrl: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isLiked: Boolean = false,
    val isShuffle: Boolean = false,
    val isRepeat: Boolean = false,
    val shuffleMode: ShuffleMode = if (isShuffle) ShuffleMode.SHUFFLE else ShuffleMode.OFF,
    val repeatMode: RepeatMode = if (isRepeat) RepeatMode.REPEAT_ALL else RepeatMode.OFF,
    val canShuffle: Boolean = true,
    val canSmartShuffle: Boolean = false,
    val canRepeat: Boolean = true,
    val isEnhanced: Boolean = false,
    val connectedDevice: String = "This Phone",
    val trackUri: String = "",
    val artistUri: String = "",
    val albumUri: String = "",
    val activeDeviceName: String = "",
    val activeDeviceId: String = "",
    val controllingRemote: Boolean = false,
    val remoteCanSeek: Boolean = true,
    val remoteCanSkipPrev: Boolean = true,
    val remoteCanShuffle: Boolean = true,
    val remoteCanRepeat: Boolean = true,
    val devices: List<ConnectDevice> = emptyList(),
    val queue: List<TrackItem> = emptyList(),
    val queueFromContext: List<TrackItem> = emptyList(),
    val queueContext: String = "",
    val queueContextLabel: String = "",
    val contextUri: String = "",
    val contextLabel: String = "",
    val explicit: Boolean = false,
    val musicVideoAvailable: Boolean = false,
    val videoActive: Boolean = false,
    val videoQualities: List<Int> = emptyList(),
    val videoSwitching: Boolean = false,
    val playbackSpeed: Float = 1f,
    val chapters: List<ChapterMarker> = emptyList(),
    val sleepTimer: SleepTimerOption? = null,
)

data class ChapterMarker(
    val title: String,
    val startMs: Long,
    val endMs: Long,
)

interface ComposePlayerListener {
    fun onPlayPause() {}
    fun onSkipNext() {}
    fun onSkipPrevious() {}
    fun onSeek(positionMs: Long) {}
    fun onLikeToggle() {}
    fun onShuffleToggle() {}
    fun onRepeatToggle() {}
    fun onTrackSelect(title: String, artist: String) {}
    fun onPlayUri(uri: String) {}
    fun onPlayTrack(uri: String, contextUri: String, index: Int) { onPlayUri(uri) }
    fun onListRendered(uris: List<String>) {}
    fun onQueueAdd(uri: String) {}
    fun onSelectDevice(deviceId: String) {}
    fun onDeviceSheetVisible(visible: Boolean) {}
    fun onVolumeChange(deviceId: String, level: Int) {}
    fun onQueueSet(uris: List<String>) {}
    fun onSearch(query: String) {}
    fun onLoadPlaylist(uri: String) {}
    fun onLoadAlbum(uri: String, albumName: String = "", artist: String = "") {}
    fun onLoadArtist(uri: String, artistName: String = "") {}
    fun onRefreshLibrary() {}
    fun onRefreshHome() {}
    fun onSwitchVideo(toVideo: Boolean) {}
    fun onVideoQuality(height: Int) {}
    fun onVideoSurface(surface: android.view.Surface?) {}
    fun onPlaybackSpeed(speed: Float) {}
    fun onSkipSeek(deltaMs: Long) {}
    fun onPlayEpisode(uri: String, contextUri: String, hasVideo: Boolean) {}
    fun onSleepTimer(option: SleepTimerOption) {}
}

internal enum class FolderMode { CREATE, RENAME, PICKER }

internal data class FolderDraft(
    val mode: FolderMode,
    val folderUri: String = "",
    val title: String = "",
    val folders: List<Pair<String, String>> = emptyList(),
)

data class DeleteAsk(
    val title: String,
    val message: String,
    val onConfirm: () -> Unit,
)

internal data class PlaybackOrigin(
    val screen: ShellScreen,
    val label: String,
)

internal class ShellUiFlags {
    var isProfileDrawerOpen by mutableStateOf(false)
    var isProfileFollowOpen by mutableStateOf(false)
    var isAccountsSheetOpen by mutableStateOf(false)
    var isTrackOptionsOpen by mutableStateOf(false)
    var isTrackOptionsFromPlayer by mutableStateOf(false)
    var isPlaylistOptionsOpen by mutableStateOf(false)
    var isPlaylistEditMode by mutableStateOf(false)
    var isAlbumOptionsOpen by mutableStateOf(false)
    var isArtistOptionsOpen by mutableStateOf(false)
    var isArtistOptionsFromPlayer by mutableStateOf(false)
    var isSortSheetOpen by mutableStateOf(false)
    var isCreateSheetOpen by mutableStateOf(false)
    var isDeviceSheetOpen by mutableStateOf(false)
    var isQueueSheetOpen by mutableStateOf(false)
    var isSleepTimerOpen by mutableStateOf(false)
    var isCanvasResolutionSheetOpen by mutableStateOf(false)
    var isArtistAboutOpen by mutableStateOf(false)
    var isDiscographyOpen by mutableStateOf(false)
    var isCreditsOpen by mutableStateOf(false)
    var isAddToPlaylistOpen by mutableStateOf(false)
    var isRenamePlaylistDialogOpen by mutableStateOf(false)
    var isCreatePlaylistOpen by mutableStateOf(false)
    var isContributorsOpen by mutableStateOf(false)
}

internal class ShellActiveEntity {
    var playlistTitle by mutableStateOf("")
    var playlistUri by mutableStateOf("")
    var playlistCoverUrl by mutableStateOf<String?>(null)
    var albumCoverUrl by mutableStateOf<String?>(null)
    var albumUri by mutableStateOf("")
    var albumTitle by mutableStateOf("")
    var albumArtist by mutableStateOf("")
    var albumYear by mutableStateOf("")
    var albumReleaseDate by mutableStateOf("")
    var albumColor by mutableStateOf(EncoreColors.Card)
    var artistName by mutableStateOf("")
    var artistUri by mutableStateOf("")
    var artistAvatarUrl by mutableStateOf<String?>(null)
    var categoryTitle by mutableStateOf("")
    var categoryColor by mutableStateOf(Color(0xFF148A08))
    var showUri by mutableStateOf("")
    var episodeUri by mutableStateOf("")
    var folderUri by mutableStateOf("")
    var folderTitle by mutableStateOf("")
}

internal class ShellNavState {
    var currentScreen by mutableStateOf(ShellScreen.HOME)
    var selectedNavIndex by mutableIntStateOf(0)
    var isPlayerExpanded by mutableStateOf(false)
    val backStack = mutableStateListOf<BackStackItem>()
}

internal class ShellLocalState {
    var isPlaying by mutableStateOf(false)
    var trackTitle by mutableStateOf("")
    var artist by mutableStateOf("")
    var progress by mutableFloatStateOf(0f)
    var positionMs by mutableLongStateOf(0L)
    var isLiked by mutableStateOf(false)
    var shuffleMode by mutableStateOf(ShuffleMode.OFF)
    var repeatMode by mutableStateOf(RepeatMode.OFF)
    var device by mutableStateOf("")
    var trackForOptions by mutableStateOf<TrackItem?>(null)
    var trackTasteExcluded by mutableStateOf<Boolean?>(null)
    var trackOptionsSaved by mutableStateOf<Boolean?>(null)
    var trackOptionsPlays by mutableStateOf<Long?>(null)
    var artistOptionsName by mutableStateOf("")
    var artistOptionsUri by mutableStateOf("")
    var artistOptionsAvatarUrl by mutableStateOf<String?>(null)
    var sortOption by mutableStateOf(LibrarySortOption.RECENTS)
    var sortDirection by mutableStateOf(SortDirection.DESCENDING)
    var playbackOrigin by mutableStateOf<PlaybackOrigin?>(null)
    var libraryItemOptions by mutableStateOf<EntityOptions?>(null)
    var activeFolderOptionsItem by mutableStateOf<LibraryItem?>(null)
    var activeFolderOptionsNested by mutableStateOf(false)
    var activeEntityInsideFolder by mutableStateOf(false)
    var deleteAsk by mutableStateOf<DeleteAsk?>(null)
    var folderDraft by mutableStateOf<FolderDraft?>(null)
    var playlistCreateName by mutableStateOf("My Playlist")
    var creditsTrack by mutableStateOf<TrackItem?>(null)
    var addTrackTarget by mutableStateOf<TrackItem?>(null)
    var sleepTimer by mutableStateOf<SleepTimerOption?>(null)
}


internal fun entityUrl(uri: String): String? {
    if (!uri.startsWith("spotify:")) return null
    val type = uri.removePrefix("spotify:").substringBefore(":")
    val id = uri.substringAfterLast(":")
    return if (type.isNotBlank() && id.isNotBlank()) "https://open.spotify.com/$type/$id" else null
}

internal fun isCollectionUri(uri: String): Boolean =
    uri.startsWith("spotify:collection:") ||
        uri.endsWith(":collection") ||
        uri == "spotify:user:@:collection"

data class ShellDeepLink(
    val kind: String,
    val uri: String,
    val query: String = "",
)
