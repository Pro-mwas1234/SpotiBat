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

package com.mwask.bat.backend.library

import android.content.Context
import com.mwask.bat.backend.core.auth.EncoreAuth
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.net.Pathfinder
import com.mwask.bat.backend.api.Podcasts
import com.mwask.bat.backend.model.TrackRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

private val log = Logs.of(Logs.Module.TRACKS)

object TrackSource {

    private const val OP_DECORATE = "decorateContextTracks"
    private const val BATCH = 50

    suspend fun decorate(context: Context, uris: List<String>): Map<String, TrackRow> {
        val unique = uris.filter { it.startsWith("spotify:track:") }.distinct()
        val episodes = uris.filter { it.startsWith("spotify:episode:") }.distinct()
        if (unique.isEmpty() && episodes.isEmpty()) return emptyMap()
        val token = EncoreAuth.token(context)
        if (token.isBlank()) {
            log.w("decorate: no token")
            return emptyMap()
        }
        val chunks = unique.chunked(BATCH)
        val answers = coroutineScope {
            chunks.map { chunk ->
                async(Dispatchers.IO) {
                    val variables = JSONObject().put("uris", JSONArray(chunk)).toString()
                    Pathfinder.queryPost(context, token, OP_DECORATE, variables)?.let { json ->
                        json.optJSONObject("data")?.optJSONArray("tracks")?.let { chunk to it }
                    }
                }
            }.awaitAll()
        }
        val out = LinkedHashMap<String, TrackRow>()
        for ((chunk, tracks) in answers.filterNotNull()) {
            for (i in 0 until tracks.length()) {
                val node = tracks.optJSONObject(i) ?: continue
                val row = track(node, chunk.getOrNull(i).orEmpty()) ?: continue
                out[row.uri] = row
            }
        }
        if (episodes.isNotEmpty()) {
            coroutineScope {
                episodes.map { uri -> async(Dispatchers.IO) { Podcasts.episode(context, uri) } }.awaitAll()
            }.filterNotNull().forEach { detail ->
                out[detail.uri] = TrackRow(
                    uri = detail.uri,
                    title = detail.title,
                    artists = detail.showName,
                    album = "",
                    imageUrl = detail.imageUrl,
                    colorHex = detail.colorHex,
                    durationMs = detail.durationMs,
                    explicit = detail.explicit,
                    playable = detail.playable,
                    artistUri = detail.showUri,
                    video = detail.video,
                )
            }
        }
        log.i("decorate ${out.size} of ${unique.size + episodes.size} row(s) in ${chunks.size + episodes.size} batch(es)")
        return out
    }
}
