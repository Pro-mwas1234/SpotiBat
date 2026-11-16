package com.mwask.bat.compose.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mwask.bat.compose.components.RepeatMode
import com.mwask.bat.compose.components.ShuffleMode
import com.mwask.bat.compose.playlist.TrackItem

@Stable
class PlayerState {

    var uri by mutableStateOf("")
    var title by mutableStateOf("")
    var artists by mutableStateOf("")
    var artworkUrl by mutableStateOf("")
    var showName by mutableStateOf("")
    var durationMs by mutableLongStateOf(0L)
    var positionMs by mutableLongStateOf(0L)
    var isPlaying by mutableStateOf(false)
    var isVideo by mutableStateOf(false)
    var isLiked by mutableStateOf(false)
    var isExplicit by mutableStateOf(false)
    var shuffleMode by mutableStateOf(ShuffleMode.OFF)
    var repeatMode by mutableStateOf(RepeatMode.OFF)
    var device by mutableStateOf("")
    var contextUri by mutableStateOf("")
    var controllingRemote by mutableStateOf(false)
    var canSeek by mutableStateOf(true)
    var canSkipPrevious by mutableStateOf(true)
    var canShuffle by mutableStateOf(true)
    var canRepeat by mutableStateOf(true)

    val progress: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val hasTrack: Boolean get() = title.isNotBlank()

    fun play(item: TrackItem, context: String = "") {
        play(
            uri = item.uri,
            title = item.title,
            subtitle = item.artist,
            artworkUrl = item.imageUrl.orEmpty(),
            durationMs = item.durationMs,
            isVideo = item.hasVideo,
            isLiked = item.saved == true,
            isExplicit = item.isExplicit,
            context = context,
        )
    }

    fun play(
        uri: String,
        title: String,
        subtitle: String,
        artworkUrl: String,
        durationMs: Long,
        isVideo: Boolean = false,
        isLiked: Boolean = false,
        isExplicit: Boolean = false,
        positionMs: Long = 0L,
        showName: String = "",
        context: String = "",
    ) {
        this.uri = uri
        this.title = title
        this.artists = subtitle
        this.artworkUrl = artworkUrl
        this.durationMs = durationMs
        this.isVideo = isVideo
        this.isLiked = isLiked
        this.isExplicit = isExplicit
        this.showName = showName
        this.positionMs = positionMs.coerceIn(0L, durationMs.takeIf { it > 0L } ?: positionMs)
        this.isPlaying = true
        if (context.isNotBlank()) contextUri = context
    }

    fun toggle() {
        if (hasTrack) isPlaying = !isPlaying
    }

    fun seekTo(ms: Long) {
        positionMs = ms.coerceIn(0L, durationMs.takeIf { it > 0L } ?: ms)
    }

    fun seekToProgress(fraction: Float) {
        seekTo((fraction.coerceIn(0f, 1f) * durationMs).toLong())
    }

    fun like(value: Boolean) {
        isLiked = value
    }

    fun clear() {
        uri = ""
        title = ""
        artists = ""
        artworkUrl = ""
        showName = ""
        durationMs = 0L
        positionMs = 0L
        isPlaying = false
        isVideo = false
        isLiked = false
        isExplicit = false
        contextUri = ""
    }
}
