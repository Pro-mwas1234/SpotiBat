package com.mwask.bat.compose.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 */
object EncoreColors {
    val Green = Color(0xFF1DB954)
    val GreenLight = Color(0xFF1ED760)
    val GreenDark = Color(0xFF169C46)

    val PureBlack = Color(0xFF000000)
    val Background = Color(0xFF121212)
    val Surface = Color(0xFF181818)         // gray_10: Cards, quick access rows
    val Card = Color(0xFF282828)            // gray_15: Floating mini-player, elevated cards
    val CardElevated = Color(0xFF333333)    // gray_20: Dialogs, sheets, bottom navigation
    val Divider = Color(0xFF535353)         // gray_30: Subtle separators, outlines
    val Outline = Color(0x33FFFFFF)         // 20% white outline

    val TextPrimary = Color(0xFFFFFFFF)     // High-emphasis white
    val TextSecondary = Color(0xFFB3B3B3)   // gray_70: Subdued artist/metadata text
    val TextDisabled = Color(0xFF7F7F7F)    // gray_50: Disabled/placeholder text

    val Error = Color(0xFFE91429)
    val Beta = Color(0xFFFFD54F)
    val HeartActive = Color(0xFF1DB954)
    val ProgressBarBackground = Color(0x33FFFFFF)
    val ScrubberThumb = Color(0xFFFFFFFF)
}

@Immutable
data class EncoreColorScheme(
    val background: Color = EncoreColors.Background,
    val surface: Color = EncoreColors.Surface,
    val card: Color = EncoreColors.Card,
    val cardElevated: Color = EncoreColors.CardElevated,
    val primary: Color = EncoreColors.Green,
    val onPrimary: Color = EncoreColors.PureBlack,
    val textPrimary: Color = EncoreColors.TextPrimary,
    val textSecondary: Color = EncoreColors.TextSecondary,
    val divider: Color = EncoreColors.Divider,
    val error: Color = EncoreColors.Error
)
