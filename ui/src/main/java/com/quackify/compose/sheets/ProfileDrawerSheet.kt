package com.quackify.compose.sheets

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.quackify.compose.R
import com.quackify.compose.components.EncoreProfileAvatar
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreAppTheme
import com.quackify.compose.theme.EncoreTheme
import androidx.compose.ui.res.stringResource

/**
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDrawerSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onAccountsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onWhatsNewClick: () -> Unit,
    onListeningHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNotificationsClick: (() -> Unit)? = null,
    notificationsUnread: Boolean = false,
    onLogoutClick: (() -> Unit)? = null,
    userDisplayName: String = "Duck User",
    userAvatarUrl: String = "",
    userColorHex: String = "",
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = DarkSystemBarsSheet,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onAccountsClick()
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EncoreProfileAvatar(
                    userDisplayName = userDisplayName,
                    avatarUrl = userAvatarUrl.ifBlank { null },
                    onClick = {
                        onDismiss()
                        onAccountsClick()
                    },
                    size = 54.dp,
                    colorHex = userColorHex
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userDisplayName.ifEmpty { "Duck User" },
                        style = EncoreTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.switch_account),
                        style = EncoreTheme.typography.bodySmall.copy(
                            color = EncoreColors.TextSecondary
                        )
                    )
                }

                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_chevron_right_24),
                    contentDescription = null,
                    tint = EncoreColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            HorizontalDivider(
                color = EncoreColors.PureBlack.copy(alpha = 0.5f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (onNotificationsClick != null) {
                ProfileDrawerItem(
                    title = stringResource(R.string.notification),
                    iconRes = R.drawable.encore_icon_notifications_active_24,
                    onClick = {
                        onDismiss()
                        onNotificationsClick()
                    },
                    showUnreadDot = notificationsUnread
                )
            }

            ProfileDrawerItem(
                title = stringResource(R.string.my_profile),
                iconRes = R.drawable.encore_icon_user_24,
                onClick = {
                    onDismiss()
                    onProfileClick()
                }
            )

            ProfileDrawerItem(
                title = stringResource(R.string.whats_new_lowercase),
                iconRes = R.drawable.encore_icon_notification_new_24,
                onClick = {
                    onDismiss()
                    onWhatsNewClick()
                }
            )

            ProfileDrawerItem(
                title = stringResource(R.string.listening_history_lowercase),
                iconRes = R.drawable.encore_icon_recently_played_24,
                onClick = {
                    onDismiss()
                    onListeningHistoryClick()
                }
            )

            ProfileDrawerItem(
                title = stringResource(R.string.settings_and_privacy),
                iconRes = R.drawable.encore_icon_tune_24,
                onClick = {
                    onDismiss()
                    onSettingsClick()
                }
            )

            if (onLogoutClick != null) {
                ProfileDrawerItem(
                    title = stringResource(R.string.log_out),
                    iconRes = R.drawable.encore_icon_arrow_left_24,
                    onClick = {
                        onDismiss()
                        onLogoutClick()
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileDrawerItem(
    title: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showUnreadDot: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            tint = EncoreColors.TextPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = EncoreTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            ),
            modifier = Modifier.weight(1f)
        )
        if (showUnreadDot) {
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3D91F4))
            )
        }
    }
}

@Preview
@Composable
fun PreviewProfileDrawerSheet() {
    EncoreAppTheme {
        ProfileDrawerSheet(
            isOpen = true,
            onDismiss = {},
            onAccountsClick = {},
            onProfileClick = {},
            onWhatsNewClick = {},
            onListeningHistoryClick = {},
            onSettingsClick = {}
        )
    }
}
