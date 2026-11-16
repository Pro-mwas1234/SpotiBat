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

data class AlbumDetail(
    val uri: String,
    val title: String,
    val artists: String,
    val artistUri: String = "",
    val artistImageUrl: String = "",
    val year: String,
    val releaseDate: String,
    val imageUrl: String,
    val colorHex: String,
    val tracks: List<TrackRow>,
    val total: Int,
    val moreByArtist: List<ShelfItem>,
    val offset: Int = 0,
    val fetched: Int = 0,
    val label: String = "",
    val copyright: String = "",
    val phonographic: String = "",
    val courtesyLine: String = "",
    val type: String = "ALBUM",
    val saved: Boolean = false,
    val discCount: Int = 1,
    val merch: List<MerchItem> = emptyList(),
) {
    val hasMore: Boolean get() = fetched > 0 && offset + fetched < total
    val complete: Boolean get() = fetched > 0 && offset + fetched >= total
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

data class ReleasePage(
    val rows: List<ShelfItem>,
    val total: Int,
    val offset: Int,
    val fetched: Int,
) {
    val hasMore: Boolean get() = fetched > 0 && offset + fetched < total
}

data class MerchItem(
    val name: String,
    val price: String,
    val imageUrl: String,
    val url: String,
)
