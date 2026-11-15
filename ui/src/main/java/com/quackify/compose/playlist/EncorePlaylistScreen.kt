package com.quackify.compose.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.backend.model.PlaylistMember
import com.quackify.compose.R
import com.quackify.compose.components.EncorePlayButton
import com.quackify.compose.components.matchesCurrent
import com.quackify.compose.components.EncoreExplicitTag
import com.quackify.compose.components.EncoreFastScroll
import com.quackify.compose.components.EncoreHeartButton
import com.quackify.compose.components.EncoreTrackRow
import com.quackify.compose.components.InlineLoadingDots
import com.quackify.compose.components.TrackListSkeleton
import com.quackify.compose.components.ShimmerBlock
import com.quackify.compose.components.ShimmerHost
import com.quackify.compose.components.ShimmerText
import com.quackify.compose.components.rememberSmoothColor
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.compose.format.formatCollectionDuration
import com.quackify.compose.sheets.SortDirection
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreAppTheme
import com.quackify.compose.theme.EncoreTheme
import com.quackify.compose.theme.rememberBottomPadding
import com.quackify.compose.util.search.GenericSearchEngine
import com.quackify.compose.util.search.SearchableFieldExtractor
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

data class TrackItem(
    val title: String,
    val artist: String,
    val isExplicit: Boolean = false,
    val uri: String = "",
    val uid: String = "",
    val imageUrl: String? = null,
    val saved: Boolean? = null,
    val durationMs: Long = 0L,
    val albumUri: String = "",
    val artistUri: String = "",
    val albumName: String = "",
    val discNumber: Int = 1,
    val trackNumber: Int = 0,
    val hasVideo: Boolean = false,
    val isEnhanced: Boolean = false,
    val plays: Long = 0L,
)

private val trackSearchEngine = GenericSearchEngine<TrackItem>(maxResult = Int.MAX_VALUE)

private val TRACK_SEARCH_FIELDS = SearchableFieldExtractor<TrackItem> {
    arrayOf(it.title, it.artist, it.albumName)
}

private const val PRELOAD_REMAINING_TRACKS = 25

enum class PlaylistSort(
    val labelRes: Int,
    val reversible: Boolean = true,
    val defaultDirection: SortDirection = SortDirection.ASCENDING
) {
    CUSTOM(R.string.custom_order, reversible = false, defaultDirection = SortDirection.ASCENDING),
    TITLE(R.string.title, reversible = true, defaultDirection = SortDirection.ASCENDING),
    ARTIST(R.string.artist, reversible = true, defaultDirection = SortDirection.ASCENDING),
    ALBUM(R.string.album, reversible = true, defaultDirection = SortDirection.ASCENDING),
    RECENTLY_ADDED(R.string.recently_added, reversible = true, defaultDirection = SortDirection.DESCENDING)
}

