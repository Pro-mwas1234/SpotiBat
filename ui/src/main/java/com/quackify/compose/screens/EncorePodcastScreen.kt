package com.quackify.compose.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.quackify.compose.R
import com.quackify.compose.components.EncoreExplicitTag
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreCornerRadii
import com.quackify.compose.theme.EncoreAppTheme
import com.quackify.compose.theme.EncoreTheme
import com.quackify.compose.theme.rememberBottomPadding
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource

data class PodcastEpisode(
    val id: String,
    val title: String,
    val description: String,
    val releaseDate: String,
    val duration: String,
    val statusText: String? = null,
    val isExplicit: Boolean = false,
    val imageUrl: String? = null,
    val isPlayed: Boolean = false
)

val DefaultPodcastEpisodes = emptyList<PodcastEpisode>()

private val RelatedPodcastShows = emptyList<Pair<String, String>>()

/**
 * - Collapsible Show Header: Two-column layout (112dp cover art on left, Bold title & publisher on right)
 * - Pinned Navigation Tabs: Episodes, About, More like this (stays directly below the Top Bar when scrolling)
 * - Gradient ends strictly before the Tabs Bar, keeping Tabs & Content over pure #121212 black background
 * - HorizontalPager supporting smooth swiping between Episodes, About, and More like this with animated sliding transitions
 * - Authentic 16:9 widescreen episode cards with snippet, metadata, action controls, and 32dp solid white play circle
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EncorePodcastScreen(
    showTitle: String = "",
    publisherName: String = "",
    rankBadge: String? = null,
    rating: String = "",
    category: String = "",
    aboutDescription: String = "",
    coverColor: Color = Color(0xFF2E3A40),
    coverUrl: String? = null,
    episodes: List<PodcastEpisode> = DefaultPodcastEpisodes,
    currentEpisodeId: String = "",
    isPlaying: Boolean = false,
    onBackClick: () -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
    onEpisodeSelect: (PodcastEpisode) -> Unit = {},
    onEpisodeOptionsClick: (PodcastEpisode) -> Unit = {},
    onShowOptionsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var isFollowing by remember { mutableStateOf(false) }
    var hasNotifications by remember { mutableStateOf(false) }
    val bottomPadding = rememberBottomPadding()

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val toolbarHeight = 54.dp
    val toolbarBottom = statusBarTop + toolbarHeight
    val toolbarBottomPx = with(density) { toolbarBottom.toPx() }
    val tabsHeight = 48.dp
    val tabsHeightPx = with(density) { tabsHeight.toPx() }

    val episodesTabLabel = stringResource(R.string.episodes)
    val aboutTabLabel = stringResource(R.string.about)
    val moreLikeTabLabel = stringResource(R.string.more_like_this)
    val tabs = remember(episodesTabLabel, aboutTabLabel, moreLikeTabLabel) {
        listOf(episodesTabLabel, aboutTabLabel, moreLikeTabLabel)
    }
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }

    val episodesListState = rememberLazyListState()
    val aboutListState = rememberLazyListState()
    val moreListState = rememberLazyListState()

    var rootHeightPx by remember { mutableFloatStateOf(0f) }
    var headerContentHeightPx by remember { mutableFloatStateOf(with(density) { 260.dp.toPx() }) }
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }

    val maxCollapsePx = headerContentHeightPx.coerceAtLeast(1f)
    val collapseFraction = (-headerOffsetPx / maxCollapsePx).coerceIn(0f, 1f)
    val topBarBgColor = coverColor.copy(alpha = collapseFraction)
    val topBarTitleAlpha = ((collapseFraction - 0.4f) * 1.67f).coerceIn(0f, 1f)
    val circleBtnAlpha = ((1f - collapseFraction) * 0.35f).coerceIn(0f, 0.35f)

    val followBorderColor by animateColorAsState(
        targetValue = if (isFollowing) EncoreColors.Green else EncoreColors.TextSecondary,
        label = "podcastFollowBorder"
    )
    val followTextColor by animateColorAsState(
        targetValue = if (isFollowing) EncoreColors.Green else EncoreColors.TextPrimary,
        label = "podcastFollowText"
    )

    val nestedScrollConnection = remember(pagerState.currentPage, maxCollapsePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0f && headerOffsetPx > -maxCollapsePx) {
                    val newOffset = (headerOffsetPx + delta).coerceIn(-maxCollapsePx, 0f)
                    val consumed = newOffset - headerOffsetPx
                    headerOffsetPx = newOffset
                    return Offset(0f, consumed)
                }

                val activeList = when (pagerState.currentPage) {
                    0 -> episodesListState
                    1 -> aboutListState
                    else -> moreListState
                }
                if (delta > 0f && activeList.firstVisibleItemIndex == 0 && activeList.firstVisibleItemScrollOffset == 0 && headerOffsetPx < 0f) {
                    val newOffset = (headerOffsetPx + delta).coerceIn(-maxCollapsePx, 0f)
                    val consumed = newOffset - headerOffsetPx
                    headerOffsetPx = newOffset
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val delta = available.y
                if (delta > 0f && headerOffsetPx < 0f) {
                    val newOffset = (headerOffsetPx + delta).coerceIn(-maxCollapsePx, 0f)
                    val consumedY = newOffset - headerOffsetPx
                    headerOffsetPx = newOffset
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }
        }
    }

    val availablePagerHeightPx = if (rootHeightPx > 0f) {
        (rootHeightPx - toolbarBottomPx - tabsHeightPx).coerceAtLeast(0f)
    } else {
        with(density) { 600.dp.toPx() }
    }
    val availablePagerHeightDp = with(density) { availablePagerHeightPx.toDp() }

    val currentGradientHeightPx = (toolbarBottomPx + headerContentHeightPx + headerOffsetPx).coerceAtLeast(toolbarBottomPx)
    val currentGradientHeightDp = with(density) { currentGradientHeightPx.toDp() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .onGloballyPositioned { coords ->
                rootHeightPx = coords.size.height.toFloat()
            }
            .nestedScroll(nestedScrollConnection)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(currentGradientHeightDp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to coverColor,
                            0.40f to coverColor.copy(alpha = 0.85f),
                            0.70f to coverColor.copy(alpha = 0.40f),
                            0.90f to coverColor.copy(alpha = 0.15f),
                            1.0f to Color(0xFF121212)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, headerOffsetPx.roundToInt()) }
        ) {
            Spacer(modifier = Modifier.height(toolbarBottom))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = (1f - collapseFraction * 1.3f).coerceIn(0f, 1f)
                    }
                    .onGloballyPositioned { coords ->
                        if (coords.size.height > 0) {
                            headerContentHeightPx = coords.size.height.toFloat()
                        }
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            val newOffset = (headerOffsetPx + dragAmount).coerceIn(-maxCollapsePx, 0f)
                            headerOffsetPx = newOffset
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .shadow(elevation = 8.dp, shape = RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(coverColor.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!coverUrl.isNullOrBlank()) {
                            EncoreArtworkImage(
                                imageUrl = coverUrl,
                                contentDescription = showTitle,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 8.dp
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_podcasts_24),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = showTitle,
                            style = EncoreTheme.typography.headlineLarge.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 24.sp,
                                color = Color.White
                            ),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = publisherName,
                            style = EncoreTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!rankBadge.isNullOrBlank()) {
                        Text(
                            text = rankBadge,
                            style = EncoreTheme.typography.marginal.copy(
                                color = EncoreColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = " • ",
                            style = EncoreTheme.typography.marginal.copy(
                                color = EncoreColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }

                    Text(
                        text = stringResource(R.string.rating_stars, rating),
                        style = EncoreTheme.typography.marginal.copy(
                            color = EncoreColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )

                    Text(
                        text = " • ",
                        style = EncoreTheme.typography.marginal.copy(
                            color = EncoreColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    )

                    Text(
                        text = category,
                        style = EncoreTheme.typography.marginal.copy(
                            color = EncoreColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { onPlayPauseClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_play_24),
                            contentDescription = stringResource(R.string.play_teaser),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(EncoreCornerRadii.FollowPill))
                            .border(1.dp, followBorderColor, RoundedCornerShape(EncoreCornerRadii.FollowPill))
                            .clickable { isFollowing = !isFollowing }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isFollowing) stringResource(R.string.following) else "Follow",
                            style = EncoreTheme.typography.bodySmallBold.copy(
                                color = followTextColor,
                                fontSize = 13.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { hasNotifications = !hasNotifications },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (hasNotifications) R.drawable.encore_icon_notifications_active_24 else R.drawable.encore_icon_notification_new_24
                            ),
                            contentDescription = stringResource(R.string.notifications),
                            tint = if (hasNotifications) EncoreColors.Green else EncoreColors.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onSettingsClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_settings_24),
                            contentDescription = stringResource(R.string.show_settings),
                            tint = EncoreColors.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tabsHeight)
                    .background(Color(0xFF121212))
                    .zIndex(5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = pagerState.currentPage == index
                        Column(
                            modifier = Modifier
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = title,
                                style = EncoreTheme.typography.bodyMedium.copy(
                                    color = if (isSelected) Color.White else EncoreColors.TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .width(28.dp)
                                        .height(2.5.dp)
                                        .background(Color.White, RoundedCornerShape(1.dp))
                                )
                            } else {
                                Spacer(modifier = Modifier.height(2.5.dp))
                            }
                        }
                    }
                }
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.08f),
                    thickness = 0.5.dp
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(availablePagerHeightDp)
                    .background(Color(0xFF121212))
            ) { page ->
                when (page) {
                    0 -> {
                        LazyColumn(
                            state = episodesListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = bottomPadding)
                        ) {
                            stickyHeader {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF121212))
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(Color(0xFF282828))
                                            .clickable { }
                                            .padding(horizontal = 14.dp, vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = stringResource(R.string.all_episodes_newest),
                                                style = EncoreTheme.typography.bodySmallBold.copy(
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                painter = painterResource(id = R.drawable.encore_icon_chevron_down_24),
                                                contentDescription = stringResource(R.string.filter),
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            items(episodes, key = { it.id }) { episode ->
                                val isCurrentEpisode = episode.id == currentEpisodeId
                                PodcastEpisodeRow(
                                    episode = episode,
                                    isPlaying = isCurrentEpisode && isPlaying,
                                    onClick = { onEpisodeSelect(episode) },
                                    onOptionsClick = { onEpisodeOptionsClick(episode) }
                                )
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.08f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                    1 -> {
                        LazyColumn(
                            state = aboutListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = bottomPadding + 24.dp
                            )
                        ) {
                            item {
                                if (aboutDescription.isNotBlank()) {
                                    Text(
                                        text = stringResource(R.string.about),
                                        style = EncoreTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 18.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = aboutDescription,
                                        style = EncoreTheme.typography.bodySmall.copy(
                                            color = EncoreColors.TextSecondary,
                                            lineHeight = 20.sp,
                                            fontSize = 14.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                }

                                if (rating.isNotBlank() || category.isNotBlank()) {
                                    Text(
                                        text = stringResource(R.string.ratings_and_reviews),
                                        style = EncoreTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 16.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val ratingCategoryText = listOfNotNull(
                                        rating.takeIf { it.isNotBlank() }?.let { "★ $it" },
                                        category.takeIf { it.isNotBlank() }
                                    ).joinToString(" • ")
                                    Text(
                                        text = ratingCategoryText,
                                        style = EncoreTheme.typography.bodyMedium.copy(
                                            color = EncoreColors.TextSecondary,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        LazyColumn(
                            state = moreListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = bottomPadding + 24.dp
                            )
                        ) {
                            item {
                                Text(
                                    text = stringResource(R.string.more_like_this),
                                    style = EncoreTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 18.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            items(RelatedPodcastShows, key = { it.first }) { (title, host) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF242424)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.encore_icon_podcasts_24),
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = title,
                                            style = EncoreTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        )
                                        Text(
                                            text = stringResource(R.string.podcast_with_host, host),
                                            style = EncoreTheme.typography.bodySmall.copy(
                                                color = EncoreColors.TextSecondary
                                            )
                                        )
                                    }
                                }
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
                if (topBarTitleAlpha > 0.01f) {
                    Text(
                        text = showTitle,
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
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = circleBtnAlpha))
                    .clickable(onClick = onShowOptionsClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_more_android_24),
                    contentDescription = stringResource(R.string.show_options),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * - Top: 16:9 widescreen artwork (114x64dp, 6dp radius) + Title (15sp bold, maxLines = 3)
 * - Description: 2-line snippet (13sp textSecondary)
 * - Metadata line: Day • Duration • Status (e.g. "Tue • 1h 15m" or "Sat • 1h 50m left • Finished")
 * - Footer:
 *     - Left: Add (+), Download (↓), Share, More (...)
 *     - Right: 32dp solid white circle with pure black play triangle
 */
