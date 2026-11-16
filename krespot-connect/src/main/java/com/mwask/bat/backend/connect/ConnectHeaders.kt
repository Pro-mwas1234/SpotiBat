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

package com.mwask.bat.backend.connect

import android.content.Context
import com.mwask.bat.backend.core.auth.EncoreAuth

internal object ConnectHeaders {

    const val APP_VERSION = "1.3.8.509.g26b6eac7300d"

    fun of(context: Context): Map<String, String> = mapOf(
        "Authorization" to "Bearer ${EncoreAuth.token(context)}",
        "client-token" to EncoreAuth.clientToken(context),
        "app-platform" to "WebPlayer",
        "spotify-app-version" to APP_VERSION,
    )
}
