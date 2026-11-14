package com.quackify.compose.shell

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import com.quackify.compose.components.RepeatMode
import com.quackify.compose.components.ShuffleMode
import com.quackify.compose.player.TimeSyncedLyric
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.util.rememberDominantColor
import kotlinx.coroutines.CoroutineScope

internal class ShellPlayerEnv(
    val flags: ShellUiFlags,
    val local: ShellLocalState,
    val navController: ShellNavController,
    val playbackState: ComposePlaybackState?,
    val progressState: PlaybackProgress?,
    val playerListener: ComposePlayerListener?,
    val shellScope: CoroutineScope,
    val currentScreen: ShellScreen,
    val isPlayerExpanded: Boolean,
    val setIsPlayerExpanded: (Boolean) -> Unit,
    val setCurrentScreen: (ShellScreen) -> Unit,
    val pushCurrentState: () -> Unit,
    val navBackStack: SnapshotStateList<BackStackItem>,
    val selectedNavIndex: Int,
    val setSelectedNavIndex: (Int) -> Unit,
    val searchFieldFocusRequester: FocusRequester,
    val navItems: List<NavItem>,
    val playerIdentity: PlayerIdentity,
    val currentTrackTitle: String,
    val currentArtist: String,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val isLiked: Boolean,
    val currentCoverUrl: String,
    val currentShuffleMode: ShuffleMode,
    val currentRepeatMode: RepeatMode,
    val connectedDevice: String,
    val resolvedAlbumUri: String,
    val resolvedArtistUri: String,
    val nowPlayingMonthlyListeners: String,
    val nowPlayingArtistBio: String,
    val nowPlayingArtistImageUrl: String?,
    val nowPlayingArtistFollowed: Boolean?,
    val nowPlayingLyrics: List<TimeSyncedLyric>,
    val canvasFilePath: String?,
    val isCanvasAllowed: Boolean,
    val isCanvasEnabled: Boolean,
    val videoFullscreen: Boolean,
    val playbackOrigin: PlaybackOrigin?,
    val handlePlayPause: () -> Unit,
    val handleSkipNext: () -> Unit,
    val handleSkipPrevious: () -> Unit,
    val onCanvasToggle: ((Boolean) -> Unit)?,
    val onToggleVideoFullscreen: ((Boolean) -> Unit)?,
    val onArtistFollowToggle: ((String, Boolean) -> Unit)?,
    val onAddToPlaylist: ((TrackItem) -> Unit)?,
    val openAlbum: (String, String, String) -> Unit,
    val openArtist: (String, String) -> Unit,
    val shareEntity: (String) -> Unit,
)

/**
 * The mini-player bar and the expanded now-playing screen, extracted from
 * EncoreAppShell so that composable stays under the JIT method-size limit.
 */