@Composable
private fun PodcastEpisodeRow(
    episode: PodcastEpisode,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isEpisodeDownloaded by remember { mutableStateOf(false) }
    var isEpisodeAdded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(width = 114.dp, height = 64.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF242424)),
                contentAlignment = Alignment.Center
            ) {
                if (!episode.imageUrl.isNullOrBlank()) {
                    EncoreArtworkImage(
                        imageUrl = episode.imageUrl,
                        contentDescription = episode.title,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 6.dp
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_podcasts_24),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = episode.title,
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isPlaying) EncoreColors.Green else Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = episode.description,
            style = EncoreTheme.typography.bodySmall.copy(
                color = EncoreColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 17.sp
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (episode.isExplicit) {
                EncoreExplicitTag(
                    modifier = Modifier.padding(end = 6.dp),
                    background = Color.White.copy(alpha = 0.6f)
                )
            }

            val metaText = buildString {
                append(episode.releaseDate)
                append(" • ")
                append(episode.duration)
                if (!episode.statusText.isNullOrBlank()) {
                    append(" • ")
                    append(episode.statusText)
                }
            }

            Text(
                text = metaText,
                style = EncoreTheme.typography.marginal.copy(
                    color = EncoreColors.TextSecondary,
                    fontSize = 12.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { isEpisodeAdded = !isEpisodeAdded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isEpisodeAdded) R.drawable.encore_icon_check_24 else R.drawable.encore_icon_plus_24
                        ),
                        contentDescription = stringResource(R.string.add_episode),
                        tint = if (isEpisodeAdded) EncoreColors.Green else EncoreColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { isEpisodeDownloaded = !isEpisodeDownloaded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isEpisodeDownloaded) R.drawable.encore_icon_downloaded_24 else R.drawable.encore_icon_download_24
                        ),
                        contentDescription = stringResource(R.string.download_episode),
                        tint = if (isEpisodeDownloaded) EncoreColors.Green else EncoreColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_share_android_24),
                        contentDescription = stringResource(R.string.share_episode),
                        tint = EncoreColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOptionsClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_more_android_24),
                        contentDescription = stringResource(R.string.episode_options),
                        tint = EncoreColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isPlaying) R.drawable.encore_icon_pause_24 else R.drawable.encore_icon_play_24
                    ),
                    contentDescription = if (isPlaying) "Pause" else stringResource(R.string.play),
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewEncorePodcastScreen() {
    EncoreAppTheme {
        EncorePodcastScreen()
    }
}
