package com.mwask.bat.compose.playlist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * - Long-press activation on drag handle ≡
 * - Real-time vertical translation with smooth target swapping
 * - Elevation z-index lift during drag
 * - Spring animation to settle into dropped position
 */
class ReorderableLazyListState(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onDragEnd: () -> Unit
) {
    var draggingItemKey by mutableStateOf<Any?>(null)
        private set

    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    val draggedDistance = Animatable(0f)

    private val scrollChannel = Channel<Float>(Channel.CONFLATED)

    init {
        scope.launch {
            for (delta in scrollChannel) {
                listState.dispatchRawDelta(delta)
            }
        }
    }

    private val currentDraggingItem: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingItemKey }

    fun onDragStart(key: Any) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggingItemKey = key
        draggingItemIndex = item.index
        scope.launch {
            draggedDistance.snapTo(0f)
        }
    }

    fun onDrag(dragAmount: Offset) {
        val current = currentDraggingItem ?: return
        scope.launch {
            draggedDistance.snapTo(draggedDistance.value + dragAmount.y)

            val currentStart = current.offset + draggedDistance.value
            val currentEnd = currentStart + current.size
            val middle = (currentStart + currentEnd) / 2f

            val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                item.key != draggingItemKey && middle >= item.offset && middle <= (item.offset + item.size)
            }

            if (targetItem != null) {
                val fromIndex = current.index
                val toIndex = targetItem.index
                if (fromIndex != toIndex) {
                    val deltaOffset = (current.offset - targetItem.offset).toFloat()
                    draggedDistance.snapTo(draggedDistance.value + deltaOffset)
                    onMove(fromIndex, toIndex)
                    draggingItemIndex = toIndex
                }
            }

            val viewportStart = listState.layoutInfo.viewportStartOffset
            val viewportEnd = listState.layoutInfo.viewportEndOffset
            val scrollThreshold = current.size * 0.75f

            if (currentStart < viewportStart + scrollThreshold) {
                scrollChannel.trySend(-(viewportStart + scrollThreshold - currentStart) * 0.2f)
            } else if (currentEnd > viewportEnd - scrollThreshold) {
                scrollChannel.trySend((currentEnd - (viewportEnd - scrollThreshold)) * 0.2f)
            }
        }
    }

    fun onDragStopped() {
        scope.launch {
            draggedDistance.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            draggingItemKey = null
            draggingItemIndex = null
            onDragEnd()
        }
    }
}

@Composable
fun rememberReorderableLazyListState(
    listState: LazyListState,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragEnd: () -> Unit = {}
): ReorderableLazyListState {
    val scope = rememberCoroutineScope()
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    return remember(listState) {
        ReorderableLazyListState(
            listState = listState,
            scope = scope,
            onMove = { from, to -> currentOnMove(from, to) },
            onDragEnd = { currentOnDragEnd() }
        )
    }
}

fun Modifier.dragHandle(
    state: ReorderableLazyListState,
    key: Any
): Modifier = this.pointerInput(key) {
    detectDragGesturesAfterLongPress(
        onDragStart = { state.onDragStart(key) },
        onDrag = { change, dragAmount ->
            change.consume()
            state.onDrag(dragAmount)
        },
        onDragEnd = { state.onDragStopped() },
        onDragCancel = { state.onDragStopped() }
    )
}
