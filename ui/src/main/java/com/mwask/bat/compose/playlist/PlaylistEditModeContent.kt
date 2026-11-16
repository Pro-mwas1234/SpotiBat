package com.mwask.bat.compose.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.compose.R
import com.mwask.bat.compose.components.EncoreArtworkImage
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreTheme
import androidx.compose.ui.res.stringResource

/**
 * - Image 1: Discard changes dialog
 * - Image 2: Initial Edit mode (←, stringResource(R.string.edit_playlist), Green stringResource(R.string.save), circular minus ⊖, artwork with play triangle, sort handle ≡, bottom stringResource(R.string.select) pill)
 * - Image 3: Edit mode with changes (Top capsule with Undo/Redo arrows)
 * - Image 4: Select mode (Checkboxes ☐/☑, "N selected" title, bottom bar with circular ✕, stringResource(R.string.move), stringResource(R.string.remove) pills)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistEditModeContent(
    initialTracks: List<TrackItem>,
    onSave: (List<TrackItem>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initialItems = remember(initialTracks) {
        initialTracks.mapIndexed { index, track ->
            EditableTrackItem(
                id = "${track.uri.ifEmpty { track.title }}_${track.trackNumber}_$index",
                track = track
            )
        }
    }

    val editedTracks = remember(initialItems) {
        mutableStateListOf<EditableTrackItem>().apply { addAll(initialItems) }
    }

    val undoStack = remember { mutableStateListOf<PlaylistEditAction>() }
    val redoStack = remember { mutableStateListOf<PlaylistEditAction>() }

    var isSelectMode by remember { mutableStateOf(false) }
    val selectedIndices = remember { mutableStateListOf<Int>() }
    var isMoveSubmenu by remember { mutableStateOf(false) }

    var showDiscardDialog by remember { mutableStateOf(false) }

    val hasChanges by remember {
        derivedStateOf {
            editedTracks.size != initialTracks.size ||
            editedTracks.map { it.track } != initialTracks
        }
    }

    val canUndo by remember { derivedStateOf { undoStack.isNotEmpty() } }
    val canRedo by remember { derivedStateOf { redoStack.isNotEmpty() } }

    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    var dragStartSnapshot by remember { mutableStateOf<List<EditableTrackItem>>(emptyList()) }

    val reorderState = rememberReorderableLazyListState(
        listState = listState,
        onMove = { fromIndex, toIndex ->
            if (fromIndex in editedTracks.indices && toIndex in editedTracks.indices) {
                if (dragStartSnapshot.isEmpty()) {
                    dragStartSnapshot = editedTracks.toList()
                }
                val item = editedTracks.removeAt(fromIndex)
                editedTracks.add(toIndex, item)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        },
        onDragEnd = {
            if (dragStartSnapshot.isNotEmpty()) {
                if (editedTracks.toList() != dragStartSnapshot) {
                    undoStack.add(PlaylistEditAction.BulkOrder(dragStartSnapshot, editedTracks.toList()))
                    redoStack.clear()
                }
                dragStartSnapshot = emptyList()
            }
        }
    )

    BackHandler {
        if (isMoveSubmenu) {
            isMoveSubmenu = false
        } else if (isSelectMode) {
            isSelectMode = false
            selectedIndices.clear()
        } else if (hasChanges) {
            showDiscardDialog = true
        } else {
            onCancel()
        }
    }

    fun applyUndo() {
        if (undoStack.isEmpty()) return
        val lastAction = undoStack.removeAt(undoStack.lastIndex)
        when (lastAction) {
            is PlaylistEditAction.Remove -> {
                val insertIndex = lastAction.index.coerceIn(0, editedTracks.size)
                editedTracks.add(insertIndex, lastAction.item)
                redoStack.add(lastAction)
            }
            is PlaylistEditAction.Move -> {
                val moved = editedTracks.removeAt(lastAction.toIndex)
                editedTracks.add(lastAction.fromIndex, moved)
                redoStack.add(lastAction)
            }
            is PlaylistEditAction.BulkOrder -> {
                redoStack.add(PlaylistEditAction.BulkOrder(lastAction.newList, lastAction.previousList))
                editedTracks.clear()
                editedTracks.addAll(lastAction.previousList)
            }
        }
    }

    fun applyRedo() {
        if (redoStack.isEmpty()) return
        val nextAction = redoStack.removeAt(redoStack.lastIndex)
        when (nextAction) {
            is PlaylistEditAction.Remove -> {
                val targetIndex = editedTracks.indexOfFirst { it.id == nextAction.item.id }
                if (targetIndex >= 0) {
                    val removed = editedTracks.removeAt(targetIndex)
                    undoStack.add(PlaylistEditAction.Remove(targetIndex, removed))
                }
            }
            is PlaylistEditAction.Move -> {
                val moved = editedTracks.removeAt(nextAction.fromIndex)
                editedTracks.add(nextAction.toIndex, moved)
                undoStack.add(nextAction)
            }
            is PlaylistEditAction.BulkOrder -> {
                undoStack.add(PlaylistEditAction.BulkOrder(previousList = nextAction.previousList, newList = nextAction.newList))
                editedTracks.clear()
                editedTracks.addAll(nextAction.newList)
            }
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusBarTop)
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        if (isMoveSubmenu) {
                            isMoveSubmenu = false
                        } else if (isSelectMode) {
                            isSelectMode = false
                            selectedIndices.clear()
                        } else if (hasChanges) {
                            showDiscardDialog = true
                        } else {
                            onCancel()
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_arrow_left_24),
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = Triple(isSelectMode, selectedIndices.size, hasChanges),
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = stringResource(R.string.topbarcenter)
                    ) { (inSelect, selCount, dirty) ->
                        if (inSelect) {
                            Text(
                                text = stringResource(R.string.selected_count, selCount),
                                style = EncoreTheme.typography.titleMedium.copy(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        } else if (dirty || canUndo || canRedo) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color(0xFF282828))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { applyUndo() },
                                    enabled = canUndo,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.encore_icon_undo_24),
                                        contentDescription = stringResource(R.string.undo),
                                        tint = if (canUndo) Color.White else Color(0xFF666666),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { applyRedo() },
                                    enabled = canRedo,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.encore_icon_undo_24),
                                        contentDescription = stringResource(R.string.redo),
                                        tint = if (canRedo) Color.White else Color(0xFF666666),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .scale(scaleX = -1f, scaleY = 1f)
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.edit_playlist),
                                style = EncoreTheme.typography.titleMedium.copy(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }

                if (!isSelectMode) {
                    Box(
                        modifier = Modifier
                            .clickable { onSave(editedTracks.map { it.track }) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            style = EncoreTheme.typography.bodyMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = EncoreColors.Green
                            )
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = if (isSelectMode) 100.dp else 80.dp)
            ) {
                itemsIndexed(
                    items = editedTracks,
                    key = { _, item -> item.id }
                ) { index, editable ->
                    val isSelected = selectedIndices.contains(index)
                    val itemKey = editable.id
                    val isDragging = reorderState.draggingItemKey == itemKey

                    val itemModifier = if (isDragging) {
                        Modifier
                            .zIndex(10f)
                            .graphicsLayer {
                                translationY = reorderState.draggedDistance.value
                                shadowElevation = 16f
                            }
                    } else {
                        Modifier.animateItem()
                    }

                    EditTrackRow(
                        track = editable.track,
                        isSelectMode = isSelectMode,
                        isSelected = isSelected,
                        itemKey = itemKey,
                        reorderState = reorderState,
                        onSelectToggle = {
                            if (isSelected) {
                                selectedIndices.remove(index)
                            } else {
                                selectedIndices.add(index)
                            }
                        },
                        onRemove = {
                            val removed = editedTracks.removeAt(index)
                            undoStack.add(PlaylistEditAction.Remove(index, removed))
                            redoStack.clear()
                        },
                        onMoveUp = {
                            if (index > 0) {
                                val item = editedTracks.removeAt(index)
                                editedTracks.add(index - 1, item)
                                undoStack.add(PlaylistEditAction.Move(index, index - 1))
                                redoStack.clear()
                            }
                        },
                        onMoveDown = {
                            if (index < editedTracks.lastIndex) {
                                val item = editedTracks.removeAt(index)
                                editedTracks.add(index + 1, item)
                                undoStack.add(PlaylistEditAction.Move(index, index + 1))
                                redoStack.clear()
                            }
                        },
                        modifier = itemModifier
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            if (!isSelectMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF282828))
                        .clickable {
                            isSelectMode = true
                            isMoveSubmenu = false
                            selectedIndices.clear()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.select),
                        style = EncoreTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            } else if (!isMoveSubmenu) {
                val hasSelection = selectedIndices.isNotEmpty()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF282828))
                            .clickable {
                                isSelectMode = false
                                isMoveSubmenu = false
                                selectedIndices.clear()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_x_24),
                            contentDescription = stringResource(R.string.exit_select_mode),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF282828))
                            .clickable(enabled = hasSelection) {
                                isMoveSubmenu = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.move),
                            style = EncoreTheme.typography.bodyMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasSelection) Color.White else Color(0xFF666666)
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF282828))
                            .clickable(enabled = hasSelection) {
                                val selectedItems = selectedIndices.sortedDescending().map { editedTracks[it] }
                                val previousList = editedTracks.toList()
                                editedTracks.removeAll(selectedItems)
                                undoStack.add(PlaylistEditAction.BulkOrder(previousList, editedTracks.toList()))
                                redoStack.clear()
                                selectedIndices.clear()
                                isSelectMode = false
                                isMoveSubmenu = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.remove),
                            style = EncoreTheme.typography.bodyMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasSelection) Color.White else Color(0xFF666666)
                            )
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF282828))
                            .clickable {
                                isMoveSubmenu = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_arrow_left_24),
                            contentDescription = stringResource(R.string.back_to_actions),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF282828))
                            .clickable {
                                val selectedItems = selectedIndices.sorted().map { editedTracks[it] }
                                val previousList = editedTracks.toList()
                                editedTracks.removeAll(selectedItems)
                                editedTracks.addAll(0, selectedItems)
                                undoStack.add(PlaylistEditAction.BulkOrder(previousList, editedTracks.toList()))
                                redoStack.clear()
                                selectedIndices.clear()
                                isSelectMode = false
                                isMoveSubmenu = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.to_top),
                            style = EncoreTheme.typography.bodyMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF282828))
                            .clickable {
                                val selectedItems = selectedIndices.sorted().map { editedTracks[it] }
                                val previousList = editedTracks.toList()
                                editedTracks.removeAll(selectedItems)
                                editedTracks.addAll(selectedItems)
                                undoStack.add(PlaylistEditAction.BulkOrder(previousList, editedTracks.toList()))
                                redoStack.clear()
                                selectedIndices.clear()
                                isSelectMode = false
                                isMoveSubmenu = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.to_bottom),
                            style = EncoreTheme.typography.bodyMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }
        }

        DiscardChangesDialog(
            isOpen = showDiscardDialog,
            onKeepEditing = { showDiscardDialog = false },
            onDiscard = {
                showDiscardDialog = false
                onCancel()
            }
        )
    }
}

/**
 * Single Edit Mode Track Row:
 * - Left: Either Subdued Circular Minus ⊖ (Image 2/3) OR Square Checkbox ☐/☑ (Image 4)
 * - Album artwork: 48dp with centered semi-transparent circle containing play preview triangle ▶
 * - Center: Title (white bold) + Artist (secondary text)
 * - Right: 3 horizontal bars drag handle ≡
 */
