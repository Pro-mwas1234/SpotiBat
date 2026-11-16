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

import com.mwask.bat.backend.core.net.GraphQl
import com.mwask.bat.backend.core.net.plainText
import com.mwask.bat.backend.model.MerchItem
import com.mwask.bat.backend.model.ShelfItem
import com.mwask.bat.backend.model.ShelfKind
import com.mwask.bat.backend.model.TrackRow
import org.json.JSONObject
import com.mwask.bat.backend.core.net.plainText

private val MONTHS = arrayOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

internal fun moreAlbums(album: JSONObject): List<ShelfItem> {
    val out = ArrayList<ShelfItem>()
    val items = album.optJSONObject("moreAlbumsByArtist")?.optJSONArray("items") ?: return out
    for (i in 0 until items.length()) {
        val popular = items.optJSONObject(i)
            ?.optJSONObject("discography")
            ?.optJSONObject("popularReleasesAlbums")
            ?.optJSONArray("items")
            ?: continue
        for (j in 0 until popular.length()) {
            release(popular.optJSONObject(j))?.let(out::add)
        }
    }
    return out.distinctBy { it.uri }
}

internal fun releases(discography: JSONObject?): List<ShelfItem> {
    val out = ArrayList<ShelfItem>()
    val popular = discography?.optJSONObject("popularReleasesAlbums")?.optJSONArray("items")
    for (i in 0 until (popular?.length() ?: 0)) {
        release(popular?.optJSONObject(i))?.let(out::add)
    }
    if (out.isEmpty()) {
        out += releaseGroup(discography?.optJSONObject("albums"), limit = 20)
        out += releaseGroup(discography?.optJSONObject("singles"), limit = 20)
        out += releaseGroup(discography?.optJSONObject("compilations"), limit = 20)
    }
    return out.distinctBy { it.uri }
}

internal fun releaseGroup(node: JSONObject?, limit: Int = 10): List<ShelfItem> {
    val items = node?.optJSONArray("items") ?: return emptyList()
    val out = ArrayList<ShelfItem>(limit)
    for (i in 0 until items.length()) {
        if (out.size >= limit) break
        val nested = items.optJSONObject(i)
            ?.optJSONObject("releases")
            ?.optJSONArray("items")
            ?: continue
        release(nested.optJSONObject(0))?.let(out::add)
    }
    return out
}

internal fun dataShelf(node: JSONObject?, limit: Int = 10): List<ShelfItem> {
    val items = node?.optJSONArray("items") ?: return emptyList()
    val out = ArrayList<ShelfItem>(limit)
    for (i in 0 until items.length()) {
        if (out.size >= limit) break
        val data = items.optJSONObject(i)?.optJSONObject("data") ?: continue
        val uri = data.optString("uri")
        if (!uri.startsWith("spotify:")) continue
        val type = data.optString("__typename")
        val kind = when (type) {
            "Album" -> ShelfKind.ALBUM
            "Artist" -> ShelfKind.ARTIST
            "Show", "Podcast" -> ShelfKind.SHOW
            else -> ShelfKind.PLAYLIST
        }
        val owner = data.optJSONObject("ownerV2")
            ?.optJSONObject("data")
            ?.optString("name")
            .orEmpty()
            .plainText()
        out += ShelfItem(
            uri = uri,
            title = data.optString("name").plainText(),
            subtitle = when {
                owner.isNotBlank() -> "$type • $owner"
                type.isNotBlank() -> type
                else -> "Playlist"
            },
            kind = kind,
            imageUrl = GraphQl.entityImage(data),
            colorHex = GraphQl.entityColor(data),
        )
    }
    return out
}

