package com.quackify.compose.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.components.EncorePlayButton
import com.quackify.compose.components.InlineLoadingDots
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.compose.components.rememberSmoothColor
import com.quackify.compose.components.matchesCurrent
import com.quackify.backend.model.EpisodeRow
import com.quackify.backend.model.ShowCard
import com.quackify.compose.format.formatCount
import com.quackify.compose.format.formatDuration
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreAppTheme
import com.quackify.compose.theme.EncoreTheme
import com.quackify.compose.theme.rememberBottomPadding
import androidx.compose.ui.res.stringResource

@Composable
fun EncoreShowScreen(
    showName: String,
    publisher: String,
    description: String,
    imageUrl: String?,
    headerColor: Color,
    rating: Double = 0.0,
    ratingCount: Int = 0,
    topics: List<String> = emptyList(),
    isFollowing: Boolean = false,
    onFollowToggle: () -> Unit = {},
    episodes: List<EpisodeRow> = emptyList(),
    total: Int = 0,
    related: List<ShowCard> = emptyList(),
    isPlaying: Boolean = false,
    currentTrackTitle: String = "",
    currentTrackUri: String = "",
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onEpisodeClick: (EpisodeRow) -> Unit = {},
    onEpisodeMoreClick: ((EpisodeRow) -> Unit)? = null,
    onRelatedClick: (ShowCard) -> Unit = {},
    onPlayEpisode: (EpisodeRow) -> Unit = {},
    onBackClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var descriptionExpanded by remember { mutableStateOf(false) }
    var descriptionOverflows by remember { mutableStateOf(false) }
    val smoothHeaderColor by rememberSmoothColor(target = headerColor)
    val bottomPadding = rememberBottomPadding()
    val listState = rememberLazyListState()

    val isScrolledPastHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 300
        }
    }

    val followBorderColor by animateColorAsState(
        targetValue = if (isFollowing) EncoreColors.Green else EncoreColors.TextSecondary,
        label = "showFollowBorder",
    )
    val followTextColor by animateColorAsState(
        targetValue = if (isFollowing) EncoreColors.Green else EncoreColors.TextPrimary,
        label = "showFollowText",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreColors.Background),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    smoothHeaderColor,
                                    smoothHeaderColor.copy(alpha = 0.55f),
                                    EncoreColors.Background,
                                ),
                            ),
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        EncoreIconButton(
                            iconRes = R.drawable.encore_icon_arrow_left_24,
                            contentDescription = stringResource(R.string.back),
                            onClick = onBackClick,
                            tint = EncoreColors.TextPrimary,
                        )
                        if (onMoreClick != null) {
                            EncoreIconButton(
                                iconRes = R.drawable.encore_icon_more_android_24,
                                contentDescription = stringResource(R.string.more_options),
                                onClick = onMoreClick,
                                tint = EncoreColors.TextPrimary,
                            )
                        } else {
                            Spacer(modifier = Modifier.size(48.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .align(Alignment.CenterHorizontally)
                            .shadow(elevation = 18.dp, shape = RoundedCornerShape(6.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .background(smoothHeaderColor.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!imageUrl.isNullOrBlank()) {
                            EncoreArtworkImage(
                                imageUrl = imageUrl,
                                contentDescription = showName,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 6.dp,
                            )
                        } else {
                            Text(text = "◉", fontSize = 64.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = showName,
                        style = EncoreTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = EncoreColors.TextPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (publisher.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = publisher,
                            style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextPrimary),
                        )
                    }

                    val ratingLabel = if (ratingCount > 0) {
                        stringResource(R.string.show_rating, "%.1f".format(rating), formatCount(ratingCount.toLong()))
                    } else {
                        ""
                    }
                    if (ratingLabel.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = ratingLabel,
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                        )
                    }

                    if (topics.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = topics.joinToString(" • "),
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                        )
                    }

                    if (description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = description,
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { if (!descriptionExpanded) descriptionOverflows = it.hasVisualOverflow },
                        )
                        if (descriptionOverflows || descriptionExpanded) {
                            Text(
                                text = if (descriptionExpanded) stringResource(R.string.show_less) else stringResource(R.string.show_more),
                                style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.TextPrimary),
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .clickable { descriptionExpanded = !descriptionExpanded },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .border(1.dp, followBorderColor, RoundedCornerShape(50))
                                    .clickable { onFollowToggle() }
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = if (isFollowing) stringResource(R.string.following) else "Follow",
                                    style = EncoreTheme.typography.bodySmallBold.copy(color = followTextColor),
                                )
                            }
                        }

                        EncorePlayButton(
                            isPlaying = isPlaying,
                            onClick = {
                                val current = episodes.firstOrNull {
                                    matchesCurrent(it.uri, currentTrackUri, it.title, currentTrackTitle)
                                }
                                if (current != null) onPlayPauseClick()
                                else episodes.firstOrNull()?.let(onPlayEpisode) ?: onPlayPauseClick()
                            },
                            size = 52.dp,
                            iconSize = 26.dp,
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.all_episodes),
                        style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = EncoreColors.TextPrimary,
                    )
                    if (total > 0) {
                        Text(
                            text = formatCount(total.toLong()),
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                        )
                    }
                }
            }

            items(episodes, key = { it.uri }) { episode ->
                EpisodeListRow(
                    episode = episode,
                    isCurrent = matchesCurrent(episode.uri, currentTrackUri, episode.title, currentTrackTitle),
                    isPlaying = isPlaying,
                    onClick = { onEpisodeClick(episode) },
                    onPlayClick = { onPlayEpisode(episode) },
                    onMoreClick = onEpisodeMoreClick?.let { handler -> { handler(episode) } },
                )
            }

            if (hasMore) {
                item(key = "load-more") {
                    LaunchedEffect(episodes.size, hasMore) { onLoadMore() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        InlineLoadingDots(color = EncoreColors.TextSecondary)
                    }
                }
            }

            if (related.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            text = stringResource(R.string.more_podcasts_like_this),
                            style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = EncoreColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(related, key = { it.uri }) { card ->
                                RelatedShowCard(card = card, onClick = { onRelatedClick(card) })
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isScrolledPastHeader,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(smoothHeaderColor.copy(alpha = 0.98f))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EncoreIconButton(
                    iconRes = R.drawable.encore_icon_arrow_left_24,
                    contentDescription = stringResource(R.string.back),
                    onClick = onBackClick,
                    tint = EncoreColors.TextPrimary,
                )
                Text(
                    text = showName,
                    style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = EncoreColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
                EncorePlayButton(
                    isPlaying = isPlaying,
                    onClick = onPlayPauseClick,
                    size = 36.dp,
                    iconSize = 18.dp,
                )
            }
        }
    }
}

