package com.quackify.compose.sheets

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme

/**
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumOptionsSheet(
    title: String,
    artist: String,
    coverUrl: String?,
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onAddToLibrary: () -> Unit = {},
    onDownload: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onAddToPlaylist: (() -> Unit)? = null,
    onAlbumRadio: (() -> Unit)? = null,
    onGoToArtist: (String) -> Unit = {},
    onShare: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!isOpen) return


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
                imageUrl = coverUrl,
                contentDescription = title,
                cornerRadius = 8.dp,
                modifier = Modifier.size(112.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
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
                text = "Album • $artist",
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = EncoreColors.TextSecondary
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.1f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            ContextMenuRowItem(
                title = "Add to Your Library",
                iconRes = R.drawable.encore_icon_plus_24,
                onClick = {
                    onDismiss()
                    onAddToLibrary()
                }
            )

            ContextMenuRowItem(
                title = "Download",
                iconRes = R.drawable.encore_icon_download_24,
                onClick = {
                    onDismiss()
                    onDownload()
                }
            )

            ContextMenuRowItem(
                title = "Add to queue",
                iconRes = R.drawable.encore_icon_add_to_queue_24,
                onClick = {
                    onDismiss()
                    onAddToQueue()
                }
            )

            if (onAddToPlaylist != null) {
                ContextMenuRowItem(
                    title = "Add to playlist",
                    iconRes = R.drawable.encore_icon_add_to_playlist_24,
                    onClick = {
                        onDismiss()
                        onAddToPlaylist()
                    }
                )
            }

            if (onAlbumRadio != null) {
                ContextMenuRowItem(
                    title = "Go to album radio",
                    iconRes = R.drawable.encore_icon_radio_24,
                    onClick = {
                        onDismiss()
                        onAlbumRadio()
                    }
                )
            }

            ContextMenuRowItem(
                title = "Go to artist",
                iconRes = R.drawable.encore_icon_artist_24,
                onClick = {
                    onDismiss()
                    onGoToArtist(artist)
                }
            )

            ContextMenuRowItem(
                title = "Share",
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

/**
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistOptionsSheet(
    artistName: String,
    avatarUrl: String?,
    isOpen: Boolean,
    onDismiss: () -> Unit,
    isFollowing: Boolean = false,
    onFollowToggle: () -> Unit = {},
    onGoToRadio: () -> Unit = {},
    onDontPlayArtist: () -> Unit = {},
    onShare: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    BackHandler { onDismiss() }

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
                imageUrl = avatarUrl,
                contentDescription = artistName,
                shape = CircleShape,
                modifier = Modifier.size(112.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = artistName,
                style = EncoreTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
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
                text = "Artist",
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = EncoreColors.TextSecondary
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.1f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            ContextMenuRowItem(
                title = if (isFollowing) "Following" else "Follow",
                iconRes = if (isFollowing) R.drawable.encore_icon_check_24 else R.drawable.encore_icon_plus_24,
                iconTint = if (isFollowing) EncoreColors.Green else EncoreColors.TextPrimary,
                onClick = {
                    onDismiss()
                    onFollowToggle()
                }
            )

            ContextMenuRowItem(
                title = "Don't play this artist",
                iconRes = R.drawable.encore_icon_ban_24,
                onClick = {
                    onDismiss()
                    onDontPlayArtist()
                }
            )

            ContextMenuRowItem(
                title = "Go to artist radio",
                iconRes = R.drawable.encore_icon_radio_24,
                onClick = {
                    onDismiss()
                    onGoToRadio()
                }
            )

            ContextMenuRowItem(
                title = "Share",
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
