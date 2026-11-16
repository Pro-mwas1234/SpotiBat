package com.mwask.bat.compose.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ColumnScope
import com.mwask.bat.compose.theme.EncoreColors

private val MenuContainer = Color(0xE6191919)

@Composable
fun EncoreMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        containerColor = MenuContainer,
        content = content,
    )
}

@Composable
fun EncoreMenuItem(
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = if (selected) EncoreColors.Green else Color.White,
            )
        },
        onClick = onClick,
    )
}
