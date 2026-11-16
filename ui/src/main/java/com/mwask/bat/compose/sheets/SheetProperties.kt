package com.mwask.bat.compose.sheets

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.ui.window.SecureFlagPolicy

/**
 * A modal sheet is its own window, so the Activity's edge-to-edge setup does
 * not reach it: the sheet falls back to a light dialog theme and repaints the
 * status and navigation bars with light icons while it is open. These flags
 * pin the bars to light icons on dark, matching the rest of the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal val DarkSystemBarsSheet = ModalBottomSheetProperties(
    isAppearanceLightStatusBars = false,
    isAppearanceLightNavigationBars = false
)

@OptIn(ExperimentalMaterial3Api::class)
internal val BackNotDismissSheet = ModalBottomSheetProperties(
    shouldDismissOnBackPress = false,
    shouldDismissOnClickOutside = true,
    securePolicy = SecureFlagPolicy.Inherit,
    isAppearanceLightStatusBars = false,
    isAppearanceLightNavigationBars = false
)
