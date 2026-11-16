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

package com.mwask.bat.backend.core.store

import android.content.Context

object SecureStore {

    private const val STORE = "spotibat_secure"

    fun prefs(context: Context): AppPrefs.Prefs = AppPrefs.prefs(context, STORE)

    fun edit(context: Context): AppPrefs.Editor = AppPrefs.edit(context, STORE)

    fun getString(context: Context, key: String, default: String? = null): String? =
        AppPrefs.getString(context, STORE, key, default)

    fun getLong(context: Context, key: String, default: Long = 0L): Long =
        AppPrefs.getLong(context, STORE, key, default)
}
