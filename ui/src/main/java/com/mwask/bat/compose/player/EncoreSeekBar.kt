package com.mwask.bat.compose.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val SEEK_HOLD_MS = 2000L

/**
 * - `seekbar_progress_default.xml` (3dp track thickness, 1.5dp corner radius, 30% white background)
 * - `seekbar_thumb_default.xml` (32dp touch target, 4dp idle thumb dot, 12dp active thumb dot)
 * - Scrubbing time preview tooltip floating directly above the thumb during user drag
 */
@Composable
fun EncoreSeekBar(
    progress: Float, // 0.0f to 1.0f
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.30f),
    bufferProgress: Float = 0f,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    markers: List<Float> = emptyList()
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(progress) }
    var seekingTo by remember { mutableStateOf<Float?>(null) }
    val currentSeek by rememberUpdatedState(onSeek)

    LaunchedEffect(progress) {
        if (!isDragging) dragProgress = progress
        val pending = seekingTo
        if (pending != null && kotlin.math.abs(progress - pending) < 0.01f) seekingTo = null
    }

    LaunchedEffect(seekingTo) {
        if (seekingTo == null) return@LaunchedEffect
        delay(SEEK_HOLD_MS)
        seekingTo = null
    }

    fun commit(target: Float) {
        seekingTo = target
        currentSeek(target)
    }

    val smoothProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 250, easing = LinearEasing),
        label = "smoothProgress"
    )
    val displayProgress = when {
        isDragging -> dragProgress
        seekingTo != null -> seekingTo!!
        else -> smoothProgress
    }

    val thumbDotSize by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 8.dp,
        animationSpec = spring(stiffness = 600f),
        label = "thumbDotSize"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp), // 24dp touch area ensuring 10dp vertical alignment to timestamps
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val thumbOffsetPx = (displayProgress * widthPx).coerceIn(0f, widthPx)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .then(
                    if (enabled) Modifier.pointerInput(widthPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isDragging = true
                            dragProgress = (down.position.x / widthPx).coerceIn(0f, 1f)
                            var released = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.changedToUpIgnoreConsumed()) {
                                    change.consume()
                                    released = true
                                    break
                                }
                                if (change.pressed) {
                                    change.consume()
                                    dragProgress = (change.position.x / widthPx).coerceIn(0f, 1f)
                                }
                            }
                            isDragging = false
                            if (released) commit(dragProgress)
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isLoading) {
                val infiniteTransition = rememberInfiniteTransition(label = "seekbarLoadingTransition")
                val pulsePhase by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "pulsePhase"
                )
                val remainingTrack = (widthPx - thumbOffsetPx).coerceAtLeast(1f)
                val waveLengthPx = (remainingTrack * 0.40f).coerceIn(60f, 240f)
                val waveCenterPx = thumbOffsetPx + (remainingTrack + waveLengthPx) * pulsePhase - (waveLengthPx / 2f)

                val pulseBrush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to inactiveColor,
                        ((waveCenterPx - waveLengthPx) / widthPx).coerceIn(0f, 1f) to inactiveColor,
                        (waveCenterPx / widthPx).coerceIn(0f, 1f) to Color.White.copy(alpha = 0.95f),
                        ((waveCenterPx + waveLengthPx) / widthPx).coerceIn(0f, 1f) to inactiveColor,
                        1.0f to inactiveColor
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(pulseBrush)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(inactiveColor)
                )
            }

            if (bufferProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferProgress.coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.50f))
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth(displayProgress)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isDragging) EncoreColors.Green else activeColor)
            )

            markers.forEach { marker ->
                val fraction = marker.coerceIn(0f, 1f)
                if (fraction <= 0f) return@forEach
                Box(
                    modifier = Modifier
                        .offset { IntOffset(x = (fraction * widthPx).roundToInt(), y = 0) }
                        .width(2.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                )
            }

            val thumbOffsetPx = (displayProgress * widthPx).coerceIn(0f, widthPx)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (thumbOffsetPx - with(density) { 16.dp.toPx() }).roundToInt(),
                            y = 0
                        )
                    }
                    .size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(thumbDotSize)
                        .shadow(
                            elevation = if (isDragging) 4.dp else 0.dp,
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .background(if (isDragging) Color.White else activeColor)
                )
            }
        }

        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    val tooltipX = (displayProgress * widthPx) - with(density) { 24.dp.toPx() }
                    IntOffset(
                        x = tooltipX.coerceIn(0f, widthPx - with(density) { 48.dp.toPx() }).roundToInt(),
                        y = with(density) { (-30).dp.roundToPx() }
                    )
                }
        ) {
            val previewMs = (displayProgress * durationMs).toLong()
            val totalSeconds = (previewMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val formatted = String.format("%d:%02d", minutes, seconds)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF282828))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatted,
                    style = EncoreTheme.typography.marginal.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}
