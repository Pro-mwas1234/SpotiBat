package com.mwask.bat.compose.search

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.compose.components.EncoreArtworkImage
import com.mwask.bat.compose.components.EncoreHeartButton
import com.mwask.bat.compose.components.InlineLoadingDots
import com.mwask.bat.compose.components.TrackListSkeleton
import com.mwask.bat.compose.components.matchesCurrent
import com.mwask.bat.compose.R
import com.mwask.bat.compose.components.EncoreExplicitTag
import com.mwask.bat.compose.components.EncoreChip
import com.mwask.bat.compose.components.EncoreProfileAvatar
import com.mwask.bat.compose.components.EncoreIconButton
import com.mwask.bat.compose.components.EncorePlayButton
import com.mwask.bat.compose.components.EncorePlayIndicator
import com.mwask.bat.compose.components.SwipeToQueueBox
import com.mwask.bat.compose.playlist.TrackItem
import com.mwask.bat.compose.format.parseHexColor
import com.mwask.bat.backend.model.SearchHit
import com.mwask.bat.backend.model.SearchKind
import com.mwask.bat.backend.model.SearchResults
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreAppTheme
import com.mwask.bat.compose.theme.EncoreTheme
import com.mwask.bat.compose.theme.rememberBottomPadding
import com.mwask.bat.compose.util.rememberIsOffline
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource

data class CategoryCard(
    val title: String,
    val color: Color,
    val uri: String = "",
    val imageUrl: String? = null,
    val subtitle: String = ""
)

data class SearchArtistItem(
    val name: String,
    val monthlyListeners: String,
    val color: Color,
    val uri: String = "",
    val imageUrl: String = "",
    val saved: Boolean = false
)

data class SearchAlbumItem(
    val title: String,
    val artist: String,
    val year: String,
    val color: Color,
    val uri: String = "",
    val imageUrl: String = "",
    val saved: Boolean = false
)

data class SearchPlaylistItem(
    val title: String,
    val creator: String,
    val followerCount: String,
    val color: Color,
    val uri: String = "",
    val imageUrl: String = ""
)

data class SearchPodcastItem(
    val title: String,
    val publisher: String,
    val color: Color,
    val description: String,
    val uri: String = "",
    val imageUrl: String = ""
)

data class SearchHistoryItem(
    val title: String,
    val subtitle: String,
    val kind: SearchKind = SearchKind.QUERY,
    val colorHex: String = "",
    val uri: String = "",
    val imageUrl: String = ""
)

/**
 * - Profile avatar shortcut
 * - Interactive Search pill with Voice Mic and Clear button
 * - In-search category filter pills (Top, Songs, Artists, Playlists, Albums, Podcasts)
 * - Top result card, Songs list with 3-dot context actions, and Artists cards
 * - Recent Searches list with stringResource(R.string.clear_recent_searches) button
 * - 2-column colorful Browse All categories grid with angled album peek (96dp category_card_max_height)
 */
