package com.quackify.compose.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.shell.ChapterMarker
import com.quackify.compose.components.CanvasVideoPlayer
import com.quackify.compose.components.MusicVideoSurface
import com.quackify.compose.components.EncoreHeartButton
import com.quackify.compose.components.EncoreExplicitTag
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.components.EncoreMenu
import com.quackify.compose.components.EncoreMenuItem
import com.quackify.compose.components.EncorePlayButton
import com.quackify.compose.components.EncoreRepeatButton
import com.quackify.compose.components.EncoreSmartShuffleButton
import com.quackify.compose.components.RepeatMode
import com.quackify.compose.components.ShuffleMode
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.compose.theme.EncoreAnimation
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreCornerRadii
import com.quackify.compose.theme.EncoreSpacers
import com.quackify.compose.theme.EncoreAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import com.quackify.compose.theme.EncoreTheme
import com.quackify.compose.util.rememberDominantColor

private const val LANDSCAPE_ARTWORK_PANE_WEIGHT = 0.40f
private const val LANDSCAPE_CONTROLS_PANE_WEIGHT = 0.60f
private const val DRAG_AXIS_SLOP_PX = 6f
private const val SNAP_BACK_FLING_VELOCITY = -100f
private const val DISMISS_FLING_VELOCITY = 350f
private const val DISMISS_THRESHOLD_DP = 96f
private const val AXIS_UNDECIDED = 0
private const val AXIS_VERTICAL = 2
private const val AXIS_YIELD = 3

/** Reveal choreography: the artwork lands first, then the rest follows. */
private const val REVEAL_MS = 420
private const val REVEAL_ARTWORK_DELAY_MS = 0
private const val REVEAL_INFO_DELAY_MS = 90
private const val REVEAL_CONTROLS_DELAY_MS = 160
private const val REVEAL_REST_DELAY_MS = 220
private const val REVEAL_RISE_FRACTION = 0.06f

/** Canvas banners are glued to the status bar and zoomed to hide the seam. */
private const val CANVAS_ZOOM = 1.06f
private val HEADER_ROW_HEIGHT = 60.dp
private val PEEK_HEIGHT = 44.dp

private val LANDSCAPE_LYRICS_PEEK_HEIGHT = 200.dp

private val InkDark = Color(0xFF191414)

/**
 * The lyrics panels and the header row sit straight on the album's dominant
 * color, so white content disappears on light covers (and dark content on
 * dark ones). Pick the ink from the background's luminance.
 */
private fun contrastInk(background: Color): Color =
    if (background.luminance() > 0.4f) InkDark else Color.White

/**
 * Staggered entrance for the player's blocks: each one rises a little and
 * fades in, in the order the eye should follow them - artwork, track info,
 * controls, then everything below. Driven off one shared progress value so
 * the whole reveal reads as a single motion rather than four animations.
 */
@Composable
private fun Modifier.revealAt(
    progress: Float,
    delayFraction: Float
): Modifier {
    val local = ((progress - delayFraction) / (1f - delayFraction)).coerceIn(0f, 1f)
    return this
        .graphicsLayer {
            alpha = local
            translationY = (1f - local) * size.height * REVEAL_RISE_FRACTION
        }
}

@Composable
private fun rememberRevealProgress(): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = REVEAL_MS, easing = EncoreAnimation.DecelerateInterpolator)
        )
    }
    return progress.value
}

