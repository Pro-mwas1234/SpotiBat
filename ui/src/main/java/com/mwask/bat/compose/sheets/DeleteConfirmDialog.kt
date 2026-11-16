package com.mwask.bat.compose.sheets

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.mwask.bat.compose.theme.EncoreColors
import androidx.compose.ui.res.stringResource
import com.mwask.bat.compose.R

@Composable
internal fun DeleteConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = stringResource(R.string.delete),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EncoreColors.CardElevated,
        title = { Text(title, color = EncoreColors.TextPrimary) },
        text = { Text(message, color = EncoreColors.TextSecondary) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(confirmLabel, color = EncoreColors.Error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = EncoreColors.TextPrimary)
            }
        }
    )
}