/**
 * - Top Bar: Back button, Centered Title (when scrolled), Green Play FAB (when scrolled) / 3-dots (when at top)
 * - Sticky Header: Filter chips row ([+ Add], [≡ Edit], [✏ Name & details], [⇅ Sort]) pins directly below Top Bar
 * - Dynamic Artwork Shrink: smoothly scales down from 230dp to 100dp with alpha fade as user scrolls
 * - Magnetic Snapping Search Bar: starts hidden; swiping/pulling down snaps it 100% open or closed
 * - Gradient strictly confined to header/play button area, fading completely to solid #121212 black
 * - Action controls row: mini stacked artwork card, (↓) download circle, share, shuffle, and 56dp Green Play FAB
 * - Standard 60dp track rows with 48dp artwork, title, artist, [E] badge, and vertical 3-dots (⋮)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EncorePlaylistScreen(
    title: String,
    description: String,
    creator: String,
    saveCount: String,
    tracks: List<TrackItem>,
    currentTrackTitle: String,
    currentTrackUri: String,
    isPlaying: Boolean,
    onBackClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onTrackSelect: (TrackItem) -> Unit,
    onTrackOptionsClick: (TrackItem) -> Unit = {},
    onTrackLikeToggle: (TrackItem) -> Unit = {},
    onTrackAddToQueue: ((TrackItem) -> Unit)? = null,
    onPlaylistOptionsClick: () -> Unit = {},
    onEditModeChange: (Boolean) -> Unit = {},
    onSaveOrder: (List<TrackItem>) -> Unit = {},
    onEditDetails: (suspend (name: String, description: String, cover: ByteArray?, clearCover: Boolean) -> Unit)? = null,
    onDeletePlaylist: (() -> Unit)? = null,
    isEditable: Boolean = false,
    canAddTracks: Boolean = false,
    contributors: List<PlaylistMember> = emptyList(),
    onContributorsClick: (() -> Unit)? = null,
    onShareClick: () -> Unit = {},
    isSaved: Boolean? = null,
    onSaveToggle: () -> Unit = {},
    recommendedTracks: List<TrackItem> = emptyList(),
    recommendationsLoading: Boolean = false,
    onRecommendRefresh: () -> Unit = {},
    onAddRecommended: (TrackItem) -> Unit = {},
    onChangeTitle: (() -> Unit)? = null,
    onAddTracksClick: (() -> Unit)? = null,
    onEditCoverClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    headerColor: Color = Color(0xFF144D3F),
    coverUrl: String? = null,
    creatorAvatarUrl: String = "",
    totalDurationMs: Long = 0L,
    totalIsEstimate: Boolean = false,
    loading: Boolean = false,
    hasMore: Boolean = false,
    loadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    loadFailed: Boolean = false,
    onRetry: () -> Unit = {}
) {
    var currentTitle by remember(title) { mutableStateOf(title) }
    val smoothHeaderColor by rememberSmoothColor(target = headerColor)
    var currentDescription by remember(description) { mutableStateOf(description) }
    var isEditMode by remember { mutableStateOf(false) }
    var isSortSheetOpen by remember { mutableStateOf(false) }
    var isEditDetailsOpen by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isDownloaded by remember { mutableStateOf(false) }
    var isShuffle by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(PlaylistSort.CUSTOM) }
    var sortDirection by remember { mutableStateOf(SortDirection.ASCENDING) }
    var isSearchRevealed by remember { mutableStateOf(false) }
    val bottomPadding = rememberBottomPadding()
    val scope = rememberCoroutineScope()

    BackHandler(enabled = isSearching) {
        isSearching = false
        searchQuery = ""
    }

    val currentTracks = remember(tracks) {
        mutableStateListOf<TrackItem>().apply { addAll(tracks) }
    }

    val displayedTracks = remember(tracks, currentTracks, searchQuery, sortOption, sortDirection) {
        val query = searchQuery.trim()
        val base = if (query.isEmpty()) {
            currentTracks.toList()
        } else {
            trackSearchEngine.filter(currentTracks, query, TRACK_SEARCH_FIELDS)
        }
        when (sortOption) {
            PlaylistSort.TITLE -> {
                if (sortDirection == SortDirection.ASCENDING) {
                    base.sortedBy { it.title.lowercase() }
                } else {
                    base.sortedByDescending { it.title.lowercase() }
                }
            }
            PlaylistSort.ARTIST -> {
                if (sortDirection == SortDirection.ASCENDING) {
                    base.sortedBy { it.artist.lowercase() }
                } else {
                    base.sortedByDescending { it.artist.lowercase() }
                }
            }
            PlaylistSort.ALBUM -> {
                if (sortDirection == SortDirection.ASCENDING) {
                    base.sortedBy { it.albumName.lowercase() }
                } else {
                    base.sortedByDescending { it.albumName.lowercase() }
                }
            }
            PlaylistSort.RECENTLY_ADDED -> {
                if (sortDirection == SortDirection.DESCENDING) {
                    base.reversed()
                } else {
                    base
                }
            }
            PlaylistSort.CUSTOM -> base
        }
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current

    val shouldLoadMore by remember(hasMore, loadingMore, displayedTracks.size, isEditable, canAddTracks, onLoadMore) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val leading = 1 + if ((isEditable || canAddTracks) && displayedTracks.isNotEmpty()) 1 else 0
            hasMore && !loadingMore && displayedTracks.isNotEmpty() &&
                lastVisible - leading >= displayedTracks.size - PRELOAD_REMAINING_TRACKS
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    val maxSearchHeightDp = 50.dp
    val maxSearchPx = with(density) { maxSearchHeightDp.toPx() }
    val searchOffsetAnim = remember { Animatable(0f) }
    val currentSearchHeightDp = with(density) { searchOffsetAnim.value.toDp() }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (searchOffsetAnim.value > 0f && available.y < 0f) {
                    val target = (searchOffsetAnim.value + available.y).coerceAtLeast(0f)
                    val consumedY = target - searchOffsetAnim.value
                    scope.launch { searchOffsetAnim.snapTo(target) }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y > 0f && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    val target = (searchOffsetAnim.value + available.y).coerceAtMost(maxSearchPx)
                    val consumedY = target - searchOffsetAnim.value
                    scope.launch { searchOffsetAnim.snapTo(target) }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (searchOffsetAnim.value > 0f && searchOffsetAnim.value < maxSearchPx) {
                    val snapTarget = if (available.y > 250f || searchOffsetAnim.value > maxSearchPx * 0.45f) {
                        maxSearchPx
                    } else {
                        0f
                    }
                    searchOffsetAnim.animateTo(
                        targetValue = snapTarget,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val scrollPx by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 2000f
        }
    }

    val shrinkMaxPx = with(density) { 200.dp.toPx() }
    val shrinkFraction by remember {
        derivedStateOf {
            (scrollPx / shrinkMaxPx).coerceIn(0f, 1f)
        }
    }

    val artworkSize by remember {
        derivedStateOf {
            (240.dp * (1f - (shrinkFraction * 0.5f))).coerceAtLeast(120.dp)
        }
    }

    val artworkAlpha by remember {
        derivedStateOf {
            (1f - (shrinkFraction * 0.95f)).coerceIn(0f, 1f)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .nestedScroll(nestedScrollConnection)
    ) {
        if (isEditMode) {
            PlaylistEditModeContent(
                initialTracks = currentTracks.toList(),
                onSave = { updatedList ->
                    currentTracks.clear()
                    currentTracks.addAll(updatedList)
                    onSaveOrder(updatedList)
                    isEditMode = false
                    onEditModeChange(false)
                },
                onCancel = {
                    isEditMode = false
                    onEditModeChange(false)
                }
            )
        } else if (isSearching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color(0xFF121212))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    isSearching = false
                    searchQuery = ""
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_arrow_left_24),
                        contentDescription = stringResource(R.string.exit_search),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF282828))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_search_24),
                            contentDescription = null,
                            tint = EncoreColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.find_in_playlist),
                                    style = EncoreTheme.typography.bodyMedium.copy(
                                        color = EncoreColors.TextSecondary,
                                        fontSize = 14.sp
                                    )
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                cursorBrush = SolidColor(EncoreColors.Green),
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                painter = painterResource(id = R.drawable.encore_icon_x_24),
                                contentDescription = stringResource(R.string.clear_search),
                                tint = Color.White,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    }
                }

                IconButton(onClick = onPlaylistOptionsClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_more_android_24),
                        contentDescription = stringResource(R.string.playlist_options),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                if (displayedTracks.isEmpty() && searchQuery.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 56.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.couldnt_find_query, searchQuery),
                                style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.try_searching_for_a_different_track_title_or_art),
                                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                            )
                        }
                    }
                } else {
                    itemsIndexed(displayedTracks) { _, track ->
                        val isCurrentTrack = matchesCurrent(track.uri, currentTrackUri, track.title, currentTrackTitle)
                        EncoreTrackRow(
                            trackNumber = null,
                            title = track.title,
                            artist = track.artist,
                            isPlaying = isCurrentTrack && isPlaying,
                            isExplicit = track.isExplicit,
                            hasVideo = track.hasVideo,
                            imageUrl = track.imageUrl,
                            artistUri = track.artistUri,
                            onClick = { onTrackSelect(track) },
                            onMoreClick = { onTrackOptionsClick(track) },
                            saved = track.saved,
                            onLikeToggle = { onTrackLikeToggle(track) },
                            onAddToQueue = onTrackAddToQueue?.let { handler -> { handler(track) } }
                        )
                    }
                }
            }
        } else {

            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val toolbarHeight = 54.dp
            val toolbarBottom = statusBarTop + toolbarHeight
            val playButtonSize = 52.dp
            val playButtonHalf = 26.dp
            val pinnedTop = toolbarBottom - playButtonHalf
            val pinnedTopPx = with(density) { pinnedTop.toPx() }

            val toolbarBottomPx = with(density) { toolbarBottom.toPx() }
            val chipsScrollState = rememberScrollState()

            val searchProgress = (currentSearchHeightDp / maxSearchHeightDp).coerceIn(0f, 1f)
            val dynamicTopOffsetDp = (toolbarHeight - 8.dp) * (1f - searchProgress)
            val dynamicTopOffsetPx = with(density) { dynamicTopOffsetDp.toPx().toInt() }

            val playDockStartPx = with(density) { 290.dp.toPx() }
            val playFadePx = with(density) { 50.dp.toPx() }
            val playFraction by remember {
                derivedStateOf { ((scrollPx - playDockStartPx) / playFadePx).coerceIn(0f, 1f) }
            }

            val topBarThreeDotsAlpha by remember {
                derivedStateOf { 1f - playFraction }
            }
            val topBarTitleAlpha by remember {
                derivedStateOf {
                    val titleScrollStartPx = with(density) { 160.dp.toPx() }
                    val titleScrollFadeRangePx = with(density) { 60.dp.toPx() }
                    ((scrollPx - titleScrollStartPx) / titleScrollFadeRangePx).coerceIn(0f, 1f)
                }
            }
            val topBarAlpha by remember {
                derivedStateOf {
                    (scrollPx / with(density) { 90.dp.toPx() }).coerceIn(0f, 1f)
                }
            }
            val circleBtnAlpha by remember {
                derivedStateOf { ((1f - topBarAlpha) * 0.35f).coerceIn(0f, 0.35f) }
            }

            val approxHeaderBottomPx = with(density) { (470.dp + currentSearchHeightDp).toPx() }
            val currentGradientHeightDp by remember {
                derivedStateOf {
                    maxOf(toolbarBottom, with(density) { (approxHeaderBottomPx - scrollPx).toDp() })
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(currentGradientHeightDp)
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to smoothHeaderColor,
                                    0.35f to smoothHeaderColor.copy(alpha = 0.90f),
                                    0.65f to smoothHeaderColor.copy(alpha = 0.55f),
                                    0.85f to smoothHeaderColor.copy(alpha = 0.20f),
                                    1.0f to Color(0xFF121212)
                                )
                            )
                        )
                )

                EncoreFastScroll(listState) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = statusBarTop + toolbarHeight,
                        bottom = bottomPadding
                    )
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    layout(placeable.width, placeable.height - dynamicTopOffsetPx) {
                                        placeable.placeRelative(0, -dynamicTopOffsetPx)
                                    }
                                }
                        ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            if (!loading && !loadFailed && displayedTracks.isEmpty()) {
                                PlaylistEmptyCurationHeader(
                                    coverUrl = coverUrl,
                                    title = currentTitle,
                                    creator = creator,
                                    contributors = contributors,
                                    creatorAvatarUrl = creatorAvatarUrl,
                                    onContributorsClick = onContributorsClick,
                                    onEditCoverClick = { onEditCoverClick?.invoke() ?: onPlaylistOptionsClick() },
                                    onChangeTitle = onChangeTitle,
                                    onShareClick = onShareClick,
                                    onAddTracksClick = {
                                        if (isEditable || canAddTracks) {
                                            onAddTracksClick?.invoke() ?: run { isSearching = true }
                                        }
                                    }
                                )
                            } else {
                            if (currentSearchHeightDp > 0.dp) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(currentSearchHeightDp)
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (currentSearchHeightDp > 12.dp) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(38.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.15f))
                                                .clickable { isSearching = true }
                                                .padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.encore_icon_search_24),
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(R.string.find_in_playlist),
                                                    style = EncoreTheme.typography.bodyMedium.copy(
                                                        color = Color.White.copy(alpha = 0.85f),
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(artworkSize)
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(artworkSize)
                                        .graphicsLayer {
                                            alpha = artworkAlpha
                                        }
                                        .shadow(elevation = (14 * (1f - shrinkFraction)).dp, shape = RoundedCornerShape(4.dp))
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(smoothHeaderColor.copy(alpha = 0.9f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!coverUrl.isNullOrBlank()) {
                                        EncoreArtworkImage(
                                            imageUrl = coverUrl,
                                            contentDescription = currentTitle,
                                            modifier = Modifier.fillMaxSize(),
                                            cornerRadius = 4.dp
                                        )
                                    } else {
                                        Text(
                                            text = "♫",
                                            fontSize = 72.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = currentTitle,
                                style = EncoreTheme.typography.headlineLarge.copy(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (isSaved) {
                                                true -> EncoreColors.Green
                                                else -> Color.White.copy(alpha = 0.12f)
                                            }
                                        )
                                        .clickable(enabled = isSaved != null) { onSaveToggle() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            id = if (isSaved == true) R.drawable.encore_icon_check_24 else R.drawable.encore_icon_plus_24
                                        ),
                                        contentDescription = when (isSaved) {
                                            true -> stringResource(R.string.remove_from_library)
                                            else -> stringResource(R.string.add_to_library)
                                        },
                                        tint = if (isSaved == true) Color.Black else Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                if (creator.isNotBlank()) {
                                    val ownerMember = contributors.firstOrNull { it.isOwner }
                                    val ownerName = ownerMember?.name?.takeIf { it.isNotBlank() } ?: creator
                                    val extraContributors = contributors.count { !it.isOwner }
                                    val stack = remember(creatorAvatarUrl, contributors) {
                                        val ownerAvatar = ownerMember?.avatarUrl?.takeIf { it.isNotBlank() }
                                            ?: creatorAvatarUrl.takeIf { it.isNotBlank() }
                                            ?: ""
                                        val others = contributors.filterNot { it.isOwner }
                                            .mapNotNull { it.avatarUrl.takeIf { url -> url.isNotBlank() } }
                                            .take(2)
                                        (listOf(ownerAvatar) + others).take(3)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .then(
                                                if (onContributorsClick != null) {
                                                    Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { onContributorsClick?.invoke() }
                                                } else {
                                                    Modifier
                                                }
                                            )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy((-8).dp)
                                        ) {
                                            stack.forEachIndexed { index, url ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(if (index == 0) 24.dp else 20.dp)
                                                        .zIndex(if (index == 0) 2f else 1f)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF333333)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (url.isNotBlank()) {
                                                        EncoreArtworkImage(
                                                            imageUrl = url,
                                                            contentDescription = ownerName,
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .clip(CircleShape),
                                                            cornerRadius = 12.dp
                                                        )
                                                    } else {
                                                        Icon(
                                                            painter = painterResource(id = R.drawable.encore_icon_user_24),
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(if (index == 0) 14.dp else 12.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Text(
                                            text = if (extraContributors > 0) {
                                                "$ownerName + $extraContributors more"
                                            } else {
                                                ownerName
                                            },
                                            style = EncoreTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 13.sp
                                            )
                                        )
                                    }
                                } else if (loading) {
                                    ShimmerHost {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ShimmerBlock(
                                                modifier = Modifier.size(24.dp),
                                                shape = CircleShape
                                            )
                                            ShimmerText(
                                                widthFraction = 0.35f,
                                                height = 13.dp
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            val metadataText = buildString {
                                if (saveCount.isNotBlank()) append(saveCount)
                                val duration = formatCollectionDuration(totalDurationMs, totalIsEstimate)
                                if (duration.isNotBlank()) {
                                    if (isNotEmpty()) append(" • ")
                                    append(duration)
                                }
                            }.ifBlank { saveCount }

                            if (metadataText.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.encore_icon_globe_24),
                                        contentDescription = stringResource(R.string.public_playlist),
                                        tint = EncoreColors.TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = metadataText,
                                        style = EncoreTheme.typography.bodySmall.copy(
                                            color = EncoreColors.TextSecondary,
                                            fontSize = 12.5.sp
                                        )
                                    )
                                }
                            } else if (loading) {
                                ShimmerHost {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    ) {
                                        ShimmerText(
                                            widthFraction = 0.45f,
                                            height = 12.dp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 30.dp, height = 38.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0xFF282828)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!coverUrl.isNullOrBlank()) {
                                            EncoreArtworkImage(
                                                imageUrl = coverUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                cornerRadius = 3.dp
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(smoothHeaderColor.copy(alpha = 0.8f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("♫", fontSize = 14.sp, color = Color.White)
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable { isDownloaded = !isDownloaded },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(
                                                id = if (isDownloaded) R.drawable.encore_icon_downloaded_24 else R.drawable.encore_icon_download_24
                                            ),
                                            contentDescription = if (isDownloaded) stringResource(R.string.downloaded) else stringResource(R.string.download),
                                            tint = if (isDownloaded) EncoreColors.Green else EncoreColors.TextSecondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color.Transparent)
                                            .clickable(onClick = onShareClick),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.encore_icon_share_android_24),
                                            contentDescription = stringResource(R.string.share),
                                            tint = EncoreColors.TextSecondary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color.Transparent)
                                            .clickable { isSortSheetOpen = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.encore_icon_sort_by_24),
                                            contentDescription = stringResource(R.string.sort),
                                            tint = if (sortOption != PlaylistSort.CUSTOM) EncoreColors.Green else EncoreColors.TextSecondary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .clickable { isShuffle = !isShuffle },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.encore_icon_shuffle_24),
                                            contentDescription = stringResource(R.string.shuffle),
                                            tint = if (isShuffle) EncoreColors.Green else EncoreColors.TextSecondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(playButtonSize)
                                            .graphicsLayer { alpha = 1f - playFraction },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        EncorePlayButton(
                                            isPlaying = isPlaying,
                                            onClick = onPlayPauseClick,
                                            size = playButtonSize,
                                            iconSize = 24.dp,
                                            showShuffleBadge = false
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            }
                        }
                    }
                }

                if (displayedTracks.isNotEmpty() && (isEditable || canAddTracks)) {
                    stickyHeader(key = "filter_chips_row") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF121212))
                            .padding(vertical = 8.dp)
                    ) {
                        PlaylistChipsRow(
                            scrollState = chipsScrollState,
                            onAddClick = {
                                if (!isEditable && canAddTracks) {
                                    onAddTracksClick?.invoke()
                                } else {
                                    scope.launch {
                                        listState.animateScrollToItem(displayedTracks.size + 2)
                                    }
                                }
                            },
                            onEditClick = if (isEditable) {
                                { isEditMode = true; onEditModeChange(true) }
                            } else {
                                null
                            },
                            onNameDetailsClick = if (isEditable) {
                                { isEditDetailsOpen = true }
                            } else {
                                null
                            }
                        )
                    }
                }
                }

                if (loading && displayedTracks.isEmpty()) {
                    item(key = "playlist_loading") {
                        TrackListSkeleton(
                            rows = 8,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                if (!loading && loadFailed && displayedTracks.isEmpty()) {
                    item(key = "playlist_load_failed") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.couldn_t_load_this_playlist),
                                style = EncoreTheme.typography.titleMedium.copy(
                                    color = EncoreColors.TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.check_your_internet_connection_and_try_again),
                                style = EncoreTheme.typography.bodySmall.copy(
                                    color = EncoreColors.TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(onClick = onRetry) {
                                Text(text = stringResource(R.string.try_again), color = EncoreColors.Green)
                            }
                        }
                    }
                }
                itemsIndexed(displayedTracks) { _, track ->
                    val isCurrentTrack = matchesCurrent(track.uri, currentTrackUri, track.title, currentTrackTitle)
                    EncoreTrackRow(
                        trackNumber = null,
                        title = track.title,
                        artist = track.artist,
                        isPlaying = isCurrentTrack && isPlaying,
                        isExplicit = track.isExplicit,
                        hasVideo = track.hasVideo,
                        imageUrl = track.imageUrl,
                        artistUri = track.artistUri,
                        onClick = { onTrackSelect(track) },
                        onMoreClick = { onTrackOptionsClick(track) },
                        saved = track.saved,
                        onLikeToggle = { onTrackLikeToggle(track) },
                        onAddToQueue = onTrackAddToQueue?.let { handler -> { handler(track) } }
                    )
                }

                if (loadingMore) {
                    item(key = "playlist_loading_more") {
                        TrackListSkeleton(
                            rows = 2,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                if (!loading && !loadFailed && isEditable && (recommendedTracks.isNotEmpty() || recommendationsLoading)) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.recommended_songs),
                                style = EncoreTheme.typography.titleLarge.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            if (displayedTracks.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.based_on_what_s_in_this_playlist),
                                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
                                )
                            }
                        }
                    }

                    itemsIndexed(recommendedTracks) { _, recTrack ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTrackSelect(recTrack) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF242424)),
                                contentAlignment = Alignment.Center
                            ) {
                                EncoreArtworkImage(
                                    imageUrl = recTrack.imageUrl,
                                    contentDescription = recTrack.title,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 4.dp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = recTrack.title,
                                    style = EncoreTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (recTrack.isExplicit) {
                                        EncoreExplicitTag(
                                            modifier = Modifier.padding(end = 4.dp),
                                            background = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                    Text(
                                        text = recTrack.artist,
                                        style = EncoreTheme.typography.bodySmall.copy(
                                            color = EncoreColors.TextSecondary,
                                            fontSize = 13.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            EncoreHeartButton(
                                isLiked = recTrack.saved == true,
                                onLikeToggle = { onTrackLikeToggle(recTrack) },
                                size = 34.dp,
                                iconSize = 18.dp,
                                modifier = Modifier.padding(end = 8.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                    .clickable { onAddRecommended(recTrack) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.encore_icon_plus_24),
                                    contentDescription = stringResource(R.string.add_to_playlist),
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color.White)
                                    .clickable(enabled = !recommendationsLoading, onClick = onRecommendRefresh)
                                    .padding(horizontal = 24.dp, vertical = 10.dp)
                            ) {
                                if (recommendationsLoading) {
                                    InlineLoadingDots(color = Color.Black)
                                } else {
                                    Text(
                                        text = stringResource(R.string.refresh),
                                        style = EncoreTheme.typography.bodySmallBold.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .height(toolbarBottom)
                    .graphicsLayer {
                        alpha = topBarAlpha
                    }
                    .background(smoothHeaderColor)
                    .zIndex(9.8f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .height(toolbarHeight)
                    .padding(horizontal = 16.dp)
                    .zIndex(10f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = circleBtnAlpha))
                        .clickable(onClick = onBackClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_arrow_left_24),
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = EncoreTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 17.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer { alpha = topBarTitleAlpha }
                    )
                }

                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = circleBtnAlpha * topBarThreeDotsAlpha))
                            .clickable(onClick = onPlaylistOptionsClick)
                            .graphicsLayer { alpha = topBarThreeDotsAlpha },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_more_android_24),
                            contentDescription = stringResource(R.string.playlist_options),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (playFraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 16.dp)
                        .graphicsLayer {
                            translationY = pinnedTopPx
                            alpha = playFraction
                        }
                        .zIndex(20f)
                ) {
                    EncorePlayButton(
                        isPlaying = isPlaying,
                        onClick = onPlayPauseClick,
                        size = playButtonSize,
                        iconSize = 24.dp,
                        showShuffleBadge = false
                    )
                }
            }

        }
    }
}

    com.quackify.compose.sheets.PlaylistSortSheet(
        isOpen = isSortSheetOpen,
        currentSort = sortOption,
        currentDirection = sortDirection,
        onSortSelected = { selected, direction ->
            sortOption = selected
            sortDirection = direction
        },
        onDismiss = { isSortSheetOpen = false }
    )

    com.quackify.compose.sheets.EditPlaylistSheet(
        isOpen = isEditDetailsOpen,
        initialName = currentTitle,
        initialDescription = currentDescription,
        coverUrl = coverUrl,
        canRemoveCover = !coverUrl.isNullOrBlank(),
        onSave = { name, desc, cover, clearCover ->
            currentTitle = name
            currentDescription = desc
            isEditDetailsOpen = false
            scope.launch { onEditDetails?.invoke(name, desc, cover, clearCover) }
        },
        onDeletePlaylist = onDeletePlaylist,
        onDismiss = { isEditDetailsOpen = false }
    )
}

/**
 * Shared Filter Chips Row Composable for seamless in-list and pinned header synchronization
 */