/**
 * - Context header ("PLAYING FROM PLAYLIST" with chevron collapse)
 * - 1:1 Album Artwork with swipe-to-skip gesture
 * - Scrubber slider with elapsed and remaining timestamps
 * - 64dp Play/Pause button with Shuffle & Repeat indicators
 * - Connect Device bar with Share & Queue buttons
 * - Lyrics Peek Card with fullscreen expansion
 * - "About the Artist" Card with Verified Artist badge and Follow button
 * - Landscape: two-pane layout with artwork/canvas on the left pane and
 *   scrollable controls, lyrics and artist info on the right pane
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncoreNowPlayingScreen(
    title: String,
    artist: String,
    albumOrPlaylistContext: String,
    isPlaying: Boolean,
    progress: Float, // 0.0f to 1.0f
    durationMs: Long,
    currentPositionMs: Long,
    isLiked: Boolean,
    isShuffle: Boolean = false,
    isRepeat: Boolean = false,
    canShuffle: Boolean = true,
    canSmartShuffle: Boolean = false,
    canRepeat: Boolean = true,
    canSkipPrevious: Boolean = true,
    canSeek: Boolean = true,
    connectedDeviceName: String,
    onCollapseClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onLikeClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onMoreClick: () -> Unit,
    onQueueClick: () -> Unit,
    onDeviceClick: () -> Unit,
    onShareClick: () -> Unit = {},
    onTitleClick: () -> Unit = {},
    onArtistClick: () -> Unit = {},
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
    modifier: Modifier = Modifier,
    onBanClick: (() -> Unit)? = null,
    contextHeaderTitle: String? = null,
    isExplicit: Boolean = false,
    isEnhancedTrack: Boolean = false,
    shuffleMode: ShuffleMode = if (isShuffle) ShuffleMode.SHUFFLE else ShuffleMode.OFF,
    repeatMode: RepeatMode = if (isRepeat) RepeatMode.REPEAT_ALL else RepeatMode.OFF,
    dominantColor: Color = Color(0xFF432371),
    coverUrl: String? = null,
    isLoading: Boolean = false,
    monthlyListeners: String = "",
    artistBio: String = "",
    artistImageUrl: String? = null,
    isArtistFollowing: Boolean = false,
    onArtistFollowToggle: (Boolean) -> Unit = {},
    lyrics: List<TimeSyncedLyric> = emptyList()
) {
    var isLyricsFullscreen by remember { mutableStateOf(false) }
    var isLyricsShareOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = isLyricsFullscreen) { isLyricsFullscreen = false }

    if (videoFullscreen && videoActive) {
        BackHandler { onToggleVideoFullscreen(false) }
        VideoFullscreenLayout(
            videoQualities = videoQualities,
            onVideoQuality = onVideoQuality,
            onVideoSurface = onVideoSurface,
            progress = progress,
            durationMs = durationMs,
            currentPositionMs = currentPositionMs,
            onSeek = onSeek,
            isLoading = isLoading,
            canSeek = canSeek,
            chapters = chapters,
            onExit = { onToggleVideoFullscreen(false) }
        )
        return
    }

    val canvasActive = isCanvasAllowed && isCanvasEnabled && canvasFilePath != null

    val coroutineScope = rememberCoroutineScope()
    val playerOffsetY = remember { Animatable(0f) }
    val scrollState = rememberScrollState()
    val reveal = rememberRevealProgress()

    val activeLyricIndex = remember(currentPositionMs, lyrics) {
        lyrics.indexOfLast { currentPositionMs >= it.startTimeMs }.coerceAtLeast(0)
    }
    val activeLyricLine = lyrics.getOrNull(activeLyricIndex)?.text.orEmpty()

    val effectiveMonthlyListeners = monthlyListeners
    val effectiveArtistBio = artistBio.ifBlank {
        if (artist.isNotBlank()) "Listen to $artist. Discover top songs and upcoming releases." else ""
    }

    val effectiveIsLoading = isLoading
    val effectiveLyricsVisible = lyrics.isNotEmpty() && activeLyricLine.isNotBlank()

    val lyricLineHeight by animateDpAsState(
        targetValue = if (effectiveLyricsVisible) 44.dp else 0.dp,
        animationSpec = tween(
            durationMillis = 350,
            easing = EncoreAnimation.DecelerateInterpolator
        ),
        label = "lyricLineHeight"
    )
    val lyricBottomSpacerHeight by animateDpAsState(
        targetValue = if (effectiveLyricsVisible) 28.dp else 0.dp,
        animationSpec = tween(
            durationMillis = 350,
            easing = EncoreAnimation.DecelerateInterpolator
        ),
        label = "lyricBottomSpacerHeight"
    )

    val artworkPushUpOffset by animateDpAsState(
        targetValue = if (effectiveLyricsVisible) 0.dp else 40.dp,
        animationSpec = tween(
            durationMillis = 350,
            easing = EncoreAnimation.DecelerateInterpolator
        ),
        label = "artworkPushUpOffset"
    )

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val canvasImmersive = canvasActive && !videoActive && !isLandscape
    var canvasControlsRevealed by remember { mutableStateOf(true) }
    val canvasControlsShown = !canvasImmersive || canvasControlsRevealed
    var prevCanvasEnabled by remember { mutableStateOf(isCanvasEnabled) }

    LaunchedEffect(canvasImmersive) {
        if (!canvasImmersive) canvasControlsRevealed = true
    }
    LaunchedEffect(isCanvasEnabled) {
        if (isCanvasEnabled && !prevCanvasEnabled) canvasControlsRevealed = false
        prevCanvasEnabled = isCanvasEnabled
    }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val dismissThresholdPx = with(density) { DISMISS_THRESHOLD_DP.dp.toPx() }
    val videoTopHeight = (configuration.screenWidthDp * 9f / 16f).dp
    val artworkSide = (configuration.screenWidthDp - 48).dp
    val mediaTransition = tween<Dp>(350, easing = EncoreAnimation.DecelerateInterpolator)
    val animatedMediaHeight by animateDpAsState(
        targetValue = if (videoActive) videoTopHeight else artworkSide,
        animationSpec = mediaTransition,
        label = "mediaHeight"
    )
    val animatedMediaPadding by animateDpAsState(
        targetValue = if (videoActive) 0.dp else 24.dp,
        animationSpec = mediaTransition,
        label = "mediaPadding"
    )
    val topSpacerWeight by animateFloatAsState(
        targetValue = if (videoActive) 0f else 1f,
        animationSpec = tween(350, easing = EncoreAnimation.DecelerateInterpolator),
        label = "topSpacerWeight"
    )
    val bottomSpacerWeight by animateFloatAsState(
        targetValue = if (videoActive) 0f else 1f,
        animationSpec = tween(350, easing = EncoreAnimation.DecelerateInterpolator),
        label = "bottomSpacerWeight"
    )

    LaunchedEffect(Unit) {
        playerOffsetY.snapTo(0f)
    }

    val dismissPlayer: () -> Unit = {
        coroutineScope.launch {
            playerOffsetY.animateTo(
                targetValue = screenHeightPx + 500f,
                animationSpec = tween(
                    durationMillis = EncoreAnimation.NowPlayingExitDuration,
                    easing = EncoreAnimation.DecelerateInterpolator
                )
            )
            onCollapseClick()
        }
    }

    val snapBackPlayer: () -> Unit = {
        coroutineScope.launch {
            playerOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = EncoreAnimation.NowPlayingExitDuration,
                    easing = EncoreAnimation.DecelerateInterpolator
                )
            )
        }
    }

    val headerDragModifier = Modifier.npvDismissDragGesture(
        coroutineScope = coroutineScope,
        playerOffsetY = playerOffsetY,
        dismissThresholdPx = dismissThresholdPx,
        onDismiss = dismissPlayer,
        onSnapBack = snapBackPlayer
    )

    val artworkGestureModifier = Modifier.npvArtworkGesture(
        coroutineScope = coroutineScope,
        playerOffsetY = playerOffsetY,
        scrollState = scrollState,
        scrollEnabled = !effectiveIsLoading,
        dismissThresholdPx = dismissThresholdPx,
        onDismiss = dismissPlayer,
        onSnapBack = snapBackPlayer
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = playerOffsetY.value.coerceAtLeast(0f)
            }
            .pointerInput(canvasImmersive) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var consumed = false
                    var dragDistance = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) consumed = true
                        dragDistance += change.positionChange().getDistance()
                        if (canvasImmersive && !canvasControlsRevealed && dragDistance > viewConfiguration.touchSlop) {
                            canvasControlsRevealed = true
                        }
                        if (!change.pressed) break
                    }
                    if (canvasImmersive && !consumed && dragDistance <= viewConfiguration.touchSlop) {
                        canvasControlsRevealed = !canvasControlsRevealed
                    }
                }
            }
    ) {
        val viewportHeight = maxHeight
        val dynamicArtworkColor by rememberDominantColor(
            imageUrl = coverUrl,
            fallbackSeed = title + artist,
            defaultColor = dominantColor
        )

        val animatedDominantColor by animateColorAsState(
            targetValue = if (videoActive) Color(0xFF121212) else dynamicArtworkColor,
            animationSpec = tween(durationMillis = 600, easing = EncoreAnimation.DecelerateInterpolator),
            label = "npvDominantColorTransition"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to animatedDominantColor,
                        0.45f to lerp(animatedDominantColor, Color(0xFF121212), 0.35f),
                        0.80f to lerp(animatedDominantColor, Color(0xFF121212), 0.75f),
                        1.0f to Color(0xFF121212)
                    )
                )
        )

        if (canvasActive && !videoActive && !isLandscape) {
            CanvasVideoPlayer(
                filePath = canvasFilePath,
                zoom = CANVAS_ZOOM,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
            )
        }

        @OptIn(ExperimentalFoundationApi::class)
        CompositionLocalProvider(
            LocalOverscrollConfiguration provides null
        ) {
            val resolvedContextTitle = contextHeaderTitle ?: when {
                albumOrPlaylistContext.isBlank() || albumOrPlaylistContext.equals("Recommended for you", ignoreCase = true) -> "Recommended for you"
                else -> "Playing from playlist"
            }
            val hasSubtitle = albumOrPlaylistContext.isNotBlank() && !albumOrPlaylistContext.equals("Recommended for you", ignoreCase = true)

            val isScrollEnabled = !effectiveIsLoading

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(LANDSCAPE_ARTWORK_PANE_WEIGHT)
                            .fillMaxHeight()
                            .then(artworkGestureModifier)
                            .padding(start = 24.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NowPlayingArtworkCard(
                            title = title,
                            coverUrl = coverUrl,
                            dominantColor = dynamicArtworkColor,
                            backgroundColor = animatedDominantColor,
                            canvasFilePath = canvasFilePath,
                            isCanvasEnabled = canvasActive,
                            videoActive = videoActive,
                            videoQualities = videoQualities,
                            onVideoQuality = onVideoQuality,
                            onVideoSurface = onVideoSurface,
                            onToggleVideoFullscreen = { onToggleVideoFullscreen(true) },
                            modifier = Modifier
                                .aspectRatio(if (videoActive) 16f / 9f else 1.0f)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(LANDSCAPE_CONTROLS_PANE_WEIGHT)
                            .fillMaxHeight()
                            .verticalScroll(state = scrollState)
                    ) {
                        NowPlayingHeaderRow(
                            resolvedContextTitle = resolvedContextTitle,
                            contextSubtitle = if (hasSubtitle) albumOrPlaylistContext else null,
                            onCollapseClick = onCollapseClick,
                            onMoreClick = onMoreClick,
                            dominantColor = animatedDominantColor,
                            dragModifier = headerDragModifier
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        NowPlayingSyncedLyricLine(
                            lyricText = activeLyricLine,
                            visible = effectiveLyricsVisible,
                            height = if (effectiveLyricsVisible) 44.dp else 0.dp,
                            onOpenLyrics = { isLyricsFullscreen = true }
                        )

                        MusicVideoSwitchRow(
                            available = musicVideoAvailable,
                            active = videoActive,
                            switching = videoSwitching,
                            onSwitch = onSwitchVideo
                        )

                        if (effectiveLyricsVisible) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        NowPlayingTrackInfoRow(
                            title = title,
                            artist = artist,
                            isExplicit = isExplicit,
                            isLiked = isLiked,
                            showSmartShuffleBadge = isEnhancedTrack,
                            onTitleClick = onTitleClick,
                            onArtistClick = onArtistClick,
                            onLikeClick = onLikeClick,
                            onBanClick = onBanClick
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        NowPlayingSeekBarSection(
                            progress = progress,
                            durationMs = durationMs,
                            currentPositionMs = currentPositionMs,
                            onSeek = onSeek,
                            isLoading = effectiveIsLoading,
                            canSeek = canSeek,
                            markers = chapterFractions(chapters, durationMs)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        if (isEpisode) {
                            EpisodeControlsRow(
                                playbackSpeed = playbackSpeed,
                                isPlaying = isPlaying,
                                isLoading = effectiveIsLoading,
                                onPlaybackSpeed = onPlaybackSpeed,
                                onSkipSeek = onSkipSeek,
                                onSkipPreviousClick = onSkipPreviousClick,
                                onPlayPauseClick = onPlayPauseClick,
                                onSkipNextClick = onSkipNextClick
                            )
                        } else {
                        NowPlayingControlsRow(
                            shuffleMode = shuffleMode,
                            repeatMode = repeatMode,
                            isPlaying = isPlaying,
                            isLoading = effectiveIsLoading,
                            canShuffle = canShuffle,
                            canSmartShuffle = canSmartShuffle,
                            canRepeat = canRepeat,
                            canSkipPrevious = canSkipPrevious,
                            onShuffleClick = onShuffleClick,
                            onSkipPreviousClick = onSkipPreviousClick,
                            onPlayPauseClick = onPlayPauseClick,
                            onSkipNextClick = onSkipNextClick,
                            onRepeatClick = onRepeatClick
                        )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        NowPlayingFooterRow(
                            connectedDeviceName = connectedDeviceName,
                            onDeviceClick = onDeviceClick,
                            onQueueClick = onQueueClick,
                            onShareClick = onShareClick
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        NowPlayingLyricsCard(
                            visible = effectiveLyricsVisible,
                            lyrics = lyrics,
                            activeIndex = activeLyricIndex,
                            dominantColor = dynamicArtworkColor,
                            peekHeight = LANDSCAPE_LYRICS_PEEK_HEIGHT,
                            onOpenLyrics = { isLyricsFullscreen = true },
                            onShareLyrics = { isLyricsShareOpen = true }
                        )

                        if (!effectiveIsLoading && artist.isNotBlank()) {
                            if (effectiveLyricsVisible) {
                                Spacer(modifier = Modifier.height(EncoreSpacers.Base))
                            }

                            NowPlayingAboutArtistCard(
                                artist = artist,
                                monthlyListeners = effectiveMonthlyListeners,
                                artistBio = effectiveArtistBio,
                                artistImageUrl = artistImageUrl,
                                isFollowingArtist = isArtistFollowing,
                                onFollowToggle = { onArtistFollowToggle(!isArtistFollowing) },
                                onArtistClick = onArtistClick
                            )

                            Spacer(modifier = Modifier.height(EncoreSpacers.Looser3))
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(state = scrollState, enabled = isScrollEnabled),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(artworkGestureModifier)
                            .heightIn(min = viewportHeight - PEEK_HEIGHT),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                            ) {
                                NowPlayingHeaderRow(
                                    resolvedContextTitle = resolvedContextTitle,
                                    contextSubtitle = if (hasSubtitle) albumOrPlaylistContext else null,
                                    onCollapseClick = onCollapseClick,
                                    onMoreClick = onMoreClick,
                                    dominantColor = animatedDominantColor,
                                    dragModifier = headerDragModifier
                                )
                            }

                            Spacer(modifier = Modifier.weight(topSpacerWeight.coerceAtLeast(0.01f)))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = animatedMediaPadding)
                                    .revealAt(reveal, REVEAL_ARTWORK_DELAY_MS / REVEAL_MS.toFloat()),
                                contentAlignment = Alignment.Center
                            ) {
                                if (canvasActive && !videoActive) {
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1.0f)
                                    )
                                } else {
                                    NowPlayingArtworkCard(
                                        title = title,
                                        coverUrl = coverUrl,
                                        dominantColor = dynamicArtworkColor,
                                        backgroundColor = animatedDominantColor,
                                        canvasFilePath = canvasFilePath,
                                        isCanvasEnabled = canvasActive,
                                        videoActive = videoActive,
                                        videoFullBleed = videoActive,
                                        videoQualities = videoQualities,
                                        onVideoQuality = onVideoQuality,
                                        onVideoSurface = onVideoSurface,
                                        onToggleVideoFullscreen = { onToggleVideoFullscreen(true) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(animatedMediaHeight)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(bottomSpacerWeight.coerceAtLeast(0.01f)))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (!videoActive) {
                                NowPlayingSyncedLyricLine(
                                    lyricText = activeLyricLine,
                                    visible = effectiveLyricsVisible,
                                    height = lyricLineHeight,
                                    onOpenLyrics = { isLyricsFullscreen = true }
                                )
                            }

                            AnimatedVisibility(
                                visible = canvasControlsShown,
                                enter = fadeIn(tween(200)) + expandVertically(tween(300, easing = EncoreAnimation.DecelerateInterpolator)),
                                exit = fadeOut(tween(200)) + shrinkVertically(tween(300))
                            ) {
                                MusicVideoSwitchRow(
                                    available = musicVideoAvailable,
                                    active = videoActive,
                                    switching = videoSwitching,
                                    onSwitch = onSwitchVideo
                                )
                            }

                            if (!videoActive) {
                                Spacer(modifier = Modifier.height(lyricBottomSpacerHeight))
                            }

                            NowPlayingTrackInfoRow(
                                title = title,
                                artist = artist,
                                isExplicit = isExplicit,
                                isLiked = isLiked,
                                showSmartShuffleBadge = isEnhancedTrack,
                                onTitleClick = onTitleClick,
                                onArtistClick = onArtistClick,
                                onLikeClick = onLikeClick,
                                onBanClick = onBanClick,
                                coverUrl = if (canvasImmersive) coverUrl else null
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            NowPlayingSeekBarSection(
                                progress = progress,
                                durationMs = durationMs,
                                currentPositionMs = currentPositionMs,
                                onSeek = onSeek,
                                isLoading = effectiveIsLoading,
                                canSeek = canSeek,
                                markers = chapterFractions(chapters, durationMs)
                            )

                            AnimatedVisibility(
                                visible = canvasControlsShown,
                                enter = fadeIn(tween(200)) + expandVertically(tween(300, easing = EncoreAnimation.DecelerateInterpolator)),
                                exit = fadeOut(tween(200)) + shrinkVertically(tween(300))
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(20.dp))

                                    if (isEpisode) {
                                        EpisodeControlsRow(
                                            playbackSpeed = playbackSpeed,
                                            isPlaying = isPlaying,
                                            isLoading = effectiveIsLoading,
                                            onPlaybackSpeed = onPlaybackSpeed,
                                            onSkipSeek = onSkipSeek,
                                            onSkipPreviousClick = onSkipPreviousClick,
                                            onPlayPauseClick = onPlayPauseClick,
                                            onSkipNextClick = onSkipNextClick
                                        )
                                    } else {
                                        NowPlayingControlsRow(
                                            shuffleMode = shuffleMode,
                                            repeatMode = repeatMode,
                                            isPlaying = isPlaying,
                                            isLoading = effectiveIsLoading,
                                            canShuffle = canShuffle,
                                            canSmartShuffle = canSmartShuffle,
                                            canRepeat = canRepeat,
                                            canSkipPrevious = canSkipPrevious,
                                            onShuffleClick = onShuffleClick,
                                            onSkipPreviousClick = onSkipPreviousClick,
                                            onPlayPauseClick = onPlayPauseClick,
                                            onSkipNextClick = onSkipNextClick,
                                            onRepeatClick = onRepeatClick
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    NowPlayingFooterRow(
                                        connectedDeviceName = connectedDeviceName,
                                        onDeviceClick = onDeviceClick,
                                        onQueueClick = onQueueClick,
                                        onShareClick = onShareClick
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .revealAt(reveal, REVEAL_REST_DELAY_MS / REVEAL_MS.toFloat())
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            NowPlayingLyricsCard(
                                visible = effectiveLyricsVisible && canvasControlsShown,
                                lyrics = lyrics,
                                activeIndex = activeLyricIndex,
                                dominantColor = dynamicArtworkColor,
                                onOpenLyrics = { isLyricsFullscreen = true },
                                onShareLyrics = { isLyricsShareOpen = true }
                            )

                            if (!effectiveIsLoading && artist.isNotBlank()) {
                                AnimatedVisibility(
                                    visible = canvasControlsShown,
                                    enter = fadeIn(tween(200)) + expandVertically(tween(300, easing = EncoreAnimation.DecelerateInterpolator)),
                                    exit = fadeOut(tween(200)) + shrinkVertically(tween(300))
                                ) {
                                    Column {
                                        if (effectiveLyricsVisible) {
                                            Spacer(modifier = Modifier.height(EncoreSpacers.Base))
                                        }

                                        NowPlayingAboutArtistCard(
                                            artist = artist,
                                            monthlyListeners = effectiveMonthlyListeners,
                                            artistBio = effectiveArtistBio,
                                            artistImageUrl = artistImageUrl,
                                            isFollowingArtist = isArtistFollowing,
                                            onFollowToggle = { onArtistFollowToggle(!isArtistFollowing) },
                                            onArtistClick = onArtistClick
                                        )

                                        Spacer(modifier = Modifier.height(48.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isLyricsFullscreen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 280)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 220)
            )
        ) {
            FullscreenLyricsView(
                title = title,
                artist = artist,
                currentPositionMs = currentPositionMs,
                dominantColor = dominantColor,
                lyrics = lyrics,
                onSeekMs = { targetMs -> if (durationMs > 0L) onSeek(targetMs.toFloat() / durationMs.toFloat()) },
                onShareClick = { isLyricsShareOpen = true },
                onClose = { isLyricsFullscreen = false }
            )
        }

        AnimatedVisibility(
            visible = isLyricsShareOpen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 280)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 220)
            )
        ) {
            LyricsShareDialog(
                title = title,
                artist = artist,
                lyrics = lyrics,
                initialDominantColor = dominantColor,
                onDismiss = { isLyricsShareOpen = false },
                onShareConfirm = { _, _, _ -> isLyricsShareOpen = false }
            )
        }
    }
}

/**
 * Drag-to-dismiss gesture for the Now Playing header: dragging down
 * translates the whole player, releasing past the threshold (or with
 * enough downward velocity) collapses it back to the mini player.
 */
