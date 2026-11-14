package com.quackify.compose.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.quackify.compose.R
import com.quackify.compose.theme.EncoreColors
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

/** How far the row must be pulled to the right before releasing fires the action. */
private val SwipeTriggerDistance = 72.dp

/** Hard stop for the drag so a long swipe cannot run away. */
private val SwipeMaxDistance = 144.dp

/**
 * Spotify's swipe-right-on-a-track gesture: dragging the row to the right
 * reveals a green surface with the add-to-queue icon; releasing past the
 * threshold fires [onAddToQueue] (with a haptic tick) and the row springs back.
 *
 * When [onAddToQueue] is null the gesture is not installed at all and the
 * content is laid out untouched, so screens without a queue keep their exact
 * current behaviour.
 */
@Composable
fun SwipeToQueueBox(
    onAddToQueue: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (onAddToQueue == null) {
        Box(modifier = modifier) { content() }
        return
    }

    val density = LocalDensity.current
    val triggerPx = with(density) { SwipeTriggerDistance.toPx() }
    val maxPx = with(density) { SwipeMaxDistance.toPx() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        val next = (offset.value + amount).coerceIn(0f, maxPx)
                        scope.launch { offset.snapTo(next) }
                    },
                    onDragEnd = {
                        if (offset.value >= triggerPx) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAddToQueue()
                        }
                        scope.launch {
                            offset.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                            )
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offset.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                            )
                        }
                    },
                )
            },
    ) {
        // Green reveal that fades in under the row as it slides right.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = (offset.value / triggerPx).coerceIn(0f, 1f)
                }
                .background(EncoreColors.Green),
        )
        Icon(
            painter = painterResource(R.drawable.encore_icon_add_to_queue_24),
            contentDescription = stringResource(R.string.add_to_queue),
            tint = Color.Black,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
                .size(24.dp)
                .graphicsLayer {
                    alpha = (offset.value / triggerPx).coerceIn(0f, 1f)
                },
        )
        Box(
            modifier = Modifier.graphicsLayer { translationX = offset.value },
        ) {
            content()
        }
    }
}
