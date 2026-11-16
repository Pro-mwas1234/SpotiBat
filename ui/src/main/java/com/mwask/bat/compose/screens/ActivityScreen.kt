package com.mwask.bat.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.backend.model.ActivityNotification
import com.mwask.bat.compose.R
import com.mwask.bat.compose.components.EncoreArtworkImage
import com.mwask.bat.compose.components.EncoreIconButton
import com.mwask.bat.compose.components.ProfileAvatar
import com.mwask.bat.compose.components.ShimmerBlock
import com.mwask.bat.compose.components.ShimmerHost
import com.mwask.bat.compose.components.ShimmerText
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreAppTheme
import com.mwask.bat.compose.theme.EncoreTheme
import com.mwask.bat.compose.theme.rememberBottomPadding
import androidx.compose.ui.res.stringResource

/** Spotify's unread marker blue used by the gander notification feed. */
private val UnreadBlue = Color(0xFF3D91F4)

@Composable
fun ActivityScreen(
    onBackClick: () -> Unit,
    notifications: List<ActivityNotification> = emptyList(),
    loading: Boolean = false,
    loadFailed: Boolean = false,
    onRetry: () -> Unit = {},
    selfName: String = "",
    selfAvatarUrl: String = "",
    selfAvatarColor: Int = 0,
    onOpenNotification: (ActivityNotification) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bottomPadding = rememberBottomPadding()
    val selfColor = remember(selfAvatarColor) {
        if (selfAvatarColor != 0) Color(0xFF000000.toInt() or selfAvatarColor) else EncoreColors.CardElevated
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreTheme.colors.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EncoreIconButton(
                iconRes = R.drawable.encore_icon_arrow_left_24,
                contentDescription = stringResource(R.string.back),
                onClick = onBackClick,
                tint = EncoreColors.TextPrimary
            )
            Text(
                text = stringResource(R.string.notification),
                style = EncoreTheme.typography.titleLarge.copy(fontSize = 20.sp),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            if (selfName.isNotBlank()) {
                ProfileAvatar(
                    displayName = selfName,
                    avatarUrl = selfAvatarUrl,
                    size = 32.dp,
                    backgroundColor = selfColor
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
        }

        when {
            loading && notifications.isEmpty() -> ActivitySkeleton()
            loadFailed && notifications.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = bottomPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_cloud_off_24),
                        contentDescription = null,
                        tint = EncoreColors.TextSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.couldn_t_load_notifications),
                        style = EncoreTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.check_your_internet_connection_and_try_again),
                        style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onRetry) {
                        Text(text = stringResource(R.string.try_again), color = EncoreColors.Green)
                    }
                }
            }
            notifications.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = bottomPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_notifications_active_24),
                        contentDescription = null,
                        tint = EncoreColors.TextSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.no_notifications_yet),
                        style = EncoreTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.when_someone_follows_you_or_interacts_with_your),
                        style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding)
            ) {
                items(notifications, key = { it.id }) { item ->
                    ActivityRow(
                        item = item,
                        onClick = { onOpenNotification(item) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(120.dp))
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(
    item: ActivityNotification,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileAvatar(
            displayName = item.primaryActor.ifBlank { item.title },
            size = 48.dp
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activityTitle(item),
                style = EncoreTheme.typography.bodyMedium,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = relativeTime(item.atMillis),
                style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
            )
        }
        if (item.entityImageUrl.isNotBlank()) {
            EncoreArtworkImage(
                imageUrl = item.entityImageUrl,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                cornerRadius = 4.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        if (item.isNew) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(UnreadBlue)
            )
        }
    }
}

/** Renders the actor's name in bold when the title starts with it. */
@Composable
private fun activityTitle(item: ActivityNotification) = buildAnnotatedString {
    val actor = item.primaryActor
    if (actor.isNotBlank() && item.title.startsWith(actor)) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(actor) }
        append(item.title.substring(actor.length))
    } else {
        append(item.title)
    }
}

@Composable
private fun ActivitySkeleton() {
    ShimmerHost {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(8) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerBlock(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerText(widthFraction = 0.82f)
                        Spacer(modifier = Modifier.height(8.dp))
                        ShimmerText(widthFraction = 0.28f)
                    }
                }
            }
        }
    }
}

@Composable
internal fun relativeTime(atMillis: Long): String {
    if (atMillis <= 0L) return ""
    val minutes = (System.currentTimeMillis() - atMillis) / 60000
    return when {
        minutes < 1 -> stringResource(R.string.just_now)
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d ago"
        else -> {
            val days = minutes / (60 * 24)
            "${days / 7}w ago"
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewActivityScreen() {
    EncoreAppTheme {
        ActivityScreen(
            onBackClick = {},
            selfName = "Duck User",
            notifications = listOf(
                ActivityNotification(
                    id = "1",
                    title = "BRIB0 saved your playlist, My playlist #19",
                    createdTimestamp = "2026-11-13T13:28:17.501Z",
                    actionUri = "spotify:playlist:24NCO8JiZlpAxH2XbtMrAl",
                    actionType = "NAVIGATE",
                    actorNames = listOf("BRIB0"),
                    isNew = true,
                ),
                ActivityNotification(
                    id = "2",
                    title = "BRIB0 started following you",
                    createdTimestamp = "2026-11-13T03:24:10.436Z",
                    actionUri = "spotify:user:31xqn3z3ikq2jusd7567w6oormja",
                    actionType = "NAVIGATE",
                    actorNames = listOf("BRIB0"),
                ),
            )
        )
    }
}
