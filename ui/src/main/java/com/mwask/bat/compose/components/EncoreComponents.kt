package com.mwask.bat.compose.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.compose.R
import com.mwask.bat.compose.format.parseHexColor
import com.mwask.bat.compose.theme.EncoreAnimation
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreTheme
import androidx.compose.ui.res.stringResource

/**
 * Supports the optional shuffle badge (11dp) at the bottom-right corner used in playlist headers.
 */
@Composable
fun EncorePlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    iconSize: Dp = 26.dp,
    backgroundColor: Color = EncoreColors.Green,
    iconColor: Color = EncoreColors.PureBlack,
    showShuffleBadge: Boolean = false,
    isLoading: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) EncoreAnimation.PressedScale else EncoreAnimation.DefaultScale,
        animationSpec = tween(
            durationMillis = EncoreAnimation.ButtonDuration,
            easing = EncoreAnimation.ButtonInterpolator
        ),
        label = "playBtnScale"
    )
    val effectiveBackground = if (isLoading) backgroundColor.copy(alpha = 0.32f) else backgroundColor

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .shadow(elevation = 8.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(effectiveBackground)
            .clickable(
                enabled = !isLoading,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(iconSize),
                color = iconColor.copy(alpha = 0.45f),
                strokeWidth = 2.5.dp,
                strokeCap = StrokeCap.Round
            )
        } else {
            val iconRes = if (isPlaying) R.drawable.encore_icon_pause_24 else R.drawable.encore_icon_play_24
            Crossfade(
                targetState = iconRes,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                label = "playPauseCrossfade"
            ) { targetIcon ->
                Icon(
                    painter = painterResource(id = targetIcon),
                    contentDescription = if (isPlaying) "Pause" else stringResource(R.string.play),
                    tint = iconColor,
                    modifier = Modifier.size(iconSize)
                )
            }
        }

        if (showShuffleBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_shuffle_16),
                    contentDescription = stringResource(R.string.shuffle),
                    tint = Color.Black,
                    modifier = Modifier.size(9.dp)
                )
            }
        }
    }
}

/**
 */
@Composable
fun EncoreIconButton(
    iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = EncoreColors.TextSecondary,
    size: Dp = 40.dp,
    iconSize: Dp = 24.dp,
    isActive: Boolean = false,
    activeTint: Color = EncoreColors.Green,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) EncoreAnimation.PressedScale else EncoreAnimation.DefaultScale,
        animationSpec = tween(
            durationMillis = EncoreAnimation.ButtonDuration,
            easing = EncoreAnimation.ButtonInterpolator
        ),
        label = "iconBtnScale"
    )
    val effectiveTint = when {
        !enabled -> EncoreColors.TextDisabled
        isActive -> activeTint
        else -> tint
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                enabled = enabled && !isLoading,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(iconSize * 0.8f),
                color = EncoreColors.TextDisabled,
                strokeWidth = 2.dp,
                strokeCap = StrokeCap.Round
            )
        } else {
            Crossfade(
                targetState = iconRes,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                label = "iconButtonCrossfade"
            ) { targetIcon ->
                Icon(
                    painter = painterResource(id = targetIcon),
                    contentDescription = contentDescription,
                    tint = effectiveTint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

/**
 */
@Composable
fun EncoreChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgAnim by animateColorAsState(
        targetValue = if (isSelected) EncoreColors.Green else EncoreColors.Card,
        label = "chipBg"
    )
    val textAnim by animateColorAsState(
        targetValue = if (isSelected) EncoreColors.PureBlack else EncoreColors.TextPrimary,
        label = "chipText"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgAnim)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = EncoreTheme.typography.bodySmallBold.copy(color = textAnim)
        )
    }
}

/**
 * - 12dp x 12dp bounding box (matches @dimen/encore_play_indicator_default_size)
 * - 3 vertical bars (width 2dp, spacing 2dp, corner radius 1dp)
 * - 1250ms cycle length at 60fps with authentic keyframed heights:
 *     Bar 1: 8dp -> 2dp -> 8dp -> 5dp -> 11dp -> 8dp
 *     Bar 2: 11dp -> 8dp -> 5dp -> 8dp -> 2dp -> 11dp
 *     Bar 3: 5dp -> 8dp -> 2dp -> 11dp -> 8dp -> 5dp
 * - Fixed 2dp bars when paused
 */