@Composable
internal fun BoxScope.ShellPlayerLayer(env: ShellPlayerEnv) {
    val bottomBarModifier = Modifier.align(Alignment.BottomCenter)
    val playbackState = env.playbackState
    val progressState = env.progressState
    val currentProgress = progressState?.progress ?: env.local.progress
    val currentPositionMs = progressState?.positionMs ?: env.local.positionMs
    val durationMs = progressState?.durationMs ?: 0L

    val nowPlayingDominantColor by rememberDominantColor(
        imageUrl = env.currentCoverUrl,
        fallbackSeed = env.currentTrackTitle + env.currentArtist,
        defaultColor = Color(0xFF432371)
    )

    val effectiveConnectedDevice = playbackState?.connectedDevice?.takeIf { it.isNotBlank() }
    val activeDevice = playbackState?.devices?.firstOrNull { it.active || it.id == playbackState.activeDeviceId }
        ?: playbackState?.devices?.firstOrNull { it.name == effectiveConnectedDevice }
    val activeDeviceKind = activeDevice?.kind

    if (!env.flags.isPlaylistEditMode && env.currentScreen != ShellScreen.ADD_TRACKS) {
        ShellBottomBar(
            title = env.playerIdentity.title,
            artist = env.playerIdentity.artist,
            isPlaying = env.isPlaying,
            isBuffering = env.isBuffering,
            progress = currentProgress,
            isLiked = env.isLiked,
            coverUrl = env.currentCoverUrl,
            coverColor = nowPlayingDominantColor,
            connectedDevice = effectiveConnectedDevice,
            connectedDeviceKind = activeDeviceKind,
            navItems = env.navItems,
            currentNavIndex = env.selectedNavIndex,
            onNavSelect = { index ->
                env.setSelectedNavIndex(index)
                when (index) {
                    0 -> if (env.currentScreen != ShellScreen.HOME) { env.navBackStack.clear(); env.setCurrentScreen(ShellScreen.HOME) }
                    1 -> if (env.currentScreen != ShellScreen.SEARCH) {
                        env.navBackStack.clear(); env.setCurrentScreen(ShellScreen.SEARCH)
                    } else {
                        runCatching { env.searchFieldFocusRequester.requestFocus() }
                    }
                    2 -> if (env.currentScreen != ShellScreen.LIBRARY) { env.navBackStack.clear(); env.setCurrentScreen(ShellScreen.LIBRARY) }
                }
            },
            onSearchDoubleTap = {
                runCatching { env.searchFieldFocusRequester.requestFocus() }
            },
            onPlayPause = env.handlePlayPause,
            onLike = {
                val playingUri = playbackState?.trackUri.orEmpty()
                if (!env.isLiked) {
                    if (env.playerListener != null) env.playerListener.onLikeToggle() else env.local.isLiked = true
                }
                val playingTrack = TrackItem(
                    title = env.currentTrackTitle,
                    artist = env.currentArtist,
                    uri = playingUri,
                    imageUrl = env.currentCoverUrl.takeIf { it.isNotBlank() },
                    saved = true
                )
                env.local.addTrackTarget = playingTrack
                env.onAddToPlaylist?.invoke(playingTrack)
                env.flags.isAddToPlaylistOpen = true
            },
            onDeviceClick = { env.flags.isDeviceSheetOpen = true },
            onBarClick = { env.setIsPlayerExpanded(true) },
            onSkipNext = env.handleSkipNext,
            onSkipPrevious = env.handleSkipPrevious,
            hasTrack = env.playerIdentity.trackLoaded,
            showMiniPlayer = env.playerIdentity.trackLoaded,
            modifier = bottomBarModifier
        )
    }

    val bridgeContextLabel = playbackState?.queueContextLabel?.takeIf { it.isNotBlank() }
        ?: playbackState?.contextLabel?.takeIf { it.isNotBlank() }
        ?: ""
    val (contextHeader, contextSub) = nowPlayingContext(
        trackLoaded = env.playerIdentity.trackLoaded,
        origin = env.playbackOrigin,
        contextUri = playbackState?.contextUri,
        contextLabel = bridgeContextLabel,
    )
    val remoteCtl = playbackState?.controllingRemote == true
    val effectiveCanShuffle = if (remoteCtl) playbackState?.remoteCanShuffle ?: true else playbackState?.canShuffle ?: true
    val effectiveCanRepeat = if (remoteCtl) playbackState?.remoteCanRepeat ?: true else playbackState?.canRepeat ?: true
    val effectiveCanSkipPrevious = if (remoteCtl) playbackState?.remoteCanSkipPrev ?: true else true
    val effectiveCanSeek = if (remoteCtl) playbackState?.remoteCanSeek ?: true else true

    ShellNowPlaying(
        visible = env.isPlayerExpanded,
        title = env.playerIdentity.title,
        artist = env.playerIdentity.artist,
        contextHeader = contextHeader,
        contextSub = contextSub,
        isPlaying = env.isPlaying,
        isBuffering = env.isBuffering,
        progress = currentProgress,
        durationMs = durationMs,
        positionMs = currentPositionMs,
        dominantColor = nowPlayingDominantColor,
        isLiked = env.isLiked,
        isExplicit = playbackState?.explicit ?: false,
        monthlyListeners = env.nowPlayingMonthlyListeners,
        artistBio = env.nowPlayingArtistBio,
        artistImageUrl = env.nowPlayingArtistImageUrl,
        isArtistFollowing = env.nowPlayingArtistFollowed == true,
        onArtistFollowToggle = { follow ->
            if (env.resolvedArtistUri.startsWith("spotify:artist:")) {
                env.onArtistFollowToggle?.invoke(env.resolvedArtistUri, follow)
            }
        },
        lyrics = env.nowPlayingLyrics,
        canvasFilePath = env.canvasFilePath,
        isCanvasAllowed = env.isCanvasAllowed,
        isCanvasEnabled = env.isCanvasEnabled,
        onCanvasToggle = { on -> env.onCanvasToggle?.invoke(on) },
        musicVideoAvailable = playbackState?.musicVideoAvailable ?: false,
        videoActive = playbackState?.videoActive ?: false,
        videoQualities = playbackState?.videoQualities.orEmpty(),
        videoSwitching = playbackState?.videoSwitching ?: false,
        onSwitchVideo = { to -> env.playerListener?.onSwitchVideo(to) },
        videoFullscreen = env.videoFullscreen,
        onToggleVideoFullscreen = { on -> env.onToggleVideoFullscreen?.invoke(on) },
        onVideoQuality = { height -> env.playerListener?.onVideoQuality(height) },
        onVideoSurface = { surface -> env.playerListener?.onVideoSurface(surface) },
        isEpisode = playbackState?.trackUri.orEmpty().startsWith("spotify:episode:"),
        playbackSpeed = playbackState?.playbackSpeed ?: 1f,
        chapters = playbackState?.chapters.orEmpty(),
        onPlaybackSpeed = { speed -> env.playerListener?.onPlaybackSpeed(speed) },
        onSkipSeek = { delta -> env.playerListener?.onSkipSeek(delta) },
        isEnhancedTrack = playbackState?.isEnhanced ?: false,
        shuffleMode = env.currentShuffleMode,
        repeatMode = env.currentRepeatMode,
        canShuffle = effectiveCanShuffle,
        canSmartShuffle = playbackState?.canSmartShuffle ?: false,
        canRepeat = effectiveCanRepeat,
        canSkipPrevious = effectiveCanSkipPrevious,
        canSeek = effectiveCanSeek,
        connectedDevice = env.connectedDevice,
        coverUrl = env.currentCoverUrl,
        onCollapse = { env.setIsPlayerExpanded(false) },
        onPlayPause = env.handlePlayPause,
        onSkipNext = env.handleSkipNext,
        onSkipPrevious = env.handleSkipPrevious,
        onLike = {
            val playingTrack = TrackItem(
                title = env.currentTrackTitle,
                artist = env.currentArtist,
                uri = playbackState?.trackUri.orEmpty(),
                imageUrl = env.currentCoverUrl.takeIf { it.isNotBlank() },
                saved = env.isLiked
            )
            env.local.addTrackTarget = playingTrack
            env.onAddToPlaylist?.invoke(playingTrack)
            env.flags.isAddToPlaylistOpen = true
        },
        onShuffle = { if (env.playerListener != null) env.playerListener.onShuffleToggle() else env.local.shuffleMode = env.local.shuffleMode.next() },
        onRepeat = { if (env.playerListener != null) env.playerListener.onRepeatToggle() else env.local.repeatMode = env.local.repeatMode.next() },
        onSeek = { p -> if (env.playerListener != null) env.playerListener.onSeek((p * durationMs).toLong()) else { env.local.progress = p; env.local.positionMs = (p * durationMs).toLong() } },
        onMore = {
            env.local.trackForOptions = TrackItem(
                title = env.currentTrackTitle,
                artist = env.currentArtist,
                isExplicit = playbackState?.explicit ?: false,
                uri = playbackState?.trackUri.orEmpty(),
                imageUrl = env.currentCoverUrl.takeIf { it.isNotBlank() },
                saved = env.isLiked,
                albumUri = env.resolvedAlbumUri,
                artistUri = env.resolvedArtistUri,
            )
            env.flags.isTrackOptionsFromPlayer = true
            env.flags.isTrackOptionsOpen = true
        },
        onQueue = { env.flags.isQueueSheetOpen = true },
        onDevice = { env.flags.isDeviceSheetOpen = true },
        onShare = { playbackState?.trackUri?.takeIf { it.isNotBlank() }?.let { uri -> env.shareEntity(uri) } },
        onTitleClick = {
            env.setIsPlayerExpanded(false)
            env.openAlbum(env.currentTrackTitle, env.currentArtist, env.resolvedAlbumUri)
        },
        onArtistClick = {
            env.setIsPlayerExpanded(false)
            env.openArtist(env.currentArtist, env.resolvedArtistUri)
        },
        onArtistLongClick = {
            env.local.artistOptionsName = env.currentArtist
            env.local.artistOptionsUri = env.resolvedArtistUri
            env.local.artistOptionsAvatarUrl = null
            env.flags.isArtistOptionsFromPlayer = true
            env.flags.isArtistOptionsOpen = true
        }
    )
}