private fun Modifier.npvDismissDragGesture(
    coroutineScope: CoroutineScope,
    playerOffsetY: Animatable<Float, AnimationVector1D>,
    dismissThresholdPx: Float,
    onDismiss: () -> Unit,
    onSnapBack: () -> Unit
): Modifier = pointerInput(dismissThresholdPx) {
    val velocityTracker = VelocityTracker()
    detectDragGestures(
        onDragStart = { velocityTracker.resetTracking() },
        onDragEnd = {
            val velocity = velocityTracker.calculateVelocity()
            coroutineScope.launch {
                if (velocity.y > DISMISS_FLING_VELOCITY || playerOffsetY.value > dismissThresholdPx) {
                    onDismiss()
                } else {
                    onSnapBack()
                }
            }
        },
        onDragCancel = { onSnapBack() },
        onDrag = { change, dragAmount ->
            velocityTracker.addPosition(change.uptimeMillis, change.position)
            if (dragAmount.y > 0 || playerOffsetY.value > 0) {
                change.consume()
                coroutineScope.launch {
                    playerOffsetY.snapTo((playerOffsetY.value + dragAmount.y).coerceAtLeast(0f))
                }
            }
        }
    )
}

/**
 * Artwork gestures: horizontal drags perform the swipe-to-skip track
 * animation while vertical drags translate the whole player for
 * drag-to-dismiss.
 *
 * The axis is resolved manually, before any event is consumed. Vertical drags
 * are only claimed once the page can no longer scroll in that direction, so
 * dragging over the artwork scrolls the page like any other part of it and
 * only turns into a dismissal once the top is reached.
 */
