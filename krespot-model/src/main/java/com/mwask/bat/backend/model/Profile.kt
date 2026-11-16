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
 * You may view and audit this source code for personal and educational inspection.
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.mwask.bat.backend.model

data class UserProfile(
    val uri: String,
    val name: String,
    val followersCount: Long,
    val followingCount: Long,
    val recentlyPlayedArtists: List<ProfileArtist>,
    val colorHex: String,
    val isCurrentUser: Boolean,
    val avatarUrl: String = "",
    val publicPlaylists: List<ProfilePlaylist> = emptyList(),
)

data class ProfilePlaylist(
    val uri: String,
    val name: String,
    val imageUrl: String = "",
)

data class ProfileArtist(
    val uri: String,
    val name: String,
    val imageUrl: String,
    val followersCount: Long = 0L,
    val isFollowing: Boolean = false,
)

data class ProfileUser(
    val uri: String,
    val name: String,
    val imageUrl: String,
    val followersCount: Long = 0L,
    val colorHex: String = "",
) {
    val isArtist: Boolean get() = uri.startsWith("spotify:artist:")
}

data class ProfileTopContent(
    val topArtists: List<ProfileArtist>,
    val topTracks: List<ProfileTopTrack>,
)

data class ProfileTopTrack(
    val uri: String,
    val title: String,
    val artists: String,
    val artistUri: String,
    val albumUri: String,
    val albumName: String,
    val imageUrl: String,
    val durationMs: Long,
    val explicit: Boolean,
)

data class ProfileContext(
    val uri: String,
    val lastPlayedTimeMs: Long,
)
