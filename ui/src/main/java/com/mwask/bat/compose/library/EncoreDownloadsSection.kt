package com.mwask.bat.compose.library

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.backend.core.offline.DownloadPhase
import com.mwask.bat.backend.core.offline.DownloadStatus
import com.mwask.bat.compose.R
import com.mwask.bat.compose.components.EncoreArtworkImage
import com.mwask.bat.compose.components.EncoreIconButton
import com.mwask.bat.compose.components.EncorePlayIndicator
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreTheme

/** One song kept on the device, listed in the library's offline section. */
data class DownloadedTrack(
    val uri: String,
    val title: String,
    val artists: String,
    val album: String,
    val imageUrl: String,
    val durationMs: Long,
    val bytes: Long,
    val offlineReady: Boolean,
    /** Live progress while the song is still being fetched, null once nothing is running. */
    val status: DownloadStatus? = null,
)

/**
 * Offline section of the library: everything the user downloaded, newest first, with the
 * space it takes and whether it can start with no connection (a licence is needed for a
 * track that Spotify serves encrypted).
 */
@Composable
fun EncoreDownloadsSection(
    tracks: List<DownloadedTrack>,
    onPlay: (DownloadedTrack) -> Unit = {},
    onRemove: (DownloadedTrack) -> Unit = {},
    onDeleteAll: (() -> Unit)? = null,
    /** Uri of the song in the player, so its row can be marked as the one playing. */
    currentUri: String = "",
    isPlaying: Boolean = false,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    var confirmDeleteAll by remember { mutableStateOf(false) }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.delete_all_downloads)) },
            text = { Text(stringResource(R.string.delete_all_downloads_message, tracks.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteAll = false
                        onDeleteAll?.invoke()
                    },
                ) {
                    Text(stringResource(R.string.delete), color = EncoreColors.Green)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) {
                    Text(stringResource(R.string.cancel), color = EncoreColors.TextSecondary)
                }
            },
            containerColor = EncoreColors.Card,
            titleContentColor = EncoreColors.TextPrimary,
            textContentColor = EncoreColors.TextSecondary,
        )
    }

    if (tracks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.no_downloads_yet),
                    style = EncoreTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = EncoreColors.TextPrimary,
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.downloads_hint),
                    style = EncoreTheme.typography.bodyMedium.copy(
                        color = EncoreColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                )
            }
        }
        return
    }

    val readyBytes = tracks.filter { it.offlineReady }.sumOf { it.bytes }
    val totalBytes = tracks.sumOf { it.bytes }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
    ) {
        item(key = "downloads_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_downloaded_24),
                    contentDescription = null,
                    tint = EncoreColors.Green,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        R.string.downloads_summary,
                        tracks.size,
                        formatSize(totalBytes),
                    ),
                    style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.TextSecondary),
                )
                if (readyBytes < totalBytes) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.some_downloads_need_a_connection),
                        style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onDeleteAll != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.delete_all_downloads),
                        style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.Green),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { confirmDeleteAll = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        items(tracks, key = { it.uri }) { track ->
            DownloadRow(
                track = track,
                isCurrent = currentUri.isNotBlank() && track.uri == currentUri,
                isPlaying = isPlaying,
                onPlay = { onPlay(track) },
                onRemove = { onRemove(track) },
            )
        }
    }
}

@Composable
private fun DownloadRow(
    track: DownloadedTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    val status = track.status?.takeIf { it.working }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) EncoreColors.Green.copy(alpha = 0.10f) else Color.Transparent,
            )
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(4.dp)),
        ) {
            EncoreArtworkImage(
                imageUrl = track.imageUrl.ifBlank { null },
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 4.dp,
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    EncorePlayIndicator(
                        isPlaying = isPlaying,
                        color = EncoreColors.Green,
                        size = 16.dp,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title.ifBlank { track.uri },
                style = EncoreTheme.typography.bodyMedium.copy(
                    color = if (isCurrent) EncoreColors.Green else EncoreColors.TextPrimary,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(track.artists, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (status != null) {
                Spacer(modifier = Modifier.height(6.dp))
                val barValue = status.barValue
                if (barValue != null) {
                    LinearProgressIndicator(
                        progress = { barValue },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = EncoreColors.Green,
                        trackColor = EncoreColors.ProgressBarBackground,
                    )
                } else {
                    // Queued, or the size is not known yet: spin instead of pretending 0%.
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = EncoreColors.Green,
                        trackColor = EncoreColors.ProgressBarBackground,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        status.phase == DownloadPhase.QUEUED -> stringResource(R.string.queued)
                        barValue != null -> stringResource(
                            R.string.downloading_percent,
                            (barValue * 100).toInt(),
                        )
                        else -> stringResource(R.string.downloading)
                    },
                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.Green),
                )
            } else {
                val failed = track.status?.phase == DownloadPhase.FAILED
                Text(
                    text = when {
                        track.offlineReady ->
                            stringResource(R.string.offline_ready) + " · " + formatSize(track.bytes)
                        failed -> listOfNotNull(
                            stringResource(R.string.download_failed),
                            track.status?.error,
                        ).joinToString(" · ")
                        else ->
                            stringResource(R.string.needs_a_connection) + " · " + formatSize(track.bytes)
                    },
                    style = EncoreTheme.typography.bodySmall.copy(
                        color = when {
                            track.offlineReady -> EncoreColors.Green
                            failed -> EncoreColors.TextSecondary
                            else -> EncoreColors.TextSecondary
                        },
                    ),
                )
            }
        }
        EncoreIconButton(
            iconRes = R.drawable.encore_icon_trash_24,
            contentDescription = stringResource(R.string.remove_download),
            onClick = onRemove,
            size = 40.dp,
            iconSize = 20.dp,
            tint = EncoreColors.TextSecondary,
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes < 1024L * 1024L -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
