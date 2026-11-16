package com.mwask.bat.compose.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mwask.bat.compose.R
import com.mwask.bat.compose.components.EncoreIconButton
import com.mwask.bat.compose.components.EncoreTrackRow
import com.mwask.bat.compose.components.EncoreArtworkImage
import com.mwask.bat.compose.components.TrackListSkeleton
import com.mwask.bat.compose.format.formatCount
import com.mwask.bat.compose.playlist.TrackItem
import com.mwask.bat.compose.theme.EncoreColors
import com.mwask.bat.compose.theme.EncoreAppTheme
import com.mwask.bat.compose.theme.EncoreTheme
import com.mwask.bat.compose.theme.EncoreCornerRadii
import com.mwask.bat.compose.theme.rememberBottomPadding
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

/**
 * artist that follow each other collapse into one expandable artist group,
 */
data class HistoryStats(
    val totalPlayedMs: Long = 0L,
    val listenStreakDays: Int = 0,
    val loginStreakDays: Int = 0,
    val topArtistName: String = "",
    val topArtistUri: String = "",
    val topArtistPlays: Int = 0,
    val topArtistImageUrl: String? = null,
    val topPlaylistName: String = "",
    val topPlaylistUri: String = "",
    val topPlaylistPlays: Int = 0,
    val topPlaylistImageUrl: String? = null,
    val topReplayTitle: String = "",
    val topReplayArtist: String = "",
    val topReplayPlays: Int = 0,
    val topReplayImageUrl: String? = null,
) {
    val hasAny: Boolean
        get() = totalPlayedMs > 0L || listenStreakDays > 0 || loginStreakDays > 0 ||
            topArtistName.isNotBlank() || topPlaylistName.isNotBlank() || topReplayTitle.isNotBlank()
}

@Composable
fun HistoryScreen(
    onBackClick: () -> Unit,
    onTrackSelect: (TrackItem) -> Unit,
    onTrackOptionsClick: (TrackItem) -> Unit = {},
    onTrackAddToQueue: (TrackItem) -> Unit = {},
    currentTrackTitle: String = "",
    currentTrackUri: String = "",
    isPlaying: Boolean = false,
    todayTracks: List<TrackItem> = emptyList(),
    yesterdayTracks: List<TrackItem> = emptyList(),
    earlierTracks: List<TrackItem> = emptyList(),
    stats: HistoryStats? = null,
    loading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bottomPadding = rememberBottomPadding()
    val hasContent = todayTracks.isNotEmpty() ||
        yesterdayTracks.isNotEmpty() ||
        earlierTracks.isNotEmpty()

    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    val sectionExpanded = remember { mutableStateMapOf<String, Boolean>() }
    val todayEntries = remember(todayTracks) { groupByArtist(todayTracks) }
    val yesterdayEntries = remember(yesterdayTracks) { groupByArtist(yesterdayTracks) }
    val earlierEntries = remember(earlierTracks) { groupByArtist(earlierTracks) }

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
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.listening_history),
                style = EncoreTheme.typography.titleLarge.copy(fontSize = 20.sp)
            )
        }

        if (loading && !hasContent) {
            TrackListSkeleton(
                rows = 9,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            )
        } else if (!hasContent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = bottomPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.tracks_you_play_will_show_up_here),
                    style = EncoreTheme.typography.bodyMedium.copy(
                        color = EncoreColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                )
            }
        } else {
            val todayLabel = stringResource(R.string.today)
            val yesterdayLabel = stringResource(R.string.yesterday)
            val earlierLabel = stringResource(R.string.earlier)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding
                )
            ) {
                if (stats != null && stats.hasAny) {
                    item(key = "stats") {
                        HistoryStatsSection(stats = stats)
                    }
                }
                historySection(
                    label = todayLabel,
                    entries = todayEntries,
                    currentTrackTitle = currentTrackTitle,
                    isPlaying = isPlaying,
                    onTrackSelect = onTrackSelect,
                    onTrackOptionsClick = onTrackOptionsClick,
                    onTrackAddToQueue = onTrackAddToQueue,
                    expandedGroups = expandedGroups,
                    isExpanded = sectionExpanded[todayLabel] != false,
                    onToggle = {
                        val wasExpanded = sectionExpanded[todayLabel] != false
                        sectionExpanded[todayLabel] = !wasExpanded
                    }
                )
                historySection(
                    label = yesterdayLabel,
                    entries = yesterdayEntries,
                    currentTrackTitle = currentTrackTitle,
                    isPlaying = isPlaying,
                    onTrackSelect = onTrackSelect,
                    onTrackOptionsClick = onTrackOptionsClick,
                    onTrackAddToQueue = onTrackAddToQueue,
                    expandedGroups = expandedGroups,
                    isExpanded = sectionExpanded[yesterdayLabel] != false,
                    onToggle = {
                        val wasExpanded = sectionExpanded[yesterdayLabel] != false
                        sectionExpanded[yesterdayLabel] = !wasExpanded
                    }
                )
                historySection(
                    label = earlierLabel,
                    entries = earlierEntries,
                    currentTrackTitle = currentTrackTitle,
                    isPlaying = isPlaying,
                    onTrackSelect = onTrackSelect,
                    onTrackOptionsClick = onTrackOptionsClick,
                    onTrackAddToQueue = onTrackAddToQueue,
                    expandedGroups = expandedGroups,
                    isExpanded = sectionExpanded[earlierLabel] != false,
                    onToggle = {
                        val wasExpanded = sectionExpanded[earlierLabel] != false
                        sectionExpanded[earlierLabel] = !wasExpanded
                    }
                )

                item {
                    Spacer(modifier = Modifier.height(120.dp))
                }
            }
        }
    }
}

