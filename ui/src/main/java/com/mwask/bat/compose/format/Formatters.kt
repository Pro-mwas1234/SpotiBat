package com.mwask.bat.compose.format

import androidx.compose.ui.graphics.Color

import java.text.NumberFormat
import java.util.Locale

fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    if (seconds <= 0L) return ""
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0L -> "$hours hr $minutes min"
        minutes > 0L -> "$minutes min"
        else -> "$seconds sec"
    }
}

fun formatCollectionDuration(ms: Long, isEstimate: Boolean = false): String {
    if (ms <= 0L) return ""
    val rounded = ms - ms % 60_000L
    val text = formatDuration(rounded)
    if (text.isBlank()) return ""
    return if (isEstimate) "about $text" else text
}

fun formatTrackLength(ms: Long): String {
    if (ms <= 0L) return ""
    val seconds = ms / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

fun formatExactCount(value: Long): String =
    NumberFormat.getIntegerInstance(Locale.US).format(value)

fun formatCount(value: Long): String = when {
    value >= 1_000_000_000L -> "%.1fB".format(value / 1_000_000_000.0)
    value >= 1_000_000L -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000L -> "%.1fK".format(value / 1_000.0)
    else -> value.toString()
}

fun parseHexColor(hex: String?): Color? {
    if (hex == null || hex.length != 7 || hex[0] != '#') return null
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
}