@Composable
private fun PlaylistChipsRow(
    scrollState: ScrollState,
    onAddClick: () -> Unit,
    onEditClick: (() -> Unit)?,
    onNameDetailsClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(16.dp))

        PlaylistChip(
            iconRes = R.drawable.encore_icon_plus_24,
            label = stringResource(R.string.add),
            onClick = onAddClick
        )

        if (onEditClick != null) {
            PlaylistChip(
                iconRes = R.drawable.encore_icon_sort_24,
                label = stringResource(R.string.edit),
                onClick = onEditClick
            )
        }

        if (onNameDetailsClick != null) {
            PlaylistChip(
                iconRes = R.drawable.encore_icon_edit_24,
                label = stringResource(R.string.name_and_details),
                onClick = onNameDetailsClick
            )
        }

        Spacer(modifier = Modifier.width(72.dp))
    }
}

/**
 * Pill shape, #2A2A2A background, white icon + bold white text.
 */
@Composable
private fun PlaylistChip(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Color(0xFF2A2A2A))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                style = EncoreTheme.typography.bodySmallBold.copy(
                    fontSize = 13.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}


/**
 * - Square Cover placeholder (120dp x 120dp) with centered edit pencil icon
 * - Playlist Title with right-side stringResource(R.string.change) pill button
 * - Collaborator row with (+) circle button, stacked owner/contributor avatars
 *   and "owner + N more" — tapping it (or the +) opens the contributors sheet
 * - Action row: Globe (public) and Share icon
 * - Large centered solid white pill button: "+ Add to this playlist"
 */
