package com.mwask.bat.compose.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings

@Composable
fun EncoreFastScroll(
    listState: LazyListState,
    content: @Composable () -> Unit,
) {
    LazyColumnScrollbar(
        state = listState,
        settings = ScrollbarSettings(
            thumbThickness = 6.dp,
            scrollbarPadding = 3.dp,
            thumbUnselectedColor = Color.White.copy(alpha = 0.30f),
            thumbSelectedColor = Color.White.copy(alpha = 0.55f),
            thumbMinLength = 0.14f,
            hideDelayMillis = 1000,
            hideDisplacement = 0.dp,
            durationAnimationMillis = 250,
        ),
        content = content,
    )
}
