package com.mwask.bat.bridge

import com.mwask.bat.backend.connect.cluster.ClusterState
import com.mwask.bat.backend.connect.cluster.ClusterTrack

class QueueTarget(val track: ClusterTrack, val index: Int, val anchor: Int)

class QueueNavigatorState(
    val engineUri: String,
    val commandedUri: String,
)

fun playingUri(cluster: ClusterState, state: QueueNavigatorState): String {
    val engine = state.engineUri
    if (engine.isNotBlank() && cluster.window().any { it.uri == engine }) return engine
    if (state.commandedUri.isNotBlank()) return state.commandedUri
    return cluster.track.uri
}

fun targetFrom(cluster: ClusterState, offset: Int, state: QueueNavigatorState): QueueTarget? {
    val window = cluster.window()
    val anchor = cluster.anchorOf(playingUri(cluster, state))
    var seen = -1
    for (position in (anchor + 1) until window.size) {
        val row = window[position]
        if (!row.isVisible) continue
        seen++
        if (seen != offset) continue
        return QueueTarget(row, if (row.isQueued) -1 else cluster.indexOf(position), anchor)
    }
    return null
}