@Composable
private fun EpisodeListRow(
    episode: EpisodeRow,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(EncoreColors.Card),
        ) {
            if (episode.imageUrl.isNotBlank()) {
                EncoreArtworkImage(
                    imageUrl = episode.imageUrl,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 6.dp,
                )
            }
            if (onPlayClick != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onPlayClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            if (isCurrent && isPlaying) R.drawable.encore_icon_pause_24
                            else R.drawable.encore_icon_play_24
                        ),
                        contentDescription = if (isCurrent && isPlaying) stringResource(R.string.pause_episode) else stringResource(R.string.play_episode),
                        tint = Color.White,
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .padding(3.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) EncoreColors.Green else EncoreColors.TextPrimary,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = episodeSubtitle(episode),
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (onMoreClick != null) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_more_android_24,
                contentDescription = stringResource(R.string.episode_options),
                onClick = onMoreClick,
                tint = EncoreColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun RelatedShowCard(card: ShowCard, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(EncoreColors.Card),
        ) {
            if (card.imageUrl.isNotBlank()) {
                EncoreArtworkImage(
                    imageUrl = card.imageUrl,
                    contentDescription = card.name,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 6.dp,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = card.name,
            style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.TextPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (card.publisher.isNotBlank()) {
            Text(
                text = card.publisher,
                style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun episodeSubtitle(episode: EpisodeRow): String {
    val parts = ArrayList<String>(4)
    if (episode.played) {
        parts += stringResource(R.string.played)
    } else if (episode.positionMs > 0L) {
        parts += "${formatDuration(episode.positionMs)} in"
    }
    if (episode.releaseDate.isNotBlank()) parts += episode.releaseDate
    formatDuration(episode.durationMs).takeIf { it.isNotBlank() }?.let(parts::add)
    if (episode.video) parts += stringResource(R.string.video)
    return parts.joinToString(" • ")
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewEncoreShowScreen() {
    EncoreAppTheme {
        EncoreShowScreen(
            showName = "The Mindset Mentor",
            publisher = "Rob Dial",
            description = "The Mindset Mentor™ podcast is designed for anyone desiring motivation, direction, " +
                "and focus in life.",
            imageUrl = null,
            headerColor = Color(0xFF1B4A3A),
            rating = 4.9,
            ratingCount = 44557,
            episodes = listOf(
                EpisodeRow(
                    uri = "spotify:episode:1",
                    title = "How to Be Respected",
                    description = "",
                    releaseDate = "October 28, 2026",
                    durationMs = 964362,
                    imageUrl = "",
                    colorHex = "",
                    explicit = true,
                    playable = true,
                    video = false,
                    played = false,
                    positionMs = 0L,
                ),
                EpisodeRow(
                    uri = "spotify:episode:2",
                    title = "How to Not get Triggered",
                    description = "",
                    releaseDate = "October 26, 2026",
                    durationMs = 1080000,
                    imageUrl = "",
                    colorHex = "",
                    explicit = false,
                    playable = true,
                    video = false,
                    played = true,
                    positionMs = 0L,
                ),
            ),
            total = 1961,
            onBackClick = {},
            onPlayPauseClick = {},
        )
    }
}