@Composable
fun EncorePlayIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = EncoreColors.GreenLight,
    size: Dp = 12.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "encoreEqualizer")

    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1250
                8f at 0
                2f at 250
                8f at 500
                5f at 750
                11f at 1000
                8f at 1250
            },
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "bar1Height"
    )

    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 11f,
        targetValue = 11f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1250
                11f at 0
                8f at 250
                5f at 500
                8f at 750
                2f at 1000
                11f at 1250
            },
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "bar2Height"
    )

    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1250
                5f at 0
                8f at 250
                2f at 500
                11f at 750
                8f at 1000
                5f at 1250
            },
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "bar3Height"
    )

    androidx.compose.foundation.layout.Arrangement.SpaceBetween
    Row(
        modifier = modifier.size(size),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(if (isPlaying) bar1Height.dp else 2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(if (isPlaying) bar2Height.dp else 2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(if (isPlaying) bar3Height.dp else 2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
    }
}

/**
 * a small rounded gray square with a tiny bold stringResource(R.string.explicit_badge). Kept deliberately smaller
 * than the surrounding text so it never visually outweighs the track title.
 */
@Composable
fun EncoreExplicitTag(
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
    cornerRadius: Dp = 2.dp,
    fontSize: TextUnit = 9.sp,
    background: Color = EncoreColors.TextSecondary,
    tint: Color = EncoreColors.PureBlack
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.explicit_badge),
            style = EncoreTheme.typography.tagLabel.copy(
                fontSize = fontSize,
                lineHeight = fontSize,
                letterSpacing = 0.sp,
                color = tint
            )
        )
    }
}

/**
 */
