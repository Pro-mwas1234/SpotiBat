package com.mwask.bat.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreTheme
import androidx.compose.ui.res.stringResource

enum class SleepTimerOption(val labelRes: Int, val minutes: Int) {
    MINUTES_5(R.string.five_minutes, 5),
    MINUTES_10(R.string.ten_minutes, 10),
    MINUTES_15(R.string.fifteen_minutes, 15),
    MINUTES_30(R.string.thirty_minutes, 30),
    MINUTES_45(R.string.forty_five_minutes, 45),
    MINUTES_60(R.string.one_hour, 60),
    END_OF_TRACK(R.string.end_of_track, -1),
    OFF(R.string.turn_off_timer, 0)
}

/**
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    activeOption: SleepTimerOption?,
    onOptionSelected: (SleepTimerOption) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = DarkSystemBarsSheet,
        sheetState = sheetState,
        containerColor = EncoreColors.Card,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.stop_audio_in),
                style = EncoreTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val options = listOf(
                SleepTimerOption.MINUTES_5,
                SleepTimerOption.MINUTES_10,
                SleepTimerOption.MINUTES_15,
                SleepTimerOption.MINUTES_30,
                SleepTimerOption.MINUTES_45,
                SleepTimerOption.MINUTES_60,
                SleepTimerOption.END_OF_TRACK
            ) + if (activeOption != null && activeOption != SleepTimerOption.OFF) {
                listOf(SleepTimerOption.OFF)
            } else {
                emptyList()
            }

            options.forEach { option ->
                val isSelected = activeOption == option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onOptionSelected(option)
                            onDismiss()
                        }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(option.labelRes),
                        style = EncoreTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) EncoreColors.Green else EncoreColors.TextPrimary
                        )
                    )

                    if (isSelected) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_check_24),
                            contentDescription = stringResource(R.string.selected),
                            tint = EncoreColors.Green,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
