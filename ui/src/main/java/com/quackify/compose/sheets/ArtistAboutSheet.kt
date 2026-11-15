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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quackify.compose.components.EncoreArtworkImage
import com.quackify.backend.model.ArtistLink
import com.quackify.backend.model.CityStat
import com.quackify.compose.theme.EncoreColors
import com.quackify.compose.theme.EncoreCornerRadii
import com.quackify.compose.theme.EncoreSpacers
import com.quackify.compose.theme.EncoreTheme
import androidx.compose.ui.res.stringResource
import com.quackify.compose.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistAboutSheet(
    isOpen: Boolean,
    artistName: String,
    imageUrl: String?,
    monthlyListeners: String,
    followers: String,
    worldRank: Int,
    biography: String,
    topCities: List<CityStat>,
    externalLinks: List<ArtistLink>,
    gallery: List<String>,
    onDismiss: () -> Unit,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return


    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EncoreArtworkImage(
                    imageUrl = imageUrl,
                    contentDescription = artistName,
                    modifier = Modifier.size(72.dp),
                    cornerRadius = 36.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.about),
                        style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary)
                    )
                    Text(
                        text = artistName,
                        style = EncoreTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(EncoreSpacers.Tighter))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (monthlyListeners.isNotBlank()) {
                    AboutStat(monthlyListeners, stringResource(R.string.monthly_listeners))
                }
                if (followers.isNotBlank()) {
                    AboutStat(followers, "followers")
                }
                if (worldRank > 0) {
                    AboutStat("#$worldRank", "in the world")
                }
            }

            if (biography.isNotBlank()) {
                Spacer(modifier = Modifier.height(EncoreSpacers.Looser))
                Text(
                    text = biography,
                    style = EncoreTheme.typography.bodyMedium.copy(color = EncoreColors.TextSecondary)
                )
            }

            if (topCities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(EncoreSpacers.Looser))
                Text(
                    text = stringResource(R.string.popular_in),
                    style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(EncoreSpacers.Tighter))
                topCities.take(5).forEach { stat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stat.city,
                            style = EncoreTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.listener_count, compact(stat.listeners)),
                            style = EncoreTheme.typography.bodySmall.copy(
                                color = EncoreColors.TextSecondary
                            )
                        )
                    }
                }
            }

            if (externalLinks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(EncoreSpacers.Looser))
                Text(
                    text = stringResource(R.string.links),
                    style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(EncoreSpacers.Tighter))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    externalLinks.forEach { link ->
                        Text(
                            text = link.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextPrimary),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF3A3A3A))
                                .clickable { onLinkClick(link.url) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            if (gallery.isNotEmpty()) {
                Spacer(modifier = Modifier.height(EncoreSpacers.Looser))
                Text(
                    text = stringResource(R.string.gallery),
                    style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(EncoreSpacers.Tighter))
                LazyRow(
                    contentPadding = PaddingValues(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(EncoreSpacers.Tighter)
                ) {
                    items(gallery, key = { it }) { url ->
                        EncoreArtworkImage(
                            imageUrl = url,
                            contentDescription = artistName,
                            modifier = Modifier
                                .size(width = 220.dp, height = 124.dp)
                                .clip(RoundedCornerShape(EncoreCornerRadii.Base)),
                            cornerRadius = EncoreCornerRadii.Base
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AboutStat(value: String, label: String) {
    Column {
        Text(
            text = value,
            style = EncoreTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            softWrap = false
        )
        Text(
            text = label,
            style = EncoreTheme.typography.marginal.copy(color = EncoreColors.TextSecondary),
            softWrap = false
        )
    }
}

private fun compact(value: Long): String = when {
    value >= 1_000_000 -> "${value / 1_000_000}M"
    value >= 1_000 -> "${value / 1_000}K"
    else -> value.toString()
}