@Composable
private fun PlaylistEmptyCurationHeader(
    coverUrl: String?,
    title: String,
    creator: String,
    contributors: List<PlaylistMember>,
    creatorAvatarUrl: String,
    onContributorsClick: (() -> Unit)?,
    onEditCoverClick: () -> Unit,
    onChangeTitle: (() -> Unit)?,
    onShareClick: () -> Unit,
    onAddTracksClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF282828))
                    .clickable(onClick = onEditCoverClick),
                contentAlignment = Alignment.Center
            ) {
                if (!coverUrl.isNullOrBlank()) {
                    EncoreArtworkImage(
                        imageUrl = coverUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 8.dp
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_edit_24),
                        contentDescription = stringResource(R.string.edit_cover),
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = EncoreTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF3E3E3E))
                            .clickable { onChangeTitle?.invoke() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.change),
                            style = EncoreTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val ownerMember = contributors.firstOrNull { it.isOwner }
                val ownerName = ownerMember?.name?.takeIf { it.isNotBlank() } ?: creator
                val extraContributors = contributors.count { !it.isOwner }
                val avatarStack = remember(creatorAvatarUrl, contributors) {
                    val ownerAvatar = ownerMember?.avatarUrl?.takeIf { it.isNotBlank() }
                        ?: creatorAvatarUrl.takeIf { it.isNotBlank() }
                        ?: ""
                    val others = contributors.filterNot { it.isOwner }
                        .mapNotNull { it.avatarUrl.takeIf { url -> url.isNotBlank() } }
                        .take(2)
                    (listOf(ownerAvatar) + others).take(3)
                }
                val contributorsClickable = onContributorsClick != null
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .then(
                            if (contributorsClickable) {
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onContributorsClick?.invoke() }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF333333))
                            .then(
                                if (contributorsClickable) {
                                    Modifier.clickable { onContributorsClick?.invoke() }
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.encore_icon_plus_24),
                            contentDescription = stringResource(R.string.add_contributor),
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy((-8).dp)
                    ) {
                        avatarStack.forEachIndexed { index, url ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == 0) 24.dp else 20.dp)
                                    .zIndex(if (index == 0) 2f else 1f)
                                    .clip(CircleShape)
                                    .background(Color(0xFF333333)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (url.isNotBlank()) {
                                    EncoreArtworkImage(
                                        imageUrl = url,
                                        contentDescription = ownerName,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        cornerRadius = 12.dp
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.encore_icon_user_24),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(if (index == 0) 14.dp else 12.dp)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = if (extraContributors > 0) {
                            "$ownerName + $extraContributors more"
                        } else {
                            ownerName
                        },
                        style = EncoreTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.encore_icon_globe_24),
                contentDescription = stringResource(R.string.public_playlist),
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
            Icon(
                painter = painterResource(id = R.drawable.encore_icon_share_android_24),
                contentDescription = stringResource(R.string.share),
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onShareClick)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .clickable(onClick = onAddTracksClick)
                    .padding(horizontal = 28.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.encore_icon_plus_24),
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.add_to_this_playlist),
                        style = EncoreTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            fontSize = 15.sp
                        )
                    )
                }
            }
        }
    }
}
