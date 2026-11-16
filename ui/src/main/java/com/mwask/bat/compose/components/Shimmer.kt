package com.mwask.bat.compose.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mwask.bat.compose.theme.EncoreAnimation

private val ShimmerBase = Color(0xFF1C1C1C)
private val ShimmerHighlight = Color(0xFF2E2E2E)

internal val LocalShimmerPhase = staticCompositionLocalOf { 0f }

@Composable
fun rememberSmoothColor(
    target: Color,
    durationMillis: Int = 650
): State<Color> = animateColorAsState(
    targetValue = target,
    animationSpec = tween(
        durationMillis = durationMillis,
        easing = FastOutSlowInEasing
    ),
    label = "smoothAccentColor"
)

@Composable
fun ShimmerHost(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = EncoreAnimation.ShimmerDuration,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerPhase"
    )
    CompositionLocalProvider(LocalShimmerPhase provides phase, content = content)
}

@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
    base: Color = ShimmerBase,
    highlight: Color = ShimmerHighlight
) {
    val phase = LocalShimmerPhase.current
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                val sweep = size.width * 1.6f + size.height
                val startX = -sweep + (size.width + sweep) * phase
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(base, highlight, base),
                        start = Offset(startX, 0f),
                        end = Offset(startX + sweep, size.height)
                    )
                )
            }
    )
}

@Composable
fun ShimmerText(
    widthFraction: Float,
    height: Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    ShimmerBlock(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height),
        shape = RoundedCornerShape(3.dp)
    )
}

@Composable
fun HomeFeedSkeleton(modifier: Modifier = Modifier) {
    ShimmerHost {
        Column(modifier = modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                ShimmerText(widthFraction = 0.62f, height = 26.dp)
                Spacer(modifier = Modifier.height(16.dp))
                QuickAccessGridSkeleton()
            }
            Spacer(modifier = Modifier.height(20.dp))
            repeat(2) { index ->
                ShelfRowSkeleton(
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 22.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickAccessGridSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(2) {
                    ShimmerBlock(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShelfRowSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        ShimmerText(
            widthFraction = 0.4f,
            height = 20.dp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(3) {
                Column(modifier = Modifier.width(148.dp)) {
                    ShimmerBlock(
                        modifier = Modifier.size(148.dp),
                        shape = RoundedCornerShape(4.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ShimmerText(widthFraction = 0.8f, height = 13.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    ShimmerText(widthFraction = 0.55f, height = 11.dp)
                }
            }
        }
    }
}

@Composable
fun TrackListSkeleton(
    rows: Int = 8,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    ShimmerHost {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(contentPadding)
        ) {
            repeat(rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerBlock(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(4.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerText(widthFraction = 0.62f, height = 13.dp)
                        Spacer(modifier = Modifier.height(7.dp))
                        ShimmerText(widthFraction = 0.38f, height = 11.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryListSkeleton(
    rows: Int = 8,
    modifier: Modifier = Modifier
) {
    ShimmerHost {
        Column(modifier = modifier.fillMaxWidth()) {
            repeat(rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerBlock(
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(6.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerText(widthFraction = 0.55f, height = 14.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        ShimmerText(widthFraction = 0.32f, height = 11.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryGridSkeleton(
    rows: Int = 4,
    modifier: Modifier = Modifier
) {
    ShimmerHost {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(2) {
                        Column(modifier = Modifier.weight(1f)) {
                            ShimmerBlock(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                shape = RoundedCornerShape(6.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            ShimmerText(widthFraction = 0.7f, height = 13.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailPageSkeleton(
    modifier: Modifier = Modifier,
    artworkSize: Dp = 200.dp,
    circle: Boolean = false
) {
    ShimmerHost {
        Column(modifier = modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(72.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ShimmerBlock(
                    modifier = Modifier.size(artworkSize),
                    shape = if (circle) CircleShape else RoundedCornerShape(4.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                ShimmerText(widthFraction = 0.6f, height = 26.dp)
                Spacer(modifier = Modifier.height(10.dp))
                ShimmerText(widthFraction = 0.35f, height = 13.dp)
            }
            Spacer(modifier = Modifier.height(24.dp))
            TrackListSkeleton(rows = 7)
        }
    }
}

@Composable
fun InlineLoadingDots(
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.6f),
    dotSize: Dp = 5.dp
) {
    val transition = rememberInfiniteTransition(label = "dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dotsPhase"
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSize * 0.8f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val wave = ((phase - index).mod(3f))
            val alpha = (1f - (wave / 3f) * 2f).coerceIn(0.25f, 1f)
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
        }
    }
}

