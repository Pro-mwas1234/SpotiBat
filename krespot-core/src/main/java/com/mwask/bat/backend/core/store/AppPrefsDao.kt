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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppPrefsDao {
    @Query("SELECT * FROM app_prefs WHERE store = :store")
    fun all(store: String): List<AppPrefEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(entry: AppPrefEntry)

    @Query("DELETE FROM app_prefs WHERE store = :store AND `key` = :key")
    fun remove(store: String, key: String)

    @Query("DELETE FROM app_prefs WHERE store = :store")
    fun clear(store: String)
}
