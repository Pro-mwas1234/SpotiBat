package com.mwask.bat.compose.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
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
import com.mwask.bat.compose.R
import com.mwask.bat.compose.components.EncoreIconButton
import com.mwask.bat.compose.components.matchesCurrent
import com.mwask.bat.compose.components.EncorePlayButton
import com.mwask.bat.backend.model.CityStat
import com.mwask.bat.backend.model.ShelfItem
import com.mwask.bat.compose.components.EncorePlayIndicator
import com.mwask.bat.compose.components.ShimmerBlock
import com.mwask.bat.compose.components.ShimmerHost
import com.mwask.bat.compose.components.EncoreArtworkImage
import com.mwask.bat.compose.components.SwipeToQueueBox
import com.mwask.bat.compose.components.rememberSmoothColor
import com.mwask.bat.compose.format.formatCount
import com.mwask.bat.compose.playlist.TrackItem
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreCornerRadii
import com.mwask.bat.compose.theme.EncoreSpacers
import com.mwask.bat.compose.theme.EncoreAppTheme
import com.mwask.bat.compose.theme.EncoreTheme
import com.mwask.bat.compose.theme.rememberBottomPadding
import androidx.compose.ui.res.stringResource

data class ArtistRelease(
    val title: String,
    val year: String,
    val type: String, // stringResource(R.string.album) or stringResource(R.string.single)
    val color: Color = Color(0xFF333333),
    val imageUrl: String? = null,
    val uri: String = ""
)

data class PopularTrack(
    val track: TrackItem,
    val streamCount: String
)

private const val POPULAR_PREVIEW_COUNT = 5

/**
 * - Edge-to-edge Hero image banner with dynamic gradient
 * - Verified Artist badge with monthly listeners count
 * - Follow / Following pill button with animated state
 * - Continuous gliding play button docking into the top bar (ComposePlayButtonBehavior)
 * - Pinned overlay top bar (transparent at rest, solid heroColor when scrolled)
 * - Top 5 Popular tracks with track numbers, equalizer, stream count, and 3-dots
 * - Popular Releases carousel plus dedicated Albums and Singles & EPs carousels
 * - stringResource(R.string.gallery) photo strip and stringResource(R.string.about_the_artist) biography card
 */
