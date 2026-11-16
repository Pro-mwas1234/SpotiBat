package com.mwask.bat.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mwask.bat.compose.R
import com.mwask.bat.compose.components.EncorePlayButton
import com.mwask.bat.compose.components.EncoreArtworkImage
import com.mwask.bat.compose.playlist.TrackItem
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreTheme
import com.mwask.bat.compose.theme.rememberBottomPadding
import androidx.compose.ui.res.stringResource

/**
 * Recommendation & Hub models for dedicated Category / Genre screens
 */
data class CategoryShelf(
    val title: String,
    val subtitle: String? = null,
    val items: List<CategoryRecommendationItem>
)

data class CategoryRecommendationItem(
    val title: String,
    val subtitle: String,
    val imageUrl: String? = null,
    val coverColor: Color = Color(0xFF282828),
    val uri: String = "",
    val isAlbum: Boolean = false,
    val artistName: String? = null
)

data class CategorySubgenre(
    val title: String,
    val color: Color
)

/**
 */
fun getCategoryColor(title: String): Color = when (title.trim()) {
    "Podcasts" -> Color(0xFFE13300)
    "Live Events" -> Color(0xFF7358FF)
    "Made For You" -> Color(0xFF1E3264)
    "New Releases" -> Color(0xFFE8115B)
    "Pop" -> Color(0xFF148A08)
    "Hip-Hop" -> Color(0xFFBC5900)
    "Rock" -> Color(0xFFE91429)
    "Latin" -> Color(0xFFE1118C)
    "Dance/Electronic" -> Color(0xFFD84000)
    "Discover" -> Color(0xFF8D67AB)
    "Indie" -> Color(0xFF608108)
    "Workout" -> Color(0xFF777777)
    "R&B" -> Color(0xFFDC148C)
    "Chill" -> Color(0xFF432371)
    "Sleep" -> Color(0xFF1E3264)
    "Gaming" -> Color(0xFFE8115B)
    "Bollywood" -> Color(0xFFBA5D00)
    "Punjabi" -> Color(0xFF8D67AB)
    "Hindi" -> Color(0xFF148A08)
    "Tamil" -> Color(0xFFE1118C)
    "Telugu" -> Color(0xFFD84000)
    "Music" -> Color(0xFF1E3264)
    else -> Color(0xFF148A08)
}

/**
 *
 * - Collapsible sticky top bar with back navigation and smooth title crossfade on scroll
 * - Dynamic category gradient starting at Y=0 tinted with the signature genre color
 * - Large Category Title header matching Encore typography
 * - Multi-shelf recommendation carousels (Featured Playlists, New Releases, Radios, Curated Mixes)
 * - Safe mini player bottom padding via [rememberBottomPadding]
 */
