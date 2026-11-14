package com.quackify.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.components.InlineLoadingDots
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.backend.model.ShelfItem
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme
import com.quackify.backend.library.Krespot

private data class DiscographyTab(val key: String, val label: String)

private val TABS = listOf(
    DiscographyTab("all", "Popular releases"),
    DiscographyTab("albums", "Albums"),
    DiscographyTab("singles", "Singles and EPs"),
    DiscographyTab("compilations", "Compilations"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscographySheet(
    isOpen: Boolean,
    artistUri: String,
    artistName: String,
    onDismiss: () -> Unit,
    onOpenRelease: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return


    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    val rows = remember { mutableStateListOf<ShelfItem>() }
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var section by remember { mutableStateOf("all") }
    var alphabetical by remember { mutableStateOf(false) }
    var grid by remember { mutableStateOf(false) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    suspend fun fetch(reset: Boolean) {
        if (loading) return
        loading = true
        failed = false
        if (reset) {
            rows.clear()
            total = 0
        }
        val order = if (alphabetical) "NAME_ASC" else "DATE_DESC"
        val page = Krespot.artists.discography(context, artistUri, section, rows.size, order)
        loading = false
        if (page == null) {
            failed = true
            return
        }
        rows.addAll(page.rows.filter { row -> rows.none { it.uri == row.uri } })
        total = page.total
    }

    LaunchedEffect(artistUri) {
        if (counts.isEmpty()) counts = Krespot.artists.discographyCounts(context, artistUri)
    }

    LaunchedEffect(section, alphabetical) {
        fetch(true)
    }

    val lastVisible = if (grid) {
        gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    } else {
        listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    }
    LaunchedEffect(lastVisible, section, alphabetical, rows.size, total) {
        if (rows.isNotEmpty() && rows.size < total && lastVisible >= rows.size - 3) fetch(false)
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
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Discography",
                        style = EncoreTheme.typography.titleLarge.copy(fontSize = 20.sp)
                    )
                    Text(
                        text = artistName,
                        style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SheetPill(
                    text = if (alphabetical) "A-Z" else "Recent",
                    onClick = { alphabetical = !alphabetical }
                )
                Spacer(modifier = Modifier.width(6.dp))
                SheetPill(
                    text = if (grid) "Grid" else "List",
                    onClick = { grid = !grid }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TABS.forEach { tab ->
                    val count = counts[tab.key] ?: 0
                    if (tab.key != "all" && count == 0) return@forEach
                    DiscographyChip(
                        label = tab.label,
                        count = count,
                        selected = section == tab.key,
                        onClick = { if (section != tab.key) section = tab.key }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (grid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    gridItems(rows, key = { it.uri }) { row ->
                        ReleaseTile(
                            row = row,
                            onClick = {
                                onDismiss()
                                onOpenRelease(row.uri, row.title)
                            }
                        )
                    }
                    if (loading || failed) {
                        item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                            StatusTile(failed)
                        }
                    } else if (rows.isEmpty()) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyTile() }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(rows, key = { it.uri }) { row ->
                        ReleaseRow(
                            row = row,
                            onClick = {
                                onDismiss()
                                onOpenRelease(row.uri, row.title)
                            }
                        )
                    }
                    if (loading || failed) {
                        item(key = "status") { StatusTile(failed) }
                    } else if (rows.isEmpty()) {
                        item(key = "empty") { EmptyTile() }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscographyChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) EncoreColors.Green else Color(0xFF2A2A2A)
    val foreground = if (selected) Color.Black else EncoreColors.TextPrimary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = EncoreTheme.typography.bodySmall.copy(
                color = foreground,
                fontWeight = FontWeight.Medium
            )
        )
        if (count > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = count.toString(),
                style = EncoreTheme.typography.marginal.copy(
                    color = if (selected) Color.Black.copy(alpha = 0.6f) else EncoreColors.TextSecondary
                )
            )
        }
    }
}

@Composable
private fun SheetPill(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = EncoreTheme.typography.bodySmall.copy(
            color = EncoreColors.Green,
            fontWeight = FontWeight.Bold
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun ReleaseTile(row: ShelfItem, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        EncoreArtworkImage(
            imageUrl = row.imageUrl.takeIf { it.isNotBlank() },
            contentDescription = row.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(6.dp)),
            cornerRadius = 6.dp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = row.title,
            style = EncoreTheme.typography.bodySmallBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (row.subtitle.isNotBlank()) {
            Text(
                text = row.subtitle,
                style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusTile(failed: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (failed) {
            Text(
                text = "Could not load this section",
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
            )
        } else {
            InlineLoadingDots(color = EncoreColors.TextSecondary)
        }
    }
}

@Composable
private fun EmptyTile() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Nothing here yet",
            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary)
        )
    }
}

@Composable
private fun ReleaseRow(row: ShelfItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EncoreArtworkImage(
            imageUrl = row.imageUrl.takeIf { it.isNotBlank() },
            contentDescription = row.title,
            modifier = Modifier.size(52.dp),
            cornerRadius = 4.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = EncoreTheme.typography.bodyMediumBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (row.subtitle.isNotBlank()) {
                Text(
                    text = row.subtitle,
                    style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
