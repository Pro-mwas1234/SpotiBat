package com.quackify.compose.sheets

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.R
import com.quackify.compose.components.InlineLoadingDots
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme
import com.quackify.compose.util.search.GenericSearchEngine
import com.quackify.compose.util.search.SearchableFieldExtractor
import com.quackify.backend.model.PlaylistChoice
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

private val playlistSearchEngine = GenericSearchEngine<PlaylistChoice>(maxResult = Int.MAX_VALUE)
private val PLAYLIST_CHOICE_FIELDS = SearchableFieldExtractor<PlaylistChoice> { arrayOf(it.name) }

/**
 *
 * Structure:
 * - Top drag handle (36dp x 4dp)
 * - Top header bar: stringResource(R.string.cancel) (left), stringResource(R.string.done) (right)
 * - Section headline: "Saved in" (if saved in 1+ destinations) / "Save in" (if empty) + green stringResource(R.string.new_playlist) button
 * - Search box filtering the visible level locally via the generic fuzzy search engine, playlists and folders alike
 * - Destination rows:
 *   - Liked Songs: 48dp purple-blue gradient square with white heart
 *   - Playlists: 48dp artwork, title, subtitle (e.g. "142 songs"), trailing green check (✓) or outline plus (+)
 *   - + New playlist row at the bottom
 * - Floating bottom green pill button: stringResource(R.string.done)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    isOpen: Boolean,
    trackTitle: String,
    isTargetLiked: Boolean = false,
    choices: List<PlaylistChoice>,
    isLoading: Boolean,
    onPick: (PlaylistChoice) -> Unit,
    onToggle: ((choice: PlaylistChoice, isAdding: Boolean) -> Unit)? = null,
    onLoadFolder: (suspend (folderUri: String) -> List<PlaylistChoice>)? = null,
    onLoadTrackCount: (suspend (playlistUri: String) -> Int)? = null,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return


    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    var currentFolder by remember { mutableStateOf<PlaylistChoice?>(null) }
    var currentRows by remember { mutableStateOf<List<PlaylistChoice>?>(null) }
    val parentLevels = remember { mutableStateListOf<Pair<PlaylistChoice?, List<PlaylistChoice>>>() }
    var folderLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val rows = currentRows ?: choices
    val inFolder = currentFolder != null
    val visibleRows = remember(rows, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) rows
        else playlistSearchEngine.filter(rows, query, PLAYLIST_CHOICE_FIELDS)
    }

    val selectionState = remember {
        mutableStateMapOf<String, Boolean>().apply {
            choices.forEach { choice ->
                val initialSelected = if (choice.uri == PlaylistChoice.LIKED_SONGS_URI) {
                    choice.containsTrack || isTargetLiked
                } else {
                    choice.containsTrack
                }
                put(choice.uri, initialSelected)
            }
        }
    }

    LaunchedEffect(choices) {
        choices.forEach { choice ->
            if (!selectionState.containsKey(choice.uri)) {
                val initialSelected = if (choice.uri == PlaylistChoice.LIKED_SONGS_URI) {
                    choice.containsTrack || isTargetLiked
                } else {
                    choice.containsTrack
                }
                selectionState[choice.uri] = initialSelected
            }
        }
    }

    val savedCount = selectionState.values.count { it }

    val trackCounts = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(visibleRows) {
        val loader = onLoadTrackCount ?: return@LaunchedEffect
        visibleRows.asSequence()
            .filter { it.kind != PlaylistChoice.Kind.FOLDER && !trackCounts.containsKey(it.uri) }
            .map { it.uri }
            .toList()
            .chunked(6)
            .forEach { batch ->
                coroutineScope {
                    batch.map { uri ->
                        async {
                            val count = loader(uri)
                            if (count >= 0) trackCounts[uri] = count
                        }
                    }.awaitAll()
                }
            }
    }

    fun handleToggle(choice: PlaylistChoice) {
        val currentlySelected = selectionState[choice.uri] ?: choice.containsTrack
        val next = !currentlySelected
        selectionState[choice.uri] = next
        if (onToggle != null) {
            onToggle(choice, next)
        } else {
            onPick(choice)
        }
    }

    fun openFolder(folder: PlaylistChoice) {
        val loader = onLoadFolder
        if (loader == null || folderLoading) return
        scope.launch {
            folderLoading = true
            val loaded = loader(folder.uri)
            parentLevels += currentFolder to (currentRows ?: choices)
            currentFolder = folder
            currentRows = loaded
            searchQuery = ""
            folderLoading = false
        }
    }

    fun backOutOfFolder() {
        val parent = parentLevels.removeLastOrNull() ?: return
        currentFolder = parent.first
        currentRows = parent.second
        searchQuery = ""
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = BackNotDismissSheet,
        sheetState = sheetState,
        containerColor = EncoreColors.Card,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
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
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    style = EncoreTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = EncoreColors.TextPrimary,
                        fontSize = 15.sp
                    ),
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 4.dp)
                )

                Text(
                    text = stringResource(R.string.done),
                    style = EncoreTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = EncoreColors.TextPrimary,
                        fontSize = 15.sp
                    ),
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (inFolder) currentFolder?.name.orEmpty()
                    else if (savedCount > 0) stringResource(R.string.saved_in) else stringResource(R.string.save_in),
                    style = EncoreTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = EncoreColors.TextPrimary
                    )
                )

                if (!inFolder) {
                    Text(
                        text = stringResource(R.string.new_playlist),
                        style = EncoreTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = EncoreColors.Green,
                            fontSize = 14.sp
                        ),
                        modifier = Modifier
                            .clickable(onClick = onCreateNew)
                            .padding(vertical = 4.dp)
                    )
                }
            }

            AddToPlaylistSearchBox(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            when {
                isLoading || folderLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    InlineLoadingDots(color = EncoreColors.TextSecondary)
                }

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    if (inFolder) {
                        item(key = "__back__") {
                            val parentName = parentLevels.lastOrNull()?.first?.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { backOutOfFolder() }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_chevron_left_24),
                                    contentDescription = null,
                                    tint = EncoreColors.TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = parentName?.let { "Back to $it" } ?: "Back to your playlists",
                                    style = EncoreTheme.typography.bodyMedium.copy(
                                        color = EncoreColors.TextSecondary,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (visibleRows.isEmpty()) {
                        item(key = "__empty__") {
                            Text(
                                text = when {
                                    searchQuery.isNotBlank() -> "No results for \"${searchQuery.trim()}\""
                                    inFolder -> stringResource(R.string.this_folder_empty)
                                    else -> stringResource(R.string.no_playlists_you_can_add_to)
                                },
                                style = EncoreTheme.typography.bodyMedium.copy(
                                    color = EncoreColors.TextSecondary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    items(visibleRows, key = { it.uri }) { choice ->
                        if (choice.kind == PlaylistChoice.Kind.FOLDER) {
                            AddToFolderRow(folder = choice, onOpen = { openFolder(choice) })
                        } else {
                            val isSelected = selectionState[choice.uri] ?: choice.containsTrack
                            AddToPlaylistRow(
                                choice = choice,
                                isSelected = isSelected,
                                songCount = trackCounts[choice.uri],
                                onToggle = { handleToggle(choice) }
                            )
                        }
                    }

                    if (!inFolder) {
                        item(key = "__new_bottom_row__") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCreateNew() }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF282828)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.encore_icon_plus_24),
                                        contentDescription = stringResource(R.string.new_playlist),
                                        tint = EncoreColors.TextSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.new_playlist),
                                    style = EncoreTheme.typography.bodyMedium.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = EncoreColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(EncoreColors.Green)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.done),
                        style = EncoreTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.Black
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun AddToPlaylistSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
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
                            text = stringResource(R.string.search),
                            style = EncoreTheme.typography.bodyMedium.copy(
                                color = EncoreColors.TextSecondary,
                                fontSize = 15.sp
                            )
                        )
                    }
                    field()
                }
            }
        )
        if (query.isNotEmpty()) {
            Icon(
                painter = painterResource(id = R.drawable.encore_icon_x_24),
                contentDescription = stringResource(R.string.clear_search),
                tint = EncoreColors.TextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable { onQueryChange("") }
            )
        }
    }
}

/**
 * When [choice.kind] is [PlaylistChoice.Kind.LIKED_SONGS], displays the official purple-blue gradient with heart.
 */
