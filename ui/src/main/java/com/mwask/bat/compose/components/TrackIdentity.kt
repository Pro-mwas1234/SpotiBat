package com.mwask.bat.compose.components

internal fun matchesCurrent(uri: String, currentUri: String, title: String, currentTitle: String): Boolean =
    if (currentUri.isNotBlank()) uri == currentUri else title.isNotBlank() && title == currentTitle
