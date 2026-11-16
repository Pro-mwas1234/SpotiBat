package com.mwask.bat.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.compose.shell.FolderDraft
import com.mwask.bat.compose.shell.FolderMode
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreTheme
import androidx.compose.ui.res.stringResource
import com.mwask.bat.compose.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderSheet(
    draft: FolderDraft?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    onPick: (String) -> Unit,
    canRemoveFromFolder: Boolean = false,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (draft == null) return


    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var name by remember(draft) {
        mutableStateOf(if (draft.mode == FolderMode.RENAME) draft.title else "")
    }
    var confirmingDelete by remember(draft) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = BackNotDismissSheet,
        sheetState = sheetState,
        containerColor = EncoreColors.Card,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(EncoreColors.TextSecondary.copy(alpha = 0.5f))
            )
        },
        modifier = modifier
    ) {
        BackHandler { onDismiss() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            val heading = when (draft.mode) {
                FolderMode.CREATE -> stringResource(R.string.new_folder)
                FolderMode.RENAME -> stringResource(R.string.rename_folder)
                FolderMode.PICKER -> stringResource(R.string.move_to_folder)
            }
            Text(
                text = heading,
                style = EncoreTheme.typography.titleLarge.copy(fontSize = 18.sp),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            if (draft.mode == FolderMode.PICKER) {
                Text(
                    text = draft.title,
                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f)
                ) {
                    items(draft.folders, key = { it.first }) { folder ->
                        PickerRow(
                            title = folder.second,
                            subtitle = "",
                            onClick = { onPick(folder.first) }
                        )
                    }
                    if (canRemoveFromFolder) {
                        item {
                            PickerRow(
                                title = stringResource(R.string.remove_from_folder),
                                subtitle = "",
                                onClick = { onPick("") }
                            )
                        }
                    }
                    if (draft.folders.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.no_folders_yet_create_one_from_the_button),
                                style = EncoreTheme.typography.bodyMedium.copy(
                                    color = EncoreColors.TextSecondary
                                ),
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                return@Column
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                singleLine = true,
                label = { Text(stringResource(R.string.folder_name)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (name.isBlank()) {
                            EncoreColors.TextSecondary.copy(alpha = 0.25f)
                        } else {
                            EncoreColors.Green
                        }
                    )
                    .clickable(enabled = name.isNotBlank()) { onSubmit(name.trim()) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (draft.mode == FolderMode.CREATE) stringResource(R.string.create) else stringResource(R.string.save),
                    style = EncoreTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (name.isBlank()) EncoreColors.TextSecondary else Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (draft.mode == FolderMode.RENAME && onDelete != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (confirmingDelete) {
                                Color(0xFFB3261E)
                            } else {
                                EncoreColors.TextSecondary.copy(alpha = 0.18f)
                            }
                        )
                        .clickable {
                            if (confirmingDelete) onDelete() else confirmingDelete = true
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (confirmingDelete) {
                            stringResource(R.string.tap_again_to_delete_folder)
                        } else {
                            stringResource(R.string.delete_folder)
                        },
                        style = EncoreTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (confirmingDelete) Color.White else EncoreColors.TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun PickerRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = EncoreTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            ),
            color = EncoreColors.TextPrimary
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
            )
        }
    }
}
