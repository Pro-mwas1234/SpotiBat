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

/** User-selectable accent presets for the "Appearance" theme. */
data class AccentPreset(val id: String, val label: String, val color: Color)

val AccentPresets: List<AccentPreset> = listOf(
    AccentPreset("green", "SpotiBat Green", Color(0xFF1DB954)),
    AccentPreset("blue", "Ocean Blue", Color(0xFF2D7FF9)),
    AccentPreset("purple", "Violet", Color(0xFF9B59B6)),
    AccentPreset("rose", "Rose", Color(0xFFE91E63)),
    AccentPreset("orange", "Sunset", Color(0xFFFF9800)),
)

fun accentById(id: String): Color =
    AccentPresets.firstOrNull { it.id == id }?.color ?: EncoreColors.Green

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
    val error: Color = EncoreColors.Error,
    val isDark: Boolean = true
) {
    companion object {
        fun dark(accent: Color = EncoreColors.Green): EncoreColorScheme = EncoreColorScheme(
            background = EncoreColors.Background,
            surface = EncoreColors.Surface,
            card = EncoreColors.Card,
            cardElevated = EncoreColors.CardElevated,
            primary = accent,
            onPrimary = EncoreColors.PureBlack,
            textPrimary = EncoreColors.TextPrimary,
            textSecondary = EncoreColors.TextSecondary,
            divider = EncoreColors.Divider,
            error = EncoreColors.Error,
            isDark = true
        )

        fun light(accent: Color = EncoreColors.Green): EncoreColorScheme = EncoreColorScheme(
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFF5F5F5),
            card = Color(0xFFEDEDED),
            cardElevated = Color(0xFFFFFFFF),
            primary = accent,
            onPrimary = Color(0xFFFFFFFF),
            textPrimary = Color(0xFF121212),
            textSecondary = Color(0xFF535353),
            divider = Color(0xFFD9D9D9),
            error = EncoreColors.Error,
            isDark = false
        )
    }
}