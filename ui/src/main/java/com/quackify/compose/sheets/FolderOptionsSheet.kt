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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme
import androidx.compose.ui.res.stringResource

/**
 * Matches Screenshot 3:
 * - Header: Folder Icon in square + Folder Title + "X playlists"
 * - Thin divider
 * - Pin to top / Unpin from top
 * - Move to folder
 * - Rename folder
 * - Move to Your Library
 * - Delete folder
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderOptionsSheet(
    isOpen: Boolean,
    folderTitle: String,
    playlistSubtitle: String,
    isNested: Boolean,
    isPinned: Boolean = false,
    canPin: Boolean = false,
    onPinToggle: () -> Unit = {},
    onMoveToFolder: () -> Unit,
    onRenameFolder: () -> Unit,
    onMoveToLibrary: () -> Unit,
    onDeleteFolder: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = DarkSystemBarsSheet,
        sheetState = sheetState,
        containerColor = Color(0xFF282828),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.4f))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF333333)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_folder_24),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folderTitle,
                        style = EncoreTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = playlistSubtitle,
                        style = EncoreTheme.typography.bodySmall.copy(
                            color = EncoreColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 0.5.dp,
                color = Color.White.copy(alpha = 0.12f)
            )

            if (canPin) {
                FolderActionItem(
                    title = if (isPinned) stringResource(R.string.unpin_from_top) else stringResource(R.string.pin_to_top),
                    iconRes = R.drawable.encore_icon_pin_24,
                    onClick = {
                        onDismiss()
                        onPinToggle()
                    }
                )
            }

            FolderActionItem(
                title = stringResource(R.string.move_to_folder),
                iconRes = R.drawable.encore_icon_folder_24,
                onClick = {
                    onDismiss()
                    onMoveToFolder()
                }
            )

            FolderActionItem(
                title = stringResource(R.string.rename_folder),
                iconRes = R.drawable.encore_icon_edit_24,
                onClick = {
                    onDismiss()
                    onRenameFolder()
                }
            )

            if (isNested) {
                FolderActionItem(
                    title = stringResource(R.string.move_to_your_library),
                    iconRes = R.drawable.encore_icon_your_library_24,
                    onClick = {
                        onDismiss()
                        onMoveToLibrary()
                    }
                )
            }

            FolderActionItem(
                title = stringResource(R.string.delete_folder),
                iconRes = R.drawable.encore_icon_trash_24,
                onClick = {
                    onDismiss()
                    onDeleteFolder()
                }
            )
        }
    }
}

@Composable
private fun FolderActionItem(
    title: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = EncoreTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                color = Color.White,
                fontSize = 15.sp
            )
        )
    }
}
