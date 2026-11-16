/*
 *  _   __                          _   
 * | | / /                         | |  
 * | |/ / _ __ ___  ___ _ __   ___ | |_ 
 * |    \| '__/ _ \/ __| '_ \ / _ \| __|
 * | |\  \ | |  __/\__ \ |_) | (_) | |_ 
 * \_| \_/_|  \___||___/ .__/ \___/ \__|
 *                     | |              
 *                     |_|              
 *
 * Copyright (c) 2026 23fpsz (SpotiBat). All Rights Reserved.
 * Original repository: https://github.com/Pro-mwas1234/SpotiBat
 *
 * PROPRIETARY / SOURCE-AVAILABLE MODULE:
 * You may view and audit this source code for personal or educational inspection.
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.mwask.bat.backend.connect.command

import java.util.concurrent.ConcurrentHashMap
import com.mwask.bat.backend.connect.cluster.ClusterState

object TrackUid {

    private const val LIMIT = 512

    private val known = ConcurrentHashMap<String, String>()

    fun get(uri: String): String = known[uri].orEmpty()

    fun remember(uri: String, uid: String) {
        if (uri.isBlank() || uid.isBlank()) return
        if (known.size >= LIMIT) known.clear()
        known[uri] = uid
    }

    fun learn(cluster: ClusterState?) {
        if (cluster == null) return
        learn(listOf(cluster.track) + cluster.nextTracks + cluster.prevTracks)
    }

    fun learn(command: PlaybackCommand?) {
        if (command == null) return
        command.states.forEach { state ->
            val uri = command.tracks.getOrNull(state.trackIndex)?.uri.orEmpty()
            remember(uri, state.trackUid)
        }
    }

    fun learn(tracks: List<com.mwask.bat.backend.connect.cluster.ClusterTrack>) {
        tracks.forEach { remember(it.uri, it.uid) }
    }

    fun forget() {
        known.clear()
    }
}