@Composable
private fun AddToPlaylistRow(
    choice: PlaylistChoice,
    isSelected: Boolean,
    songCount: Int?,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (choice.kind == PlaylistChoice.Kind.LIKED_SONGS) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF450AF5), Color(0xFF8E8EE5))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_heart_active_24),
                    contentDescription = choice.name,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            EncoreArtworkImage(
                imageUrl = choice.imageUrl.takeIf { it.isNotBlank() },
                contentDescription = choice.name,
                modifier = Modifier.size(48.dp),
                cornerRadius = 4.dp
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = choice.name,
                style = EncoreTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = EncoreColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (songCount != null && songCount >= 0) {
                Text(
                    text = "$songCount song" + if (songCount == 1) "" else "s",
                    style = EncoreTheme.typography.bodySmall.copy(
                        color = EncoreColors.TextSecondary,
                        fontSize = 13.sp
                    ),
                    maxLines = 1
                )
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(EncoreColors.Green),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_check_24),
                    contentDescription = "Saved in ${choice.name}",
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(
                        BorderStroke(1.5.dp, EncoreColors.TextSecondary.copy(alpha = 0.65f)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_plus_24),
                    contentDescription = "Save in ${choice.name}",
                    tint = EncoreColors.TextSecondary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/** A library folder: picking it drills into the playlists inside it. */
@Composable
private fun AddToFolderRow(
    folder: PlaylistChoice,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF282828)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.encore_icon_folder_24),
                contentDescription = null,
                tint = EncoreColors.TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = folder.name,
            style = EncoreTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            ),
            color = EncoreColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(id = R.drawable.encore_icon_chevron_right_24),
            contentDescription = "Open ${folder.name}",
            tint = EncoreColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}
