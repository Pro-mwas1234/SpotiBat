package com.mwask.bat.compose.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mwask.bat.compose.components.RepeatMode
import com.mwask.bat.compose.components.ShuffleMode
import com.mwask.bat.compose.player.EncoreNowPlayingScreen
import com.mwask.bat.compose.player.TimeSyncedLyric
import com.mwask.bat.compose.theme.EncoreAnimation

/** The screen itself only fades: the artwork carries the movement. */
private const val NowPlayingRevealFadeMs = 160

@Composable
internal fun ShellNowPlaying(
    visible: Boolean,
    title: String,
    artist: String,
    contextHeader: String,
    contextSub: String,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    progress: Float,
    durationMs: Long,
    positionMs: Long,
    dominantColor: Color,
    isLiked: Boolean,
    isExplicit: Boolean,
    monthlyListeners: String,
    artistBio: String,
    artistImageUrl: String?,
    isArtistFollowing: Boolean = false,
    onArtistFollowToggle: (Boolean) -> Unit = {},
    isEnhancedTrack: Boolean = false,
    shuffleMode: ShuffleMode,
    repeatMode: RepeatMode,
    canShuffle: Boolean,
    canSmartShuffle: Boolean,
    canRepeat: Boolean,
    canSkipPrevious: Boolean = true,
    canSeek: Boolean = true,
    connectedDevice: String,
    coverUrl: String,
    lyrics: List<TimeSyncedLyric> = emptyList(),
    canvasFilePath: String? = null,
    isCanvasAllowed: Boolean = true,
    isCanvasEnabled: Boolean = false,
    onCanvasToggle: (Boolean) -> Unit = {},
    musicVideoAvailable: Boolean = false,
    videoActive: Boolean = false,
    videoQualities: List<Int> = emptyList(),
    videoSwitching: Boolean = false,
    onSwitchVideo: (Boolean) -> Unit = {},
    videoFullscreen: Boolean = false,
    onToggleVideoFullscreen: (Boolean) -> Unit = {},
    onVideoQuality: (Int) -> Unit = {},
    onVideoSurface: (android.view.Surface?) -> Unit = {},
    isEpisode: Boolean = false,
    playbackSpeed: Float = 1f,
    chapters: List<ChapterMarker> = emptyList(),
    onPlaybackSpeed: (Float) -> Unit = {},
    onSkipSeek: (Long) -> Unit = {},
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onLike: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSeek: (Float) -> Unit,
    onMore: () -> Unit,
    onQueue: () -> Unit,
    onDevice: () -> Unit,
    onShare: () -> Unit = {},
    onTitleClick: () -> Unit = {},
    onArtistClick: () -> Unit = {},
    onArtistLongClick: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(durationMillis = NowPlayingRevealFadeMs)
        ),
        exit = slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(
                durationMillis = EncoreAnimation.NowPlayingExitDuration,
                easing = EncoreAnimation.DecelerateInterpolator
            )
        )
    ) {
        EncoreNowPlayingScreen(
            title = title,
            artist = artist,
            contextHeaderTitle = contextHeader,
            albumOrPlaylistContext = contextSub,
            isPlaying = isPlaying,
            isLoading = isBuffering,
            progress = progress,
            durationMs = durationMs,
            currentPositionMs = positionMs,
            dominantColor = dominantColor,
            isLiked = isLiked,
            isExplicit = isExplicit,
            monthlyListeners = monthlyListeners,
            artistBio = artistBio,
            artistImageUrl = artistImageUrl,
            isArtistFollowing = isArtistFollowing,
            onArtistFollowToggle = onArtistFollowToggle,
            isEnhancedTrack = isEnhancedTrack,
            shuffleMode = shuffleMode,
            repeatMode = repeatMode,
            canShuffle = canShuffle,
            canSmartShuffle = canSmartShuffle,
            canRepeat = canRepeat,
            canSkipPrevious = canSkipPrevious,
            canSeek = canSeek,
            connectedDeviceName = connectedDevice,
            coverUrl = coverUrl,
            lyrics = lyrics,
            canvasFilePath = canvasFilePath,
            isCanvasAllowed = isCanvasAllowed,
            isCanvasEnabled = isCanvasEnabled,
            onCanvasToggle = onCanvasToggle,
            musicVideoAvailable = musicVideoAvailable,
            videoActive = videoActive,
            videoQualities = videoQualities,
            videoSwitching = videoSwitching,
            onSwitchVideo = onSwitchVideo,
            videoFullscreen = videoFullscreen,
            onToggleVideoFullscreen = onToggleVideoFullscreen,
            onVideoQuality = onVideoQuality,
            onVideoSurface = onVideoSurface,
            isEpisode = isEpisode,
            playbackSpeed = playbackSpeed,
            chapters = chapters,
            onPlaybackSpeed = onPlaybackSpeed,
            onSkipSeek = onSkipSeek,
            onCollapseClick = onCollapse,
            onPlayPauseClick = onPlayPause,
            onSkipNextClick = onSkipNext,
            onSkipPreviousClick = onSkipPrevious,
            onLikeClick = onLike,
            onShuffleClick = onShuffle,
            onRepeatClick = onRepeat,
            onSeek = onSeek,
            onMoreClick = onMore,
            onQueueClick = onQueue,
            onDeviceClick = onDevice,
            onShareClick = onShare,
            onTitleClick = onTitleClick,
            onArtistClick = onArtistClick,
            onArtistLongClick = onArtistLongClick
        )
    }
}