private fun Modifier.npvArtworkGesture(
    coroutineScope: CoroutineScope,
    playerOffsetY: Animatable<Float, AnimationVector1D>,
    scrollState: ScrollState,
    scrollEnabled: Boolean,
    dismissThresholdPx: Float,
    onDismiss: () -> Unit,
    onSnapBack: () -> Unit
): Modifier = pointerInput(dismissThresholdPx, scrollEnabled) {
    var axis = AXIS_UNDECIDED
    var totalDragX = 0f
    var totalDragY = 0f
    val velocityTracker = VelocityTracker()

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        axis = AXIS_UNDECIDED
        totalDragX = 0f
        totalDragY = 0f
        velocityTracker.resetTracking()
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break

            val delta = change.positionChange()
            totalDragX += delta.x
            totalDragY += delta.y

            if (axis == AXIS_UNDECIDED) {
                if (totalDragY > DRAG_AXIS_SLOP_PX) {
                    val pageAtTop = !scrollEnabled || scrollState.value <= 0
                    axis = if (pageAtTop) AXIS_VERTICAL else AXIS_YIELD
                } else if (totalDragY < -DRAG_AXIS_SLOP_PX || totalDragX.absoluteValue > DRAG_AXIS_SLOP_PX) {
                    axis = AXIS_YIELD
                }
            }

            when (axis) {
                AXIS_VERTICAL -> {
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    coroutineScope.launch {
                        playerOffsetY.snapTo((playerOffsetY.value + delta.y).coerceAtLeast(0f))
                    }
                }
                AXIS_YIELD -> Unit
            }
        }

        val velocity = velocityTracker.calculateVelocity()
        coroutineScope.launch {
            when (axis) {
                AXIS_VERTICAL -> {
                    when {
                        velocity.y < SNAP_BACK_FLING_VELOCITY -> onSnapBack()
                        velocity.y > DISMISS_FLING_VELOCITY -> onDismiss()
                        playerOffsetY.value > dismissThresholdPx -> onDismiss()
                        else -> onSnapBack()
                    }
                }
            }
            axis = AXIS_UNDECIDED
        }
    }
}

