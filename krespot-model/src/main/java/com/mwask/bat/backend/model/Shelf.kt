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

enum class ShelfKind { LIKED, PLAYLIST, ALBUM, ARTIST, SHOW, EPISODE, FOLDER, UNKNOWN }

data class ShelfItem(
    val uri: String,
    val title: String,
    val subtitle: String,
    val kind: ShelfKind,
    val imageUrl: String = "",
    val colorHex: String = "",
    val pinned: Boolean = false,
    val pinnable: Boolean = false,
    val count: Int = 0,
)

data class LibraryPage(
    val items: List<ShelfItem>,
    val filters: List<String>,
    val sort: String,
    val total: Int,
    val offset: Int = 0,
    val fetched: Int = 0,
) {
    val hasMore: Boolean get() = fetched > 0 && offset + fetched < total
}

data class TrackPage(
    val rows: List<TrackRow>,
    val total: Int,
    val offset: Int,
    val fetched: Int,
) {
    val hasMore: Boolean get() = fetched > 0 && offset + fetched < total
}
