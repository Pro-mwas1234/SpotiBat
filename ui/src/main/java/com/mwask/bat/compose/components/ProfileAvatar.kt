package com.mwask.bat.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreTheme

@Composable
fun ProfileAvatar(
    displayName: String,
    avatarUrl: String = "",
    size: Dp = 36.dp,
    backgroundColor: Color = EncoreColors.Card,
    textStyle: TextStyle? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val base = modifier
        .size(size)
        .clip(CircleShape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }

    if (avatarUrl.isNotBlank()) {
        EncoreArtworkImage(
            imageUrl = avatarUrl,
            contentDescription = displayName,
            modifier = base,
            shape = CircleShape,
            fallbackColor = backgroundColor
        )
    } else {
        val style = textStyle ?: EncoreTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Bold,
            color = EncoreColors.TextPrimary,
            fontSize = (size.value * 0.36f).sp
        )
        Box(modifier = base.background(backgroundColor), contentAlignment = Alignment.Center) {
            Text(
                text = (displayName.trim().firstOrNull() ?: 'D').uppercaseChar().toString(),
                style = style
            )
        }
    }
}