/**
 * Top Navigation Bar (Collapse Chevron, Context Header, More Options).
 * Reference: context_header.xml & revised_template_overlay.xml (@id/player_overlay_header)
 */
@Composable
private fun NowPlayingHeaderRow(
    resolvedContextTitle: String,
    contextSubtitle: String?,
    onCollapseClick: () -> Unit,
    onMoreClick: () -> Unit,
    dominantColor: Color,
    dragModifier: Modifier = Modifier
) {
    val ink = contrastInk(dominantColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .then(dragModifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        EncoreIconButton(
            iconRes = R.drawable.encore_icon_chevron_down_24,
            contentDescription = "Collapse player",
            onClick = onCollapseClick,
            tint = ink,
            size = 48.dp,
            iconSize = 24.dp
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = resolvedContextTitle,
                style = EncoreTheme.typography.marginal.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = ink
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (contextSubtitle != null) {
                Text(
                    text = contextSubtitle,
                    style = EncoreTheme.typography.bodySmallBold.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ink
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        EncoreIconButton(
            iconRes = R.drawable.encore_icon_more_android_24,
            contentDescription = "More options",
            onClick = onMoreClick,
            tint = ink,
            size = 48.dp,
            iconSize = 24.dp
        )
    }
}

/**
 * Large 1:1 Album Artwork with 8dp rounded corners, the real looping canvas
 * video (when the track has one) and the CANVAS toggle pill.
 * Reference: revised_template_page_layout.xml (@id/track_carousel) -> bounds [63,308][1017,1262] (24dp margins)
 */
@Composable
private fun NowPlayingArtworkCard(
    title: String,
    coverUrl: String?,
    dominantColor: Color,
    backgroundColor: Color,
    canvasFilePath: String?,
    isCanvasEnabled: Boolean,
    gestureModifier: Modifier = Modifier,
    videoActive: Boolean = false,
    videoFullBleed: Boolean = false,
    videoOverlayVisible: Boolean? = null,
    onVideoOverlayTap: (() -> Unit)? = null,
    videoQualities: List<Int> = emptyList(),
    onVideoQuality: (Int) -> Unit = {},
    onVideoSurface: (android.view.Surface?) -> Unit = {},
    onToggleVideoFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val corner = when {
        videoActive || videoFullBleed -> 0.dp
        else -> EncoreCornerRadii.Larger2
    }
    val canvasActive = isCanvasEnabled && canvasFilePath != null
    val surfaceModifier = if (canvasActive || videoActive) {
        Modifier
    } else {
        Modifier.background(backgroundColor)
    }
    Box(
        modifier = modifier
            .shadow(elevation = if (videoActive || videoFullBleed) 0.dp else 16.dp, shape = RoundedCornerShape(corner))
            .then(gestureModifier)
            .clip(RoundedCornerShape(corner))
            .then(surfaceModifier),
        contentAlignment = Alignment.Center
    ) {
        when {
            videoActive -> {
                var internalOverlay by remember { mutableStateOf(false) }
                val overlayVisible = videoOverlayVisible ?: internalOverlay
                if (videoOverlayVisible == null) {
                    LaunchedEffect(internalOverlay) {
                        if (internalOverlay) {
                            delay(3000)
                            internalOverlay = false
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .then(
                            if (videoFullBleed) Modifier.fillMaxSize()
                            else Modifier.fillMaxWidth(0.8f).aspectRatio(16f / 9f)
                        )
                        .clip(if (videoFullBleed) RectangleShape else RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                if (videoOverlayVisible != null) onVideoOverlayTap?.invoke()
                                else internalOverlay = !internalOverlay
                            }
                        }
                ) {
                    MusicVideoSurface(
                        onSurface = onVideoSurface,
                        modifier = Modifier.fillMaxSize()
                    )
                    AnimatedVisibility(
                        visible = overlayVisible,
                        enter = fadeIn(tween(durationMillis = 250)),
                        exit = fadeOut(tween(durationMillis = 250))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.25f))
                        ) {
                            var menuOpen by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .clickable { onToggleVideoFullscreen() }
                                        .padding(7.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.encore_icon_fullscreen_24),
                                        contentDescription = "Toggle fullscreen video",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Box {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(Color.Black.copy(alpha = 0.45f))
                                            .clickable { menuOpen = true }
                                            .padding(7.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.encore_icon_settings_24),
                                            contentDescription = "Video quality",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    EncoreMenu(
                                        expanded = menuOpen,
                                        onDismiss = { menuOpen = false },
                                    ) {
                                        EncoreMenuItem(
                                            label = "Auto",
                                            onClick = {
                                                onVideoQuality(0)
                                                menuOpen = false
                                            }
                                        )
                                        videoQualities.forEach { height ->
                                            EncoreMenuItem(
                                                label = "${height}p",
                                                onClick = {
                                                    onVideoQuality(height)
                                                    menuOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            canvasActive -> {
                CanvasVideoPlayer(
                    filePath = canvasFilePath,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                EncoreArtworkImage(
                    imageUrl = coverUrl,
                    contentDescription = title,
                    fallbackColor = dominantColor,
                    requestSize = 640,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun VideoFullscreenLayout(
    videoQualities: List<Int>,
    onVideoQuality: (Int) -> Unit,
    onVideoSurface: (android.view.Surface?) -> Unit,
    progress: Float,
    durationMs: Long,
    currentPositionMs: Long,
    onSeek: (Float) -> Unit,
    isLoading: Boolean,
    canSeek: Boolean,
    chapters: List<ChapterMarker>,
    onExit: () -> Unit,
) {
    var overlayVisible by remember { mutableStateOf(true) }
    LaunchedEffect(overlayVisible) {
        if (overlayVisible) {
            delay(3000)
            overlayVisible = false
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        NowPlayingArtworkCard(
            title = "",
            coverUrl = null,
            dominantColor = Color.Black,
            backgroundColor = Color.Black,
            canvasFilePath = null,
            isCanvasEnabled = false,
            videoActive = true,
            videoFullBleed = true,
            videoOverlayVisible = overlayVisible,
            videoQualities = videoQualities,
            onVideoQuality = onVideoQuality,
            onVideoSurface = onVideoSurface,
            onToggleVideoFullscreen = onExit,
            onVideoOverlayTap = { overlayVisible = !overlayVisible },
            modifier = Modifier.fillMaxSize()
        )
        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(tween(durationMillis = 250)),
            exit = fadeOut(tween(durationMillis = 250)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            NowPlayingSeekBarSection(
                progress = progress,
                durationMs = durationMs,
                currentPositionMs = currentPositionMs,
                onSeek = onSeek,
                isLoading = isLoading,
                canSeek = canSeek,
                markers = chapterFractions(chapters, durationMs),
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }
    }
}

@Composable
private fun MusicVideoSwitchRow(
    available: Boolean,
    active: Boolean,
    switching: Boolean,
    onSwitch: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!available && !active) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        MusicVideoSwitchPill(
            label = if (active) "Audio" else "Music video",
            iconRes = if (active) R.drawable.encore_icon_audio_24 else R.drawable.encore_icon_video_24,
            switching = switching,
            onClick = { onSwitch(!active) }
        )
    }
}

@Composable
private fun MusicVideoSwitchPill(
    label: String,
    iconRes: Int,
    switching: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (switching) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = EncoreTheme.typography.bodySmall,
            color = Color.White
        )
    }
}

/**
 * Real-time synchronized lyrics element, up to 2 lines with ellipsis.
 * Reference: revised_template_overlay.xml (@id/lyrics_element)
 * Constant 44dp height container ensures track title and controls below NEVER shift position!
 */
@Composable
private fun NowPlayingSyncedLyricLine(
    lyricText: String,
    visible: Boolean,
    height: Dp,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (height <= 0.dp && !visible) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300)) + slideInVertically(tween(350)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 }
        ) {
            AnimatedContent(
                targetState = lyricText,
                transitionSpec = {
                    (slideInVertically { height -> height / 2 } + fadeIn(tween(200)))
                        .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut(tween(150)))
                },
                label = "1LineLyricsContent"
            ) { lyricLine ->
                Text(
                    text = lyricLine,
                    style = EncoreTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenLyrics() }
                )
            }
        }
    }
}

/**
 * Track Title, Artist & Feedback Buttons Row.
 * Tapping the title opens the album, tapping the artist opens the artist page.
 * Reference: track_info_nowplaying_view.xml & revised_template_overlay.xml (@id/track_info_feedback_container)
 * Live dump: track_info_view_title [63, 1598], feedback_buttons_container [765, 1596][1038, 1722]
 */
@Composable
private fun NowPlayingTrackInfoRow(
    title: String,
    artist: String,
    isExplicit: Boolean,
    isLiked: Boolean,
    showSmartShuffleBadge: Boolean,
    onTitleClick: () -> Unit,
    onArtistClick: () -> Unit,
    onLikeClick: () -> Unit,
    onBanClick: (() -> Unit)? = null,
    coverUrl: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (coverUrl != null) {
            EncoreArtworkImage(
                imageUrl = coverUrl,
                contentDescription = null,
                cornerRadius = 8.dp,
                requestSize = 128,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = EncoreTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onTitleClick)
            )
            Spacer(modifier = Modifier.height(EncoreSpacers.Tighter4))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isExplicit) {
                    EncoreExplicitTag(
                        modifier = Modifier.padding(end = 8.dp),
                        size = 16.dp,
                        cornerRadius = 3.dp,
                        fontSize = 10.sp
                    )
                }
                if (showSmartShuffleBadge) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_enhance_badge_16),
                        contentDescription = "Smart Shuffle on",
                        tint = EncoreColors.Green,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(14.dp)
                    )
                }
                Text(
                    text = artist,
                    style = EncoreTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.70f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onArtistClick)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBanClick != null) {
                EncoreIconButton(
                    iconRes = R.drawable.encore_icon_ban_24,
                    contentDescription = "Don't play this",
                    onClick = onBanClick,
                    tint = Color.White.copy(alpha = 0.70f),
                    size = 48.dp,
                    iconSize = 24.dp
                )
            }
            EncoreHeartButton(
                isLiked = isLiked,
                onLikeToggle = onLikeClick,
                size = 48.dp,
                iconSize = 28.dp
            )
        }
    }
}

/**
 * Horizontal padding 24dp aligns perfectly with timestamps, track title, and album artwork.
 * Live dump bounds: position [63, 1809], duration [948, 1809] (24dp screen margins)
 */
@Composable
private fun NowPlayingSeekBarSection(
    progress: Float,
    durationMs: Long,
    currentPositionMs: Long,
    onSeek: (Float) -> Unit,
    isLoading: Boolean,
    canSeek: Boolean = true,
    markers: List<Float> = emptyList(),
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        EncoreSeekBar(
            progress = progress,
            durationMs = durationMs,
            onSeek = onSeek,
            isLoading = isLoading,
            enabled = canSeek,
            markers = markers,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentPositionMs),
                style = EncoreTheme.typography.marginal.copy(
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.70f),
                    fontFeatureSettings = "tnum"
                )
            )
            Text(
                text = formatTime(durationMs),
                style = EncoreTheme.typography.marginal.copy(
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.70f),
                    fontFeatureSettings = "tnum"
                )
            )
        }
    }
}

