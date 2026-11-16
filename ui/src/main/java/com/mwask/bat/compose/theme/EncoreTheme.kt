package com.mwask.bat.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LocalEncoreColors = staticCompositionLocalOf { EncoreColorScheme() }
private val LocalEncoreTypography = staticCompositionLocalOf { EncoreTypography() }

object EncoreTheme {
    val colors: EncoreColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalEncoreColors.current

    val typography: EncoreTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalEncoreTypography.current
}

@Composable
fun EncoreAppTheme(
    darkTheme: Boolean = true,
    accent: Color = EncoreColors.Green,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) EncoreColorScheme.dark(accent) else EncoreColorScheme.light(accent)
    val typography = EncoreTypography()

    val materialColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accent,
            onPrimary = EncoreColors.PureBlack,
            background = colors.background,
            surface = colors.surface,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color(0xFFFFFFFF),
            background = colors.background,
            surface = colors.surface,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary
        )
    }

    CompositionLocalProvider(
        LocalEncoreColors provides colors,
        LocalEncoreTypography provides typography
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}