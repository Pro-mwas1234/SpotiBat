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
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AppPrefEntry::class], version = 1, exportSchema = false)
abstract class AppPrefsDb : RoomDatabase() {
    abstract fun dao(): AppPrefsDao

    companion object {
        @Volatile
        private var instance: AppPrefsDb? = null

        fun get(context: Context): AppPrefsDb {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppPrefsDb::class.java,
                    "app_prefs.db"
                ).allowMainThreadQueries().build().also { instance = it }
            }
        }
    }
}