/**
 * Podcast controls: speed pill, skip 15s, previous, play/pause, next.
 * Reference: web player control-button-seek-back-15 / control-button-playback-speed.
 */
private val EPISODE_SPEED_OPTIONS = listOf(0.5f, 0.8f, 1f, 1.2f, 1.5f, 2f, 2.5f, 3f, 3.5f)

private fun chapterFractions(chapters: List<ChapterMarker>, durationMs: Long): List<Float> {
    if (durationMs <= 0L) return emptyList()
    return chapters.map { it.startMs / durationMs.toFloat() }
}

@Composable
private fun EpisodeControlsRow(
    playbackSpeed: Float,
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlaybackSpeed: (Float) -> Unit,
    onSkipSeek: (Long) -> Unit,
    onSkipPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpeedPill(
            speed = playbackSpeed,
            onSelect = onPlaybackSpeed
        )

        EncoreIconButton(
            iconRes = R.drawable.encore_icon_seek_back_15,
            contentDescription = "Back 15 seconds",
            onClick = { onSkipSeek(-15_000L) },
            tint = EncoreColors.TextPrimary,
            size = 48.dp,
            iconSize = 26.dp
        )

        EncoreIconButton(
            iconRes = R.drawable.encore_icon_skip_back_24,
            contentDescription = "Previous",
            onClick = onSkipPreviousClick,
            tint = EncoreColors.TextPrimary,
            size = 44.dp,
            iconSize = 26.dp
        )

        EncorePlayButton(
            isPlaying = isPlaying,
            onClick = onPlayPauseClick,
            size = 64.dp,
            iconSize = 32.dp,
            backgroundColor = Color.White,
            iconColor = EncoreColors.PureBlack,
            isLoading = isLoading
        )

        EncoreIconButton(
            iconRes = R.drawable.encore_icon_skip_forward_24,
            contentDescription = "Next",
            onClick = onSkipNextClick,
            tint = EncoreColors.TextPrimary,
            size = 48.dp,
            iconSize = 30.dp
        )

        EncoreIconButton(
            iconRes = R.drawable.encore_icon_seek_forward_15,
            contentDescription = "Forward 15 seconds",
            onClick = { onSkipSeek(15_000L) },
            tint = EncoreColors.TextPrimary,
            size = 48.dp,
            iconSize = 26.dp
        )
    }
}

