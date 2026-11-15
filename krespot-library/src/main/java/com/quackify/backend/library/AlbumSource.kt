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
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.net.GraphQl
import com.quackify.backend.core.net.Pathfinder
import com.quackify.backend.core.net.plainText
import com.quackify.backend.model.AlbumDetail
import com.quackify.backend.model.CreditGroup
import com.quackify.backend.model.CreditPerson
import com.quackify.backend.model.Credits
import com.quackify.backend.model.MerchItem
import com.quackify.backend.model.TrackPage
import com.quackify.backend.model.TrackRow
import org.json.JSONObject
import com.quackify.backend.core.net.plainText

private val log = Logs.of(Logs.Module.ALBUM)

object AlbumSource {

    private const val OP_ALBUM = "getAlbum"
    private const val OP_ALBUM_TRACKS = "queryAlbumTracks"
    private const val TRACKS_LIMIT = 300
    private const val OP_ALBUM_MERCH = "queryAlbumMerch"
    private const val OP_CREDITS = "queryTrackCreditsGroupedModal"

    suspend fun album(context: Context, uri: String): AlbumDetail? {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) return null
        val album = albumPage(context, token, uri, 0, PAGE) ?: return null
        val coverUrl = GraphQl.entityImage(album)
        val tracks = album.optJSONObject("tracksV2")
        val rows = albumRows(tracks, coverUrl)
        val iso = album.optJSONObject("date")?.optString("isoString").orEmpty().plainText()
        val precision = album.optJSONObject("date")?.optString("precision").orEmpty().plainText()
        val copyrights = album.optJSONObject("copyright")?.optJSONArray("items")
        var credit = ""
        var phonographic = ""
        for (i in 0 until (copyrights?.length() ?: 0)) {
            val item = copyrights?.optJSONObject(i) ?: continue
            val text = item.optString("text").plainText()
            when (item.optString("type")) {
                "C" -> credit = "© $text"
                "P" -> phonographic = "℗ $text"
            }
        }
        val artistNode = album.optJSONObject("artists")?.optJSONArray("items")?.optJSONObject(0)
        val detail = AlbumDetail(
            uri = uri,
            title = album.optString("name").plainText(),
            artists = GraphQl.artistNames(album),
            artistUri = artistNode?.optString("uri").orEmpty(),
            artistImageUrl = GraphQl.entityImage(artistNode?.optJSONObject("visuals") ?: artistNode),
            year = iso.take(4),
            releaseDate = formatDate(iso, precision),
            imageUrl = coverUrl,
            colorHex = GraphQl.visualColor(album).ifBlank { GraphQl.entityColor(album) },
            tracks = rows,
            total = tracks?.optInt("totalCount", rows.size) ?: rows.size,
            moreByArtist = moreAlbums(album),
            offset = 0,
            fetched = tracks?.optJSONArray("items")?.length() ?: 0,
            label = album.optString("label").plainText(),
            copyright = credit,
            phonographic = phonographic,
            courtesyLine = album.optString("courtesyLine").plainText(),
            type = album.optString("type").ifBlank { "ALBUM" },
            saved = album.optBoolean("saved", false),
            discCount = album.optJSONObject("discs")?.optInt("totalCount", 1) ?: 1,
            merch = albumMerch(context, token, uri),
        )
        log.i("album $uri '${detail.title}' -> ${rows.size}/${detail.total} more=${detail.moreByArtist.size} " +
                "label='${detail.label}' type=${detail.type} saved=${detail.saved} discs=${detail.discCount} " +
                "merch=${detail.merch.size}"
        )
        return detail
    }

    suspend fun albumMore(context: Context, uri: String, offset: Int, coverUrl: String = ""): TrackPage? {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) return null
        val variables = JSONObject()
            .put("uri", uri)
            .put("offset", offset)
            .put("limit", TRACKS_LIMIT)
            .toString()
        val tracks = Pathfinder.queryPost(context, token, OP_ALBUM_TRACKS, variables)
            ?.optJSONObject("data")
            ?.optJSONObject("albumUnion")
            ?.optJSONObject("tracksV2")
            ?: return null
        val rows = albumRows(tracks, coverUrl)
        return TrackPage(
            rows = rows,
            total = tracks.optInt("totalCount", offset + rows.size),
            offset = offset,
            fetched = rows.size,
        )
    }

    suspend fun credits(context: Context, trackUri: String): Credits? {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) return null
        val variables = JSONObject()
            .put("trackUri", trackUri)
            .put("contributorsLimit", 100)
            .put("contributorsOffset", 0)
            .toString()
        val union = Pathfinder.queryPost(context, token, OP_CREDITS, variables)
            ?.optJSONObject("data")
            ?.optJSONObject("trackUnion")
            ?: return null
        val trait = union.optJSONObject("creditsTrait")
        val items = trait?.optJSONObject("contributors")?.optJSONArray("items")
        val groups = LinkedHashMap<String, MutableList<CreditPerson>>()
        for (i in 0 until (items?.length() ?: 0)) {
            val item = items?.optJSONObject(i) ?: continue
            val name = item.optString("name").plainText()
            if (name.isBlank()) continue
            val group = item.optJSONObject("roleGroup")?.optString("name").orEmpty().plainText()
                .ifBlank { "Other" }
            groups.getOrPut(group) { ArrayList() } += CreditPerson(
                name = name,
                role = item.optString("role").plainText(),
                uri = item.optString("uri").orEmpty(),
            )
        }
        val sources = ArrayList<String>()
        val sourceItems = trait?.optJSONObject("sources")?.optJSONArray("items")
        for (i in 0 until (sourceItems?.length() ?: 0)) {
            val name = sourceItems?.optJSONObject(i)?.optString("name").orEmpty().plainText()
            if (name.isNotBlank()) sources += name
        }
        val credits = Credits(
            trackName = union.optString("name").plainText(),
            groups = groups.map { CreditGroup(it.key, it.value) },
            sources = sources,
        )
        log.i("credits $trackUri '${credits.trackName}' -> ${credits.groups.size} groups, " +
                "${credits.contributors} contributors, ${credits.sources.size} sources"
        )
        return credits
    }

    private suspend fun albumPage(
        context: Context,
        token: String,
        uri: String,
        offset: Int,
        limit: Int,
    ): JSONObject? {
        val variables = JSONObject()
            .put("uri", uri)
            .put("locale", "")
            .put("offset", offset)
            .put("limit", limit)
            .toString()
        return Pathfinder.queryPost(context, token, OP_ALBUM, variables)
            ?.optJSONObject("data")
            ?.optJSONObject("albumUnion")
    }

    private fun albumRows(tracks: JSONObject?, coverUrl: String): List<TrackRow> {
        val items = tracks?.optJSONArray("items")
        val rows = ArrayList<TrackRow>(items?.length() ?: 0)
        for (i in 0 until (items?.length() ?: 0)) {
            track(items?.optJSONObject(i)?.optJSONObject("track"), "", fallbackImageUrl = coverUrl)?.let(rows::add)
        }
        return rows
    }

    private suspend fun albumMerch(context: Context, token: String, uri: String): List<MerchItem> {
        val variables = JSONObject().put("uri", uri).toString()
        val union = Pathfinder.queryPost(context, token, OP_ALBUM_MERCH, variables)
            ?.optJSONObject("data")
            ?.optJSONObject("albumUnion")
            ?: return emptyList()
        return merch(union.optJSONObject("merch"))
    }
}
