package com.mwask.bat.compose.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 */
object EncoreSpacers {
    val Tighter5: Dp = 2.dp   // encore_spacer_tighter_5
    val Tighter4: Dp = 4.dp   // encore_spacer_tighter_4
    val Tighter3: Dp = 6.dp   // encore_spacer_tighter_3
    val Tighter2: Dp = 8.dp   // encore_spacer_tighter_2
    val Tighter: Dp = 12.dp   // encore_spacer_tighter
    val Base: Dp = 16.dp      // encore_spacer_base (standard margin)
    val Looser: Dp = 20.dp    // encore_spacer_looser
    val Looser2: Dp = 24.dp   // encore_spacer_looser_2
    val Looser3: Dp = 32.dp   // encore_spacer_looser_3
    val Looser4: Dp = 40.dp   // encore_spacer_looser_4
    val Looser5: Dp = 48.dp   // encore_spacer_looser_5
    val Looser6: Dp = 64.dp   // encore_spacer_looser_6
}

object EncoreCornerRadii {
    val Base: Dp = 4.dp             // encore_corner_radius_base (small album art, badges)
    val Larger: Dp = 6.dp           // encore_corner_radius_larger
    val Larger2: Dp = 8.dp          // encore_corner_radius_larger_2 (floating player bar, cards)
    val Larger3: Dp = 16.dp         // encore_corner_radius_larger_3 (lyrics cards, dialogs)
    val ActionPill: Dp = 48.dp      // encore_action_button_corner_radius
    val FollowPill: Dp = 999.dp     // encore_follow_button_rounded_corner_radius
}

object EncoreControlSizes {
    val FloatingPlayerHeight: Dp = 56.dp           // floating_now_playing_bar_height
    val FloatingPlayerCoverSize: Dp = 40.dp        // floating_now_playing_bar_content_size
    val FloatingPlayerCoverRadius: Dp = 4.dp       // floating_now_playing_bar_cover_art_radius
    val FloatingPlayerMargin: Dp = 8.dp            // floating_now_playing_bar_margin
    val FloatingPlayerProgressBarHeight: Dp = 2.dp // progress_bar height in now_playing_bar.xml
    val FloatingPlayerButtonSize: Dp = 40.dp       // now_playing_bar_actions_button_size

    val LargePlayButtonSize: Dp = 52.dp            // encore_play_button_large_view_size (playlist header)
    val PlayButtonShuffleBadgeSize: Dp = 11.dp     // encore_play_button_badge_size
    val PlayPauseButtonLarge: Dp = 48.dp           // encore_play_and_pause_button_size_large
    val PlayPauseButtonExtraLarge: Dp = 54.dp      // encore_play_and_pause_button_size_extra_large (64dp with tap target)
    val PlayPauseIconExtraLarge: Dp = 32.dp        // encore_play_and_pause_icon_extra_large

    val IconButtonBase: Dp = 40.dp                 // encore_control_size_smaller + tap area
    val BackButtonSize: Dp = 48.dp                 // encore_back_button_size
    val BackButtonBackground: Dp = 32.dp           // encore_back_button_background_size
    val LoadingIndicatorSize: Dp = 24.dp           // encore_loading_indicator_size

    const val ButtonPressScale: Float = 0.90f      // encore_button_animation_pressed_scale
}

/**
 */
object EncoreAnimation {
    const val ButtonDuration: Int = 200            // encore_button_animation_time
    const val BoxDuration: Int = 200               // encore_box_animation_time
    const val DefaultScale: Float = 1.0f           // encore_button_animation_default_scale
    const val PressedScale: Float = 0.90f          // encore_button_animation_pressed_scale
    const val ShimmerDuration: Int = 1200          // shimmer_animation_duration

    const val NowPlayingEnterDuration: Int = 150
    const val NowPlayingExitDuration: Int = 200
    const val LyricsFullscreenDuration: Int = 250
    const val ScreenEnterDuration: Int = 260
    const val ScreenExitDuration: Int = 200

    val ButtonInterpolator = CubicBezierEasing(0.3f, 0.0f, 0.0f, 1.0f)
    val BoxInterpolator = CubicBezierEasing(0.3f, 0.0f, 0.4f, 1.0f)
    val DecelerateInterpolator = FastOutSlowInEasing
}

/**
 * Computes the exact bottom clearance padding required so that scrollable content
 * finishes directly at the top edge of the floating mini player, bottom navigation bar,
 * and system navigation bar insets.
 *
 * Formula:
 * 56dp (bottom nav) + 64dp (floating player + margin) + system navigationBars inset.
 */
@Composable
fun rememberBottomPadding(extraBuffer: Dp = 0.dp): Dp {
    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return 120.dp + navBarsBottom + extraBuffer
}