@Composable
fun EncoreSearchScreen(
    onCategoryClick: (CategoryCard) -> Unit,
    onTrackSelect: (TrackItem) -> Unit = {},
    onTrackOptionsClick: (TrackItem) -> Unit = {},
    onTrackAddToQueue: ((TrackItem) -> Unit)? = null,
    onProfileClick: () -> Unit = {},
    onAlbumClick: (String, String, String) -> Unit = { _, _, _ -> },
    onArtistClick: (String, String) -> Unit = { _, _ -> },
    onPodcastClick: (String, String) -> Unit = { _, _ -> },
    onEpisodeClick: (String, String) -> Unit = { _, _ -> },
    onShowClick: (String, String) -> Unit = { _, _ -> },
    onUserClick: (String, String) -> Unit = { _, _ -> },
    searchData: SearchResults? = null,
    onPlaylistClick: (String, String) -> Unit = { _, _ -> },
    searchResults: List<TrackItem>? = null,
    onSearchQueryChange: (String) -> Unit = {},
    onSearchSubmit: (String) -> Unit = {},
    recentSearches: List<SearchHistoryItem> = emptyList(),
    suggestions: List<TrackItem> = emptyList(),
    suggestionHits: List<SearchHit> = emptyList(),
    loading: Boolean = false,
    loadFailed: Boolean = false,
    onRetry: () -> Unit = {},
    onClearRecent: () -> Unit = {},
    onRemoveRecent: (SearchHistoryItem) -> Unit = {},
    onRecordRecent: (SearchHit) -> Unit = {},
    onLoadSection: (suspend (SearchKind, Int) -> List<SearchHit>)? = null,
    onToggleSaved: (uri: String, currentlySaved: Boolean) -> Unit = { _, _ -> },
    savedOverrides: Map<String, Boolean> = emptyMap(),
    browseCategories: List<CategoryCard> = emptyList(),
    userDisplayName: String = "Duck User",
    userAvatarUrl: String = "",
    userColorHex: String = "",
    currentTrackTitle: String = "",
    currentTrackUri: String = "",
    isPlaying: Boolean = false,
    searchFieldFocusRequester: FocusRequester? = null,
    initialQuery: String = "",
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf(initialQuery) }
    var selectedSearchTab by remember { mutableIntStateOf(0) }
    var isSearchBarFocused by remember { mutableStateOf(false) }
    var suggestionsPinned by remember { mutableStateOf(false) }
    val searchTabs = listOf("Top", stringResource(R.string.songs), stringResource(R.string.artists), stringResource(R.string.playlists), stringResource(R.string.albums), "Podcasts", stringResource(R.string.profiles))
    val focusManager = LocalFocusManager.current
    val bottomPadding = rememberBottomPadding()

    val isOffline = rememberIsOffline()

    val appContext = LocalContext.current
    val voicePrompt = stringResource(R.string.what_do_you_want_to_listen_to)
    val speechIntent = remember(voicePrompt) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
        }
    }
    val canVoiceSearch = remember {
        appContext.packageManager.queryIntentActivities(speechIntent, 0).isNotEmpty()
    }
    val voiceSearchLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val heard = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull().orEmpty()
        if (heard.isNotBlank()) {
            suggestionsPinned = false
            searchQuery = heard
            onSearchQueryChange(heard)
        }
    }

    val recentSearches = remember(recentSearches) {
        mutableStateListOf<SearchHistoryItem>().apply { addAll(recentSearches) }
    }

    val categories = remember(browseCategories) { browseCategories }

    var categoriesFailed by remember { mutableStateOf(false) }
    LaunchedEffect(categories.isEmpty()) {
        categoriesFailed = false
        if (categories.isEmpty()) {
            delay(6_000)
            categoriesFailed = true
        }
    }

    val displaySearchData = remember(searchData, savedOverrides) {
        savedOverrides.entries.fold(searchData) { acc, (uri, saved) ->
            acc?.withSaved(uri, saved)
        }
    }

    val allSearchableTracks = remember(displaySearchData) {
        displaySearchData?.tracks.orEmpty().map { it.toTrackItem() }
    }
    val allSearchableArtists = remember(displaySearchData) {
        displaySearchData?.artists.orEmpty().map { hit ->
            SearchArtistItem(
                name = hit.title,
                monthlyListeners = hit.subtitle,
                color = parseHexColor(hit.colorHex) ?: Color(0xFF1E3264),
                uri = hit.uri,
                imageUrl = hit.imageUrl,
                saved = hit.saved
            )
        }
    }
    val allSearchableAlbums = remember(displaySearchData) {
        displaySearchData?.albums.orEmpty().map { hit ->
            SearchAlbumItem(
                title = hit.title,
                artist = hit.subtitle,
                year = "",
                color = parseHexColor(hit.colorHex) ?: Color(0xFF1E3264),
                uri = hit.uri,
                imageUrl = hit.imageUrl,
                saved = hit.saved
            )
        }
    }
    val allSearchablePlaylists = remember(displaySearchData) {
        displaySearchData?.playlists.orEmpty().map { hit ->
            SearchPlaylistItem(
                title = hit.title,
                creator = hit.subtitle,
                followerCount = "",
                color = parseHexColor(hit.colorHex) ?: Color(0xFF1E3264),
                uri = hit.uri,
                imageUrl = hit.imageUrl
            )
        }
    }

    val allSearchablePodcasts = remember(displaySearchData) {
        displaySearchData?.others.orEmpty()
            .filter { it.kind == SearchKind.SHOW || it.kind == SearchKind.EPISODE }
            .map { hit ->
                SearchPodcastItem(
                    title = hit.title,
                    publisher = hit.subtitle,
                    color = parseHexColor(hit.colorHex) ?: Color(0xFF1E3264),
                    description = "",
                    uri = hit.uri,
                    imageUrl = hit.imageUrl
                )
            }
    }

    val filteredTracks = remember(searchQuery, searchResults, suggestions, loading, displaySearchData) {
        when {
            searchQuery.isBlank() -> emptyList()
            !displaySearchData?.tracks.isNullOrEmpty() -> displaySearchData!!.tracks.map { it.toTrackItem() }
            !searchResults.isNullOrEmpty() -> searchResults
            !loading && suggestions.isNotEmpty() -> suggestions
            else -> allSearchableTracks.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val filteredArtists = remember(displaySearchData, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else allSearchableArtists.filter {
            it.name.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredAlbums = remember(displaySearchData, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else allSearchableAlbums.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredPlaylists = remember(displaySearchData, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else allSearchablePlaylists.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.creator.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredPodcasts = remember(displaySearchData, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else allSearchablePodcasts.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.publisher.contains(searchQuery, ignoreCase = true)
        }
    }

    val isSearching = searchQuery.isNotBlank() || isSearchBarFocused
    val showRecentSearches = isSearchBarFocused && searchQuery.isEmpty() && recentSearches.isNotEmpty()
    val showSuggestions = (isSearchBarFocused || suggestionsPinned) &&
            searchQuery.isNotBlank() && suggestionHits.isNotEmpty()

    var sectionHits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var sectionExhausted by remember { mutableStateOf(false) }
    var sectionLoadingMore by remember { mutableStateOf(false) }

    val tabKind = kindOfTab(selectedSearchTab)
    val currentLoader by rememberUpdatedState(onLoadSection)

    LaunchedEffect(tabKind, searchQuery, isSearchBarFocused) {
        if (tabKind == null || currentLoader == null || isSearchBarFocused || searchQuery.isBlank()) {
            sectionHits = emptyList()
            sectionExhausted = true
            return@LaunchedEffect
        }
        sectionHits = emptyList()
        sectionExhausted = false
        val first = currentLoader?.invoke(tabKind, 0).orEmpty()
        sectionHits = first
        sectionExhausted = first.isEmpty()
    }

    val loadMoreSection: suspend () -> Unit = {
        val kind = tabKind
        if (kind != null && currentLoader != null && !sectionLoadingMore && !sectionExhausted) {
            sectionLoadingMore = true
            val next = currentLoader?.invoke(kind, sectionHits.size).orEmpty()
            if (next.isEmpty()) sectionExhausted = true else sectionHits = sectionHits + next
            sectionLoadingMore = false
        }
    }

    val tabHits = remember(selectedSearchTab, sectionHits, displaySearchData, savedOverrides) {
        if (sectionHits.isNotEmpty()) {
            sectionHits.map { hit -> savedOverrides[hit.uri]?.let { hit.copy(saved = it) } ?: hit }
        } else {
            tabKind?.let { displaySearchData?.of(it).orEmpty() }.orEmpty()
        }
    }

    val playFromSearch: (TrackItem) -> Unit = { track ->
        if (isSearchBarFocused) {
            isSearchBarFocused = false
            focusManager.clearFocus()
        }
        onTrackSelect(track)
    }

    val openHit: (SearchHit) -> Unit = { hit ->
        onRecordRecent(hit)
        when (hit.kind) {
            SearchKind.ARTIST -> onArtistClick(hit.title, hit.uri)
            SearchKind.ALBUM -> onAlbumClick(hit.title, hit.subtitle, hit.uri)
            SearchKind.PLAYLIST -> onPlaylistClick(hit.title, hit.uri)
            SearchKind.EPISODE -> onEpisodeClick(hit.title, hit.uri)
            SearchKind.SHOW -> onShowClick(hit.title, hit.uri)
            SearchKind.USER -> onUserClick(hit.uri, hit.title)
            SearchKind.TRACK -> playFromSearch(hit.toTrackItem())
            else -> Unit
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EncoreColors.Background)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (!isSearching) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EncoreProfileAvatar(
                        userDisplayName = userDisplayName,
                        avatarUrl = userAvatarUrl.ifBlank { null },
                        onClick = onProfileClick,
                        colorHex = userColorHex
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = stringResource(R.string.search),
                        style = EncoreTheme.typography.titleMedium.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = EncoreColors.TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSearching) {
                    EncoreIconButton(
                        iconRes = R.drawable.encore_icon_chevron_left_24,
                        contentDescription = stringResource(R.string.back),
                        onClick = {
                            searchQuery = ""
                            selectedSearchTab = 0
                            suggestionsPinned = false
                            isSearchBarFocused = false
                            focusManager.clearFocus()
                            onSearchQueryChange("")
                        },
                        size = 48.dp,
                        iconSize = 24.dp,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

                BasicTextField(
                    value = searchQuery,
                    onValueChange = {
                        suggestionsPinned = false
                        searchQuery = it
                        onSearchQueryChange(it)
                    },
                    textStyle = EncoreTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = if (isSearching) Color.White else Color(0xFF121212)
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(EncoreColors.Green),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        if (searchQuery.isNotBlank()) onSearchSubmit(searchQuery.trim())
                    }),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_search_24),
                                contentDescription = stringResource(R.string.search),
                                tint = if (isSearching) EncoreColors.TextSecondary else Color(0xFF121212),
                                modifier = Modifier.size(22.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.what_do_you_want_to_listen_to),
                                        style = EncoreTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium,
                                            color = if (isSearching) EncoreColors.TextSecondary else Color(0xFF757575)
                                        )
                                    )
                                }
                                innerTextField()
                            }

                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_x_24),
                                    contentDescription = stringResource(R.string.clear_search),
                                    tint = EncoreColors.TextSecondary,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable {
                                            searchQuery = ""
                                            selectedSearchTab = 0
                                            suggestionsPinned = false
                                            onSearchQueryChange("")
                                            focusManager.clearFocus()
                                        }
                                )
                            } else if (isSearchBarFocused && canVoiceSearch) {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_mic_24),
                                    contentDescription = stringResource(R.string.voice_search),
                                    tint = EncoreColors.TextSecondary,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clickable {
                                            runCatching { voiceSearchLauncher.launch(speechIntent) }
                                        }
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .then(
                            searchFieldFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSearching) Color(0xFF282828) else Color.White)
                        .onFocusChanged {
                            isSearchBarFocused = it.isFocused
                            if (it.isFocused) suggestionsPinned = false
                        }
                        .padding(horizontal = 12.dp)
                )
            }

            if (searchQuery.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(searchTabs.size) { index ->
                        EncoreChip(
                            text = searchTabs[index],
                            isSelected = selectedSearchTab == index,
                            onClick = {
                                selectedSearchTab = index
                                suggestionsPinned = false
                                if (isSearchBarFocused) {
                                    isSearchBarFocused = false
                                    focusManager.clearFocus()
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showRecentSearches) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EncoreColors.Background),
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.recent_searches),
                        style = EncoreTheme.typography.headlineLarge.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Bold,
                        color = EncoreColors.TextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                items(recentSearches, key = { it.uri.ifBlank { it.title } }) { searchItem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                suggestionsPinned = false
                                when (searchItem.kind) {
                                    SearchKind.TRACK -> playFromSearch(
                                        TrackItem(
                                            title = searchItem.title,
                                            artist = searchItem.subtitle,
                                            uri = searchItem.uri,
                                            imageUrl = searchItem.imageUrl.takeIf { it.isNotBlank() },
                                        )
                                    )
                                    SearchKind.ARTIST -> onArtistClick(searchItem.title, searchItem.uri)
                                    SearchKind.ALBUM -> onAlbumClick(searchItem.title, searchItem.subtitle, searchItem.uri)
                                    SearchKind.PLAYLIST -> onPlaylistClick(searchItem.title, searchItem.uri)
                                    SearchKind.EPISODE -> onEpisodeClick(searchItem.title, searchItem.uri)
                                    SearchKind.SHOW -> onShowClick(searchItem.title, searchItem.uri)
                                    SearchKind.USER -> onUserClick(searchItem.uri, searchItem.title)
                                    else -> {
                                        searchQuery = searchItem.title
                                        onSearchQueryChange(searchItem.title)
                                    }
                                }
                            }
                            .padding(start = 16.dp, end = 0.dp)
                            .height(64.dp), // minHeight = 64.0dp from history_row_search_layout.xml
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RecentSearchArtwork(item = searchItem)

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = searchItem.title,
                                style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = EncoreColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = searchItem.subtitle,
                                style = EncoreTheme.typography.bodySmall,
                                color = EncoreColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable {
                                    recentSearches.remove(searchItem)
                                    onRemoveRecent(searchItem)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_x_24),
                                contentDescription = stringResource(R.string.remove_from_recent_searches),
                                tint = EncoreColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (recentSearches.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(
                                        BorderStroke(1.dp, Color(0xFF727272)),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                recentSearches.clear()
                                onClearRecent()
                            }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.clear_recent_searches),
                                    style = EncoreTheme.typography.bodySmallBold.copy(
                                        color = EncoreColors.TextPrimary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } else if (showSuggestions) {
            val dropHits = remember(suggestionHits, savedOverrides) {
                suggestionHits.map { hit -> savedOverrides[hit.uri]?.let { hit.copy(saved = it) } ?: hit }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EncoreColors.Background),
                contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding)
            ) {
                items(dropHits, key = { it.uri.ifBlank { it.title } }) { hit ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (hit.kind == SearchKind.QUERY) {
                                    suggestionsPinned = false
                                    isSearchBarFocused = false
                                    searchQuery = hit.title
                                    focusManager.clearFocus()
                                    onSearchQueryChange(hit.title)
                                } else {
                                    if (hit.kind == SearchKind.TRACK) suggestionsPinned = true
                                    isSearchBarFocused = false
                                    focusManager.clearFocus()
                                    openHit(hit)
                                }
                            }
                            .padding(horizontal = 16.dp)
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hit.kind == SearchKind.QUERY) {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_search_24),
                                contentDescription = null,
                                tint = EncoreColors.TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            SearchHitArtwork(hit = hit, size = 40.dp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = hit.title,
                                style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = EncoreColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (hit.subtitle.isNotBlank()) {
                                Text(
                                    text = hit.subtitle,
                                    style = EncoreTheme.typography.bodySmall,
                                    color = EncoreColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (hit.kind == SearchKind.TRACK) {
                            EncoreIconButton(
                                iconRes = if (hit.saved) R.drawable.encore_icon_heart_active_24
                                else R.drawable.encore_icon_heart_24,
                                contentDescription = if (hit.saved) "Remove ${hit.title} from Liked Songs"
                                else "Save ${hit.title} to Liked Songs",
                                onClick = { onToggleSaved(hit.uri, hit.saved) },
                                size = 36.dp,
                                iconSize = 20.dp,
                                tint = if (hit.saved) EncoreColors.Green else EncoreColors.TextSecondary
                            )
                        }
                    }
                }
            }
        } else if (searchQuery.isNotEmpty()) {
            val hasResults = filteredTracks.isNotEmpty() ||
                    filteredArtists.isNotEmpty() ||
                    filteredAlbums.isNotEmpty() ||
                    filteredPlaylists.isNotEmpty() ||
                    filteredPodcasts.isNotEmpty() ||
                    displaySearchData?.sections?.isNotEmpty() == true ||
                    (selectedSearchTab != 0 && tabHits.isNotEmpty())

            if (loading && !isOffline) {
                TrackListSkeleton(
                    rows = 8,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp)
                )
            } else if (!hasResults && (isOffline || loadFailed)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
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
                            text = if (isOffline) stringResource(R.string.youre_offline) else stringResource(R.string.couldnt_load_search_results),
                            style = EncoreTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.check_your_internet_connection_and_try_again),
                            style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = onRetry) {
                            Text(text = stringResource(R.string.try_again), color = EncoreColors.Green)
                        }
                    }
                }
            } else if (!hasResults) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.couldnt_find_query, searchQuery),
                            style = EncoreTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.try_searching_again_using_a_different_spelling_o),
                            style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (selectedSearchTab == 0) {
                        val topHit = displaySearchData?.top
                        val topTrack = if (topHit == null) filteredTracks.firstOrNull() else null
                        if (topHit != null || topTrack != null) {
                            item {
                                val top = topHit?.toTrackItem() ?: topTrack!!
                                val topLabel = topHit?.kind?.sectionLabel() ?: stringResource(R.string.song)
                                val topRound = topHit?.kind == SearchKind.ARTIST || topHit?.kind == SearchKind.USER
                                val openTopResult = {
                                    val hit = topHit
                                    if (hit == null) {
                                        onRecordRecent(
                                            SearchHit(
                                                kind = SearchKind.TRACK,
                                                uri = top.uri,
                                                title = top.title,
                                                subtitle = top.artist,
                                                imageUrl = top.imageUrl.orEmpty(),
                                            )
                                        )
                                        playFromSearch(top)
                                    } else openHit(hit)
                                }
                                val isThisPlaying = isPlaying && matchesCurrent(
                                    top.uri, currentTrackUri, top.title, currentTrackTitle
                                )

                                Text(
                                    text = stringResource(R.string.top_result),
                                    style = EncoreTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                    modifier = Modifier.padding(bottom = 12.dp, top = 4.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(EncoreColors.Card)
                                        .clickable { openTopResult() }
                                        .padding(16.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(if (topRound) CircleShape else RoundedCornerShape(4.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFF8D67AB), Color(0xFF1E3264))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (top.imageUrl != null) {
                                                EncoreArtworkImage(
                                                    imageUrl = top.imageUrl,
                                                    contentDescription = top.title,
                                                    modifier = Modifier.fillMaxSize(),
                                                    cornerRadius = if (topRound) 32.dp else 4.dp
                                                )
                                            } else {
                                                Text(text = if (topRound) "★" else "♫", fontSize = 32.sp, color = Color.White)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Text(
                                            text = top.title,
                                            style = EncoreTheme.typography.headlineLarge.copy(fontSize = 22.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = topLabel,
                                                style = EncoreTheme.typography.bodySmallBold.copy(color = EncoreColors.TextSecondary)
                                            )
                                            if (top.artist.isNotBlank() && !top.artist.equals(topLabel, ignoreCase = true)) {
                                                Text(
                                                    text = " • ${top.artist}",
                                                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    if (topHit?.kind != SearchKind.USER) {
                                        EncorePlayButton(
                                            isPlaying = isThisPlaying,
                                            onClick = { openTopResult() },
                                            size = 48.dp,
                                            iconSize = 22.dp,
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }

                        if (filteredTracks.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.songs),
                                    style = EncoreTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            items(filteredTracks.take(4), key = { it.uri }) { track ->
                                val isThisPlaying = isPlaying &&
                                    matchesCurrent(track.uri, currentTrackUri, track.title, currentTrackTitle)
                                SearchTrackRow(
                                    track = track,
                                    isPlaying = isThisPlaying,
                                    onClick = {
                                        onRecordRecent(
                                            SearchHit(
                                                kind = SearchKind.TRACK,
                                                uri = track.uri,
                                                title = track.title,
                                                subtitle = track.artist,
                                                imageUrl = track.imageUrl.orEmpty(),
                                            )
                                        )
                                        playFromSearch(track)
                                    },
                                    onMoreClick = { onTrackOptionsClick(track) },
                                    onLikeClick = { onToggleSaved(track.uri, track.saved == true) },
                                    onAddToQueue = onTrackAddToQueue?.let { handler -> { handler(track) } }
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        if (filteredArtists.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.artists),
                                    style = EncoreTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            items(filteredArtists.take(2), key = { it.uri }) { artist ->
                                ArtistRow(
                                    artist = artist,
                                    onArtistClick = {
                                        onRecordRecent(
                                            SearchHit(
                                                kind = SearchKind.ARTIST,
                                                uri = artist.uri,
                                                title = artist.name,
                                                subtitle = artist.monthlyListeners,
                                                imageUrl = artist.imageUrl,
                                            )
                                        )
                                        onArtistClick(artist.name, artist.uri)
                                    },
                                    onFollowToggle = { onToggleSaved(artist.uri, artist.saved) }
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        if (filteredAlbums.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.albums),
                                    style = EncoreTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            items(filteredAlbums.take(2), key = { it.uri }) { album ->
                                AlbumRow(
                                    album = album,
                                    onClick = {
                                        onRecordRecent(
                                            SearchHit(
                                                kind = SearchKind.ALBUM,
                                                uri = album.uri,
                                                title = album.title,
                                                subtitle = album.artist,
                                                imageUrl = album.imageUrl,
                                            )
                                        )
                                        onAlbumClick(album.title, album.artist, album.uri)
                                    },
                                    onToggleSaved = { onToggleSaved(album.uri, album.saved) }
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        if (filteredPlaylists.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.playlists),
                                    style = EncoreTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            items(filteredPlaylists.take(2), key = { it.uri }) { playlist ->
                                PlaylistRow(
                                    playlist = playlist,
                                    onClick = {
                                        onRecordRecent(
                                            SearchHit(
                                                kind = SearchKind.PLAYLIST,
                                                uri = playlist.uri,
                                                title = playlist.title,
                                                subtitle = playlist.creator,
                                                imageUrl = playlist.imageUrl,
                                            )
                                        )
                                        onPlaylistClick(playlist.title, playlist.uri)
                                    }
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        val profileHits = displaySearchData?.others.orEmpty().filter { it.kind == SearchKind.USER }
                        if (profileHits.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.profiles),
                                    style = EncoreTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            items(profileHits.take(4), key = { "user_${it.uri}" }) { hit ->
                                SearchHitRow(
                                    hit = hit,
                                    isPlaying = isPlaying,
                                    currentTrackTitle = currentTrackTitle,
                                    currentTrackUri = currentTrackUri,
                                    onTrackSelect = playFromSearch,
                                    onTrackOptionsClick = onTrackOptionsClick,
                                    onTrackAddToQueue = onTrackAddToQueue,
                                    onAlbumClick = onAlbumClick,
                                    onArtistClick = onArtistClick,
                                    onPlaylistClick = onPlaylistClick,
                                    onToggleSaved = onToggleSaved,
                                    onOpen = { openHit(hit) },
                                    onRecordRecent = onRecordRecent
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        if (filteredPodcasts.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.podcasts_and_shows),
                                    style = EncoreTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            items(filteredPodcasts.take(2), key = { it.uri }) { podcast ->
                                PodcastRow(
                                    podcast = podcast,
                                    onClick = {
                                        onRecordRecent(
                                            SearchHit(
                                                kind = SearchKind.SHOW,
                                                uri = podcast.uri,
                                                title = podcast.title,
                                                subtitle = podcast.publisher,
                                                imageUrl = podcast.imageUrl,
                                            )
                                        )
                                        onPodcastClick(podcast.title, podcast.uri)
                                    }
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        displaySearchData?.sections.orEmpty().forEach { section ->
                            if (section.items.isNotEmpty()) {
                                item(key = "section_${section.title}") {
                                    Text(
                                        text = section.title,
                                        style = EncoreTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }

                                items(section.items, key = { "hit_${it.uri}_${it.title}" }) { hit ->
                                    SearchHitRow(
                                        hit = hit,
                                        isPlaying = isPlaying,
                                        currentTrackTitle = currentTrackTitle,
                                        currentTrackUri = currentTrackUri,
                                        onTrackSelect = playFromSearch,
                                        onTrackOptionsClick = onTrackOptionsClick,
                                        onTrackAddToQueue = onTrackAddToQueue,
                                        onAlbumClick = onAlbumClick,
                                        onArtistClick = onArtistClick,
                                        onPlaylistClick = onPlaylistClick,
                                        onToggleSaved = onToggleSaved,
                                        onOpen = { openHit(hit) },
                                        onRecordRecent = onRecordRecent
                                    )
                                }

                                item(key = "section_gap_${section.title}") {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }

                    if (tabKind != null) {
                        items(tabHits, key = { "tab_${it.uri}_${it.title}" }) { hit ->
                            SearchHitRow(
                                hit = hit,
                                isPlaying = isPlaying,
                                currentTrackTitle = currentTrackTitle,
                                currentTrackUri = currentTrackUri,
                                onTrackSelect = playFromSearch,
                                onTrackOptionsClick = onTrackOptionsClick,
                                onTrackAddToQueue = onTrackAddToQueue,
                                onAlbumClick = onAlbumClick,
                                onArtistClick = onArtistClick,
                                onPlaylistClick = onPlaylistClick,
                                onToggleSaved = onToggleSaved,
                                onOpen = { openHit(hit) },
                                onRecordRecent = onRecordRecent
                            )
                        }

                        if (tabHits.isEmpty()) {
                            item(key = "tab_empty_${tabKind}") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (loading || !sectionExhausted) {
                                        InlineLoadingDots(color = EncoreColors.TextSecondary)
                                    } else {
                                        Text(
                                            text = stringResource(R.string.no_kind_found_for_query, kindLabel(tabKind), searchQuery),
                                            style = EncoreTheme.typography.bodyMedium.copy(
                                                color = EncoreColors.TextSecondary
                                            ),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        if (onLoadSection != null && !sectionExhausted && sectionHits.isNotEmpty()) {
                            item(key = "section_more") {
                                LaunchedEffect(tabKind, sectionHits.size) {
                                    loadMoreSection()
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    InlineLoadingDots(color = EncoreColors.TextSecondary)
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(bottomPadding))
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(span = { GridItemSpan(2) }) {
                    Text(
                        text = stringResource(R.string.browse_all),
                        style = EncoreTheme.typography.titleLarge.copy(fontSize = 18.sp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                if (categories.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (categoriesFailed) {
                                Text(
                                    text = stringResource(R.string.couldn_t_load_categories),
                                    style = EncoreTheme.typography.bodyMedium.copy(
                                        color = EncoreColors.TextSecondary
                                    )
                                )
                            } else {
                                InlineLoadingDots(color = EncoreColors.TextSecondary)
                            }
                        }
                    }
                }

                items(categories, key = { it.uri.ifBlank { it.title } }) { category ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(category.color)
                            .clickable { onCategoryClick(category) }
                    ) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .fillMaxWidth(0.65f)
                        ) {
                            Text(
                                text = category.title,
                                style = EncoreTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (category.subtitle.isNotBlank()) {
                                Text(
                                    text = category.subtitle,
                                    style = EncoreTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.75f)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = 16.dp, y = 6.dp)
                                .rotate(25f)
                                .shadow(elevation = 6.dp, shape = RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!category.imageUrl.isNullOrBlank()) {
                                EncoreArtworkImage(
                                    imageUrl = category.imageUrl,
                                    contentDescription = category.title,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 4.dp
                                )
                            } else {
                                Text(text = "♫", fontSize = 24.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * - 48dp cover art thumbnail (`@dimen/track_row_cover_art_size`)
 * - Title: `TextAppearance.Encore.BodyMedium`, colored green when playing
 * - Subtitle: `TextAppearance.Encore.BodySmall` with explicit badge + "Song • <artist>"
 * - Animated play indicator if track is currently playing
 * - Context action: 3-dots more button (`encore_icon_more_android_24`)
 *
 * Search results display cover artwork, NOT album track numbers (1, 2, 3...)!
 */
@Composable
private fun SearchTrackRow(
    track: TrackItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onLikeClick: () -> Unit = {},
    onAddToQueue: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isBanned = com.mwask.bat.compose.bans.ArtistBans.isBanned(track.artistUri)
    SwipeToQueueBox(
        onAddToQueue = onAddToQueue,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isBanned, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(EncoreColors.Card)
                    .graphicsLayer { alpha = if (isBanned) 0.4f else 1f },
                contentAlignment = Alignment.Center
            ) {
                if (track.imageUrl != null) {
                    EncoreArtworkImage(
                        imageUrl = track.imageUrl,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 4.dp
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_track_24),
                        contentDescription = null,
                        tint = EncoreColors.TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPlaying) {
                        EncorePlayIndicator(
                            isPlaying = true,
                            color = EncoreColors.Green,
                            size = 12.dp,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    Text(
                        text = track.title,
                        style = EncoreTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                isPlaying -> EncoreColors.Green
                                isBanned -> EncoreColors.TextSecondary.copy(alpha = 0.55f)
                                else -> EncoreColors.TextPrimary
                            }
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (track.isExplicit) {
                        EncoreExplicitTag(modifier = Modifier.padding(end = 4.dp))
                    }
                    Text(
                        text = "Song • ${track.artist}",
                        style = EncoreTheme.typography.bodySmall.copy(
                            color = EncoreColors.TextSecondary.takeIf { !isBanned }
                                ?: EncoreColors.TextSecondary.copy(alpha = 0.55f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!isBanned) {
                EncoreIconButton(
                    iconRes = if (track.saved == true) R.drawable.encore_icon_heart_active_24
                    else R.drawable.encore_icon_heart_24,
                    contentDescription = if (track.saved == true) "Remove ${track.title} from Liked Songs"
                    else "Save ${track.title} to Liked Songs",
                    onClick = onLikeClick,
                    size = 36.dp,
                    iconSize = 20.dp,
                    tint = if (track.saved == true) EncoreColors.Green else EncoreColors.TextSecondary
                )
            }

            EncoreIconButton(
                iconRes = R.drawable.encore_icon_more_android_24,
                contentDescription = "More options for ${track.title}",
                onClick = onMoreClick,
                size = 36.dp,
                iconSize = 20.dp
            )
        }
    }
}

@Composable
private fun ArtistRow(
    artist: SearchArtistItem,
    onArtistClick: () -> Unit,
    onFollowToggle: () -> Unit = {}
) {
    var isFollowing by remember(artist.uri, artist.saved) { mutableStateOf(artist.saved) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onArtistClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(artist.color),
            contentAlignment = Alignment.Center
        ) {
            if (artist.imageUrl.isNotBlank()) {
                EncoreArtworkImage(
                    imageUrl = artist.imageUrl,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 26.dp
                )
            } else {
                Text(text = "★", color = Color.White, fontSize = 22.sp)
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.artist),
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isFollowing) EncoreColors.Green else EncoreColors.Card)
                .clickable {
                    isFollowing = !isFollowing
                    onFollowToggle()
                }
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (isFollowing) stringResource(R.string.following) else "Follow",
                style = EncoreTheme.typography.bodySmallBold.copy(
                    color = if (isFollowing) Color.Black else EncoreColors.TextPrimary
                )
            )
        }
    }
}

@Composable
private fun AlbumRow(
    album: SearchAlbumItem,
    onClick: () -> Unit,
    onToggleSaved: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(album.color),
            contentAlignment = Alignment.Center
        ) {
            if (album.imageUrl.isNotBlank()) {
                EncoreArtworkImage(
                    imageUrl = album.imageUrl,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 4.dp
                )
            } else {
                Text(text = "♫", color = Color.White, fontSize = 22.sp)
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title,
                style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (album.year.isBlank()) "Album • ${album.artist}" else "Album • ${album.year} • ${album.artist}",
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        EncoreHeartButton(
            isLiked = album.saved,
            onLikeToggle = onToggleSaved,
            size = 32.dp,
            iconSize = 18.dp
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: SearchPlaylistItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(playlist.color),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.imageUrl.isNotBlank()) {
                EncoreArtworkImage(
                    imageUrl = playlist.imageUrl,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 4.dp
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_playlist_24),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.playlist_by_creator, playlist.creator),
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchKind.sectionLabel(): String = when (this) {
    SearchKind.TRACK -> stringResource(R.string.song)
    SearchKind.ARTIST -> stringResource(R.string.artist)
    SearchKind.ALBUM -> stringResource(R.string.album)
    SearchKind.PLAYLIST -> stringResource(R.string.playlist)
    SearchKind.SHOW, SearchKind.EPISODE -> stringResource(R.string.podcast)
    SearchKind.USER -> stringResource(R.string.profile)
    else -> stringResource(R.string.result)
}

private fun kindOfTab(index: Int): SearchKind? = when (index) {
    1 -> SearchKind.TRACK
    2 -> SearchKind.ARTIST
    3 -> SearchKind.PLAYLIST
    4 -> SearchKind.ALBUM
    5 -> SearchKind.SHOW
    6 -> SearchKind.USER
    else -> null
}

/** The plain-English name of a filter tab, for its empty state. */
private fun kindLabel(kind: SearchKind): String = when (kind) {
    SearchKind.TRACK -> "songs"
    SearchKind.ARTIST -> "artists"
    SearchKind.ALBUM -> "albums"
    SearchKind.PLAYLIST -> "playlists"
    SearchKind.SHOW, SearchKind.EPISODE -> "podcasts"
    SearchKind.USER -> "profiles"
    else -> "results"
}

private fun SearchHit.toArtistItem(): SearchArtistItem = SearchArtistItem(
    name = title,
    monthlyListeners = subtitle,
    color = parseHexColor(colorHex) ?: Color(0xFF1E3264),
    uri = uri,
    imageUrl = imageUrl,
    saved = saved
)

private fun SearchHit.toAlbumItem(): SearchAlbumItem = SearchAlbumItem(
    title = title,
    artist = subtitle,
    year = "",
    color = parseHexColor(colorHex) ?: Color(0xFF1E3264),
    uri = uri,
    imageUrl = imageUrl,
    saved = saved
)

private fun SearchHit.toPlaylistItem(): SearchPlaylistItem = SearchPlaylistItem(
    title = title,
    creator = subtitle,
    followerCount = "",
    color = parseHexColor(colorHex) ?: Color(0xFF1E3264),
    uri = uri,
    imageUrl = imageUrl
)

private fun SearchKind.recentIconRes(): Int = when (this) {
    SearchKind.TRACK -> R.drawable.encore_icon_track_24
    SearchKind.ARTIST -> R.drawable.encore_icon_artist_24
    SearchKind.ALBUM -> R.drawable.encore_icon_album_24
    SearchKind.PLAYLIST -> R.drawable.encore_icon_playlist_24
    SearchKind.EPISODE, SearchKind.SHOW -> R.drawable.encore_icon_podcasts_24
    SearchKind.USER -> R.drawable.encore_icon_user_24
    else -> R.drawable.encore_icon_search_24
}

@Composable
private fun RecentSearchArtwork(item: SearchHistoryItem) {
    val round = item.kind == SearchKind.ARTIST || item.kind == SearchKind.USER
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(if (round) CircleShape else RoundedCornerShape(4.dp))
            .background(parseHexColor(item.colorHex) ?: Color(0xFF1E3264)),
        contentAlignment = Alignment.Center
    ) {
        if (item.imageUrl.isNotBlank()) {
            EncoreArtworkImage(
                imageUrl = item.imageUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = if (round) 24.dp else 4.dp
            )
        } else {
            Icon(
                painter = painterResource(id = item.kind.recentIconRes()),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SearchHitRow(
    hit: SearchHit,
    isPlaying: Boolean,
    currentTrackTitle: String,
    currentTrackUri: String,
    onTrackSelect: (TrackItem) -> Unit,
    onTrackOptionsClick: (TrackItem) -> Unit,
    onTrackAddToQueue: ((TrackItem) -> Unit)? = null,
    onAlbumClick: (String, String, String) -> Unit,
    onArtistClick: (String, String) -> Unit,
    onPlaylistClick: (String, String) -> Unit,
    onToggleSaved: (uri: String, currentlySaved: Boolean) -> Unit,
    onOpen: () -> Unit,
    onRecordRecent: (SearchHit) -> Unit = {},
) {
    when (hit.kind) {
        SearchKind.TRACK -> {
            val item = hit.toTrackItem()
            SearchTrackRow(
                track = item,
                isPlaying = isPlaying && matchesCurrent(item.uri, currentTrackUri, item.title, currentTrackTitle),
                onClick = {
                    onRecordRecent(hit)
                    onTrackSelect(item)
                },
                onMoreClick = { onTrackOptionsClick(item) },
                onLikeClick = { onToggleSaved(item.uri, item.saved == true) },
                onAddToQueue = onTrackAddToQueue?.let { handler -> { handler(item) } }
            )
        }
        SearchKind.PLAYLIST -> PlaylistRow(
            playlist = hit.toPlaylistItem(),
            onClick = {
                onRecordRecent(hit)
                onPlaylistClick(hit.title, hit.uri)
            }
        )
        SearchKind.ALBUM -> AlbumRow(
            album = hit.toAlbumItem(),
            onClick = {
                onRecordRecent(hit)
                onAlbumClick(hit.title, hit.subtitle, hit.uri)
            },
            onToggleSaved = { onToggleSaved(hit.uri, hit.saved) }
        )
        SearchKind.ARTIST -> ArtistRow(
            artist = hit.toArtistItem(),
            onArtistClick = {
                onRecordRecent(hit)
                onArtistClick(hit.title, hit.uri)
            },
            onFollowToggle = { onToggleSaved(hit.uri, hit.saved) }
        )
        else -> GenericHitRow(hit = hit, onClick = onOpen)
    }
}

@Composable
private fun GenericHitRow(
    hit: SearchHit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchHitArtwork(hit = hit, size = 52.dp)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hit.title,
                style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hit.subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = hit.subtitle,
                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SearchHitArtwork(hit: SearchHit, size: androidx.compose.ui.unit.Dp) {
    val round = hit.kind == SearchKind.ARTIST || hit.kind == SearchKind.USER
    val shape = if (round) CircleShape else RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(parseHexColor(hit.colorHex) ?: Color(0xFF282828)),
        contentAlignment = Alignment.Center
    ) {
        if (hit.imageUrl.isNotBlank()) {
            EncoreArtworkImage(
                imageUrl = hit.imageUrl,
                contentDescription = hit.title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = if (round) size / 2 else 4.dp
            )
        } else {
            Text(
                text = if (round) "★" else "♫",
                fontSize = 20.sp,
                color = Color.White
            )
        }
    }
}

private fun SearchHit.toTrackItem(): TrackItem = TrackItem(
    title = title,
    artist = subtitle,
    isExplicit = explicit,
    uri = uri,
    imageUrl = imageUrl.takeIf { it.isNotBlank() },
    saved = saved,
    durationMs = durationMs,
    hasVideo = video,
    albumUri = albumUri,
    artistUri = artistUri,
    albumName = albumName,
)

@Composable
private fun PodcastRow(
    podcast: SearchPodcastItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(podcast.color),
            contentAlignment = Alignment.Center
        ) {
            if (podcast.imageUrl.isNotBlank()) {
                EncoreArtworkImage(
                    imageUrl = podcast.imageUrl,
                    contentDescription = podcast.title,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 6.dp
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.encore_icon_podcasts_24),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = podcast.title,
                style = EncoreTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Podcast • ${podcast.publisher}",
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun PreviewEncoreSearchScreen() {
    EncoreAppTheme {
        EncoreSearchScreen(
            onCategoryClick = {},
            onTrackSelect = {},
            onTrackOptionsClick = {},
            onProfileClick = {}
        )
    }
}