@Composable
private fun SpeedPill(
    speed: Float,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (speed == 1f) "1" else String.format(java.util.Locale.US, "%.1f", speed),
                style = EncoreTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = Color.White
            )
            Text(
                text = "×",
                style = EncoreTheme.typography.marginal.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                modifier = Modifier.padding(start = 1.dp)
            )
        }
        EncoreMenu(
            expanded = expanded,
            onDismiss = { expanded = false }
        ) {
            EPISODE_SPEED_OPTIONS.forEach { option ->
                EncoreMenuItem(
                    label = if (option == 1f) "1x" else String.format(java.util.Locale.US, "%.1fx", option),
                    selected = kotlin.math.abs(option - speed) < 0.01f,
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

/**
 * Playback Controls (Shuffle, Previous, Play/Pause, Next, Repeat).
 * Reference: revised_template_overlay.xml (@id/playback_controls_container) -> paddingHorizontal 16dp
 */
@Composable
private fun NowPlayingControlsRow(
    shuffleMode: ShuffleMode,
    repeatMode: RepeatMode,
    isPlaying: Boolean,
    isLoading: Boolean,
    canShuffle: Boolean,
    canSmartShuffle: Boolean,
    canRepeat: Boolean,
    canSkipPrevious: Boolean = true,
    onShuffleClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onRepeatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EncoreSmartShuffleButton(
            mode = shuffleMode,
            onClick = onShuffleClick,
            size = 48.dp,
            enabled = canShuffle
        )

        EncoreIconButton(
            iconRes = R.drawable.encore_icon_skip_back_24,
            contentDescription = "Previous",
            onClick = onSkipPreviousClick,
            tint = EncoreColors.TextPrimary,
            size = 48.dp,
            iconSize = 30.dp,
            enabled = canSkipPrevious
        )

        EncorePlayButton(
            isPlaying = isPlaying,
            onClick = onPlayPauseClick,
            size = 64.dp,
            iconSize = 32.dp,
            backgroundColor = Color.White,
            iconColor = EncoreColors.PureBlack,
            isLoading = isLoading
        )

        EncoreIconButton(
            iconRes = R.drawable.encore_icon_skip_forward_24,
            contentDescription = "Next",
            onClick = onSkipNextClick,
            tint = EncoreColors.TextPrimary,
            size = 48.dp,
            iconSize = 30.dp
        )

        EncoreRepeatButton(
            mode = repeatMode,
            onClick = onRepeatClick,
            size = 48.dp,
            enabled = canRepeat
        )
    }
}

/**
 * Bottom Device & Share / Queue Row.
 * Reference: revised_template_overlay_footer.xml -> marginStart/End 8dp (@dimen/footer_button_margin)
 */
@Composable
private fun NowPlayingFooterRow(
    connectedDeviceName: String,
    onDeviceClick: () -> Unit,
    onQueueClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onDeviceClick)
                .padding(vertical = EncoreSpacers.Tighter4, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_devices_24,
                contentDescription = "Connected devices",
                onClick = onDeviceClick,
                tint = EncoreColors.Green,
                size = 32.dp,
                iconSize = 20.dp
            )
            Text(
                text = connectedDeviceName,
                style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.Green),
                modifier = Modifier.padding(start = 2.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_share_android_24,
                contentDescription = "Share",
                onClick = onShareClick,
                tint = Color.White.copy(alpha = 0.70f),
                size = 40.dp,
                iconSize = 20.dp
            )
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_queue_24,
                contentDescription = "Queue",
                onClick = onQueueClick,
                tint = Color.White.copy(alpha = 0.70f),
                size = 40.dp,
                iconSize = 22.dp
            )
        }
    }
}

/**
 * Lyrics Peek Card (16dp rounded corner, dominant color background, horizontal margin 16dp).
 * Fixed peek height: the card keeps its layout size while the synced lyrics fill it
 * and scroll inside, auto-following the active line.
 * Reference: live dump [42, 2269][1038, 2400]
 */
