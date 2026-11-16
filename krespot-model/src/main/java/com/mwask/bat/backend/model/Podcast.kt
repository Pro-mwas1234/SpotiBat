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

data class EpisodeRow(
    val uri: String,
    val title: String,
    val description: String,
    val releaseDate: String,
    val durationMs: Long,
    val imageUrl: String,
    val colorHex: String,
    val explicit: Boolean,
    val playable: Boolean,
    val video: Boolean,
    val played: Boolean,
    val positionMs: Long,
    val showName: String = "",
    val showUri: String = "",
)

data class ChapterSpan(
    val title: String,
    val startMs: Long,
    val endMs: Long,
)

data class ShowCard(
    val uri: String,
    val name: String,
    val publisher: String,
    val imageUrl: String,
)

data class ShowDetail(
    val uri: String,
    val name: String,
    val publisher: String,
    val description: String,
    val imageUrl: String,
    val colorHex: String,
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val saved: Boolean = false,
    val mediaType: String = "AUDIO",
    val topics: List<String> = emptyList(),
    val episodes: List<EpisodeRow> = emptyList(),
    val total: Int = 0,
    val offset: Int = 0,
    val fetched: Int = 0,
    val nextOffset: Int? = null,
    val related: List<ShowCard> = emptyList(),
) {
    val hasMore: Boolean get() = nextOffset != null
}

data class EpisodePage(
    val rows: List<EpisodeRow>,
    val total: Int,
    val offset: Int,
    val fetched: Int,
    val nextOffset: Int? = null,
)

data class EpisodeDetail(
    val uri: String,
    val title: String,
    val description: String,
    val showName: String,
    val showUri: String,
    val imageUrl: String,
    val wideImageUrl: String,
    val colorHex: String,
    val durationMs: Long,
    val releaseDate: String,
    val explicit: Boolean,
    val playable: Boolean,
    val video: Boolean,
    val played: Boolean,
    val positionMs: Long,
    val shareUrl: String,
)