internal fun artistNodes(node: JSONObject?, limit: Int = 10): List<ShelfItem> {
    val items = node?.optJSONArray("items") ?: return emptyList()
    val out = ArrayList<ShelfItem>(limit)
    for (i in 0 until items.length()) {
        if (out.size >= limit) break
        val item = items.optJSONObject(i) ?: continue
        val uri = item.optString("uri")
        if (!uri.startsWith("spotify:artist:")) continue
        out += ShelfItem(
            uri = uri,
            title = item.optJSONObject("profile")?.optString("name").orEmpty().plainText(),
            subtitle = "Artist",
            kind = ShelfKind.ARTIST,
            imageUrl = GraphQl.entityImage(item),
            colorHex = GraphQl.entityColor(item),
        )
    }
    return out
}

internal fun videoShelf(node: JSONObject?, limit: Int = 10): List<TrackRow> {
    val items = node?.optJSONArray("items") ?: return emptyList()
    val out = ArrayList<TrackRow>(limit)
    for (i in 0 until items.length()) {
        if (out.size >= limit) break
        track(items.optJSONObject(i)?.optJSONObject("data"), "")?.let(out::add)
    }
    return out
}

internal fun pinned(node: JSONObject?): ShelfItem? {
    if (node == null) return null
    val uri = node.optString("uri")
    if (!uri.startsWith("spotify:")) return null
    val kind = when (node.optString("type")) {
        "ALBUM" -> ShelfKind.ALBUM
        "ARTIST" -> ShelfKind.ARTIST
        "PLAYLIST" -> ShelfKind.PLAYLIST
        else -> ShelfKind.UNKNOWN
    }
    return ShelfItem(
        uri = uri,
        title = node.optString("title").plainText(),
        subtitle = node.optString("subtitle").plainText(),
        kind = kind,
        imageUrl = GraphQl.largestSource(
            node.optJSONObject("thumbnailImage")?.optJSONObject("data")
                ?: node.optJSONObject("itemV2")?.optJSONObject("data"),
        ),
        pinned = true,
    )
}

internal fun release(album: JSONObject?): ShelfItem? {
    if (album == null) return null
    val uri = album.optString("uri")
    if (uri.isBlank()) return null
    val year = album.optJSONObject("date")?.let { date ->
        date.optInt("year").takeIf { it > 0 }?.toString()
            ?: date.optString("isoString").plainText().take(4)
    }.orEmpty()
    val type = album.optString("type").plainText().lowercase().replaceFirstChar { it.uppercase() }
    return ShelfItem(
        uri = uri,
        title = album.optString("name").plainText(),
        subtitle = listOfNotNull(year.takeIf { it.isNotBlank() }, type.takeIf { it.isNotBlank() })
            .joinToString(" • "),
        kind = ShelfKind.ALBUM,
        imageUrl = GraphQl.entityImage(album),
        colorHex = GraphQl.entityColor(album),
    )
}

internal fun merch(node: JSONObject?, limit: Int = 10): List<MerchItem> {
    val items = node?.optJSONArray("items") ?: return emptyList()
    val out = ArrayList<MerchItem>(limit)
    for (i in 0 until items.length()) {
        if (out.size >= limit) break
        val item = items.optJSONObject(i) ?: continue
        val name = item.optString("nameV2").plainText()
            .ifBlank { item.optString("name").plainText() }
        val url = item.optString("url").plainText()
        if (name.isBlank() || !url.startsWith("http")) continue
        out += MerchItem(
            name = name,
            price = item.optString("price").plainText(),
            imageUrl = GraphQl.largestSource(item.optJSONObject("image")),
            url = url,
        )
    }
    return out
}

internal fun compact(value: Long): String = when {
    value >= 1_000_000 -> "${value / 1_000_000}M"
    value >= 1_000 -> "${value / 1_000}K"
    else -> value.toString()
}

internal fun formatDate(iso: String, precision: String): String {
    if (iso.length < 10) return iso
    val year = iso.substring(0, 4)
    val month = iso.substring(5, 7).toIntOrNull()?.takeIf { it in 1..12 } ?: return year
    val day = iso.substring(8, 10).toIntOrNull()
    return when (precision) {
        "YEAR" -> year
        "MONTH" -> "${MONTHS[month - 1]} $year"
        else -> if (day == null) "${MONTHS[month - 1]} $year" else "${MONTHS[month - 1]} $day, $year"
    }
}
