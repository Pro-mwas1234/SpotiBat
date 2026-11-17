package com.mwask.bat.compose.shell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.mwask.bat.compose.library.LibraryItem
import com.mwask.bat.compose.library.LibraryItemType
import com.mwask.bat.compose.playlist.TrackItem
import com.mwask.bat.compose.sheets.EntityOptions
import androidx.compose.ui.res.stringResource
import com.mwask.bat.compose.R

internal class ShellActions(
    private val flags: ShellUiFlags,
    private val local: ShellLocalState,
    private val navController: ShellNavController,
    private val playerListener: ComposePlayerListener?,
    private val playlistTracks: List<TrackItem>?,
    private val albumTracks: List<TrackItem>?,
    private val artistTracks: List<com.mwask.bat.compose.screens.PopularTrack>?,
    private val searchQuery: () -> String,
    private val clipboard: ClipboardManager,
    private val onDownloadTrack: ((String) -> Unit)? = null,
    private val onDownloadRequest: ((String, String, String, Long) -> Unit)? = null,
    private val onRemoveDownload: ((String) -> Unit)? = null,
) {
    private val active get() = navController.active

    /** Keeps a song on the device; the host resolves it and queues the download. */
    fun downloadTrack(uri: String) {
        if (uri.isBlank()) return
        onDownloadTrack?.invoke(uri)
    }

    /**
     * Queues a row the list already knows. The name travels with the request because a playlist
     * row is usually outside the playing window, and a download with no title cannot be looked
     * up at all.
     */
    fun downloadTrack(track: TrackItem) {
        if (track.uri.isBlank()) return
        val request = onDownloadRequest
        if (request != null && track.title.isNotBlank()) {
            request(track.uri, track.title, track.artist, track.durationMs)
        } else {
            onDownloadTrack?.invoke(track.uri)
        }
    }

    fun downloadTracks(tracks: List<TrackItem>) {
        tracks.filter { it.uri.isNotBlank() }.distinctBy { it.uri }.forEach { downloadTrack(it) }
    }

    fun removeDownload(uri: String) {
        if (uri.isBlank()) return
        onRemoveDownload?.invoke(uri)
    }

    fun onTrackPlay(track: TrackItem) {
        local.playbackOrigin = when (navController.currentScreen) {
            ShellScreen.SEARCH -> PlaybackOrigin(ShellScreen.SEARCH, searchQuery().trim())
            ShellScreen.PLAYLIST -> PlaybackOrigin(ShellScreen.PLAYLIST, active.playlistTitle)
            ShellScreen.ALBUM -> PlaybackOrigin(ShellScreen.ALBUM, active.albumTitle)
            ShellScreen.ARTIST -> PlaybackOrigin(ShellScreen.ARTIST, active.artistName)
            else -> null
        }
        if (playerListener != null) {
            if (track.uri.isNotEmpty()) {
                val rows = when (navController.currentScreen) {
                    ShellScreen.ARTIST -> artistTracks.orEmpty().map { it.track.uri }
                    ShellScreen.PLAYLIST -> playlistTracks.orEmpty().map { it.uri }
                    ShellScreen.ALBUM -> albumTracks.orEmpty().map { it.uri }
                    else -> emptyList()
                }
                val contextUri = when (navController.currentScreen) {
                    ShellScreen.ARTIST -> active.artistUri
                    ShellScreen.PLAYLIST -> active.playlistUri
                    ShellScreen.ALBUM -> active.albumUri
                    else -> ""
                }
                val index = rows.indexOfFirst { it == track.uri }
                if (contextUri.isNotBlank() && index >= 0) {
                    playerListener.onPlayTrack(track.uri, contextUri, index)
                } else {
                    playerListener.onPlayUri(track.uri)
                }
            } else {
                playerListener.onTrackSelect(track.title, track.artist)
            }
        } else {
            localPlay(track)
        }
    }

    fun localPlay(track: TrackItem) {
        local.trackTitle = track.title
        local.artist = track.artist
        local.isPlaying = true
        local.progress = 0.05f
        local.positionMs = 12000L
    }

    fun playUriShortcut(uri: String, title: String) {
        local.playbackOrigin = PlaybackOrigin(ShellScreen.HOME, "Recently played")
        if (playerListener != null) {
            playerListener.onPlayUri(uri)
        } else {
            local.trackTitle = title
            local.isPlaying = true
            local.progress = 0.05f
        }
    }

    fun playPause() {
        if (playerListener != null) playerListener.onPlayPause() else local.isPlaying = !local.isPlaying
    }

    fun skipNext() {
        playerListener?.onSkipNext()
    }

    fun skipPrevious() {
        playerListener?.onSkipPrevious()
    }

    fun openTrackOptions(track: TrackItem) {
        local.trackForOptions = track
        flags.isTrackOptionsFromPlayer = false
        flags.isTrackOptionsOpen = true
    }

    fun libraryLongPress(item: LibraryItem) {
        if (item.type == LibraryItemType.FOLDER) {
            local.activeFolderOptionsNested = navController.currentScreen == ShellScreen.FOLDER
            local.activeFolderOptionsItem = item
        } else {
            local.activeEntityInsideFolder = navController.currentScreen == ShellScreen.FOLDER
            local.libraryItemOptions = EntityOptions(
                title = item.title,
                subtitle = item.subtitle,
                imageUrl = item.imageUrl,
                uri = item.uri,
                pinned = item.isPinned,
                pinnable = item.isPinnable,
            )
        }
    }

    fun copyEntityLink(uri: String) {
        val url = entityUrl(uri) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Track link", url))
    }

    fun shareEntity(context: Context, uri: String) {
        val url = entityUrl(uri) ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(send, null))
    }
}
