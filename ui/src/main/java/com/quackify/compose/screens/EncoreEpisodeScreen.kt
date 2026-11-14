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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.compose.components.rememberSmoothColor
import com.quackify.compose.format.formatDuration
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreAppTheme
import com.quackify.compose.theme.EncoreTheme
import com.quackify.compose.theme.rememberBottomPadding
import androidx.compose.ui.res.stringResource

@Composable
fun EncoreEpisodeScreen(
    title: String,
    showName: String,
    description: String,
    imageUrl: String?,
    wideImageUrl: String? = null,
    headerColor: Color,
    metaLabel: String = "",
    durationMs: Long = 0L,
    positionMs: Long = 0L,
    isVideo: Boolean = false,
    isPlaying: Boolean = false,
    isCurrent: Boolean = false,
    onBackClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPlayClick: () -> Unit = {},
    onShowClick: () -> Unit = {},
    onMoreClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var descriptionExpanded by remember { mutableStateOf(false) }
    var descriptionOverflows by remember { mutableStateOf(false) }
    val smoothHeaderColor by rememberSmoothColor(target = headerColor)
    val bottomPadding = rememberBottomPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        smoothHeaderColor,
                        smoothHeaderColor.copy(alpha = 0.5f),
                        EncoreColors.Background,
                    ),
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding),
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isVideo && !wideImageUrl.isNullOrBlank()) {
                                    Modifier.aspectRatio(16f / 9f)
                                } else {
                                    Modifier.size(180.dp)
                                },
                            )
                            .align(if (isVideo) Alignment.CenterHorizontally else Alignment.Start)
                            .shadow(elevation = 16.dp, shape = RoundedCornerShape(6.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .background(smoothHeaderColor.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val art = wideImageUrl?.takeIf { it.isNotBlank() } ?: imageUrl
                        if (!art.isNullOrBlank()) {
                            EncoreArtworkImage(
                                imageUrl = art,
                                contentDescription = title,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 6.dp,
                            )
                        } else {
                            Text(text = "◉", fontSize = 56.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    if (showName.isNotBlank()) {
                        Text(
                            text = showName,
                            style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.TextPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(onClick = onShowClick),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Text(
                        text = title,
                        style = EncoreTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = EncoreColors.TextPrimary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = metaLabel,
                        style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (positionMs > 0L && durationMs > 0L) {
                                Text(
                                    text = "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
                                    style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary),
                                )
                            }
                        }
                        EncorePlayButton(
                            isPlaying = isPlaying,
                            onClick = { if (isCurrent) onPlayPauseClick() else onPlayClick() },
                            size = 52.dp,
                            iconSize = 26.dp,
                        )
                    }

                    if (description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = description,
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            maxLines = if (descriptionExpanded) Int.MAX_VALUE else 4,
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

                    if (showName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(EncoreColors.Card)
                                .clickable(onClick = onShowClick)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.go_to_show),
                                    style = EncoreTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = EncoreColors.TextPrimary,
                                    ),
                                )
                                Text(
                                    text = showName,
                                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            androidx.compose.material3.Icon(
                                painter = painterResource(id = R.drawable.encore_icon_arrow_left_24),
                                contentDescription = null,
                                tint = EncoreColors.TextSecondary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(180f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewEncoreEpisodeScreen() {
    EncoreAppTheme {
        EncoreEpisodeScreen(
            title = "How to Be Respected",
            showName = "The Mindset Mentor",
            description = "What makes someone truly worth looking up to? In this episode I share the four " +
                "daily habits that build genuine respect.",
            imageUrl = null,
            headerColor = Color(0xFF1B4A3A),
            metaLabel = "October 28, 2026 • 16 min",
            durationMs = 964362,
            onBackClick = {},
            onPlayPauseClick = {},
        )
    }
}
