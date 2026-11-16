package com.mwask.bat

import android.content.Context
import com.mwask.bat.backend.core.store.AppPrefs
import com.mwask.bat.backend.model.TrackRow
import com.mwask.bat.compose.playlist.TrackItem
import org.json.JSONArray
import org.json.JSONObject

data class PlayedTrack(
    val item: TrackItem,
    val playedAtMs: Long,
    val plays: Int = 1,
)

object PlayHistory {

    private const val PREFS = "spotibat.client"
    private const val KEY = "playHistory"
    private const val LIMIT = 200

    fun all(context: Context): List<PlayedTrack> {
        val raw = prefs(context).getString(KEY, null)
            ?: return emptyList()

        val array = runCatching {
            JSONArray(raw)
        }.getOrNull() ?: return emptyList()

        val out = ArrayList<PlayedTrack>(array.length())

        for (i in 0 until array.length()) {
            val node = array.optJSONObject(i) ?: continue

            val uri = node.optString("uri")
            if (!uri.startsWith("spotify:track:") && !uri.startsWith("spotify:episode:")) continue

            val candidate = PlayedTrack(
                item = TrackItem(
                    title = node.optString("title"),
                    artist = node.optString("artist"),
                    uri = uri,
                    imageUrl = node.optString("image")
                        .takeIf { it.isNotBlank() },
                    albumUri = node.optString("albumUri"),
                    albumName = node.optString("albumName"),
                    artistUri = node.optString("artistUri"),
                    isExplicit = node.optBoolean("explicit", false),
                    durationMs = node.optLong("durationMs", 0L),
                ),
                playedAtMs = node.optLong("at", 0L),
                plays = node.optInt("plays", 1).coerceAtLeast(1),
            )

            if (out.none { sameSong(it.item, candidate.item) && sameDay(it.playedAtMs, candidate.playedAtMs) }) {
                out += candidate
            }
        }

        return out
    }

    fun record(
        context: Context,
        track: TrackItem,
        playedAtMs: Long,
    ): List<PlayedTrack> {
        if (!track.uri.startsWith("spotify:track:") &&
            !track.uri.startsWith("spotify:episode:")
        ) {
            return all(context)
        }

        val previous = all(context)

        val enriched = if (track.imageUrl.isNullOrBlank()) {
            track.copy(
                imageUrl = previous
                    .firstOrNull { it.item.uri == track.uri }
                    ?.item?.imageUrl
            )
        } else {
            track
        }

        val previousPlays = previous.firstOrNull { sameSong(it.item, track) }?.plays ?: 0
        val next = (
                listOf(PlayedTrack(enriched, playedAtMs, previousPlays + 1)) +
                        previous
                ).take(LIMIT)

        write(context, next)

        return next
    }

    /**
     * Backfills album uri/name (and a missing cover) on recorded rows using
     * decorated track metadata, persists the result and returns the updated
     * history. Rows that already know their album are left untouched.
     */
    fun applyAlbums(context: Context, rows: Map<String, TrackRow>): List<PlayedTrack> {
        if (rows.isEmpty()) return all(context)
        val current = all(context)
        val updated = current.map { played ->
            val row = rows[played.item.uri] ?: return@map played
            played.copy(
                item = played.item.copy(
                    albumUri = played.item.albumUri.ifBlank { row.albumUri },
                    albumName = played.item.albumName.ifBlank { row.album },
                    imageUrl = played.item.imageUrl ?: row.imageUrl.takeIf { it.isNotBlank() },
                )
            )
        }
        write(context, updated)
        return updated
    }

    /**
     * Two entries describe the same song when they share the track URI, or
     * when their title and artist match — re-releases and regional variants
     * of a song carry different URIs but identical metadata.
     */
    private fun sameSong(a: TrackItem, b: TrackItem): Boolean {
        if (a.uri.isNotEmpty() && a.uri == b.uri) return true
        return a.title.trim().equals(b.title.trim(), ignoreCase = true) &&
                a.artist.trim().equals(b.artist.trim(), ignoreCase = true)
    }

    /**
     * A replay belongs to the day it was replayed on: the history keeps the
     * yesterday row and adds a today row instead of moving the old one.
     */
    private fun sameDay(aMs: Long, bMs: Long): Boolean {
        if (aMs <= 0L || bMs <= 0L) return true
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = aMs
        val a = cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
        cal.timeInMillis = bMs
        val b = cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
        return a == b
    }

    private fun write(
        context: Context,
        entries: List<PlayedTrack>,
    ) {
        val array = JSONArray()

        entries.forEach { played ->
            array.put(
                JSONObject()
                    .put("uri", played.item.uri)
                    .put("title", played.item.title)
                    .put("artist", played.item.artist)
                    .put("image", played.item.imageUrl.orEmpty())
                    .put("albumUri", played.item.albumUri)
                    .put("albumName", played.item.albumName)
                    .put("artistUri", played.item.artistUri)
                    .put("explicit", played.item.isExplicit)
                    .put("durationMs", played.item.durationMs)
                    .put("at", played.playedAtMs)
                    .put("plays", played.plays)
            )
        }

        prefs(context)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }

    private fun prefs(context: Context) = AppPrefs.prefs(context, PREFS)
}