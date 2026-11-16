package com.mwask.bat.bridge

import com.mwask.bat.backend.connect.cluster.ClusterState
import com.mwask.bat.compose.playlist.TrackItem

class UriSources(
    val cluster: ClusterState?,
    val decorated: Map<String, TrackItem>,
    val queue: List<TrackItem>,
    val queueFromContext: List<TrackItem>,
)

fun artistUriOf(uri: String, sources: UriSources): String {
    if (uri.isBlank()) return ""
    sources.cluster?.let { at ->
        if (at.track.uri == uri && at.track.artistUri.isNotBlank()) return at.track.artistUri
        at.window().firstOrNull { it.uri == uri && it.artistUri.isNotBlank() }?.let { return it.artistUri }
    }
    sources.decorated[uri]?.let { if (it.artistUri.isNotBlank()) return it.artistUri }
    (sources.queue + sources.queueFromContext)
        .firstOrNull { it.uri == uri && it.artistUri.isNotBlank() }
        ?.let { return it.artistUri }
    return ""
}

fun albumUriOf(uri: String, sources: UriSources): String {
    if (uri.isBlank()) return ""
    sources.cluster?.let { at ->
        if (at.track.uri == uri && at.track.albumUri.isNotBlank()) return at.track.albumUri
        at.window().firstOrNull { it.uri == uri && it.albumUri.isNotBlank() }?.let { return it.albumUri }
    }
    sources.decorated[uri]?.let { if (it.albumUri.isNotBlank()) return it.albumUri }
    (sources.queue + sources.queueFromContext)
        .firstOrNull { it.uri == uri && it.albumUri.isNotBlank() }
        ?.let { return it.albumUri }
    return ""
}
