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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.compose.R
import com.mwask.bat.compose.playlist.PlaylistSort
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreTheme
import androidx.compose.ui.res.stringResource

/**
 * - sort_filter_bottom_sheet_layout.xml
 * - Options: Custom order, Title, Artist, Album, Recently added
 * - Active option highlighted in Encore Green with direction arrow (for reversible) or checkmark (for non-reversible)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSortSheet(
    isOpen: Boolean,
    currentSort: PlaylistSort,
    currentDirection: SortDirection = SortDirection.ASCENDING,
    onSortSelected: (PlaylistSort, SortDirection) -> Unit,
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
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.sort_by),
                style = EncoreTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = EncoreColors.TextPrimary
                ),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            PlaylistSort.values().forEach { option ->
                val isSelected = option == currentSort
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newDirection = if (isSelected && option.reversible) {
                                currentDirection.toggle()
                            } else {
                                option.defaultDirection
                            }
                            onSortSelected(option, newDirection)
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(option.labelRes),
                        style = EncoreTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) EncoreColors.Green else EncoreColors.TextPrimary
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
        }
    }
}
