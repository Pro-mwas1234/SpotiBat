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

data class AccountAttributes(
    val product: String = "",
    val country: String = "",
    val catalogue: String = "",
    val explicitFilter: Boolean = false,
    val canvasDisabled: Boolean = false,
    val musicVideoDisabled: Boolean = false,
    val otherVideoDisabled: Boolean = false,
    val canvasLocked: Boolean = false,
    val musicVideoLocked: Boolean = false,
    val otherVideoLocked: Boolean = false,
    val hasPodcasts: Boolean = true,
) {
    val premium: Boolean get() = product.equals("PREMIUM", ignoreCase = true)
}

data class ProfilePrivacy(
    val showFollows: Boolean = false,
    val showPublishedPlaylists: Boolean = false,
    val allowFollows: Boolean = false,
)
