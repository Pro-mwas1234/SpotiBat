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

data class BrowseCard(
    val uri: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val colorHex: String,
)

data class BrowseSection(
    val title: String,
    val cards: List<BrowseCard>,
)

data class BrowsePage(
    val uri: String,
    val title: String,
    val subtitle: String,
    val colorHex: String,
    val imageUrl: String,
    val sections: List<BrowseSection>,
    val nextOffset: Int?,
) {
    val isEmpty: Boolean get() = sections.isEmpty()
}