/**
 * One rendered history row: a lone play, or a run of consecutive plays by the
 * same artist collapsed behind an expandable header naming the artist.
 */
private sealed interface HistoryEntry {
    data class Single(val track: TrackItem, val id: String) : HistoryEntry

    data class Group(
        val artist: String,
        val imageUrl: String?,
        val tracks: List<TrackItem>,
        val id: String,
    ) : HistoryEntry
}

private fun artistKey(track: TrackItem): String =
    track.artistUri.takeIf { it.isNotBlank() } ?: track.artist.lowercase()

/** Runs of 2+ consecutive plays by one artist become a group; singletons stay rows. */
private fun groupByArtist(tracks: List<TrackItem>): List<HistoryEntry> {
    val out = ArrayList<HistoryEntry>(tracks.size)
    var i = 0
    while (i < tracks.size) {
        val head = tracks[i]
        val key = artistKey(head)
        var j = i + 1
        while (j < tracks.size && artistKey(tracks[j]) == key) j++
        val run = tracks.subList(i, j)
        val id = "$i-${head.artistUri.ifBlank { head.artist }}"
        out += if (run.size >= 2) {
            HistoryEntry.Group(
                artist = head.artist,
                imageUrl = head.imageUrl,
                tracks = run.toList(),
                id = id,
            )
        } else {
            HistoryEntry.Single(head, id)
        }
        i = j
    }
    return out
}

private fun LazyListScope.historySection(
    label: String,
    entries: List<HistoryEntry>,
    currentTrackTitle: String,
    isPlaying: Boolean,
    onTrackSelect: (TrackItem) -> Unit,
    onTrackOptionsClick: (TrackItem) -> Unit,
    onTrackAddToQueue: (TrackItem) -> Unit,
    expandedGroups: SnapshotStateMap<String, Boolean>,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    if (entries.isEmpty()) return
    item(key = "section-$label") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .animateItem()
                .clickable(onClick = onToggle)
                .padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = EncoreTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(
                    id = if (isExpanded) R.drawable.encore_icon_chevron_up_24
                    else R.drawable.encore_icon_chevron_down_24
                ),
                contentDescription = if (isExpanded) "Collapse $label" else "Expand $label",
                tint = EncoreColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    if (isExpanded) {
        entries.forEach { entry ->
            when (entry) {
                is HistoryEntry.Single -> item(key = "$label:row:${entry.id}") {
                    EncoreTrackRow(
                        trackNumber = null,
                        title = entry.track.title,
                        artist = entry.track.artist,
                        isExplicit = entry.track.isExplicit,
                        imageUrl = entry.track.imageUrl,
                        artistUri = entry.track.artistUri,
                        isPlaying = isPlaying && entry.track.title == currentTrackTitle,
                        onClick = { onTrackSelect(entry.track) },
                        onMoreClick = { onTrackOptionsClick(entry.track) },
                        onAddToQueue = { onTrackAddToQueue(entry.track) },
                        modifier = Modifier.animateItem()
                    )
                }
                is HistoryEntry.Group -> item(key = "$label:group:${entry.id}") {
                    val groupKey = "$label:${entry.id}"
                    val expanded = expandedGroups[groupKey] == true
                    Box(modifier = Modifier.animateItem()) {
                        HistoryArtistGroup(
                            group = entry,
                            isExpanded = expanded,
                            onToggle = {
                                expandedGroups[groupKey] = !expanded
                            },
                            currentTrackTitle = currentTrackTitle,
                            isPlaying = isPlaying,
                            onTrackSelect = onTrackSelect,
                            onTrackOptionsClick = onTrackOptionsClick,
                            onTrackAddToQueue = onTrackAddToQueue
                        )
                    }
                }
            }
        }
    }
}

