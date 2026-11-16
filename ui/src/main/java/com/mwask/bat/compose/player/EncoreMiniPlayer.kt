package com.mwask.bat.compose.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mwask.bat.backend.model.DeviceKind
import com.mwask.bat.compose.R
import com.mwask.bat.compose.components.EncoreHeartButton
import com.mwask.bat.compose.components.EncoreIconButton
import com.mwask.bat.compose.components.rememberSmoothColor
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreControlSizes
import com.mwask.bat.compose.theme.EncoreCornerRadii
import com.mwask.bat.compose.theme.EncoreSpacers
import com.mwask.bat.compose.theme.EncoreAppTheme
import com.mwask.bat.compose.theme.EncoreTheme
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import androidx.compose.ui.res.stringResource

private val MINI_PLAYER_BASE = Color(0xFF282828)
private const val MINI_PLAYER_TINT_STRENGTH = 0.22f
private const val MINI_PLAYER_MAX_LUMINANCE = 0.30f
private const val MINI_PLAYER_TINT_DURATION = 650

private fun miniPlayerTint(cover: Color): Color {
    var tint = lerp(MINI_PLAYER_BASE, cover, MINI_PLAYER_TINT_STRENGTH)
    var guard = 0
    while (tint.luminance() > MINI_PLAYER_MAX_LUMINANCE && guard < 8) {
        tint = lerp(tint, MINI_PLAYER_BASE, 0.18f)
        guard++
    }
    return tint
}

/**
 * Supports swiping the track title/artist (CarouselView) to skip tracks with real-time translation and spring physics.
 */
private fun resolveDeviceIcon(kind: DeviceKind?): Int = when (kind) {
    DeviceKind.COMPUTER -> R.drawable.encore_icon_device_computer_24
    DeviceKind.SPEAKER -> R.drawable.encore_icon_device_speaker_24
    DeviceKind.TV, DeviceKind.STICK, DeviceKind.RECEIVER -> R.drawable.encore_icon_device_tv_24
    DeviceKind.PHONE -> R.drawable.encore_icon_device_mobile_24
    DeviceKind.TABLET -> R.drawable.encore_icon_device_tablet_24
    DeviceKind.CAR -> R.drawable.encore_icon_device_car_24
    DeviceKind.CAST -> R.drawable.encore_icon_chromecast_connected_24
    DeviceKind.HEADPHONES -> R.drawable.encore_icon_device_headphones_24
    else -> R.drawable.encore_icon_device_computer_24
}

