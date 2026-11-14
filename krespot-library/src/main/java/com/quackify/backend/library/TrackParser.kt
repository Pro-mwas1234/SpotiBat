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
 * Copyright (c) 2026 23fpsz (Quackify). All Rights Reserved.
 * Original repository: https://github.com/23fpsz/Quackify
 *
 * PROPRIETARY / SOURCE-AVAILABLE MODULE:
 * You may view and audit this source code for personal or educational inspection.
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.quackify.backend.library

import android.content.Context
import com.quackify.backend.api.Library
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.GraphQl
import com.quackify.backend.core.net.plainText
import com.quackify.backend.model.TrackRow
import org.json.JSONObject
import com.quackify.backend.core.net.plainText

private val log = Logs.of(Logs.Module.TRACKS)

internal fun track(
    data: JSONObject?,
    fallbackUri: String,
    savedHint: Boolean? = null,
    fallbackImageUrl: String = ""
): TrackRow? {
    if (data == null) return null
    val title = data.optString("name").plainText()
    if (title.isBlank()) return null
    val albumNode = data.optJSONObject("albumOfTrack")
    val art = albumNode ?: data
    val duration = data.optJSONObject("duration")?.optLong("totalMilliseconds", 0L)
        ?.takeIf { it > 0L }
        ?: data.optJSONObject("trackDuration")?.optLong("totalMilliseconds", 0L)
        ?: 0L
    return TrackRow(
        uri = data.optString("uri").ifBlank { fallbackUri },
        title = title,
        artists = GraphQl.artistNames(data).plainText().ifBlank {
            data.optJSONObject("podcastV2")?.optJSONObject("data")
                ?.optString("name").orEmpty().plainText()
        },
        album = albumNode?.optString("name").orEmpty().plainText(),
        imageUrl = GraphQl.entityImage(art).ifBlank { fallbackImageUrl },
        colorHex = GraphQl.entityColor(art),
        durationMs = duration,
        explicit = data.optJSONObject("contentRating")?.optString("label") == "EXPLICIT",
        playable = data.optJSONObject("playability")?.optBoolean("playable", true) ?: true,
        plays = data.optString("playcount").toLongOrNull() ?: data.optLong("playcount", 0L).takeIf { it > 0L } ?: 0L,
        saved = if (data.has("saved")) data.optBoolean("saved", false) else savedHint,
        albumUri = albumNode?.optString("uri").orEmpty(),
        artistUri = data.optJSONObject("artists")?.optJSONArray("items")
            ?.optJSONObject(0)?.optString("uri").orEmpty(),
        discNumber = data.optInt("discNumber", 1),
        trackNumber = data.optInt("trackNumber", 0),
        video = data.hasVideo(),
    )
}

internal suspend fun withSaved(context: Context, rows: List<TrackRow>): List<TrackRow> {
    val unknown = rows.filter { it.saved == null }.map { it.uri }.filter { it.isNotBlank() }
    if (unknown.isEmpty()) return rows
    val saved = Library.saved(context, unknown)
    log.i("withSaved ${saved.size}/${unknown.size}")
    return rows.map { row -> if (row.saved == null) row.copy(saved = row.uri in saved) else row }
}

private fun JSONObject.hasVideo(): Boolean =
    optString("mediaType") == "VIDEO" ||
        (optJSONObject("associationsV3")
            ?.optJSONObject("videoAssociations")
            ?.optInt("totalCount", 0) ?: 0) > 0