@Composable
fun EncoreCategoryScreen(
    categoryTitle: String,
    categoryColor: Color = getCategoryColor(categoryTitle),
    onBackClick: () -> Unit = {},
    onPlaylistClick: (String, String) -> Unit = { _, _ -> },
    onAlbumClick: (String, String, String) -> Unit = { _, _, _ -> },
    onArtistClick: (String, String) -> Unit = { _, _ -> },
    onTrackSelect: (TrackItem) -> Unit = {},
    onSubcategoryClick: (String) -> Unit = {},
    shelves: List<CategoryShelf>? = null,
    subgenres: List<CategorySubgenre>? = null
) {
    val listState = rememberLazyListState()
    val bottomPadding = rememberBottomPadding()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val toolbarHeight = 48.dp

    val (fallbackShelves, fallbackSubgenres) = remember(categoryTitle, categoryColor) {
        getCategoryContent(categoryTitle, categoryColor)
    }

    val effectiveShelves = shelves?.takeIf { it.isNotEmpty() } ?: fallbackShelves
    val effectiveSubgenres = subgenres?.takeIf { it.isNotEmpty() } ?: fallbackSubgenres

    val scrollOffset by remember {
        derivedStateOf {
            listState.firstVisibleItemScrollOffset + listState.firstVisibleItemIndex * 400
        }
    }
    val topBarAlpha by remember {
        derivedStateOf {
            ((scrollOffset - 30f) / 70f).coerceIn(0f, 1f)
        }
    }
    val topBarTitleAlpha by remember {
        derivedStateOf {
            ((scrollOffset - 80f) / 50f).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EncoreColors.PureBlack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            categoryColor.copy(alpha = 0.82f),
                            categoryColor.copy(alpha = 0.35f),
                            EncoreColors.PureBlack
                        )
                    )
                )
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = statusBarHeight + toolbarHeight)
        ) {
            item(key = "category_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = categoryTitle,
                        style = EncoreTheme.typography.headlineLarge.copy(
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }

            items(effectiveShelves, key = { it.title }) { shelf ->
                Column(modifier = Modifier.padding(top = 18.dp)) {
                    Text(
                        text = shelf.title,
                        style = EncoreTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    if (!shelf.subtitle.isNullOrBlank()) {
                        Text(
                            text = shelf.subtitle,
                            style = EncoreTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                color = EncoreColors.TextSecondary
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(shelf.items, key = { it.title + it.uri }) { item ->
                            Column(
                                modifier = Modifier
                                    .width(148.dp)
                                    .clickable {
                                        if (item.isAlbum) {
                                            onAlbumClick(item.title, item.artistName ?: "", item.uri)
                                        } else {
                                            onPlaylistClick(item.title, item.uri.ifEmpty { "spotify:playlist:${item.title}" })
                                        }
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(148.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(item.coverColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!item.imageUrl.isNullOrBlank()) {
                                        EncoreArtworkImage(
                                            imageUrl = item.imageUrl,
                                            contentDescription = item.title,
                                            modifier = Modifier.fillMaxSize(),
                                            cornerRadius = 4.dp
                                        )
                                    } else {
                                        Text(
                                            text = "♫",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 44.sp
                                        )
                                    }

                                    EncorePlayButton(
                                        isPlaying = false,
                                        onClick = {
                                            if (item.isAlbum) {
                                                onAlbumClick(item.title, item.artistName ?: "", item.uri)
                                            } else {
                                                onPlaylistClick(
                                                    item.title,
                                                    item.uri.ifEmpty { "spotify:playlist:${item.title}" }
                                                )
                                            }
                                        },
                                        size = 36.dp,
                                        iconSize = 16.dp,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .shadow(elevation = 6.dp, shape = CircleShape)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = item.title,
                                    style = EncoreTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = EncoreColors.TextPrimary,
                                        fontSize = 14.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = item.subtitle,
                                    style = EncoreTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        color = EncoreColors.TextSecondary,
                                        lineHeight = 16.sp
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            if (effectiveSubgenres.isNotEmpty()) {
                item(key = "subcategories_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 28.dp, bottom = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.explore_category, categoryTitle),
                            style = EncoreTheme.typography.titleLarge.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }

                items(effectiveSubgenres.chunked(2), key = { pair -> pair.joinToString { it.title } }) { rowPair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (sub in rowPair) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(84.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(sub.color)
                                    .clickable { onSubcategoryClick(sub.title) }
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = sub.title,
                                    style = EncoreTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    ),
                                    modifier = Modifier.align(Alignment.TopStart)
                                )
                            }
                        }
                        if (rowPair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(bottomPadding + 16.dp))
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(10f)
                .background(categoryColor.copy(alpha = topBarAlpha))
                .statusBarsPadding()
                .height(toolbarHeight)
                .padding(horizontal = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = (0.4f * (1f - topBarAlpha)).coerceIn(0f, 0.4f)))
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

            if (topBarTitleAlpha > 0.01f) {
                Text(
                    text = categoryTitle,
                    style = EncoreTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = topBarTitleAlpha),
                        fontSize = 16.sp
                    ),
                    modifier = Modifier.align(Alignment.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 */
private fun getCategoryContent(
    title: String,
    categoryColor: Color
): Pair<List<CategoryShelf>, List<CategorySubgenre>> =
    emptyList<CategoryShelf>() to emptyList()
