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

package com.mwask.bat.backend.model

enum class SearchKind { QUERY, TRACK, ARTIST, ALBUM, PLAYLIST, EPISODE, SHOW, USER, GENRE, UNKNOWN }

data class SearchHit(
    val kind: SearchKind,
    val uri: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String = "",
    val colorHex: String = "",
    val durationMs: Long = 0L,
    val explicit: Boolean = false,
    val saved: Boolean = false,
    val video: Boolean = false,
    val albumUri: String = "",
    val artistUri: String = "",
    val albumName: String = "",
)

data class SearchSection(
    val title: String,
    val sectionType: String,
    val attributionUri: String,
    val items: List<SearchHit>,
)

data class SearchResults(
    val query: String,
    val top: SearchHit?,
    val tracks: List<SearchHit>,
    val artists: List<SearchHit>,
    val albums: List<SearchHit>,
    val playlists: List<SearchHit>,
    val others: List<SearchHit>,
    val sections: List<SearchSection>,
    val totals: Map<SearchKind, Int>,
    val chips: List<SearchKind>,
) {
    val isEmpty: Boolean
        get() = tracks.isEmpty() && artists.isEmpty() && albums.isEmpty() &&
            playlists.isEmpty() && others.isEmpty() && sections.isEmpty()

    val total: Int get() = totals.values.sum()

    val hasVideo: Boolean get() = tracks.any { it.video }

    fun of(kind: SearchKind): List<SearchHit> = when (kind) {
        SearchKind.TRACK -> tracks
        SearchKind.ARTIST -> artists
        SearchKind.ALBUM -> albums
        SearchKind.PLAYLIST -> playlists
        else -> others.filter { it.kind == kind }
    }

    fun withSaved(uri: String, saved: Boolean): SearchResults {
        fun mark(hit: SearchHit) = if (hit.uri == uri) hit.copy(saved = saved) else hit
        return copy(
            top = top?.let(::mark),
            tracks = tracks.map(::mark),
            artists = artists.map(::mark),
            albums = albums.map(::mark),
            playlists = playlists.map(::mark),
            others = others.map(::mark),
            sections = sections.map { section ->
                section.copy(items = section.items.map(::mark))
            },
        )
    }
}
