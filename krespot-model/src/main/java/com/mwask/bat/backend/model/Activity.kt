/*
 *  _   __                          _   
 * | | / /                         | |  
 * | |/ / _ __ ___  ___ _ __   ___ | |_ 
 * |    \| '__/ _ \/ __/ '_ \ / _ \| __|
 * |\  \ | |  __/\__ \ |_) | (_) | |_ 
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

/** One entry of the gander notification feed (saved playlist, new collaborator, new follower...). */
data class ActivityNotification(
    val id: String,
    val title: String,
    val createdTimestamp: String,
    val actionUri: String,
    val actionType: String,
    val actorNames: List<String> = emptyList(),
    val entityImageUrl: String = "",
    val entityUri: String = "",
    val isNew: Boolean = false,
) {
    val primaryActor: String get() = actorNames.firstOrNull().orEmpty()
    val atMillis: Long get() = runCatching {
        java.time.Instant.parse(createdTimestamp).toEpochMilli()
    }.getOrDefault(0L)
}

/** The parsed answer of gander/v2/GetNotifications. */
data class ActivityFeed(
    val items: List<ActivityNotification> = emptyList(),
    val latestCursor: String = "",
)

/** The current user as described by the profileAttributes persisted query. */
data class SelfProfile(
    val accountId: String = "",
    val name: String = "",
    val username: String = "",
    val uri: String = "",
    val avatarUrl: String = "",
    val avatarBackgroundColor: Int = 0,
    val socialHandle: String = "",
)
