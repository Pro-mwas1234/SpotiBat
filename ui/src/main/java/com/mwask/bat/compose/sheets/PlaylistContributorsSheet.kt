package com.mwask.bat.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.backend.model.PlaylistMember
import com.mwask.bat.backend.model.PlaylistPermissions
import com.mwask.bat.backend.model.SearchHit
import com.mwask.bat.backend.model.SearchKind
import com.mwask.bat.compose.R
import com.mwask.bat.compose.components.EncoreProfileAvatar
import com.mwask.bat.compose.components.InlineLoadingDots
import com.mwask.bat.compose.components.ShimmerBlock
import com.mwask.bat.compose.components.ShimmerHost
import com.mwask.bat.compose.components.ShimmerText
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreAppTheme
import com.mwask.bat.compose.theme.EncoreTheme
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistContributorsSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    permissions: PlaylistPermissions?,
    loading: Boolean,
    onRetry: () -> Unit = {},
    busyUserIds: Set<String> = emptySet(),
    onSearchUsers: (suspend (String) -> List<SearchHit>)? = null,
    onAddContributor: (String) -> Unit,
    onRemoveAsContributor: (String) -> Unit,
    onRemoveFromPlaylist: (String) -> Unit,
    onOpenProfile: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return


    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var addSearchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SearchHit>>(emptyList()) }

    val isAdmin = permissions?.canAdministratePermissions == true
    val members = remember(permissions) {
        permissions?.members?.sortedWith(
            compareByDescending<PlaylistMember> { it.isOwner }
                .thenBy { rankOf(it) }
                .thenBy { it.name.lowercase() }
        ).orEmpty()
    }
    val memberUris = remember(members) { members.map { it.uri }.toHashSet() }

    LaunchedEffect(query, addSearchOpen) {
        if (!addSearchOpen) return@LaunchedEffect
        val term = query.trim()
        if (term.length < 2) {
            searchResults = emptyList()
            searching = false
            return@LaunchedEffect
        }
        delay(320)
        if (query.trim() != term) return@LaunchedEffect
        val loader = onSearchUsers ?: return@LaunchedEffect
        searching = true
        val hits = runCatching { loader(term) }.getOrDefault(emptyList())
        if (query.trim() == term) {
            searchResults = hits.filter {
                it.kind == SearchKind.USER && it.uri.startsWith("spotify:user:") && it.uri !in memberUris
            }
            searching = false
        }
    }

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
                text = stringResource(R.string.contributors),
                style = EncoreTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                when {
                    addSearchOpen -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ContributorSearchBox(
                                query = query,
                                onQueryChange = { query = it },
                                onClose = {
                                    addSearchOpen = false
                                    query = ""
                                    searchResults = emptyList()
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            when {
                                searching -> Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    InlineLoadingDots(color = EncoreColors.TextSecondary)
                                }
                                searchResults.isEmpty() -> Text(
                                    text = if (query.trim().length < 2) stringResource(R.string.type_at_least_2_characters)
                                    else stringResource(R.string.no_profiles_found),
                                    style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp)
                                )
                                else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                    items(searchResults, key = { it.uri }) { hit ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onAddContributor(hit.uri.substringAfterLast(":"))
                                                    addSearchOpen = false
                                                    query = ""
                                                    searchResults = emptyList()
                                                }
                                                .padding(vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            EncoreProfileAvatar(
                                                userDisplayName = hit.title,
                                                onClick = {},
                                                avatarUrl = hit.imageUrl.takeIf { it.isNotBlank() },
                                                size = 44.dp
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = hit.title,
                                                    style = EncoreTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = hit.subtitle.ifBlank { stringResource(R.string.profile) },
                                                    style = EncoreTheme.typography.marginal.copy(
                                                        color = EncoreColors.TextSecondary
                                                    )
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .border(1.dp, EncoreColors.Outline, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.encore_icon_plus_24),
                                                    contentDescription = stringResource(R.string.add_contributor),
                                                    tint = EncoreColors.TextPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    loading && members.isEmpty() -> ShimmerHost {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            repeat(4) {
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
                                        ShimmerText(widthFraction = 0.5f)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        ShimmerText(widthFraction = 0.3f)
                                    }
                                }
                            }
                        }
                    }
                    members.isEmpty() && permissions == null -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.couldn_t_load_contributors),
                            style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextPrimary)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.try_again),
                            style = EncoreTheme.typography.bodyMediumBold.copy(color = EncoreColors.Green),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClick = onRetry)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    members.isEmpty() -> Text(
                        text = stringResource(R.string.no_contributors_yet),
                        style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        if (isAdmin && onSearchUsers != null) {
                            item(key = "__add__") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { addSearchOpen = true }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(EncoreColors.CardElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.encore_icon_plus_24),
                                            contentDescription = null,
                                            tint = EncoreColors.TextPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = stringResource(R.string.add_contributor),
                                        style = EncoreTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = EncoreColors.TextPrimary
                                        )
                                    )
                                }
                            }
                        }
                        items(members, key = { it.uri }) { member ->
                            ContributorRow(
                                member = member,
                                isAdmin = isAdmin,
                                busy = member.userId in busyUserIds,
                                onOpenProfile = onOpenProfile,
                                onRemoveAsContributor = onRemoveAsContributor,
                                onRemoveFromPlaylist = onRemoveFromPlaylist
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun rankOf(member: PlaylistMember): Int = when (member.permissionLevel) {
    PlaylistMember.PERMISSION_CONTRIBUTOR -> 0
    PlaylistMember.PERMISSION_VIEWER -> 1
    PlaylistMember.PERMISSION_BLOCKED -> 2
    else -> 3
}

@Composable
private fun statusOf(member: PlaylistMember): String = when {
    member.isOwner -> stringResource(R.string.owner)
    member.permissionLevel == PlaylistMember.PERMISSION_CONTRIBUTOR -> stringResource(R.string.contributor)
    member.permissionLevel == PlaylistMember.PERMISSION_VIEWER -> stringResource(R.string.viewer)
    member.permissionLevel == PlaylistMember.PERMISSION_BLOCKED -> stringResource(R.string.blocked)
    else -> stringResource(R.string.member)
}

@Composable
private fun ContributorRow(
    member: PlaylistMember,
    isAdmin: Boolean,
    busy: Boolean,
    onOpenProfile: (String, String) -> Unit,
    onRemoveAsContributor: (String) -> Unit,
    onRemoveFromPlaylist: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EncoreProfileAvatar(
            userDisplayName = member.name,
            onClick = { onOpenProfile(member.uri, member.name) },
            avatarUrl = member.avatarUrl.takeIf { it.isNotBlank() },
            size = 48.dp
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.name,
                style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = statusOf(member),
                style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
            )
            if (isAdmin && !member.isOwner && member.permissionLevel != PlaylistMember.PERMISSION_BLOCKED) {
                Spacer(modifier = Modifier.height(8.dp))
                if (busy) {
                    InlineLoadingDots(color = EncoreColors.TextSecondary)
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        if (member.permissionLevel == PlaylistMember.PERMISSION_CONTRIBUTOR) {
                            ContributorActionLabel(
                                text = stringResource(R.string.remove_as_contributor),
                                onClick = { onRemoveAsContributor(member.userId) }
                            )
                        }
                        ContributorActionLabel(
                            text = stringResource(R.string.remove_from_playlist),
                            onClick = { onRemoveFromPlaylist(member.userId) }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, EncoreColors.Outline, RoundedCornerShape(16.dp))
                .clickable { onOpenProfile(member.uri, member.name) }
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                text = stringResource(R.string.view_profile),
                style = EncoreTheme.typography.bodySmall.copy(
                    color = EncoreColors.TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun ContributorActionLabel(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = EncoreTheme.typography.bodySmall.copy(
            color = EncoreColors.Error,
            fontWeight = FontWeight.SemiBold
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    )
}

@Composable
private fun ContributorSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(EncoreColors.Background)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.encore_icon_search_24),
            contentDescription = null,
            tint = EncoreColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = EncoreTheme.typography.bodyMedium.copy(
                color = EncoreColors.TextPrimary,
                fontSize = 15.sp
            ),
            cursorBrush = SolidColor(EncoreColors.TextPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f),
            decorationBox = { field ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_usernames),
                            style = EncoreTheme.typography.bodyMedium.copy(
                                color = EncoreColors.TextSecondary,
                                fontSize = 15.sp
                            )
                        )
                    } else {
                        field()
                    }
                }
            }
        )
        if (query.isNotEmpty()) {
            Icon(
                painter = painterResource(id = R.drawable.encore_icon_x_24),
                contentDescription = stringResource(R.string.clear_search),
                tint = EncoreColors.TextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onQueryChange("") }
            )
        } else {
            Icon(
                painter = painterResource(id = R.drawable.encore_icon_x_24),
                contentDescription = stringResource(R.string.close_search),
                tint = EncoreColors.TextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onClose)
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewPlaylistContributorsSheet() {
    EncoreAppTheme {
        PlaylistContributorsSheet(
            isOpen = true,
            onDismiss = {},
            permissions = PlaylistPermissions(
                uri = "spotify:playlist:preview",
                members = listOf(
                    PlaylistMember(
                        userId = "owner",
                        uri = "spotify:user:owner",
                        name = "Malrusez",
                        isOwner = true,
                        permissionLevel = "CONTRIBUTOR"
                    ),
                    PlaylistMember(
                        userId = "friend",
                        uri = "spotify:user:friend",
                        name = "BRIB0",
                        permissionLevel = "CONTRIBUTOR"
                    )
                ),
                canAdministratePermissions = true
            ),
            loading = false,
            onAddContributor = {},
            onRemoveAsContributor = {},
            onRemoveFromPlaylist = {},
            onOpenProfile = { _, _ -> }
        )
    }
}
