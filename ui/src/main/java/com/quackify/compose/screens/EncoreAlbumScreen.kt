package com.quackify.compose.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.quackify.compose.R
import com.quackify.compose.components.EncoreExplicitTag
import com.quackify.compose.components.matchesCurrent
import com.quackify.compose.components.EncoreFastScroll
import com.quackify.compose.components.EncoreHeartButton
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.components.EncorePlayButton
import com.quackify.compose.components.EncorePlayIndicator
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.compose.components.SwipeToQueueBox
import com.quackify.compose.components.rememberSmoothColor
import com.quackify.compose.components.ShimmerBlock
import com.quackify.compose.components.ShimmerHost
import com.quackify.compose.theme.EncoreAnimation
import com.quackify.compose.format.formatCollectionDuration
import com.quackify.compose.format.formatExactCount
import com.quackify.backend.model.collectionDuration
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreAppTheme
import com.quackify.compose.theme.EncoreTheme
import com.quackify.compose.theme.rememberBottomPadding
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

data class AlbumDisc(
    val discNumber: Int,
    val tracks: List<TrackItem>
)

private const val PRELOAD_REMAINING_TRACKS = 25

/**
 * - Collapsible overlay top bar (transparent at rest, solid coverColor when scrolled)
 * - Album artwork starting directly under status bar at statusBarTop + 8.dp with smooth scaling
 * - Continuous header gradient extending from Y=0 and smoothly fading to pure #121212
 * - Official Encore download, heart, and 3-dot option buttons
 * - Track rows with disc headers, numbers, and copyrights section
 * - "More by [Artist]" discography shelf
 */