@Composable
fun EncoreArtistScreen(
    artistName: String,
    monthlyListeners: String,
    heroColor: Color = Color(0xFF7A1C28),
    popularTracks: List<PopularTrack>,
    releases: List<ArtistRelease>,
    /** The overview's dedicated albums group; falls back to splitting [releases]. */
    albums: List<ArtistRelease> = emptyList(),
    /** The overview's singles group (EPs included); falls back to splitting [releases]. */
    singles: List<ArtistRelease> = emptyList(),
    /** Related artists shelf (stringResource(R.string.fans_also_like)) from the overview. */
    relatedArtists: List<ShelfItem> = emptyList(),
    /** Albums the artist appears on, from the overview. */
    appearsOn: List<ShelfItem> = emptyList(),
    /** Playlists featuring the artist, from the overview. */
    featuring: List<ShelfItem> = emptyList(),
    /** Playlists the artist is discovered on, from the overview. */
    discoveredOn: List<ShelfItem> = emptyList(),
    currentTrackTitle: String,
    currentTrackUri: String,
    isPlaying: Boolean,
    onBackClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onTrackSelect: (TrackItem) -> Unit,
    onTrackOptionsClick: (TrackItem) -> Unit = {},
    onTrackLikeToggle: (TrackItem) -> Unit = {},
    onTrackAddToQueue: ((TrackItem) -> Unit)? = null,
    onArtistOptionsClick: () -> Unit = {},
    onReleaseClick: (ArtistRelease) -> Unit = {},
    onArtistClick: (String, String) -> Unit = { _, _ -> },
    onShelfClick: (String, String) -> Unit = { _, _ -> },
    onAboutClick: () -> Unit = {},
    onDiscographyClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    headerImageUrl: String? = null,
    biography: String = "",
    verified: Boolean = false,
    followers: Long = 0L,
    worldRank: Int = 0,
    topCities: List<CityStat> = emptyList(),
    gallery: List<String> = emptyList(),
    isLoading: Boolean = false,
    loadFailed: Boolean = false,
    onRetry: () -> Unit = {},
    /** The artist's real follow state from the library; the pill only flips
     *  once addToLibrary/removeFromLibrary has answered. */
    isFollowing: Boolean = false,
    onFollowToggle: (Boolean) -> Unit = {},
    isBanned: Boolean = false,
    onUnbanClick: () -> Unit = {}
) {
    val density = LocalDensity.current
    var isShuffle by remember { mutableStateOf(false) }
    val bottomPadding = rememberBottomPadding()
    val listState = rememberLazyListState()

    val albumSection = albums.ifEmpty {
        releases.filter { it.type.equals("Album", ignoreCase = true) }
    }
    val singleSection = singles.ifEmpty {
        releases.filter { it.type.equals("Single", ignoreCase = true) || it.type.equals("EP", ignoreCase = true) }
    }
    var popularExpanded by remember { mutableStateOf(false) }
    val visiblePopular = if (popularExpanded) popularTracks else popularTracks.take(POPULAR_PREVIEW_COUNT)

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val toolbarHeight = 54.dp
    val toolbarBottom = statusBarTop + toolbarHeight
    val toolbarBottomPx = with(density) { toolbarBottom.toPx() }
    val playButtonSize = 52.dp
    val playButtonHalf = 26.dp
    val pinnedTop = toolbarBottom - playButtonHalf
    val pinnedTopPx = with(density) { pinnedTop.toPx() }
    val heroHeight = 360.dp
    val heroHeightPx = with(density) { heroHeight.toPx() }

    val scrollPx by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 2000f
        }
    }
    val playDockStartPx = with(density) { 400.dp.toPx() }
    val playFadePx = with(density) { 60.dp.toPx() }
    val playFraction by remember {
        derivedStateOf { ((scrollPx - playDockStartPx) / playFadePx).coerceIn(0f, 1f) }
    }

    val topBarThreeDotsAlpha by remember { derivedStateOf { 1f - playFraction } }
    val topBarTitleAlpha by remember {
        derivedStateOf {
            ((scrollPx - with(density) { 260.dp.toPx() }) / with(density) { 60.dp.toPx() }).coerceIn(0f, 1f)
        }
    }
    val topBarAlpha by remember {
        derivedStateOf { (scrollPx / with(density) { 180.dp.toPx() }).coerceIn(0f, 1f) }
    }
    val smoothHeroColor by rememberSmoothColor(target = heroColor)
    val topBarBgColor = smoothHeroColor.copy(alpha = topBarAlpha)
    val circleBtnAlpha = ((1f - topBarAlpha) * 0.35f).coerceIn(0f, 0.35f)

    val followBorderColor by animateColorAsState(
        targetValue = if (isFollowing) EncoreColors.Green else EncoreColors.TextSecondary,
        label = "followBorder"
    )
    val followTextColor by animateColorAsState(
        targetValue = if (isFollowing) EncoreColors.Green else EncoreColors.TextPrimary,
        label = "followText"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .graphicsLayer {
                    val scrollOffset = if (listState.firstVisibleItemIndex == 0) {
                        listState.firstVisibleItemScrollOffset.toFloat()
                    } else {
                        heroHeightPx
                    }
                    translationY = -scrollOffset * 0.45f
                    alpha = (1f - (scrollOffset / (heroHeightPx * 0.85f))).coerceIn(0f, 1f)
                }
        ) {
            val heroOnScreen = listState.firstVisibleItemIndex == 0
            key(heroOnScreen) {
                if (headerImageUrl != null) {
                    EncoreArtworkImage(
                        imageUrl = headerImageUrl,
                        contentDescription = artistName,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 0.dp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(smoothHeroColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            ShimmerHost {
                                ShimmerBlock(
                                    modifier = Modifier.fillMaxSize(),
                                    shape = RectangleShape,
                                    base = smoothHeroColor,
                                    highlight = smoothHeroColor.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.45f to Color.Transparent,
                                0.78f to Color(0xCC121212),
                                1.0f to Color(0xFF121212)
                            )
                        )
                    )
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                        .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Base),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column {
                        Text(
                            text = artistName,
                            style = EncoreTheme.typography.headlineLarge.copy(
                                color = Color.White,
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-1).sp,
                                lineHeight = 46.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (verified) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_verified_check_active_24),
                                    contentDescription = stringResource(R.string.verified_artist),
                                    tint = Color(0xFF3D91F4),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.verified_artist),
                                    style = EncoreTheme.typography.bodySmall.copy(
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                        }

                        if (monthlyListeners.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.monthly_listeners_count, monthlyListeners),
                                style = EncoreTheme.typography.bodySmall.copy(
                                    color = Color(0xFFB3B3B3),
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Base),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(EncoreSpacers.Tighter)
                    ) {
                        val latestRelease = releases.firstOrNull()
                        if (latestRelease?.imageUrl != null) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onReleaseClick(latestRelease) }
                            ) {
                                EncoreArtworkImage(
                                    imageUrl = latestRelease.imageUrl,
                                    contentDescription = latestRelease.title,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 4.dp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(EncoreCornerRadii.FollowPill))
                                .border(1.dp, followBorderColor, RoundedCornerShape(EncoreCornerRadii.FollowPill))
                                .clickable { onFollowToggle(!isFollowing) }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isFollowing) stringResource(R.string.following) else "Follow",
                                style = EncoreTheme.typography.bodySmallBold.copy(color = followTextColor)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onArtistOptionsClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_more_android_24),
                                contentDescription = stringResource(R.string.artist_options),
                                tint = EncoreColors.TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        if (isBanned) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onUnbanClick),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_ban_24),
                                    contentDescription = stringResource(R.string.blocked_artist_tap_to_allow),
                                    tint = EncoreColors.TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
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
                                contentDescription = stringResource(R.string.shuffle_artist),
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
            }

            item {
                Text(
                    text = stringResource(R.string.popular),
                    style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                    modifier = Modifier.padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter)
                )
            }

            if (isLoading && popularTracks.isEmpty()) {
                item { ArtistLoadingRows() }
            }

            itemsIndexed(visiblePopular) { index, popTrack ->
                val track = popTrack.track
                val isBanned = com.mwask.bat.compose.bans.ArtistBans.isBanned(track.artistUri)
                val isCurrentTrack =
                    matchesCurrent(track.uri, currentTrackUri, track.title, currentTrackTitle)
                SwipeToQueueBox(
                    onAddToQueue = onTrackAddToQueue?.takeIf { !isBanned }?.let { handler -> { handler(track) } }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isBanned) { onTrackSelect(track) }
                            .padding(
                                horizontal = EncoreSpacers.Base,
                                vertical = EncoreSpacers.Tighter3
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.width(24.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (isCurrentTrack && isPlaying) {
                                EncorePlayIndicator(
                                    isPlaying = true,
                                    color = EncoreColors.Green,
                                    size = 12.dp
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    style = EncoreTheme.typography.bodySmall.copy(
                                        fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrentTrack) EncoreColors.Green else EncoreColors.TextSecondary
                                    )
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF242424))
                                .graphicsLayer { alpha = if (isBanned) 0.4f else 1f },
                            contentAlignment = Alignment.Center
                        ) {
                            if (track.imageUrl != null) {
                                EncoreArtworkImage(
                                    imageUrl = track.imageUrl,
                                    contentDescription = track.title,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 4.dp
                                )
                            } else {
                                Text("♫", color = Color.White, fontSize = 16.sp)
                            }
                        }

                        Spacer(modifier = Modifier.width(EncoreSpacers.Tighter2))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = EncoreTheme.typography.bodySmall.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when {
                                        isCurrentTrack -> EncoreColors.Green
                                        isBanned -> EncoreColors.TextSecondary.copy(alpha = 0.55f)
                                        else -> Color.White
                                    }
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (popTrack.streamCount.isNotBlank()) {
                                Text(
                                    text = popTrack.streamCount,
                                    style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary),
                                    maxLines = 1
                                )
                            }
                        }

                        if (!isBanned) {
                            EncoreIconButton(
                                iconRes = if (track.saved == true) R.drawable.encore_icon_heart_active_24 else R.drawable.encore_icon_heart_24,
                                contentDescription = if (track.saved == true) stringResource(R.string.remove_from_liked_songs) else stringResource(R.string.add_to_liked_songs),
                                onClick = { onTrackLikeToggle(track) },
                                tint = if (track.saved == true) EncoreColors.Green else EncoreColors.TextSecondary,
                                size = 32.dp,
                                iconSize = 18.dp
                            )
                        }

                        EncoreIconButton(
                            iconRes = R.drawable.encore_icon_more_android_24,
                            contentDescription = stringResource(R.string.track_options),
                            onClick = { onTrackOptionsClick(track) },
                            tint = EncoreColors.TextSecondary,
                            size = 32.dp,
                            iconSize = 18.dp
                        )
                    }
                }
            }

            if (popularTracks.size > POPULAR_PREVIEW_COUNT) {
                item {
                    Text(
                        text = if (popularExpanded) stringResource(R.string.show_less) else stringResource(R.string.see_more),
                        style = EncoreTheme.typography.bodyMedium.copy(
                            color = EncoreColors.TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { popularExpanded = !popularExpanded }
                            .padding(horizontal = EncoreSpacers.Base, vertical = 12.dp)
                    )
                }
            }

            if (loadFailed && !isLoading) {
                item {
                    ArtistLoadError(
                        title = stringResource(R.string.couldn_t_load_this_artist),
                        onRetry = onRetry
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.popular_releases),
                        style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    Text(
                        text = stringResource(R.string.discography),
                        style = EncoreTheme.typography.bodyMedium.copy(
                            color = EncoreColors.TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.clickable { onDiscographyClick() }
                    )
                }
            }

            item { ArtistReleaseCarousel(releases, onReleaseClick) }

            if (albumSection.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.albums),
                        style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                        modifier = Modifier.padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter)
                    )
                }
                item { ArtistReleaseCarousel(albumSection, onReleaseClick) }
            }

            if (singleSection.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.singles_and_eps),
                        style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                        modifier = Modifier.padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter)
                    )
                }
                item { ArtistReleaseCarousel(singleSection, onReleaseClick) }
            }

            if (relatedArtists.isNotEmpty()) {
                item { ArtistShelfTitle(stringResource(R.string.fans_also_like)) }
                item { ArtistCircleRow(relatedArtists, onArtistClick) }
            }

            if (appearsOn.isNotEmpty()) {
                item { ArtistShelfTitle(stringResource(R.string.appears_on)) }
                item { ArtistShelfCardRow(appearsOn, onShelfClick) }
            }

            if (featuring.isNotEmpty()) {
                item { ArtistShelfTitle(stringResource(R.string.featuring)) }
                item { ArtistShelfCardRow(featuring, onShelfClick) }
            }

            if (discoveredOn.isNotEmpty()) {
                item { ArtistShelfTitle(stringResource(R.string.discovered_on)) }
                item { ArtistShelfCardRow(discoveredOn, onShelfClick) }
            }

            if (monthlyListeners.isNotBlank() || biography.isNotBlank() ||
                followers > 0L || worldRank > 0 || topCities.isNotEmpty()
            ) {
                item {
                    val aboutStatLine = listOfNotNull(
                        followers.takeIf { it > 0L }?.let { stringResource(R.string.follower_count, formatCount(it)) },
                        worldRank.takeIf { it > 0 }?.let { "#$it in the world" },
                    ).joinToString(" • ")
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.about),
                        style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                        modifier = Modifier.padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = EncoreSpacers.Base, vertical = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF242424))
                            .clickable { onAboutClick() }
                    ) {
                        Column {
                            gallery.firstOrNull()?.let { photo ->
                                EncoreArtworkImage(
                                    imageUrl = photo,
                                    contentDescription = artistName,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(132.dp),
                                    cornerRadius = 0.dp
                                )
                            }
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (monthlyListeners.isNotBlank()) {
                                    Text(
                                        text = stringResource(R.string.monthly_listeners_count, monthlyListeners),
                                        style = EncoreTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                }
                                if (aboutStatLine.isNotBlank()) {
                                    if (monthlyListeners.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    Text(
                                        text = aboutStatLine,
                                        style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                                    )
                                }
                                if (topCities.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.popular_in_cities, topCities.take(3).joinToString(", ") { it.city }),
                                        style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (biography.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = biography,
                                        style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (gallery.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.gallery),
                        style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                        modifier = Modifier.padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter)
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = EncoreSpacers.Base),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(gallery.take(10), key = { it }) { photo ->
                            Box(
                                modifier = Modifier
                                    .width(232.dp)
                                    .height(174.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF242424))
                            ) {
                                EncoreArtworkImage(
                                    imageUrl = photo,
                                    contentDescription = stringResource(R.string.artist_photo_cd, artistName),
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 8.dp
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(topBarBgColor)
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
                    text = artistName,
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
                        .clickable(onClick = onArtistOptionsClick)
                        .graphicsLayer { alpha = topBarThreeDotsAlpha },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_more_android_24),
                        contentDescription = stringResource(R.string.artist_options),
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

/** One release tile: 140dp cover, bold title, stringResource(R.string.type_year) line. */
@Composable
private fun ArtistReleaseCard(
    release: ArtistRelease,
    onReleaseClick: (ArtistRelease) -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onReleaseClick(release) }
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
                Text("♫", color = Color.White, fontSize = 36.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = release.title,
            style = EncoreTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
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

@Composable
private fun ArtistReleaseCarousel(
    releases: List<ArtistRelease>,
    onReleaseClick: (ArtistRelease) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = EncoreSpacers.Base),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(releases, key = { it.title + it.year }) { release ->
            ArtistReleaseCard(release, onReleaseClick)
        }
    }
}

@Composable
private fun ArtistShelfTitle(text: String) {
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = text,
        style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
        modifier = Modifier.padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter)
    )
}

@Composable
private fun ArtistShelfCardRow(
    rows: List<ShelfItem>,
    onClick: (String, String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = EncoreSpacers.Base),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(rows) { row ->
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clickable { onClick(row.title, row.uri) }
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF242424))
                ) {
                    EncoreArtworkImage(
                        imageUrl = row.imageUrl,
                        contentDescription = row.title,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 4.dp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = row.title,
                    style = EncoreTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (row.subtitle.isNotBlank()) {
                    Text(
                        text = row.subtitle,
                        style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistCircleRow(
    artists: List<ShelfItem>,
    onClick: (String, String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = EncoreSpacers.Base),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(artists) { artist ->
            Column(
                modifier = Modifier
                    .width(108.dp)
                    .clickable { onClick(artist.title, artist.uri) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF242424))
                ) {
                    EncoreArtworkImage(
                        imageUrl = artist.imageUrl,
                        contentDescription = artist.title,
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = artist.title,
                    style = EncoreTheme.typography.bodySmall.copy(color = Color.White),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ArtistLoadingRows() {
    ShimmerHost {
        Column(modifier = Modifier.fillMaxWidth()) {
            repeat(5) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerBlock(
                        modifier = Modifier
                            .width(16.dp)
                            .height(12.dp),
                        shape = RoundedCornerShape(2.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ShimmerBlock(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(4.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(12.dp),
                            shape = RoundedCornerShape(2.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ShimmerBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.35f)
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
private fun ArtistLoadError(title: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EncoreSpacers.Base, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = EncoreTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            textAlign = TextAlign.Center
        )
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

@Preview
@Composable
fun PreviewEncoreArtistScreen() {
    EncoreAppTheme {
        EncoreArtistScreen(
            artistName = "The Weeknd",
            monthlyListeners = "104,239,812",
            heroColor = Color(0xFF6B1A24),
            popularTracks = listOf(
                PopularTrack(TrackItem("Blinding Lights", "The Weeknd"), "4,250,810,912"),
                PopularTrack(TrackItem("Starboy", "The Weeknd, Daft Punk"), "3,119,402,118"),
                PopularTrack(TrackItem("Save Your Tears", "The Weeknd"), "2,845,190,441"),
                PopularTrack(TrackItem("Die For You", "The Weeknd"), "2,301,894,520"),
                PopularTrack(TrackItem("The Hills", "The Weeknd"), "2,190,341,080")
            ),
            releases = listOf(
                ArtistRelease("After Hours", "2020", stringResource(R.string.album), Color(0xFF5A1A22)),
                ArtistRelease("Dawn FM", "2022", stringResource(R.string.album), Color(0xFF243B55)),
                ArtistRelease("Starboy", "2016", stringResource(R.string.album), Color(0xFF8E0E00)),
                ArtistRelease("Timeless", "2024", stringResource(R.string.single), Color(0xFF333333))
            ),
            currentTrackTitle = "Starboy",
            currentTrackUri = "",
            isPlaying = true,
            onBackClick = {},
            onPlayPauseClick = {},
            onTrackSelect = {}
        )
    }
}