@Composable
fun EncoreTrackRow(
    trackNumber: Int?,
    title: String,
    artist: String,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    isExplicit: Boolean = false,
    hasVideo: Boolean = false,
    isDownloaded: Boolean = false,
    imageUrl: String? = null,
    saved: Boolean? = null,
    artistUri: String = "",
    onLikeToggle: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null
) {
    val isBanned = com.mwask.bat.compose.bans.ArtistBans.isBanned(artistUri)
    SwipeToQueueBox(
        onAddToQueue = onAddToQueue?.takeIf { !isBanned },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clickable(enabled = !isBanned, onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (trackNumber != null) {
                Box(
                    modifier = Modifier.width(28.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (isPlaying) {
                        EncorePlayIndicator(
                            isPlaying = true,
                            color = EncoreColors.Green,
                            size = 14.dp
                        )
                    } else {
                        Text(
                            text = trackNumber.toString(),
                            style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .graphicsLayer { alpha = if (isBanned) 0.4f else 1f },
                    contentAlignment = Alignment.Center
                ) {
                    EncoreArtworkImage(
                        imageUrl = imageUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 4.dp
                    )
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            EncorePlayIndicator(
                                isPlaying = true,
                                color = EncoreColors.Green,
                                size = 16.dp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = if (title.isBlank()) "Unknown" else title,
                    style = EncoreTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            isPlaying -> EncoreColors.Green
                            isBanned -> EncoreColors.TextSecondary.copy(alpha = 0.55f)
                            else -> EncoreColors.TextPrimary
                        }
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isExplicit) {
                        EncoreExplicitTag(modifier = Modifier.padding(end = 4.dp))
                    }
                    if (hasVideo) {
                        Icon(
                            painter = painterResource(R.drawable.encore_icon_video_24),
                            contentDescription = stringResource(R.string.music_video),
                            tint = EncoreColors.TextSecondary,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(14.dp)
                        )
                    }
                    if (isDownloaded) {
                        Icon(
                            painter = painterResource(R.drawable.encore_icon_downloaded_24),
                            contentDescription = stringResource(R.string.downloaded),
                            tint = EncoreColors.Green,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(14.dp)
                        )
                    }
                    Text(
                        text = if (artist.isBlank()) "Unknown" else artist,
                        style = EncoreTheme.typography.bodySmall.copy(
                            color = EncoreColors.TextSecondary.takeIf { !isBanned }
                                ?: EncoreColors.TextSecondary.copy(alpha = 0.55f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (onLikeToggle != null && !isBanned) {
                EncoreHeartButton(
                    isLiked = saved == true,
                    onLikeToggle = onLikeToggle,
                    size = 36.dp,
                    iconSize = 20.dp
                )
            }

            EncoreIconButton(
                iconRes = R.drawable.encore_icon_more_android_24,
                contentDescription = stringResource(R.string.more_options_for, title),
                onClick = onMoreClick,
                size = 36.dp,
                iconSize = 20.dp
            )
        }
    }
}

enum class ShuffleMode {
    OFF,
    SHUFFLE,
    SMART_SHUFFLE;

    fun next(): ShuffleMode = when (this) {
        OFF -> SHUFFLE
        SHUFFLE -> SMART_SHUFFLE
        SMART_SHUFFLE -> OFF
    }
}

enum class RepeatMode {
    OFF,
    REPEAT_ALL,
    REPEAT_ONE;

    fun next(): RepeatMode = when (this) {
        OFF -> REPEAT_ALL
        REPEAT_ALL -> REPEAT_ONE
        REPEAT_ONE -> OFF
    }
}

/**
 * Replicates `SmartShuffleButtonView.smali` with 3-way toggle (Off -> Shuffle -> Smart Shuffle)
 * and the active green indicator dot beneath the icon.
 */
@Composable
fun EncoreSmartShuffleButton(
    mode: ShuffleMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        label = "smartShuffleScale"
    )

    val isActive = mode != ShuffleMode.OFF
    val iconRes = if (mode == ShuffleMode.SMART_SHUFFLE) {
        R.drawable.encore_icon_shuffle_smart_24
    } else {
        R.drawable.encore_icon_shuffle_24
    }
    val tint = when {
        !enabled -> EncoreColors.TextDisabled
        isActive -> EncoreColors.Green
        else -> EncoreColors.TextSecondary
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = when (mode) {
                    ShuffleMode.OFF -> stringResource(R.string.shuffle_off)
                    ShuffleMode.SHUFFLE -> stringResource(R.string.shuffle_on)
                    ShuffleMode.SMART_SHUFFLE -> stringResource(R.string.smart_shuffle_on)
                },
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
            if (isActive) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(EncoreColors.Green)
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

/**
 * Supports 3-way repeat toggle (Off -> Repeat All -> Repeat One)
 * with the authentic '1' badge icon and active green dot indicator.
 */
@Composable
fun EncoreRepeatButton(
    mode: RepeatMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        label = "repeatScale"
    )

    val isActive = mode != RepeatMode.OFF
    val iconRes = if (mode == RepeatMode.REPEAT_ONE) {
        R.drawable.encore_icon_repeat_once_24
    } else {
        R.drawable.encore_icon_repeat_24
    }
    val tint = when {
        !enabled -> EncoreColors.TextDisabled
        isActive -> EncoreColors.Green
        else -> EncoreColors.TextSecondary
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = when (mode) {
                    RepeatMode.OFF -> stringResource(R.string.repeat_off)
                    RepeatMode.REPEAT_ALL -> stringResource(R.string.repeat_all_on)
                    RepeatMode.REPEAT_ONE -> stringResource(R.string.repeat_one_on)
                },
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
            if (isActive) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(EncoreColors.Green)
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

/**
 * - On like: snaps to 0.80f -> animates to 1.30f (120ms tween) -> springs back to 1.0f (bouncy spring).
 * - Button press scale: uses EncoreAnimation.PressedScale and EncoreAnimation.ButtonInterpolator.
 * - Icon: smooth 150ms crossfade between outline and filled active heart.
 */
@Composable
fun EncoreHeartButton(
    isLiked: Boolean,
    onLikeToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 24.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) EncoreAnimation.PressedScale else EncoreAnimation.DefaultScale,
        animationSpec = tween(
            durationMillis = EncoreAnimation.ButtonDuration,
            easing = EncoreAnimation.ButtonInterpolator
        ),
        label = "heartPressScale"
    )

    val bounceScale = remember { Animatable(1f) }
    var prevLiked by remember { mutableStateOf(isLiked) }

    LaunchedEffect(isLiked) {
        if (isLiked && !prevLiked) {
            bounceScale.snapTo(0.80f)
            bounceScale.animateTo(
                targetValue = 1.30f,
                animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)
            )
            bounceScale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        } else if (!isLiked && prevLiked) {
            bounceScale.snapTo(0.90f)
            bounceScale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing)
            )
        }
        prevLiked = isLiked
    }

    val totalScale = pressScale * bounceScale.value

    Box(
        modifier = modifier
            .size(size)
            .scale(totalScale)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onLikeToggle
            ),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = isLiked,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "heartIconCrossfade"
        ) { liked ->
            Icon(
                painter = painterResource(
                    id = if (liked) R.drawable.encore_icon_heart_active_24 else R.drawable.encore_icon_heart_24
                ),
                contentDescription = if (liked) stringResource(R.string.remove_from_your_library) else stringResource(R.string.save_to_your_library),
                tint = if (liked) EncoreColors.Green else EncoreColors.TextSecondary,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * Displays user avatar image when [avatarUrl] is provided, or a colored circle with the user's initial.
 * When [colorHex] carries the profile accent color it tints the placeholder so
 * every surface (top bar, drawer, sheets) matches the profile page.
 */
@Composable
fun EncoreProfileAvatar(
    userDisplayName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    size: Dp = 36.dp,
    backgroundColor: Color = EncoreColors.CardElevated,
    colorHex: String = ""
) {
    val placeholderColor = parseHexColor(colorHex) ?: backgroundColor
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(placeholderColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            EncoreArtworkImage(
                imageUrl = avatarUrl,
                contentDescription = userDisplayName,
                shape = CircleShape,
                fallbackColor = placeholderColor,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = (userDisplayName.trim().firstOrNull() ?: 'D').uppercaseChar().toString(),
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
        }
    }
}


