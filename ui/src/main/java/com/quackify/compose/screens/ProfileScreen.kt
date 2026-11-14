package com.quackify.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.backend.model.ProfileArtist
import com.quackify.backend.model.ProfileTopContent
import com.quackify.backend.model.ProfileTopTrack
import com.quackify.backend.model.UserProfile
import com.quackify.compose.R
import com.quackify.compose.components.DetailPageSkeleton
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.compose.components.EncoreIconButton
import com.quackify.compose.components.EncoreTrackRow
import com.quackify.compose.components.rememberSmoothColor
import com.quackify.compose.format.formatCount
import com.quackify.compose.format.parseHexColor
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreAppTheme
import com.quackify.compose.theme.EncoreTheme
import com.quackify.compose.theme.rememberBottomPadding
import androidx.compose.ui.res.stringResource

data class ProfileContextCard(
    val uri: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
)

fun ProfileTopTrack.toTrackItem(): TrackItem = TrackItem(
    title = title,
    artist = artists,
    isExplicit = explicit,
    uri = uri,
    imageUrl = imageUrl.takeIf { it.isNotBlank() },
    durationMs = durationMs,
    albumUri = albumUri,
    artistUri = artistUri,
    albumName = albumName,
)

@Composable
fun EncoreProfileScreen(
    profile: UserProfile?,
    topContent: ProfileTopContent?,
    contexts: List<ProfileContextCard>,
    loading: Boolean,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onBackClick: () -> Unit,
    currentTrackUri: String,
    isPlaying: Boolean,
    onFollowStatsClick: () -> Unit,
    onArtistClick: (String, String) -> Unit,
    onTrackSelect: (ProfileTopTrack) -> Unit,
    onTrackOptionsClick: (ProfileTopTrack) -> Unit,
    onTrackAddToQueue: ((ProfileTopTrack) -> Unit)? = null,
    onContextClick: (ProfileContextCard) -> Unit,
    contextsTitle: String = stringResource(R.string.recently_played),
    fallbackName: String = "",
    avatarUrl: String = "",
    topContentUnavailable: Boolean = false,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val bottomPadding = rememberBottomPadding()
    val topBarRaised by remember {
        derivedStateOf { listState.firstVisibleItemScrollOffset > 32 }
    }
    val topBarColor by rememberSmoothColor(
        if (topBarRaised) EncoreColors.Background else Color.Transparent
    )

    val hasData = profile != null || topContent != null || contexts.isNotEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreColors.Background)
    ) {
        when {
            loading && !hasData -> DetailPageSkeleton(
                modifier = Modifier.align(Alignment.Center),
                circle = true
            )
            loadFailed && !hasData -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = bottomPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_cloud_off_24),
                        contentDescription = null,
                        tint = EncoreColors.TextSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.couldn_t_load_this_profile),
                        style = EncoreTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.check_your_internet_connection_and_try_again),
                        style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onRetry) {
                        Text(text = stringResource(R.string.try_again), color = EncoreColors.Green)
                    }
                }
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                item {
                    ProfileHeader(
                        profile = profile,
                        fallbackName = fallbackName,
                        avatarUrl = avatarUrl,
                        onFollowStatsClick = onFollowStatsClick
                    )
                }
                val topArtists = topContent?.topArtists.orEmpty()
                if (topArtists.isNotEmpty()) {
                    item {
                        ProfileSectionTitle(title = stringResource(R.string.top_artists_this_month))
                    }
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(topArtists) { artist ->
                                ProfileArtistCard(
                                    artist = artist,
                                    onClick = { onArtistClick(artist.name, artist.uri) }
                                )
                            }
                        }
                    }
                }
                val topTracks = topContent?.topTracks.orEmpty()
                if (topTracks.isNotEmpty()) {
                    item {
                        ProfileSectionTitle(title = stringResource(R.string.top_tracks_this_month))
                    }
                    items(topTracks) { track ->
                        EncoreTrackRow(
                            trackNumber = null,
                            title = track.title,
                            artist = track.artists,
                            isPlaying = isPlaying && track.uri == currentTrackUri,
                            onClick = { onTrackSelect(track) },
                            onMoreClick = { onTrackOptionsClick(track) },
                            isExplicit = track.explicit,
                            imageUrl = track.imageUrl.takeIf { it.isNotBlank() },
                            artistUri = track.artistUri,
                            onAddToQueue = onTrackAddToQueue?.let { handler -> { handler(track) } }
                        )
                    }
                }
                if (topContentUnavailable && topArtists.isEmpty() && topTracks.isEmpty()) {
                    item {
                        ProfileSectionTitle(title = stringResource(R.string.top_artists_this_month))
                    }
                    item {
                        Text(
                            text = stringResource(R.string.their_top_artists_and_tracks_are_private_or_they),
                            style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                if (contexts.isNotEmpty()) {
                    item {
                        ProfileSectionTitle(title = contextsTitle)
                    }
                    val rows = contexts.chunked(3)
                    items(rows) { rowCards ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowCards.forEach { card ->
                                ProfileContextCardItem(
                                    card = card,
                                    onClick = { onContextClick(card) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - rowCards.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(topBarColor)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_arrow_left_24,
                contentDescription = stringResource(R.string.back),
                onClick = onBackClick,
                tint = EncoreColors.TextPrimary
            )
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: UserProfile?,
    fallbackName: String,
    avatarUrl: String,
    onFollowStatsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val name = profile?.name?.takeIf { it.isNotBlank() } ?: fallbackName.ifBlank { stringResource(R.string.quackify) }
        val fallbackColor = parseHexColor(profile?.colorHex) ?: EncoreColors.CardElevated
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(fallbackColor),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl.isNotBlank()) {
                EncoreArtworkImage(
                    imageUrl = avatarUrl,
                    contentDescription = name,
                    shape = CircleShape,
                    fallbackColor = fallbackColor,
                    modifier = Modifier.fillMaxSize(),
                    requestSize = 256
                )
            } else {
                Text(
                    text = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "Q",
                    style = EncoreTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = name,
            style = EncoreTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        if (profile != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${formatCount(profile.followersCount)} followers • ${formatCount(profile.followingCount)} following",
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onFollowStatsClick)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ProfileSectionTitle(title: String) {
    Text(
        text = title,
        style = EncoreTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        ),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)
    )
}

@Composable
private fun ProfileArtistCard(
    artist: ProfileArtist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(116.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EncoreArtworkImage(
            imageUrl = artist.imageUrl.takeIf { it.isNotBlank() },
            contentDescription = artist.name,
            shape = CircleShape,
            fallbackColor = EncoreColors.Card,
            modifier = Modifier.size(116.dp),
            requestSize = 256
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = EncoreTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun ProfileContextCardItem(
    card: ProfileContextCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(bottom = 16.dp)
            .clickable(onClick = onClick)
    ) {
        EncoreArtworkImage(
            imageUrl = card.imageUrl.takeIf { it.isNotBlank() },
            contentDescription = card.title,
            shape = if (card.uri.startsWith("spotify:artist:")) CircleShape else RoundedCornerShape(4.dp),
            fallbackColor = EncoreColors.Card,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            requestSize = 256
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = card.title,
            style = EncoreTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (card.subtitle.isNotBlank()) {
            Text(
                text = card.subtitle,
                style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewEncoreProfileScreen() {
    EncoreAppTheme {
        EncoreProfileScreen(
            profile = UserProfile(
                uri = "spotify:user:preview",
                name = "Duck User",
                followersCount = 5,
                followingCount = 4,
                recentlyPlayedArtists = emptyList(),
                colorHex = "",
                isCurrentUser = true
            ),
            topContent = null,
            contexts = emptyList(),
            loading = false,
            loadFailed = false,
            onRetry = {},
            onBackClick = {},
            currentTrackUri = "",
            isPlaying = false,
            onFollowStatsClick = {},
            onArtistClick = { _, _ -> },
            onTrackSelect = {},
            onTrackOptionsClick = {},
            onContextClick = {}
        )
    }
}
