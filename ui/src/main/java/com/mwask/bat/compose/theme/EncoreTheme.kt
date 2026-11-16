package com.mwask.bat.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

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
    content: @Composable () -> Unit
) {
    val colors = EncoreColorScheme()
    val typography = EncoreTypography()

    val materialColorScheme = darkColorScheme(
        primary = EncoreColors.Green,
        onPrimary = EncoreColors.PureBlack,
        background = EncoreColors.Background,
        surface = EncoreColors.Surface,
        onBackground = EncoreColors.TextPrimary,
        onSurface = EncoreColors.TextPrimary
    )

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
