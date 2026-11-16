package com.mwask.bat.compose.shell

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.backend.model.DeviceKind
import com.mwask.bat.compose.R
import com.mwask.bat.compose.player.EncoreMiniPlayer
import com.mwask.bat.compose.theme.EncoreAnimation
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreTheme
import com.mwask.bat.compose.util.rememberIsOffline
import androidx.compose.ui.res.stringResource

private const val NAV_BAR_TOP_ALPHA = 0.70f

private val NAV_BAR_FADE = Brush.verticalGradient(
    0.0f to EncoreColors.PureBlack.copy(alpha = NAV_BAR_TOP_ALPHA),
    0.55f to EncoreColors.PureBlack.copy(alpha = 0.88f),
    1.0f to EncoreColors.PureBlack
)

@Composable
internal fun ShellBottomBar(
    title: String,
    artist: String,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    progress: Float,
    isLiked: Boolean,
    coverUrl: String,
    coverColor: Color,
    connectedDevice: String?,
    connectedDeviceKind: DeviceKind? = null,
    navItems: List<NavItem>,
    currentNavIndex: Int,
    onNavSelect: (Int) -> Unit,
    onSearchDoubleTap: (() -> Unit)? = null,
    onPlayPause: () -> Unit,
    onLike: () -> Unit,
    onDeviceClick: () -> Unit,
    onBarClick: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    hasTrack: Boolean = true,
    showMiniPlayer: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val isOffline = rememberIsOffline()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = false) {}
    ) {
        AnimatedVisibility(
            visible = isOffline,
            enter = fadeIn(animationSpec = tween(EncoreAnimation.ButtonDuration)) +
                    expandVertically(animationSpec = tween(EncoreAnimation.ButtonDuration)),
            exit = fadeOut(animationSpec = tween(EncoreAnimation.ButtonDuration)) +
                    shrinkVertically(animationSpec = tween(EncoreAnimation.ButtonDuration))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(EncoreColors.CardElevated)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_cloud_off_24),
                    contentDescription = null,
                    tint = EncoreColors.TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.you_re_offline_check_your_internet_connection),
                    style = EncoreTheme.typography.bodySmall.copy(
                        color = EncoreColors.TextPrimary,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }

        AnimatedVisibility(
            visible = showMiniPlayer,
            enter = fadeIn(animationSpec = tween(EncoreAnimation.ButtonDuration)) +
                    expandVertically(animationSpec = tween(EncoreAnimation.ButtonDuration)),
            exit = fadeOut(animationSpec = tween(EncoreAnimation.ButtonDuration)) +
                    shrinkVertically(animationSpec = tween(EncoreAnimation.ButtonDuration))
        ) {
            EncoreMiniPlayer(
                title = title,
                artist = artist,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                progress = progress,
                isLiked = isLiked,
                coverUrl = coverUrl,
                coverColor = coverColor,
                connectedDeviceName = connectedDevice,
                connectedDeviceKind = connectedDeviceKind,
                onPlayPauseClick = onPlayPause,
                onLikeClick = onLike,
                onDeviceClick = onDeviceClick,
                onBarClick = onBarClick,
                onSkipNextClick = onSkipNext,
                onSkipPreviousClick = onSkipPrevious,
                hasTrack = hasTrack
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NAV_BAR_FADE)
                .navigationBarsPadding()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEachIndexed { index, item ->
                EncoreBottomNavItem(
                    item = item,
                    isSelected = currentNavIndex == index,
                    onClick = { onNavSelect(index) },
                    onDoubleTap = if (item.title == "Search") onSearchDoubleTap else null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EncoreBottomNavItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val doubleTapTimeoutMillis = LocalViewConfiguration.current.doubleTapTimeoutMillis
    var lastTapAtMillis by remember { mutableLongStateOf(0L) }
    Column(
        modifier = modifier
            .clickable {
                val now = SystemClock.elapsedRealtime()
                val isDoubleTap = now - lastTapAtMillis <= doubleTapTimeoutMillis
                lastTapAtMillis = now
                if (isDoubleTap) onDoubleTap?.invoke()
                onClick()
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.material3.Icon(
            painter = androidx.compose.ui.res.painterResource(
                if (isSelected) item.activeIconRes else item.iconRes
            ),
            contentDescription = item.title,
            tint = if (isSelected) EncoreColors.TextPrimary else EncoreColors.TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = item.title,
            style = EncoreTheme.typography.tagLabel.copy(fontSize = 10.sp),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) EncoreColors.TextPrimary else EncoreColors.TextSecondary
        )
    }
}
