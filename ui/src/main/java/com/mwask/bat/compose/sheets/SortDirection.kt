package com.mwask.bat.compose.sheets

enum class SortDirection {
    ASCENDING,
    DESCENDING;

    fun toggle(): SortDirection = if (this == ASCENDING) DESCENDING else ASCENDING
}
