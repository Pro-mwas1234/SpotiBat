package com.project.lol.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

@Composable
fun SpotifyTheme(
    useDynamicColor: Boolean = false,
    amoled: Boolean = false,
    seedColor: Color? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicDarkColorScheme(context)
        }
        seedColor != null -> schemeFromSeed(seedColor)
        else -> defaultDarkScheme()
    }

    val colorScheme = if (amoled) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF0F0F0F),
            surfaceContainer = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainerHigh = Color(0xFF141414),
            surfaceContainerLowest = Color.Black,
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SairaTypography,
        content = content
    )
}

fun defaultDarkScheme(): ColorScheme = darkColorScheme(
    primary = Color(0xFFE0E0E0),
    onPrimary = Color(0xFF121212),
    primaryContainer = Color(0xFF2E2E2E),
    onPrimaryContainer = Color(0xFFF5F5F5),
    inversePrimary = Color(0xFF121212),
    secondary = Color(0xFFCCCCCC),
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFF262626),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFFB0B0B0),
    onTertiary = Color(0xFF181818),
    tertiaryContainer = Color(0xFF202020),
    onTertiaryContainer = Color(0xFFD6D6D6),
    outline = Color(0xFF767676),
    outlineVariant = Color(0xFF444444)
)

private fun schemeFromSeed(seed: Color): ColorScheme {
    fun rotated(degrees: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
        val h = (hsv[0] + degrees + 360f) % 360f
        val s = hsv[1].coerceIn(0.3f, 0.85f)
        return Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, hsv[2])))
    }

    fun onColor(c: Color): Color =
        if (c.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White

    val secondary = rotated(30f)
    val tertiary = rotated(-30f)

    return defaultDarkScheme().copy(
        primary = seed,
        onPrimary = onColor(seed),
        primaryContainer = lerp(seed, Color.White, 0.22f),
        onPrimaryContainer = Color.White,
        secondary = secondary,
        onSecondary = onColor(secondary),
        secondaryContainer = lerp(secondary, Color.White, 0.22f),
        onSecondaryContainer = Color.White,
        tertiary = tertiary,
        onTertiary = onColor(tertiary),
        tertiaryContainer = lerp(tertiary, Color.White, 0.22f),
        onTertiaryContainer = Color.White
    )
}
