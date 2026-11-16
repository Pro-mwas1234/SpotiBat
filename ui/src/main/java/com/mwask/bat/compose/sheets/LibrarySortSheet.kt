package com.mwask.bat.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.compose.R
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreAppTheme
import com.mwask.bat.compose.theme.EncoreTheme
import androidx.compose.ui.res.stringResource

enum class LibrarySortOption(
    val displayNameRes: Int,
    val apiId: String,
    val reversible: Boolean = true,
    val defaultDirection: SortDirection = SortDirection.ASCENDING
) {
    RECENTS(R.string.recents, "Recents", reversible = true, defaultDirection = SortDirection.DESCENDING),
    RECENTLY_ADDED(R.string.recently_added, "Recently Added", reversible = true, defaultDirection = SortDirection.DESCENDING),
    ALPHABETICAL(R.string.alphabetical, "Alphabetical", reversible = true, defaultDirection = SortDirection.ASCENDING),
    CREATOR(R.string.creator, "Creator", reversible = true, defaultDirection = SortDirection.ASCENDING);

    companion object {
        fun fromApiId(id: String?): LibrarySortOption? =
            values().firstOrNull { it.apiId.equals(id, ignoreCase = true) }
    }
}

/**
 * - `sort_filter_bottom_sheet_layout.xml`
 * - `sort_and_filter_bottom_sheet_handle.xml` (36dp x 4dp grapple, #4DFFFFFF)
 * - `@style/CwpSortAndFilterTitleStyle` (48dp minHeight, 16dp horizontal padding)
 * - `@style/CwpSortAndFilterOptionStyle` (BodyMedium, 48dp minHeight, Green text + direction arrow/check for active)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySortSheet(
    isOpen: Boolean,
    currentSort: LibrarySortOption,
    currentDirection: SortDirection = SortDirection.DESCENDING,
    onSortSelected: (LibrarySortOption, SortDirection) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = DarkSystemBarsSheet,
        sheetState = sheetState,
        containerColor = EncoreColors.Card,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x4DFFFFFF))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.sort_by),
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = EncoreColors.TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x1AFFFFFF))
            )
            Spacer(modifier = Modifier.height(8.dp))

            LibrarySortOption.values().forEach { option ->
                val isSelected = option == currentSort
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            val newDirection = if (isSelected && option.reversible) {
                                currentDirection.toggle()
                            } else {
                                option.defaultDirection
                            }
                            onSortSelected(option, newDirection)
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(option.displayNameRes),
                        style = EncoreTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) EncoreColors.Green else EncoreColors.TextPrimary,
                            fontSize = 15.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    if (isSelected) {
                        if (option.reversible) {
                            Icon(
                                painter = painterResource(
                                    id = if (currentDirection == SortDirection.ASCENDING) {
                                        R.drawable.encore_icon_arrow_up_24
                                    } else {
                                        R.drawable.encore_icon_arrow_down_24
                                    }
                                ),
                                contentDescription = if (currentDirection == SortDirection.ASCENDING) "Sorted ascending" else "Sorted descending",
                                tint = EncoreColors.Green,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_check_24),
                                contentDescription = stringResource(R.string.selected),
                                tint = EncoreColors.Green,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview
@Composable
fun PreviewLibrarySortSheet() {
    EncoreAppTheme {
        LibrarySortSheet(
            isOpen = true,
            currentSort = LibrarySortOption.RECENTS,
            currentDirection = SortDirection.DESCENDING,
            onSortSelected = { _, _ -> },
            onDismiss = {}
        )
    }
}
