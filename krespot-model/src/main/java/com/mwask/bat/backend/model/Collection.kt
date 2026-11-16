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

data class PlaylistMember(
    val userId: String,
    val uri: String,
    val name: String,
    val avatarUrl: String = "",
    val isOwner: Boolean = false,
    val permissionLevel: String = "",
) {
    val isContributor: Boolean get() = !isOwner && permissionLevel == PERMISSION_CONTRIBUTOR

    companion object {
        const val PERMISSION_CONTRIBUTOR = "CONTRIBUTOR"
        const val PERMISSION_VIEWER = "VIEWER"
        const val PERMISSION_BLOCKED = "BLOCKED"
    }
}

data class PlaylistPermissions(
    val uri: String,
    val members: List<PlaylistMember> = emptyList(),
    val canAdministratePermissions: Boolean = false,
    val canEditItems: Boolean = false,
    val canEditMetadata: Boolean = false,
    val grantableLevels: List<String> = emptyList(),
)

data class CollectionDetail(
    val uri: String,
    val title: String,
    val creator: String,
    val creatorAvatarUrl: String = "",
    val saveCount: String,
    val description: String,
    val imageUrl: String,
    val colorHex: String,
    val tracks: List<TrackRow>,
    val total: Int,
    val offset: Int = 0,
    val fetched: Int = 0,
    val editable: Boolean = false,
    val members: List<PlaylistMember> = emptyList(),
) {
    val hasMore: Boolean get() = fetched > 0 && offset + fetched < total
    val complete: Boolean get() = fetched > 0 && offset + fetched >= total
    val durationMs: Long get() = collectionDuration(tracks.map { it.durationMs }, total)
    val durationIsEstimate: Boolean get() = collectionDurationIsEstimate(tracks.map { it.durationMs }, total)
}

fun collectionDuration(durations: List<Long>, total: Int): Long {
    val counted = durations.filter { it > 0L }
    if (counted.isEmpty()) return 0L
    val sum = counted.sum()
    if (total <= 0 || total <= counted.size) return sum
    return sum / counted.size * total
}

fun collectionDurationIsEstimate(durations: List<Long>, total: Int): Boolean {
    val counted = durations.count { it > 0L }
    return counted > 0 && total > counted
}

data class PlaylistChoice(
    val uri: String,
    val name: String,
    val imageUrl: String = "",
    val kind: Kind = Kind.PLAYLIST,
    val containsTrack: Boolean = false,
) {
    enum class Kind { PLAYLIST, LIKED_SONGS, FOLDER }

    companion object {
        const val LIKED_SONGS_URI = "spotify:collection:tracks"
    }
}