@Composable
fun EncoreAlbumScreen(
    albumTitle: String,
    artistName: String,
    releaseYear: String,
    releaseDate: String = "",
    coverColor: Color = Color(0xFF6B1A24),
    tracks: List<TrackItem>,
    discs: List<AlbumDisc>? = null,
    copyrightNotice: String = "",
    phonographicNotice: String = "",
    currentTrackTitle: String = "",
    currentTrackUri: String = "",
    isPlaying: Boolean = false,
    moreByArtist: List<ArtistRelease>? = null,
    onBackClick: () -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
    onTrackSelect: (TrackItem) -> Unit = {},
    onTrackOptionsClick: (TrackItem) -> Unit = {},
    onTrackLikeToggle: (TrackItem) -> Unit = {},
    onTrackAddToQueue: ((TrackItem) -> Unit)? = null,
    onAlbumOptionsClick: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (ArtistRelease) -> Unit = {},
    modifier: Modifier = Modifier,
    coverUrl: String? = null,
    artistImageUrl: String? = null,
    isLoading: Boolean = false,
    loadFailed: Boolean = false,
    hasMore: Boolean = false,
    loadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val density = LocalDensity.current
    var isLiked by remember { mutableStateOf(false) }
    var isDownloaded by remember { mutableStateOf(false) }
    var isShuffle by remember { mutableStateOf(false) }
    val bottomPadding = rememberBottomPadding()
    val listState = rememberLazyListState()

    val shouldLoadMore by remember(hasMore, loadingMore, tracks.size, onLoadMore) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            hasMore && !loadingMore && tracks.isNotEmpty() &&
                lastVisible - 1 >= tracks.size - PRELOAD_REMAINING_TRACKS
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val toolbarHeight = 54.dp
    val toolbarBottom = statusBarTop + toolbarHeight
    val toolbarBottomPx = with(density) { toolbarBottom.toPx() }
    val playButtonSize = 52.dp
    val playButtonHalf = 26.dp
    val pinnedTop = toolbarBottom - playButtonHalf
    val pinnedTopPx = with(density) { pinnedTop.toPx() }

    val scrollPx by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 2000f
        }
    }
    val playDockStartPx = with(density) { 300.dp.toPx() }
    val playFadePx = with(density) { 50.dp.toPx() }
    val playFraction by remember {
        derivedStateOf { ((scrollPx - playDockStartPx) / playFadePx).coerceIn(0f, 1f) }
    }

    val topBarThreeDotsAlpha by remember {
        derivedStateOf { 1f - playFraction }
    }
    val topBarTitleAlpha by remember {
        derivedStateOf {
            val titleScrollStartPx = with(density) { 160.dp.toPx() }
            val titleScrollFadeRangePx = with(density) { 60.dp.toPx() }
            ((scrollPx - titleScrollStartPx) / titleScrollFadeRangePx).coerceIn(0f, 1f)
        }
    }
    val topBarAlpha by remember {
        derivedStateOf {
            (scrollPx / with(density) { 90.dp.toPx() }).coerceIn(0f, 1f)
        }
    }
    val smoothCoverColor by rememberSmoothColor(target = coverColor)
    val circleBtnAlpha by remember {
        derivedStateOf { ((1f - topBarAlpha) * 0.35f).coerceIn(0f, 0.35f) }
    }
    val topOffsetPx = with(density) { (toolbarHeight - 8.dp).toPx().toInt() }

    val currentGradientHeightDp by remember {
        derivedStateOf {
            maxOf(toolbarBottom, toolbarBottom + with(density) { (320.dp.toPx() - scrollPx).toDp() })
        }
    }

    val shrinkFraction by remember {
        derivedStateOf {
            val scrollOffset = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else with(density) { 200.dp.toPx() }
            (scrollOffset / with(density) { 200.dp.toPx() }).coerceIn(0f, 1f)
        }
    }
    val artworkSize by remember {
        derivedStateOf { 240.dp - (50.dp * shrinkFraction) }
    }
    val artworkAlpha by remember {
        derivedStateOf { (1f - (shrinkFraction * 0.35f)).coerceIn(0f, 1f) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(currentGradientHeightDp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to smoothCoverColor,
                            0.35f to smoothCoverColor.copy(alpha = 0.90f),
                            0.65f to smoothCoverColor.copy(alpha = 0.55f),
                            0.85f to smoothCoverColor.copy(alpha = 0.20f),
                            1.0f to Color(0xFF121212)
                        )
                    )
                )
        )

        EncoreFastScroll(listState) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = statusBarTop + toolbarHeight,
                bottom = bottomPadding
            )
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height - topOffsetPx) {
                                placeable.placeRelative(0, -topOffsetPx)
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(artworkSize)
                                    .graphicsLayer {
                                        alpha = artworkAlpha
                                    }
                                    .shadow(elevation = (14 * (1f - shrinkFraction)).dp, shape = RoundedCornerShape(4.dp))
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(smoothCoverColor.copy(alpha = 0.9f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!coverUrl.isNullOrBlank()) {
                                    EncoreArtworkImage(
                                        imageUrl = coverUrl,
                                        contentDescription = albumTitle,
                                        modifier = Modifier.fillMaxSize(),
                                        cornerRadius = 4.dp
                                    )
                                } else {
                                    Text(
                                        text = "♫",
                                        fontSize = 72.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = albumTitle,
                            style = EncoreTheme.typography.headlineLarge.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .clickable { onArtistClick(artistName) }
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF333333)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!artistImageUrl.isNullOrBlank()) {
                                    EncoreArtworkImage(
                                        imageUrl = artistImageUrl,
                                        contentDescription = artistName,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        cornerRadius = 12.dp
                                    )
                                } else {
                                    Text(
                                        text = (artistName.trim().firstOrNull() ?: 'A').uppercaseChar().toString(),
                                        style = EncoreTheme.typography.bodySmallBold.copy(fontSize = 11.sp, color = Color.White)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = artistName,
                                style = EncoreTheme.typography.bodySmallBold.copy(
                                    color = EncoreColors.TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = stringResource(R.string.album_with_year, releaseYear),
                            style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable { isDownloaded = !isDownloaded },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            id = if (isDownloaded) R.drawable.encore_icon_downloaded_24 else R.drawable.encore_icon_download_24
                                        ),
                                        contentDescription = if (isDownloaded) stringResource(R.string.downloaded) else stringResource(R.string.download),
                                        tint = if (isDownloaded) EncoreColors.Green else EncoreColors.TextSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable { isLiked = !isLiked },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            id = if (isLiked) R.drawable.encore_icon_heart_active_24 else R.drawable.encore_icon_heart_24
                                        ),
                                        contentDescription = stringResource(R.string.save_album),
                                        tint = if (isLiked) EncoreColors.Green else EncoreColors.TextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable(onClick = onAlbumOptionsClick),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.encore_icon_more_android_24),
                                        contentDescription = stringResource(R.string.album_options),
                                        tint = EncoreColors.TextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable { isShuffle = !isShuffle },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.encore_icon_shuffle_24),
                                        contentDescription = stringResource(R.string.shuffle),
                                        tint = if (isShuffle) EncoreColors.Green else EncoreColors.TextSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(playButtonSize)
                                        .graphicsLayer { alpha = 1f - playFraction },
                                    contentAlignment = Alignment.Center
                                ) {
                                    EncorePlayButton(
                                        isPlaying = isPlaying,
                                        onClick = onPlayPauseClick,
                                        size = playButtonSize,
                                        iconSize = 24.dp,
                                        showShuffleBadge = false
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }

            if (discs != null && discs.size > 1) {
                discs.forEach { disc ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_album_24),
                                contentDescription = null,
                                tint = EncoreColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DISC ${disc.discNumber}",
                                style = EncoreTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = EncoreColors.TextSecondary
                                )
                            )
                        }
                    }

                    itemsIndexed(disc.tracks) { index, track ->
                        val isCurrentTrack = matchesCurrent(track.uri, currentTrackUri, track.title, currentTrackTitle)
                        AlbumTrackRow(
                            trackNumber = index + 1,
                            track = track,
                            isPlaying = isCurrentTrack && isPlaying,
                            onClick = { onTrackSelect(track) },
                            onMoreClick = { onTrackOptionsClick(track) },
                            onLikeToggle = { onTrackLikeToggle(track) },
                            onAddToQueue = onTrackAddToQueue?.let { handler -> { handler(track) } }
                        )
                    }
                }
            } else if (isLoading) {
                item { AlbumLoadingRows() }
            } else if (loadFailed) {
                item {
                    AlbumLoadError(
                        title = stringResource(R.string.couldn_t_load_this_album),
                        onRetry = onRetry
                    )
                }
            } else if (tracks.isEmpty()) {
                item {
                    AlbumLoadError(
                        title = stringResource(R.string.no_tracks_in_this_album),
                        onRetry = null
                    )
                }
            } else {
                itemsIndexed(tracks) { index, track ->
                    val isCurrentTrack = matchesCurrent(track.uri, currentTrackUri, track.title, currentTrackTitle)
                    AlbumTrackRow(
                        trackNumber = index + 1,
                        track = track,
                        isPlaying = isCurrentTrack && isPlaying,
                        onClick = { onTrackSelect(track) },
                        onMoreClick = { onTrackOptionsClick(track) },
                        onLikeToggle = { onTrackLikeToggle(track) },
                        onAddToQueue = onTrackAddToQueue?.let { handler -> { handler(track) } }
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    if (releaseDate.isNotBlank()) {
                        Text(
                            text = releaseDate,
                            style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.TextPrimary)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    if (tracks.isNotEmpty()) {
                        val totalDuration = remember(tracks) {
                            calculateTotalDuration(tracks.map { it.durationMs })
                        }
                        Text(
                            text = listOf(pluralStringResource(R.plurals.song_count, tracks.size, tracks.size), totalDuration)
                                .filter { it.isNotBlank() }
                                .joinToString(" • "),
                            style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                        )
                    }

                    if (copyrightNotice.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = copyrightNotice,
                            style = EncoreTheme.typography.marginal.copy(
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                color = EncoreColors.TextSecondary
                            )
                        )
                    }

                    if (phonographicNotice.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = phonographicNotice,
                            style = EncoreTheme.typography.marginal.copy(
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                color = EncoreColors.TextSecondary
                            )
                        )
                    }
                }
            }

            if (!moreByArtist.isNullOrEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.more_by_artist, artistName),
                        style = EncoreTheme.typography.titleLarge.copy(fontSize = 18.sp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(moreByArtist, key = { it.title + it.year }) { release ->
                            Column(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clickable { onAlbumClick(release) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(release.color),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (release.imageUrl != null) {
                                        EncoreArtworkImage(
                                            imageUrl = release.imageUrl,
                                            contentDescription = release.title,
                                            modifier = Modifier.fillMaxSize(),
                                            cornerRadius = 4.dp
                                        )
                                    } else {
                                        Text(text = "♫", color = Color.White, fontSize = 36.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = release.title,
                                    style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${release.type} • ${release.year}",
                                    style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                                )
                            }
                        }
                    }
                }
            }
        }

        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .height(toolbarBottom)
                .graphicsLayer {
                    alpha = topBarAlpha
                }
                .background(smoothCoverColor)
                .zIndex(9.8f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .height(toolbarHeight)
                .padding(horizontal = 16.dp)
                .zIndex(10f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = circleBtnAlpha))
                    .clickable(onClick = onBackClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_arrow_left_24),
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = albumTitle,
                    style = EncoreTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 17.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { alpha = topBarTitleAlpha }
                )
            }

            Box(
                modifier = Modifier.size(38.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = circleBtnAlpha * topBarThreeDotsAlpha))
                        .clickable(onClick = onAlbumOptionsClick)
                        .graphicsLayer { alpha = topBarThreeDotsAlpha },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_more_android_24),
                        contentDescription = stringResource(R.string.album_options),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (playFraction > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
                    .graphicsLayer {
                        translationY = pinnedTopPx
                        alpha = playFraction
                    }
                    .zIndex(20f)
            ) {
                EncorePlayButton(
                    isPlaying = isPlaying,
                    onClick = onPlayPauseClick,
                    size = playButtonSize,
                    iconSize = 24.dp,
                    showShuffleBadge = false
                )
            }
        }
    }
}