@Composable
private fun NowPlayingLyricsCard(
    visible: Boolean,
    lyrics: List<TimeSyncedLyric>,
    activeIndex: Int,
    dominantColor: Color,
    onOpenLyrics: () -> Unit,
    onShareLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    peekHeight: Dp = 360.dp
) {
    val ink = contrastInk(dominantColor)
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(durationMillis = 400, easing = EncoreAnimation.DecelerateInterpolator)
        ) + fadeIn(tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(durationMillis = 300)
        ) + fadeOut(tween(200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(peekHeight) // Fixed peek size: lyrics fill the card instead of growing it
                .clip(RoundedCornerShape(EncoreCornerRadii.Larger3))
                .background(dominantColor.copy(alpha = 0.65f))
                .clickable { onOpenLyrics() }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PEEK_HEIGHT)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lyrics",
                        style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = ink)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EncoreIconButton(
                            iconRes = R.drawable.encore_icon_share_android_24,
                            contentDescription = "Share lyrics",
                            onClick = onShareLyrics,
                            tint = ink,
                            size = 32.dp,
                            iconSize = 18.dp
                        )
                        EncoreIconButton(
                            iconRes = R.drawable.encore_icon_fullscreen_24,
                            contentDescription = "Expand lyrics",
                            onClick = onOpenLyrics,
                            tint = ink,
                            size = 32.dp,
                            iconSize = 18.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val lyricsListState = rememberLazyListState()
                LaunchedEffect(activeIndex, lyrics.size) {
                    if (lyrics.isEmpty()) return@LaunchedEffect
                    lyricsListState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
                }
                LazyColumn(
                    state = lyricsListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .weight(1f)
                ) {
                    itemsIndexed(lyrics) { i, lyric ->
                        val isActive = i == activeIndex
                        Text(
                            text = lyric.text,
                            style = EncoreTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                lineHeight = 30.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                color = when {
                                    isActive -> ink.copy(alpha = 0.95f)
                                    i < activeIndex -> ink.copy(alpha = 0.45f)
                                    else -> ink.copy(alpha = 0.55f)
                                }
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * "About the Artist" Card (16dp rounded corner, horizontal margin 16dp)
 * with Verified Artist badge and Follow button.
 */
@Composable
private fun NowPlayingAboutArtistCard(
    artist: String,
    monthlyListeners: String,
    artistBio: String,
    artistImageUrl: String?,
    isFollowingArtist: Boolean,
    onFollowToggle: () -> Unit,
    onArtistClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(EncoreCornerRadii.Larger3))
            .background(EncoreColors.Card)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PEEK_HEIGHT)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "About the artist",
                    style = EncoreTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onArtistClick)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!artistImageUrl.isNullOrBlank()) {
                            EncoreArtworkImage(
                                imageUrl = artistImageUrl,
                                contentDescription = artist,
                                modifier = Modifier.fillMaxSize(),
                                fallbackColor = Color(0xFFE8115B)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFFE8115B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = artist.firstOrNull()?.uppercase() ?: "A",
                                    style = EncoreTheme.typography.titleMedium.copy(color = Color.White)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(EncoreSpacers.Tighter))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = artist,
                                style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(EncoreSpacers.Tighter4))
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_verified_check_active_24),
                                contentDescription = "Verified Artist",
                                tint = EncoreColors.Green,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = monthlyListeners,
                            style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(EncoreCornerRadii.FollowPill))
                        .border(
                            width = 1.dp,
                            color = if (isFollowingArtist) EncoreColors.Green else EncoreColors.TextSecondary,
                            shape = RoundedCornerShape(EncoreCornerRadii.FollowPill)
                        )
                        .clickable { onFollowToggle() }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isFollowingArtist) "Following" else "Follow",
                        style = EncoreTheme.typography.bodySmallBold.copy(
                            color = if (isFollowingArtist) EncoreColors.Green else EncoreColors.TextPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = artistBio,
                style = EncoreTheme.typography.bodySmall.copy(
                    color = EncoreColors.TextSecondary,
                    lineHeight = 18.sp
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
}

/**
 * - Dynamic active line calculation from current playback timestamp
 * - Tap any line to seek directly to that lyric position
 * - Smooth auto-scrolling to current line
 */
@Composable
private fun FullscreenLyricsView(
    title: String,
    artist: String,
    currentPositionMs: Long,
    dominantColor: Color,
    lyrics: List<TimeSyncedLyric>,
    onSeekMs: (Long) -> Unit,
    onShareClick: () -> Unit,
    onClose: () -> Unit
) {
    val activeIndex = lyrics.indexOfLast { currentPositionMs >= it.startTimeMs }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    val ink = contrastInk(dominantColor)

    LaunchedEffect(activeIndex) {
        listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(dominantColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = EncoreSpacers.Looser2)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = EncoreSpacers.Tighter2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_chevron_down_24,
                contentDescription = "Close lyrics",
                onClick = onClose,
                tint = ink
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = EncoreTheme.typography.bodySmallBold.copy(color = ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    style = EncoreTheme.typography.marginal.copy(color = ink.copy(alpha = 0.7f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            EncoreIconButton(
                iconRes = R.drawable.encore_icon_share_android_24,
                contentDescription = "Share",
                onClick = onShareClick,
                tint = ink
            )
        }

        Spacer(modifier = Modifier.height(EncoreSpacers.Looser2))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isCurrentLine = index == activeIndex
                Text(
                    text = line.text,
                    style = EncoreTheme.typography.titleLarge.copy(
                        fontSize = 24.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrentLine) ink else ink.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeekMs(line.startTimeMs) }
                        .padding(vertical = EncoreSpacers.Tighter)
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewEncoreNowPlayingScreen() {
    EncoreAppTheme {
        var isPlaying by remember { mutableStateOf(true) }
        var isLiked by remember { mutableStateOf(true) }
        var isShuffle by remember { mutableStateOf(false) }
        var isRepeat by remember { mutableStateOf(true) }

        EncoreNowPlayingScreen(
            title = "Starboy",
            artist = "The Weeknd, Daft Punk",
            albumOrPlaylistContext = "Today's Top Hits",
            isPlaying = isPlaying,
            progress = 0.35f,
            durationMs = 230000L,
            currentPositionMs = 80000L,
            isLiked = isLiked,
            isShuffle = isShuffle,
            isRepeat = isRepeat,
            connectedDeviceName = "Living Room Speaker",
            onCollapseClick = {},
            onPlayPauseClick = { isPlaying = !isPlaying },
            onSkipNextClick = {},
            onSkipPreviousClick = {},
            onLikeClick = { isLiked = !isLiked },
            onShuffleClick = { isShuffle = !isShuffle },
            onRepeatClick = { isRepeat = !isRepeat },
            onSeek = {},
            onMoreClick = {},
            onQueueClick = {},
            onDeviceClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 800, heightDp = 360)
@Composable
fun PreviewEncoreNowPlayingScreenLandscape() {
    EncoreAppTheme {
        var isPlaying by remember { mutableStateOf(true) }
        var isLiked by remember { mutableStateOf(true) }
        var isShuffle by remember { mutableStateOf(false) }
        var isRepeat by remember { mutableStateOf(true) }

        EncoreNowPlayingScreen(
            title = "Starboy",
            artist = "The Weeknd, Daft Punk",
            albumOrPlaylistContext = "Today's Top Hits",
            isPlaying = isPlaying,
            progress = 0.35f,
            durationMs = 230000L,
            currentPositionMs = 80000L,
            isLiked = isLiked,
            isShuffle = isShuffle,
            isRepeat = isRepeat,
            connectedDeviceName = "Living Room Speaker",
            onCollapseClick = {},
            onPlayPauseClick = { isPlaying = !isPlaying },
            onSkipNextClick = {},
            onSkipPreviousClick = {},
            onLikeClick = { isLiked = !isLiked },
            onShuffleClick = { isShuffle = !isShuffle },
            onRepeatClick = { isRepeat = !isRepeat },
            onSeek = {},
            onMoreClick = {},
            onQueueClick = {},
            onDeviceClick = {}
        )
    }
}