/** Collapsed-by-default artist header that expands into the run of plays under it. */
@Composable
private fun HistoryArtistGroup(
    group: HistoryEntry.Group,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    currentTrackTitle: String,
    isPlaying: Boolean,
    onTrackSelect: (TrackItem) -> Unit,
    onTrackOptionsClick: (TrackItem) -> Unit,
    onTrackAddToQueue: (TrackItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EncoreArtworkImage(
                imageUrl = group.imageUrl,
                contentDescription = group.artist,
                modifier = Modifier.size(48.dp),
                shape = CircleShape
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.artist,
                    style = EncoreTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = EncoreColors.TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${group.tracks.size} song" +
                        if (group.tracks.size == 1) "" else "s",
                    style = EncoreTheme.typography.bodySmall.copy(
                        color = EncoreColors.TextSecondary
                    ),
                    maxLines = 1
                )
            }
            Icon(
                painter = painterResource(
                    id = if (isExpanded) R.drawable.encore_icon_chevron_up_24
                    else R.drawable.encore_icon_chevron_down_24
                ),
                contentDescription = if (isExpanded) "Collapse ${group.artist}"
                else "Expand ${group.artist}",
                tint = EncoreColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        if (isExpanded) {
            group.tracks.forEach { track ->
                EncoreTrackRow(
                    trackNumber = null,
                    title = track.title,
                    artist = track.artist,
                    isExplicit = track.isExplicit,
                    imageUrl = track.imageUrl,
                    artistUri = track.artistUri,
                    isPlaying = isPlaying && track.title == currentTrackTitle,
                    onClick = { onTrackSelect(track) },
                    onMoreClick = { onTrackOptionsClick(track) },
                    onAddToQueue = { onTrackAddToQueue(track) }
                )
            }
        }
    }
}

@Composable
private fun HistoryStatsSection(
    stats: HistoryStats,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.your_stats),
            style = EncoreTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HistoryStatCard(
                label = stringResource(R.string.minutes_played),
                value = formatMinutes(stats.totalPlayedMs),
                modifier = Modifier.weight(1f)
            )
            HistoryStatCard(
                label = stringResource(R.string.listening_streak),
                value = daysLabel(stats.listenStreakDays),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HistoryStatCard(
                label = stringResource(R.string.login_streak),
                value = daysLabel(stats.loginStreakDays),
                modifier = Modifier.weight(1f)
            )
            HistoryStatCard(
                label = stringResource(R.string.top_artist),
                value = stats.topArtistName.ifBlank { "—" },
                sub = playsLabel(stats.topArtistPlays),
                imageUrl = stats.topArtistImageUrl?.takeIf { it.isNotBlank() },
                imageShape = CircleShape,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HistoryStatCard(
                label = stringResource(R.string.top_playlist),
                value = stats.topPlaylistName.ifBlank { "—" },
                sub = playsLabel(stats.topPlaylistPlays),
                imageUrl = stats.topPlaylistImageUrl?.takeIf { it.isNotBlank() },
                imageShape = RoundedCornerShape(4.dp),
                modifier = Modifier.weight(1f)
            )
            HistoryStatCard(
                label = stringResource(R.string.most_replayed),
                value = stats.topReplayTitle.ifBlank { "—" },
                sub = listOfNotNull(
                    stats.topReplayArtist.takeIf { it.isNotBlank() },
                    playsLabel(stats.topReplayPlays).takeIf { stats.topReplayPlays > 1 },
                ).joinToString(" · ").ifBlank { null },
                imageUrl = stats.topReplayImageUrl?.takeIf { it.isNotBlank() },
                imageShape = RoundedCornerShape(4.dp),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HistoryStatCard(
    label: String,
    value: String,
    sub: String? = null,
    imageUrl: String? = null,
    imageShape: Shape? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .background(EncoreColors.Card, RoundedCornerShape(EncoreCornerRadii.Larger2))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (imageUrl != null && imageShape != null) {
            EncoreArtworkImage(
                imageUrl = imageUrl,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                shape = imageShape
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = EncoreTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = EncoreColors.TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sub != null) {
                Text(
                    text = sub,
                    style = EncoreTheme.typography.bodySmall.copy(color = EncoreColors.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatMinutes(totalMs: Long): String {
    val minutes = totalMs / 60_000L
    return when {
        minutes >= 60L -> "${minutes / 60L}h ${minutes % 60L}m"
        else -> "$minutes min"
    }
}

@Composable
private fun daysLabel(days: Int): String =
    pluralStringResource(R.plurals.day_count, days, days)

@Composable
private fun playsLabel(plays: Int): String =
    pluralStringResource(R.plurals.play_count, plays, formatCount(plays.toLong()))
