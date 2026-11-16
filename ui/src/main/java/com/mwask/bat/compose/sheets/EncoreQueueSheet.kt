package com.mwask.bat.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mwask.bat.compose.R
import com.mwask.bat.compose.components.EncoreExplicitTag
import com.mwask.bat.compose.components.EncoreIconButton
import com.mwask.bat.compose.components.EncorePlayIndicator
import com.mwask.bat.compose.components.EncoreArtworkImage
import com.mwask.bat.compose.playlist.EditableTrackItem
import com.mwask.bat.compose.playlist.TrackItem
import com.mwask.bat.compose.playlist.dragHandle
import com.mwask.bat.compose.playlist.rememberReorderableLazyListState
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreCornerRadii
import com.mwask.bat.compose.theme.EncoreSpacers
import com.mwask.bat.compose.theme.EncoreAppTheme
import com.mwask.bat.compose.theme.EncoreTheme
import androidx.compose.ui.res.stringResource

/**
 * - stringResource(R.string.now_playing) item with animated Encore equalizer indicator
 * - stringResource(R.string.next_in_queue) with dismiss action and reordering handles
 * - "Next from: [Context]" with upcoming queue items
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncoreQueueSheet(
    currentTrackTitle: String,
    currentTrackArtist: String,
    currentTrackCoverUrl: String? = null,
    queueSourceContext: String, // e.g. "Today's Top Hits"
    userQueue: List<TrackItem>,
    nextFromContext: List<TrackItem>,
    onDismissRequest: () -> Unit,
    onTrackSelect: (TrackItem) -> Unit,
    onRemoveFromQueue: (TrackItem) -> Unit = {},
    onClearQueue: () -> Unit = {},
    onReorderQueue: (List<TrackItem>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val queueRows = remember(userQueue) { userQueue.toQueueRows() }
    val currentOnReorder by rememberUpdatedState(onReorderQueue)
    val reorderState = rememberReorderableLazyListState(
        listState = listState,
        onMove = { fromIndex, toIndex ->
            val visible = listState.layoutInfo.visibleItemsInfo
            val fromId = visible.firstOrNull { it.index == fromIndex }?.key
            val toId = visible.firstOrNull { it.index == toIndex }?.key
            val from = queueRows.indexOfFirst { it.id == fromId }
            val to = queueRows.indexOfFirst { it.id == toId }
            if (from >= 0 && to >= 0 && from != to) {
                queueRows.add(to, queueRows.removeAt(from))
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        },
        onDragEnd = {
            val tracks = queueRows.map { it.track }
            if (tracks != userQueue) currentOnReorder(tracks)
        }
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        properties = BackNotDismissSheet,
        sheetState = sheetState,
        containerColor = EncoreColors.Background,
        contentColor = EncoreColors.TextPrimary,
        shape = RoundedCornerShape(topStart = EncoreCornerRadii.Larger3, topEnd = EncoreCornerRadii.Larger3),
        dragHandle = null,
        modifier = modifier
    ) {
        BackHandler { onDismissRequest() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EncoreIconButton(
                    iconRes = R.drawable.encore_icon_chevron_down_24,
                    contentDescription = stringResource(R.string.collapse_queue),
                    onClick = onDismissRequest,
                    size = 40.dp,
                    iconSize = 24.dp,
                    tint = EncoreColors.TextPrimary
                )

                Text(
                    text = stringResource(R.string.queue),
                    style = EncoreTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                if (userQueue.isNotEmpty()) {
                    TextButton(onClick = onClearQueue) {
                        Text(
                            text = stringResource(R.string.clear_queue),
                            style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.Green)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.now_playing),
                        style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.TextSecondary),
                        modifier = Modifier.padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter2),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EncoreArtworkImage(
                            imageUrl = currentTrackCoverUrl,
                            contentDescription = currentTrackTitle,
                            modifier = Modifier.size(48.dp),
                            cornerRadius = EncoreCornerRadii.Base
                        )

                        Spacer(modifier = Modifier.width(EncoreSpacers.Tighter))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTrackTitle,
                                style = EncoreTheme.typography.bodyMediumBold.copy(color = EncoreColors.Green),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentTrackArtist,
                                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        EncorePlayIndicator(
                            isPlaying = true,
                            color = EncoreColors.Green,
                            size = 14.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(EncoreSpacers.Base))
                }

                if (queueRows.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.next_in_queue),
                            style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier.padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter)
                        )
                    }

                    itemsIndexed(queueRows, key = { _, row -> row.id }) { _, row ->
                        val track = row.track
                        val isDragging = reorderState.draggingItemKey == row.id
                        QueueTrackRow(
                            track = track,
                            showDragHandle = true,
                            onSelect = { onTrackSelect(track) },
                            onRemove = { onRemoveFromQueue(track) },
                            handleModifier = Modifier.dragHandle(state = reorderState, key = row.id),
                            modifier = if (isDragging) {
                                Modifier
                                    .zIndex(10f)
                                    .graphicsLayer {
                                        translationY = reorderState.draggedDistance.value
                                        shadowElevation = 16f
                                    }
                            } else {
                                Modifier.animateItem()
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(EncoreSpacers.Base))
                    }
                }

                if (nextFromContext.isNotEmpty()) {
                    item {
                        Text(
                            text = if (queueSourceContext.isBlank()) {
                                stringResource(R.string.next_up)
                            } else {
                                "Next from: $queueSourceContext"
                            },
                            style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.TextSecondary),
                            modifier = Modifier.padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter)
                        )
                    }

                    itemsIndexed(nextFromContext) { _, track ->
                        QueueTrackRow(
                            track = track,
                            showDragHandle = false,
                            onSelect = { onTrackSelect(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    track: TrackItem,
    showDragHandle: Boolean,
    onSelect: () -> Unit,
    onRemove: (() -> Unit)? = null,
    handleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val isBanned = com.mwask.bat.compose.bans.ArtistBans.isBanned(track.artistUri)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isBanned, onClick = onSelect)
            .padding(horizontal = EncoreSpacers.Base, vertical = EncoreSpacers.Tighter2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EncoreArtworkImage(
            imageUrl = track.imageUrl,
            contentDescription = track.title,
            modifier = Modifier.size(44.dp),
            cornerRadius = EncoreCornerRadii.Base
        )

        Spacer(modifier = Modifier.width(EncoreSpacers.Tighter))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = EncoreTheme.typography.bodyMediumBold.copy(
                    color = if (isBanned) EncoreColors.TextSecondary.copy(alpha = 0.55f)
                    else EncoreColors.TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isExplicit) {
                    EncoreExplicitTag(modifier = Modifier.padding(end = 4.dp))
                }
                if (track.isEnhanced) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_enhance_badge_16),
                        contentDescription = stringResource(R.string.smart_shuffle_recommendation),
                        tint = EncoreColors.Green,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(12.dp)
                    )
                }
                Text(
                    text = track.artist,
                    style = EncoreTheme.typography.bodySmall.copy(
                        color = EncoreColors.TextSecondary.takeIf { !isBanned }
                            ?: EncoreColors.TextSecondary.copy(alpha = 0.55f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onRemove != null) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_x_24,
                contentDescription = stringResource(R.string.remove_from_queue),
                onClick = onRemove,
                size = 36.dp,
                iconSize = 18.dp,
                tint = EncoreColors.TextSecondary
            )
        }

        if (showDragHandle) {
            Box(
                modifier = handleModifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_sort_24),
                    contentDescription = stringResource(R.string.reorder),
                    tint = EncoreColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun List<TrackItem>.toQueueRows(): MutableList<EditableTrackItem> {
    val seen = HashMap<String, Int>()
    val rows = map { track ->
        val base = track.uri.ifBlank { track.title }
        val occurrence = seen.getOrDefault(base, 0)
        seen[base] = occurrence + 1
        EditableTrackItem("${base}_$occurrence", track)
    }
    return mutableStateListOf<EditableTrackItem>().apply { addAll(rows) }
}

@Preview
@Composable
fun PreviewEncoreQueueSheet() {
    EncoreAppTheme {
        EncoreQueueSheet(
            currentTrackTitle = "Starboy",
            currentTrackArtist = "The Weeknd, Daft Punk",
            queueSourceContext = "Today's Top Hits",
            userQueue = listOf(
                TrackItem("Blinding Lights", "The Weeknd"),
                TrackItem("Save Your Tears", "The Weeknd")
            ),
            nextFromContext = listOf(
                TrackItem("Espresso", "Sabrina Carpenter", true),
                TrackItem("Birds of a Feather", "Billie Eilish"),
                TrackItem("Good Luck, Babe!", "Chappell Roan")
            ),
            onDismissRequest = {},
            onTrackSelect = {}
        )
    }
}
