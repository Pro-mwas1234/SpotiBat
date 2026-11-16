package com.mwask.bat.widget

import android.content.Context
import com.mwask.bat.backend.core.store.AppPrefs
import com.mwask.bat.compose.shell.ComposePlaybackState
import com.mwask.bat.compose.shell.PlaybackProgress

data class WidgetSnapshot(
    val title: String = "",
    val artist: String = "",
    val coverUrl: String = "",
    val trackUri: String = "",
    val isPlaying: Boolean = false,
    val isLiked: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
) {
    val hasTrack: Boolean get() = title.isNotBlank() || trackUri.isNotBlank()

    val progress: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    internal fun fingerprint(): String =
        "$trackUri|$title|$artist|$coverUrl|$isPlaying|$isLiked|${durationMs}|${positionMs / 5000L}"

    companion object {
        fun from(state: ComposePlaybackState, progress: PlaybackProgress): WidgetSnapshot = WidgetSnapshot(
            title = state.title,
            artist = state.artist,
            coverUrl = state.coverUrl,
            trackUri = state.trackUri,
            isPlaying = state.isPlaying,
            isLiked = state.isLiked,
            positionMs = progress.positionMs,
            durationMs = progress.durationMs,
        )
    }
}

object WidgetSnapshotStore {

    private const val PREFS = "spotibat.widget"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_COVER = "coverUrl"
    private const val KEY_TRACK = "trackUri"
    private const val KEY_PLAYING = "isPlaying"
    private const val KEY_LIKED = "isLiked"
    private const val KEY_POSITION = "positionMs"
    private const val KEY_DURATION = "durationMs"

    fun save(context: Context, snapshot: WidgetSnapshot) {
        AppPrefs.edit(context, PREFS)
            .putString(KEY_TITLE, snapshot.title)
            .putString(KEY_ARTIST, snapshot.artist)
            .putString(KEY_COVER, snapshot.coverUrl)
            .putString(KEY_TRACK, snapshot.trackUri)
            .putBoolean(KEY_PLAYING, snapshot.isPlaying)
            .putBoolean(KEY_LIKED, snapshot.isLiked)
            .putLong(KEY_POSITION, snapshot.positionMs)
            .putLong(KEY_DURATION, snapshot.durationMs)
            .apply()
    }

    fun load(context: Context): WidgetSnapshot {
        val prefs = AppPrefs.prefs(context, PREFS)
        return WidgetSnapshot(
            title = prefs.getString(KEY_TITLE, "").orEmpty(),
            artist = prefs.getString(KEY_ARTIST, "").orEmpty(),
            coverUrl = prefs.getString(KEY_COVER, "").orEmpty(),
            trackUri = prefs.getString(KEY_TRACK, "").orEmpty(),
            isPlaying = prefs.getBoolean(KEY_PLAYING, false),
            isLiked = prefs.getBoolean(KEY_LIKED, false),
            positionMs = prefs.getLong(KEY_POSITION, 0L),
            durationMs = prefs.getLong(KEY_DURATION, 0L),
        )
    }
}