@Composable
fun EncoreMiniPlayer(
    title: String,
    artist: String,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    progress: Float, // 0.0f to 1.0f
    isLiked: Boolean,
    onPlayPauseClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDeviceClick: () -> Unit,
    onBarClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSkipNextClick: () -> Unit = {},
    onSkipPreviousClick: () -> Unit = {},
    connectedDeviceName: String? = null,
    connectedDeviceKind: DeviceKind? = null,
    coverColor: Color = Color(0xFF5A3E85),
    coverUrl: String? = null,
    hasTrack: Boolean = true
) {
    val coroutineScope = rememberCoroutineScope()
    val titleOffsetX = remember { Animatable(0f) }
    var containerWidthPx by remember { mutableFloatStateOf(600f) }

    val dynamicTint by rememberSmoothColor(
        target = miniPlayerTint(coverColor),
        durationMillis = MINI_PLAYER_TINT_DURATION
    )

    LaunchedEffect(title) {
        if (!titleOffsetX.isRunning && titleOffsetX.value != 0f) {
            titleOffsetX.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = EncoreControlSizes.FloatingPlayerMargin)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(EncoreCornerRadii.Larger2))
            .clip(RoundedCornerShape(EncoreCornerRadii.Larger2))
            .background(dynamicTint)
            .clickable(onClick = onBarClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(EncoreControlSizes.FloatingPlayerHeight)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    coroutineScope.launch {
                                        val offset = titleOffsetX.value
                                        val threshold = (containerWidthPx * 0.25f).coerceAtLeast(40f)
                                        val exitDistance = containerWidthPx.coerceAtLeast(200f)
                                        if (offset < -threshold) {
                                            titleOffsetX.animateTo(-exitDistance, tween(160, easing = FastOutSlowInEasing))
                                            onSkipNextClick()
                                            titleOffsetX.snapTo(exitDistance)
                                            titleOffsetX.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                                        } else if (offset > threshold) {
                                            titleOffsetX.animateTo(exitDistance, tween(160, easing = FastOutSlowInEasing))
                                            onSkipPreviousClick()
                                            titleOffsetX.snapTo(-exitDistance)
                                            titleOffsetX.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                                        } else {
                                            titleOffsetX.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                                        }
                                    }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    coroutineScope.launch {
                                        titleOffsetX.snapTo(titleOffsetX.value + dragAmount)
                                    }
                                }
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.mwask.bat.compose.components.EncoreArtworkImage(
                        imageUrl = coverUrl,
                        contentDescription = title,
                        shape = RoundedCornerShape(EncoreCornerRadii.Base),
                        fallbackColor = coverColor,
                        modifier = Modifier.size(EncoreControlSizes.FloatingPlayerCoverSize)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clipToBounds()
                            .onSizeChanged { size ->
                                if (size.width > 0) {
                                    containerWidthPx = size.width.toFloat()
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationX = titleOffsetX.value
                                    val alphaFraction = (titleOffsetX.value.absoluteValue / containerWidthPx.coerceAtLeast(1f))
                                    alpha = (1f - alphaFraction).coerceIn(0.15f, 1f)
                                }
                        ) {
                            Text(
                                text = buildAnnotatedString {
                                    append(title)
                                    if (artist.isNotBlank()) {
                                        append(" \u2022 ")
                                        withStyle(SpanStyle(color = EncoreColors.TextSecondary)) {
                                            append(artist)
                                        }
                                    }
                                },
                                style = EncoreTheme.typography.bodySmallBold.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = EncoreColors.TextPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        initialDelayMillis = 2000,
                                        velocity = 30.dp
                                    )
                            )
                            if (!connectedDeviceName.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .basicMarquee(
                                            iterations = Int.MAX_VALUE,
                                            initialDelayMillis = 2000,
                                            velocity = 30.dp
                                        )
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.encore_icon_connect_24),
                                        contentDescription = null,
                                        tint = EncoreColors.Green,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = connectedDeviceName,
                                        style = EncoreTheme.typography.marginal.copy(
                                            color = EncoreColors.Green
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip
                                    )
                                }
                            }
                        }
                    }
                }

                val hasConnectedDevice = !connectedDeviceName.isNullOrBlank()
                val connectIconRes = if (hasConnectedDevice) {
                    resolveDeviceIcon(connectedDeviceKind)
                } else {
                    R.drawable.encore_icon_device_mobile_24
                }
                val connectIconTint = if (hasConnectedDevice) EncoreColors.Green else Color.White

                EncoreIconButton(
                    iconRes = connectIconRes,
                    contentDescription = if (hasConnectedDevice) stringResource(R.string.connected_to_device, connectedDeviceName) else stringResource(R.string.connect_to_device),
                    onClick = onDeviceClick,
                    size = EncoreControlSizes.FloatingPlayerButtonSize,
                    iconSize = 20.dp,
                    tint = connectIconTint
                )

                EncoreHeartButton(
                    isLiked = isLiked,
                    onLikeToggle = onLikeClick,
                    size = EncoreControlSizes.FloatingPlayerButtonSize,
                    iconSize = 22.dp
                )

                EncoreIconButton(
                    iconRes = if (isPlaying) R.drawable.encore_icon_pause_24 else R.drawable.encore_icon_play_24,
                    contentDescription = if (isPlaying) "Pause" else stringResource(R.string.play),
                    onClick = if (hasTrack) onPlayPauseClick else ({}),
                    size = EncoreControlSizes.FloatingPlayerButtonSize,
                    iconSize = 24.dp,
                    tint = if (hasTrack) EncoreColors.TextPrimary else EncoreColors.TextSecondary,
                    isLoading = isBuffering,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = EncoreColors.TextPrimary,
                trackColor = EncoreColors.ProgressBarBackground
            )
        }
    }
}
