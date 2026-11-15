package com.quackify.compose.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.components.InlineLoadingDots
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.backend.model.ShelfItem
import com.quackify.backend.model.ShelfKind
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreTheme
import com.quackify.backend.library.Krespot
import androidx.compose.ui.res.stringResource
import com.quackify.compose.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistShelfSheet(
    isOpen: Boolean,
    title: String,
    artistUri: String,
    section: String,
    onDismiss: () -> Unit,
    onOpenItem: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return


    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val rows = remember { mutableStateListOf<ShelfItem>() }
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
        val page = Krespot.artists.artistShelf(context, artistUri, section, rows.size)
        loading = false
        if (page == null) {
            failed = true
            return
        }
        val before = rows.size
        rows.addAll(page.rows.filter { row -> rows.none { it.uri == row.uri } })
        total = if (rows.size == before) rows.size else page.total
    }

    LaunchedEffect(section, artistUri) {
        fetch(true)
    }

    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    LaunchedEffect(lastVisible, rows.size, total) {
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
                .fillMaxHeight(0.9f)
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
                        text = title,
                        style = EncoreTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (total > 0) {
                        Text(
                            text = stringResource(R.string.item_count, total),
                            style = EncoreTheme.typography.bodySmall.copy(
                                color = EncoreColors.TextSecondary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(rows, key = { it.uri }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onOpenItem(row.uri, row.title)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EncoreArtworkImage(
                            imageUrl = row.imageUrl.takeIf { it.isNotBlank() },
                            contentDescription = row.title,
                            modifier = Modifier.size(52.dp),
                            cornerRadius = if (row.kind == ShelfKind.ARTIST) 26.dp else 4.dp
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
                                    style = EncoreTheme.typography.marginal.copy(
                                        color = EncoreColors.TextSecondary
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                if (loading || failed) {
                    item(key = "status") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (failed) {
                                Text(
                                    text = stringResource(R.string.could_not_load_this_list),
                                    style = EncoreTheme.typography.bodySmall.copy(
                                        color = EncoreColors.TextSecondary
                                    )
                                )
                            } else {
                                InlineLoadingDots(color = EncoreColors.TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