/**
 * - Numbered left index or 3-bar Encore equalizer
 * - Title and secondary artist subtitle
 * - Explicit [E] badge
 * - 3-dot context action
 */
@Composable
private fun AlbumTrackRow(
    trackNumber: Int,
    track: TrackItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onLikeToggle: () -> Unit = {},
    onAddToQueue: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isBanned = com.quackify.compose.bans.ArtistBans.isBanned(track.artistUri)
    SwipeToQueueBox(
        onAddToQueue = onAddToQueue?.takeIf { !isBanned },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(enabled = !isBanned, onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isPlaying) {
                    EncorePlayIndicator(
                        isPlaying = true,
                        color = EncoreColors.Green,
                        size = 14.dp
                    )
                } else {
                    Text(
                        text = trackNumber.toString(),
                        style = EncoreTheme.typography.bodyMedium.copy(
                            color = EncoreColors.TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = track.title,
                    style = EncoreTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isPlaying -> EncoreColors.Green
                            isBanned -> EncoreColors.TextSecondary.copy(alpha = 0.55f)
                            else -> EncoreColors.TextPrimary
                        }
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (track.isExplicit) {
                        EncoreExplicitTag(
                            modifier = Modifier.padding(end = 6.dp),
                            background = EncoreColors.TextSecondary.copy(alpha = 0.6f)
                        )
                    }

                    Text(
                        text = track.artist,
                        style = EncoreTheme.typography.bodySmall.copy(
                            color = EncoreColors.TextSecondary.takeIf { !isBanned }
                                ?: EncoreColors.TextSecondary.copy(alpha = 0.55f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (track.plays > 0L) {
                Text(
                    text = formatExactCount(track.plays),
                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                    maxLines = 1,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }

            if (!isBanned) {
                EncoreHeartButton(
                    isLiked = track.saved == true,
                    onLikeToggle = onLikeToggle,
                    size = 36.dp,
                    iconSize = 20.dp
                )
            }

            EncoreIconButton(
                iconRes = R.drawable.encore_icon_more_android_24,
                contentDescription = stringResource(R.string.track_options),
                onClick = onMoreClick,
                tint = EncoreColors.TextSecondary,
                size = 36.dp,
                iconSize = 20.dp
            )
        }
    }
}

private fun calculateTotalDuration(durations: List<Long>): String {
    val total = collectionDuration(durations, durations.size)
    if (total <= 0L) return ""
    return formatCollectionDuration(total)
}

@Composable
private fun AlbumLoadingRows() {
    ShimmerHost {
        Column(modifier = Modifier.fillMaxWidth()) {
            repeat(6) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerBlock(
                        modifier = Modifier.size(16.dp),
                        shape = RoundedCornerShape(2.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    ShimmerBlock(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(4.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .height(12.dp),
                            shape = RoundedCornerShape(2.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ShimmerBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.4f)
                                .height(10.dp),
                            shape = RoundedCornerShape(2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumLoadError(title: String, onRetry: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = EncoreTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = EncoreColors.TextPrimary
            ),
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.try_again),
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = EncoreColors.Green
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewEncoreAlbumScreen() {
    EncoreAppTheme {
        EncoreAlbumScreen(
            albumTitle = "After Hours",
            artistName = "The Weeknd",
            releaseYear = "2020",
            releaseDate = "March 20, 2020",
            tracks = listOf(
                TrackItem("Alone Again", "The Weeknd"),
                TrackItem("Too Late", "The Weeknd"),
                TrackItem("Hardest To Love", "The Weeknd"),
                TrackItem("Scared To Live", "The Weeknd"),
                TrackItem("Snowchild", "The Weeknd", isExplicit = true),
                TrackItem("Escape From LA", "The Weeknd", isExplicit = true),
                TrackItem("Heartless", "The Weeknd", isExplicit = true),
                TrackItem("Faith", "The Weeknd", isExplicit = true),
                TrackItem("Blinding Lights", "The Weeknd"),
                TrackItem("In Your Eyes", "The Weeknd"),
                TrackItem("Save Your Tears", "The Weeknd"),
                TrackItem("Repeat After Me (Interlude)", "The Weeknd"),
                TrackItem("After Hours", "The Weeknd"),
                TrackItem("Until I Bleed Out", "The Weeknd")
            ),
            currentTrackTitle = "Blinding Lights",
            isPlaying = true
        )
    }
}
