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

data class WhatsNewItem(
    val id: String,
    val uri: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val timestamp: String,
    val isNew: Boolean,
    val isMusic: Boolean,
    val kind: String,
)

data class WhatsNewFeed(
    val items: List<WhatsNewItem> = emptyList(),
    val total: Int = 0,
    val nextOffset: Int? = null,
    val seen: List<String> = emptyList(),
) {
    val hasMore: Boolean get() = nextOffset != null
    val newCount: Int get() = items.count { it.isNew }
}
