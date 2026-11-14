package com.quackify.compose.home

import androidx.compose.ui.graphics.Color
import com.quackify.backend.api.HomeFeed
import com.quackify.backend.model.HomeItem
import com.quackify.backend.model.TrackRow
import com.quackify.backend.model.ShelfItem
import com.quackify.compose.format.parseHexColor
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.screens.ArtistRelease
import com.quackify.backend.model.HomeSection

private val FallbackCardColor = Color(0xFF535353)

private const val RECENT_SHELF_SIZE = 20

private const val QUICK_ACCESS_SIZE = 8

private const val LIKED_SONGS_URI = "spotify:collection:tracks"
private val LikedSongsColor = Color(0xFF5038A0)

private const val LOCAL_RECENT_SHELF_ID = "local:recently-played"
private const val LOCAL_FAVORITE_ARTISTS_SHELF_ID = "local:favorite-artists"

private fun colorOf(item: HomeItem): Color {
    val hex = item.colorHex
    if (hex.length != 7 || hex[0] != '#') return FallbackCardColor
    val rgb = hex.substring(1).toLongOrNull(16) ?: return FallbackCardColor
    return Color(0xFF000000L or rgb)
}

private fun tagOf(kind: String): String = when {
    kind.contains("Playlist", true) -> "PLAYLIST"
    kind.contains("Album", true) -> "ALBUM"
    kind.contains("Artist", true) -> "ARTIST"
    kind.contains("Episode", true) -> "EPISODE"
    kind.contains("Show", true) -> "SHOW"
    else -> kind.uppercase().take(12)
}

data class HomeShelf(
    val id: String,
    val title: String,
    val cards: List<RecommendationCard> = emptyList(),
    val artists: List<ArtistCardItem> = emptyList(),
    val grid: Boolean = false,
)

private fun HomeItem.toCard() = RecommendationCard(
    title = title,
    subtitle = subtitle.ifBlank { kind },
    coverColor = colorOf(this),
    uri = uri,
    imageUrl = imageUrl.takeIf { it.isNotBlank() },
    tag = tagOf(kind),
    caption = caption,
)

private fun List<HomeItem>.toCards(): List<RecommendationCard> = map { it.toCard() }

fun List<HomeSection>.toShelves(): List<HomeShelf> {
    val out = ArrayList<HomeShelf>()
    var run: MutableList<HomeItem>? = null
    var runIndex = -1
    for (section in this) {
        if (section.kind == HomeFeed.SHORTS || section.kind == HomeFeed.RECENT) continue
        if (section.kind == HomeFeed.BASELINE) {
            if (section.items.isEmpty()) continue
            val open = run
            if (open == null) {
                val started = ArrayList(section.items)
                run = started
                runIndex = out.size
                out += HomeShelf(
                    id = "baseline:${section.id}",
                    title = "",
                    grid = true,
                    cards = started.toCards(),
                )
            } else {
                open += section.items
                out[runIndex] = out[runIndex].copy(cards = open.toCards())
            }
            continue
        }
        run = null
        val cards = section.items.toCards()
        if (cards.isEmpty()) continue
        val artistsOnly = section.items.all { tagOf(it.kind) == "ARTIST" }
        out += HomeShelf(
            id = section.id.ifBlank { "${section.title}:${section.kind}" },
            title = section.title,
            cards = if (artistsOnly) emptyList() else cards,
            artists = if (artistsOnly) {
                section.items.map { item ->
                    ArtistCardItem(
                        name = item.title,
                        color = colorOf(item),
                        uri = item.uri,
                        imageUrl = item.imageUrl.takeIf { it.isNotBlank() },
                    )
                }
            } else {
                emptyList()
            },
        )
    }
    return out
}

fun List<HomeSection>.toQuickAccess(): List<QuickAccessItem> =
    firstOrNull { it.kind == HomeFeed.SHORTS }?.items?.map { item ->
        QuickAccessItem(
            title = item.title,
            coverColor = colorOf(item),
            uri = item.uri,
            imageUrl = item.imageUrl.takeIf { it.isNotBlank() },
        )
    }.orEmpty()

fun List<TrackItem>.toQuickAccessFallback(): List<QuickAccessItem> {
    val artists = LinkedHashMap<String, TrackItem>()
    for (track in this) {
        val uri = track.artistUri
        if (!uri.startsWith("spotify:artist:")) continue
        if (track.artist.isBlank() || uri in artists) continue
        artists[uri] = track
    }
    val out = ArrayList<QuickAccessItem>(QUICK_ACCESS_SIZE)
    out += QuickAccessItem(
        title = "Liked Songs",
        coverColor = LikedSongsColor,
        uri = LIKED_SONGS_URI,
    )
    for ((uri, track) in artists) {
        if (out.size >= QUICK_ACCESS_SIZE) break
        out += QuickAccessItem(
            title = track.artist.substringBefore(", "),
            coverColor = FallbackCardColor,
            uri = uri,
            imageUrl = track.imageUrl,
        )
    }
    return out
}

/** The listening history, newest distinct tracks first, as the home Recently played shelf. */
fun List<TrackItem>.toRecentlyPlayedShelf(): HomeShelf? {
    if (isEmpty()) return null
    return HomeShelf(
        id = LOCAL_RECENT_SHELF_ID,
        title = "Recently played",
        cards = take(RECENT_SHELF_SIZE).map { track ->
            RecommendationCard(
                title = track.title,
                subtitle = track.artist,
                coverColor = FallbackCardColor,
                uri = track.uri,
                imageUrl = track.imageUrl,
                tag = "SONG",
            )
        },
    )
}

/** The history's favorite artists, each as its most recent track, as the home favorites shelf. */
fun List<TrackItem>.toFavoriteArtistsShelf(artistImages: Map<String, String> = emptyMap()): HomeShelf? {
    if (isEmpty()) return null
    return HomeShelf(
        id = LOCAL_FAVORITE_ARTISTS_SHELF_ID,
        title = "Your favorite artist",
        artists = map { track ->
            ArtistCardItem(
                name = track.artist.substringBefore(", "),
                color = FallbackCardColor,
                uri = track.artistUri,
                imageUrl = artistImages[track.artistUri],
            )
        },
    )
}

fun TrackRow.toTrackItem(): TrackItem = TrackItem(
    title = title,
    artist = artists,
    isExplicit = explicit,
    uri = uri,
    uid = uid,
    imageUrl = imageUrl.takeIf { it.isNotBlank() },
    saved = saved,
    durationMs = durationMs,
    albumUri = albumUri,
    artistUri = artistUri,
    discNumber = discNumber,
    trackNumber = trackNumber,
    hasVideo = video,
    plays = plays,
)

fun ShelfItem.toArtistRelease(): ArtistRelease {
    val rawType = subtitle.substringAfter(" \u2022 ", "Album").trim()
    return ArtistRelease(
        title = title,
        year = subtitle.substringBefore(" \u2022 ").trim(),
        type = if (rawType.equals("Ep", ignoreCase = true)) "EP" else rawType,
        color = parseHexColor(colorHex) ?: Color(0xFF333333),
        imageUrl = imageUrl.takeIf { it.isNotBlank() },
        uri = uri
    )
}
