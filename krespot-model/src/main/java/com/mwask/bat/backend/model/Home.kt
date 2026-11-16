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

data class HomeItem(
    val uri: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val kind: String,
    val colorHex: String = "",
    val caption: String = "",
)

data class HomeSection(
    val id: String,
    val title: String,
    val kind: String,
    val items: List<HomeItem>,
)

data class HomeChip(
    val id: String,
    val label: String,
    val subChips: List<HomeChip> = emptyList(),
)

data class HomeFeedPayload(
    val greeting: String = "",
    val chips: List<HomeChip> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
)
