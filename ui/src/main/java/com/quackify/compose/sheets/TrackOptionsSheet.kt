package com.quackify.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreAppTheme
import com.quackify.compose.format.formatExactCount
import com.quackify.compose.theme.EncoreTheme
import androidx.compose.ui.res.stringResource

/**
 * - 112dp artwork thumbnail with 8dp rounded corners
 * - Centered title & artist metadata
 * - Quick action row: Add to queue
 * - Vertical action list: taste-profile exclude, Go to artist, Go to album, Show credits, Share
 * - Grapple dimensions: 36dp x 4dp with 10dp vertical margin
 * - Background: #242424 (bg_page_bottom_sheet_color)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsSheet(
    track: TrackItem?,
    isOpen: Boolean,
    onDismiss: () -> Unit,
    streamCount: Long? = null,
    onAddToQueue: () -> Unit = {},
    canAddToQueue: Boolean = true,
    onViewArtist: (String, String) -> Unit = { _, _ -> },
    onViewAlbum: (String, String) -> Unit = { _, _ -> },
    onShare: () -> Unit = {},
    onShowCredits: () -> Unit = {},
    canvasAvailable: Boolean = false,
    isCanvasEnabled: Boolean = false,
    onCanvasToggle: () -> Unit = {},
    canvasResolution: Int = 1080,
    onCanvasResolutionClick: () -> Unit = {},
    /** null while the taste-profile check is still loading (row hidden). */
    isTasteExcluded: Boolean? = null,
    onToggleTasteExclusion: (Boolean) -> Unit = {},
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onSongRadio: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!isOpen || track == null) return


    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = BackNotDismissSheet,
        sheetState = sheetState,
        containerColor = Color(0xFF242424),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        },
        modifier = modifier
    ) {
        BackHandler { onDismiss() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EncoreArtworkImage(
                imageUrl = track.imageUrl,
                contentDescription = track.title,
                cornerRadius = 8.dp,
                modifier = Modifier.size(112.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = track.title,
                style = EncoreTheme.typography.titleLarge.copy(
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = EncoreColors.TextPrimary
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = track.artist,
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = EncoreColors.TextSecondary
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            streamCount?.takeIf { it > 0L }?.let { plays ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatExactCount(plays) + stringResource(R.string.plays_suffix),
                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(enabled = canAddToQueue) {
                            onDismiss()
                            onAddToQueue()
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_add_to_queue_24),
                        contentDescription = stringResource(R.string.add_to_queue),
                        tint = if (canAddToQueue) EncoreColors.TextPrimary else EncoreColors.TextPrimary.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.add_to_queue),
                        style = EncoreTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = if (canAddToQueue) EncoreColors.TextSecondary else EncoreColors.TextSecondary.copy(alpha = 0.4f)
                        )
                    )
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.1f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            if (onRemoveFromPlaylist != null) {
                ContextMenuRowItem(
                    title = stringResource(R.string.remove_from_this_playlist),
                    iconRes = R.drawable.encore_icon_minus_alt_24,
                    onClick = {
                        onDismiss()
                        onRemoveFromPlaylist()
                    }
                )
            }

            if (isTasteExcluded != null) {
                ContextMenuRowItem(
                    title = if (isTasteExcluded) {
                        stringResource(R.string.include_in_taste_profile)
                    } else {
                        stringResource(R.string.exclude_from_taste_profile)
                    },
                    iconRes = R.drawable.encore_icon_tune_24,
                    onClick = {
                        onDismiss()
                        onToggleTasteExclusion(!isTasteExcluded)
                    }
                )
            }

            if (onSongRadio != null) {
                ContextMenuRowItem(
                    title = stringResource(R.string.go_to_song_radio),
                    iconRes = R.drawable.encore_icon_radio_24,
                    onClick = {
                        onDismiss()
                        onSongRadio()
                    }
                )
            }

            ContextMenuRowItem(
                title = stringResource(R.string.go_to_artist),
                iconRes = R.drawable.encore_icon_artist_24,
                onClick = {
                    onDismiss()
                    onViewArtist(track.artist, track.artistUri)
                }
            )

            ContextMenuRowItem(
                title = stringResource(R.string.go_to_album),
                iconRes = R.drawable.encore_icon_album_24,
                onClick = {
                    onDismiss()
                    onViewAlbum(track.albumName.ifBlank { track.title }, track.albumUri)
                }
            )

            ContextMenuRowItem(
                title = stringResource(R.string.view_song_credits),
                iconRes = R.drawable.encore_icon_user_24,
                onClick = {
                    onDismiss()
                    onShowCredits()
                }
            )

            if (canvasAvailable) {
                ContextMenuRowItem(
                    title = if (isCanvasEnabled) stringResource(R.string.turn_off_canvas) else stringResource(R.string.turn_on_canvas),
                    iconRes = if (isCanvasEnabled) {
                        R.drawable.encore_icon_video_24
                    } else {
                        R.drawable.encore_icon_video_outline_24
                    },
                    iconTint = if (isCanvasEnabled) EncoreColors.Green else EncoreColors.TextPrimary,
                    onClick = {
                        onDismiss()
                        onCanvasToggle()
                    }
                )
            }

            if (canvasAvailable) {
                ContextMenuRowItem(
                    title = stringResource(R.string.canvas_resolution),
                    iconRes = R.drawable.encore_icon_settings_24,
                    trailing = {
                        Text(
                            text = "${canvasResolution}p",
                            style = EncoreTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                color = EncoreColors.TextSecondary
                            )
                        )
                    },
                    onClick = {
                        onDismiss()
                        onCanvasResolutionClick()
                    }
                )
            }

            ContextMenuRowItem(
                title = stringResource(R.string.share),
                iconRes = R.drawable.encore_icon_share_android_24,
                onClick = {
                    onDismiss()
                    onShare()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ContextMenuRowItem(
    title: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = EncoreColors.TextPrimary,
    textColor: Color = EncoreColors.TextPrimary,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = EncoreTheme.typography.bodyMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            ),
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Preview
@Composable
fun PreviewTrackOptionsSheet() {
    EncoreAppTheme {
        TrackOptionsSheet(
            track = TrackItem("Starboy", "The Weeknd, Daft Punk", true),
            isOpen = true,
            onDismiss = {}
        )
    }
}
