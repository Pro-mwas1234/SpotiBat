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

data class TrackRow(
    val uri: String,
    val uid: String = "",
    val title: String,
    val artists: String,
    val album: String,
    val imageUrl: String,
    val colorHex: String,
    val durationMs: Long,
    val explicit: Boolean,
    val playable: Boolean,
    val plays: Long = 0L,
    val saved: Boolean? = null,
    val albumUri: String = "",
    val artistUri: String = "",
    val discNumber: Int = 1,
    val trackNumber: Int = 0,
    val video: Boolean = false,
)

data class TrackItem(
    val title: String,
    val artist: String,
    val isExplicit: Boolean = false,
    val uri: String = "",
    val imageUrl: String? = null,
    val saved: Boolean? = null,
    val durationMs: Long = 0L,
    val albumUri: String = "",
    val artistUri: String = "",
    val albumName: String = "",
    val discNumber: Int = 1,
    val trackNumber: Int = 0,
    val hasVideo: Boolean = false,
)
