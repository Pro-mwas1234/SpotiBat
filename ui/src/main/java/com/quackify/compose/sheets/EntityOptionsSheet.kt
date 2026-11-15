package com.quackify.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme
import androidx.compose.ui.res.stringResource

data class EntityOptions(
    val title: String,
    val subtitle: String = "",
    val imageUrl: String? = null,
    val uri: String = "",
    val pinned: Boolean = false,
    val pinnable: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityOptionsSheet(
    entity: EntityOptions?,
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onCopyLink: () -> Unit = {},
    onShare: () -> Unit = {},
    canEditPlaylist: Boolean = false,
    onEditPlaylist: () -> Unit = {},
    canPin: Boolean = false,
    isPinned: Boolean = false,
    onPinToggle: () -> Unit = {},
    isSaved: Boolean? = null,
    onSaveToggle: () -> Unit = {},
    canMoveToFolder: Boolean = false,
    onMoveToFolder: () -> Unit = {},
    onAddToPlaylist: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    canGoToRadio: Boolean = false,
    onGoToRadio: () -> Unit = {},
    canDeletePlaylist: Boolean = false,
    onDeletePlaylist: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!isOpen || entity == null) return

    val isEpisode = entity.uri.startsWith("spotify:episode:")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = DarkSystemBarsSheet,
        sheetState = sheetState,
        containerColor = EncoreColors.Card,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(EncoreColors.TextSecondary.copy(alpha = 0.5f))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EncoreArtworkImage(
                    imageUrl = entity.imageUrl,
                    contentDescription = entity.title,
                    modifier = Modifier.size(56.dp),
                    cornerRadius = 6.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entity.title,
                        style = EncoreTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entity.subtitle.isNotBlank()) {
                        Text(
                            text = entity.subtitle,
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(
                color = EncoreColors.PureBlack.copy(alpha = 0.5f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            if (canEditPlaylist) {
                EntityRow(
                    title = stringResource(R.string.edit_details),
                    iconRes = R.drawable.encore_icon_edit_24,
                    onClick = {
                        onDismiss()
                        onEditPlaylist()
                    }
                )
            }

            if (isEpisode && onAddToPlaylist != null) {
                AddToPlaylistRow {
                    onDismiss()
                    onAddToPlaylist()
                }
            }

            if (canPin) {
                EntityRow(
                    title = if (isPinned) stringResource(R.string.unpin_from_top) else stringResource(R.string.pin_to_top),
                    iconRes = R.drawable.encore_icon_pin_24,
                    onClick = {
                        onDismiss()
                        onPinToggle()
                    }
                )
            }

            if (isSaved != null) {
                EntityRow(
                    title = when {
                        isEpisode && isSaved -> stringResource(R.string.remove_from_your_episodes)
                        isEpisode -> stringResource(R.string.add_to_your_episodes)
                        isSaved -> stringResource(R.string.remove_from_your_library)
                        else -> stringResource(R.string.add_to_your_library)
                    },
                    iconRes = when {
                        isSaved -> R.drawable.encore_icon_trash_24
                        else -> R.drawable.encore_icon_plus_24
                    },
                    onClick = {
                        onDismiss()
                        onSaveToggle()
                    }
                )
            }

            if (canMoveToFolder) {
                EntityRow(
                    title = stringResource(R.string.move_to_folder),
                    iconRes = R.drawable.encore_icon_folder_24,
                    onClick = {
                        onDismiss()
                        onMoveToFolder()
                    }
                )
            }

            if (onAddToQueue != null) {
                EntityRow(
                    title = stringResource(R.string.add_to_queue),
                    iconRes = R.drawable.encore_icon_add_to_queue_24,
                    onClick = {
                        onDismiss()
                        onAddToQueue()
                    }
                )
            }

            if (canGoToRadio) {
                EntityRow(
                    title = if (entity.uri.startsWith("spotify:artist:")) stringResource(R.string.go_to_artist_radio) else stringResource(R.string.go_to_playlist_radio),
                    iconRes = R.drawable.encore_icon_radio_24,
                    onClick = {
                        onDismiss()
                        onGoToRadio()
                    }
                )
            }

            if (!isEpisode && onAddToPlaylist != null) {
                AddToPlaylistRow {
                    onDismiss()
                    onAddToPlaylist()
                }
            }

            EntityRow(
                title = stringResource(R.string.copy_link),
                iconRes = R.drawable.encore_icon_link_24,
                onClick = {
                    onDismiss()
                    onCopyLink()
                }
            )

            EntityRow(
                title = stringResource(R.string.share),
                iconRes = R.drawable.encore_icon_share_android_24,
                onClick = {
                    onDismiss()
                    onShare()
                }
            )

            if (canDeletePlaylist) {
                HorizontalDivider(
                    color = EncoreColors.PureBlack.copy(alpha = 0.5f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                EntityRow(
                    title = stringResource(R.string.delete_playlist),
                    iconRes = R.drawable.encore_icon_delete_24,
                    onClick = {
                        onDismiss()
                        onDeletePlaylist()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AddToPlaylistRow(onClick: () -> Unit) {
    EntityRow(
        title = stringResource(R.string.add_to_playlist),
        iconRes = R.drawable.encore_icon_add_to_playlist_24,
        onClick = onClick
    )
}

@Composable
private fun EntityRow(
    title: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            tint = EncoreColors.TextPrimary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = EncoreTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )
    }
}
