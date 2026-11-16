package com.mwask.bat.compose.sheets

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.compose.R
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreAppTheme
import com.mwask.bat.compose.theme.EncoreTheme
import androidx.compose.ui.res.stringResource

/**
 * When opened from root library: shows stringResource(R.string.create) header + Playlist, Folder.
 * When opened from within a folder: shows Playlist only (no header).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryCreateSheet(
    isOpen: Boolean,
    onCreatePlaylist: () -> Unit,
    onCreateFolder: () -> Unit = {},
    showHeader: Boolean = true,
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
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            if (showHeader) {
                Text(
                    text = stringResource(R.string.create),
                    style = EncoreTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }

            CreateOptionItem(
                title = stringResource(R.string.playlist),
                subtitle = stringResource(R.string.create_a_playlist_with_songs_or_episodes),
                iconRes = R.drawable.encore_icon_playlist_24,
                onClick = {
                    onDismiss()
                    onCreatePlaylist()
                }
            )

            if (showHeader) {
                CreateOptionItem(
                    title = stringResource(R.string.folder),
                    subtitle = stringResource(R.string.organize_your_playlists),
                    iconRes = R.drawable.encore_icon_folder_24,
                    onClick = {
                        onDismiss()
                        onCreateFolder()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CreateOptionItem(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF383838)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = EncoreTheme.typography.bodySmall.copy(
                    color = EncoreColors.TextSecondary,
                    fontSize = 13.sp
                )
            )
        }
    }
}

@Preview
@Composable
fun PreviewLibraryCreateSheet() {
    EncoreAppTheme {
        LibraryCreateSheet(
            isOpen = true,
            onCreatePlaylist = {},
            onCreateFolder = {},
            onDismiss = {}
        )
    }
}
