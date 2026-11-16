package com.mwask.bat.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.backend.model.ProfileUser
import com.mwask.bat.compose.R
import com.mwask.bat.compose.components.EncoreChip
import com.mwask.bat.compose.components.EncoreProfileAvatar
import com.mwask.bat.compose.components.ShimmerBlock
import com.mwask.bat.compose.components.ShimmerHost
import com.mwask.bat.compose.components.ShimmerText
import com.mwask.bat.compose.format.formatCount
import com.mwask.bat.compose.format.parseHexColor
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreAppTheme
import com.mwask.bat.compose.theme.EncoreTheme
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileFollowSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    followers: List<ProfileUser>,
    following: List<ProfileUser>,
    loading: Boolean,
    onArtistClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return


    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var selectedTab by remember { mutableIntStateOf(0) }
    val people = if (selectedTab == 0) followers else following

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
            Text(
                text = stringResource(R.string.followers_and_following),
                style = EncoreTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    EncoreChip(
                        text = stringResource(R.string.followers),
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                }
                item {
                    EncoreChip(
                        text = stringResource(R.string.following),
                        isSelected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                when {
                    loading && people.isEmpty() -> ShimmerHost {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            repeat(6) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ShimmerBlock(
                                        modifier = Modifier.size(48.dp),
                                        shape = CircleShape
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        ShimmerText(widthFraction = 0.55f)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        ShimmerText(widthFraction = 0.35f)
                                    }
                                }
                            }
                        }
                    }
                    people.isEmpty() -> Text(
                        text = if (selectedTab == 0) stringResource(R.string.no_followers_yet) else stringResource(R.string.not_following_anyone),
                        style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(people) { person ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (person.isArtist) {
                                            Modifier.clickable { onArtistClick(person.name, person.uri) }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                EncoreProfileAvatar(
                                    userDisplayName = person.name,
                                    onClick = {
                                        if (person.isArtist) onArtistClick(person.name, person.uri)
                                    },
                                    avatarUrl = person.imageUrl.takeIf { it.isNotBlank() },
                                    size = 48.dp,
                                    backgroundColor = parseHexColor(person.colorHex)
                                        ?: EncoreColors.CardElevated
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = person.name,
                                        style = EncoreTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    if (person.followersCount > 0L) {
                                        Text(
                                            text = "${formatCount(person.followersCount)} followers",
                                            style = EncoreTheme.typography.marginal.copy(
                                                color = EncoreColors.TextSecondary
                                            )
                                        )
                                    }
                                }
                                if (person.isArtist) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.encore_icon_chevron_right_24),
                                        contentDescription = null,
                                        tint = EncoreColors.TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewProfileFollowSheet() {
    EncoreAppTheme {
        ProfileFollowSheet(
            isOpen = true,
            onDismiss = {},
            followers = emptyList(),
            following = emptyList(),
            loading = false,
            onArtistClick = { _, _ -> }
        )
    }
}
