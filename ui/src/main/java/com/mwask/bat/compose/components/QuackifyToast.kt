package com.mwask.bat.compose.components

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long a toast stays on screen before dismissing itself. */
private const val TOAST_DURATION_MS = 2400L

/** Toasts with an action stay a little longer so the button can be reached. */
private const val TOAST_ACTION_DURATION_MS = 4000L

/** How many toasts may be stacked above the player at the same time. */
private const val TOAST_MAX_STACK = 3

/** Horizontal distance the toast must be dragged before the release flings it away. */
private val TOAST_SWIPE_THRESHOLD = 72.dp

/** Gap between the message and the action button. */
private val TOAST_ACTION_SPACING = 8.dp

/** Vertical gap between stacked toasts. */
private val TOAST_STACK_SPACING = 6.dp

/** Spotify's in-app toast surface, white like the real snackbar. */
private val QuackifyToastBackground = Color(0xFFFFFFFF)

private val QuackifyToastTextColor = Color(0xFF191414)

/** Spotify renders snackbar actions in their signature green. */
private val QuackifyToastActionColor = Color(0xFF1DB954)

/** A tappable action rendered next to the toast message. */
class QuackifyToastAction(
    val label: String,
    val onClick: () -> Unit,
)

/** One stacked toast: identity, message, optional action and how many times
 * the same message was shown back to back. */
data class QuackifyToastEntry(
    val id: Long,
    val message: String,
    val action: QuackifyToastAction?,
    val count: Int,
)

/**
 * State holder for the Spotify-style in-app toasts. `show()` can be called
 * from any callback; repeated messages bump the existing toast's counter
 * ("x2") instead of stacking a twin, while new messages stack on top of the
 * player with the oldest at the very top. Once [TOAST_MAX_STACK] toasts are
 * up, the next arrival drops the top-most one even if its time has not run
 * out yet.
 */
class QuackifyToastState {
    var entries by mutableStateOf<List<QuackifyToastEntry>>(emptyList())
        private set

    private var nextId = 0L

    fun show(
        text: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        val existing = entries.firstOrNull { it.message == text }
        if (existing != null) {
            entries = entries.map { entry ->
                if (entry.id == existing.id) entry.copy(count = entry.count + 1) else entry
            }
            return
        }
        val action = if (actionLabel != null && onAction != null) {
            QuackifyToastAction(actionLabel, onAction)
        } else {
            null
        }
        val fresh = QuackifyToastEntry(nextId++, text, action, 1)
        val stacked = listOf(fresh) + entries
        entries = if (stacked.size > TOAST_MAX_STACK) {
            stacked.dropLast(stacked.size - TOAST_MAX_STACK)
        } else {
            stacked
        }
    }

    fun dismiss(id: Long) {
        entries = entries.filterNot { it.id == id }
    }

    fun clear() {
        entries = emptyList()
    }
}

@Composable
fun rememberQuackifyToastState(): QuackifyToastState = remember { QuackifyToastState() }

/**
 * Spotify-style toast stack: white bars with dark text floating above the
 * mini player. The newest toast lands closest to the player and pushes the
 * older ones up; every toast can be swiped away horizontally, dismisses
 * itself after a few seconds, and repeated messages are folded into a
 * counter on the existing toast instead of a new bar. Portrait rows span
 * the full screen width, landscape rows hug their content.
 */
@Composable
fun QuackifyToastHost(
    state: QuackifyToastState,
    modifier: Modifier = Modifier,
) {
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    LazyColumn(
        modifier = modifier,
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(TOAST_STACK_SPACING),
        horizontalAlignment = if (isLandscape) Alignment.CenterHorizontally else Alignment.Start,
        userScrollEnabled = false,
    ) {
        items(state.entries, key = { it.id }) { entry ->
            QuackifyToastRow(
                entry = entry,
                state = state,
                isLandscape = isLandscape,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun QuackifyToastRow(
    entry: QuackifyToastEntry,
    state: QuackifyToastState,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(entry.id, entry.count) {
        delay(if (entry.action != null) TOAST_ACTION_DURATION_MS else TOAST_DURATION_MS)
        state.dismiss(entry.id)
    }

    val swipeThresholdPx = with(LocalDensity.current) { TOAST_SWIPE_THRESHOLD.toPx() }
    val xOffset = remember(entry.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Row(
        modifier = modifier
            .then(
                if (isLandscape) Modifier.wrapContentWidth()
                else Modifier.fillMaxWidth()
            )
            .offset { IntOffset(xOffset.value.roundToInt(), 0) }
            .pointerInput(entry.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { xOffset.snapTo(xOffset.value + dragAmount) }
                    },
                    onDragEnd = {
                        if (abs(xOffset.value) > swipeThresholdPx) {
                            val direction = if (xOffset.value > 0f) 1f else -1f
                            scope.launch {
                                xOffset.animateTo(direction * size.width.toFloat(), tween(180))
                                state.dismiss(entry.id)
                            }
                        } else {
                            scope.launch { xOffset.animateTo(0f, spring()) }
                        }
                    },
                )
            }
            .background(
                QuackifyToastBackground,
                if (isLandscape) RoundedCornerShape(12.dp) else RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isLandscape) Arrangement.Center else Arrangement.Start,
    ) {
        Row(
            modifier = if (isLandscape) Modifier else Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.message,
                color = QuackifyToastTextColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = if (isLandscape) TextAlign.Center else TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = if (isLandscape) Modifier else Modifier.weight(1f, fill = false),
            )
            if (entry.count > 1) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "x${entry.count}",
                    color = QuackifyToastTextColor.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
        val currentAction = entry.action
        if (currentAction != null) {
            Spacer(modifier = Modifier.width(TOAST_ACTION_SPACING))
            Text(
                text = currentAction.label,
                color = QuackifyToastActionColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable {
                        currentAction.onClick()
                        state.dismiss(entry.id)
                    }
                    .padding(vertical = 2.dp),
            )
        }
    }
}