@Composable
private fun EditTrackRow(
    track: TrackItem,
    isSelectMode: Boolean,
    isSelected: Boolean,
    itemKey: Any,
    reorderState: ReorderableLazyListState,
    onSelectToggle: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(enabled = isSelectMode, onClick = onSelectToggle)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isSelectMode) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_minus_alt_24),
                    contentDescription = stringResource(R.string.remove_track),
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onSelectToggle),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .border(
                            width = 2.dp,
                            color = if (isSelected) EncoreColors.Green else Color(0xFF666666),
                            shape = RoundedCornerShape(3.dp)
                        )
                        .background(if (isSelected) EncoreColors.Green else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_check_24),
                            contentDescription = stringResource(R.string.selected),
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF282828)),
            contentAlignment = Alignment.Center
        ) {
            EncoreArtworkImage(
                imageUrl = track.imageUrl,
                contentDescription = track.title,
                cornerRadius = 4.dp,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_play_16),
                    contentDescription = stringResource(R.string.play_preview),
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = track.title,
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = track.artist,
                style = EncoreTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    color = EncoreColors.TextSecondary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .dragHandle(state = reorderState, key = itemKey),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.encore_icon_sort_24),
                contentDescription = stringResource(R.string.drag_to_reorder),
                tint = Color(0xFF888888),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
