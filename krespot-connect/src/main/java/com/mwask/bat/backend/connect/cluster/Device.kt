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

package com.mwask.bat.backend.connect.cluster

import android.content.Context
import com.mwask.bat.backend.core.store.SecureStore
import java.security.SecureRandom
import com.mwask.bat.backend.core.log.Logs

private val log = Logs.of(Logs.Module.CLUSTER)

object Device {

    const val CLIENT_ID = "d8a5ed958d274c2e8ee717e6a4b0971d"

    private const val KEY_ID = "connect_device_id"


    private val random = SecureRandom()

    @Volatile
    private var cached: String? = null

    fun id(context: Context): String {
        cached?.let { return it }
        val prefs = SecureStore.prefs(context)
        val stored = prefs.getString(KEY_ID, "").orEmpty()
        if (stored.isNotBlank()) {
            cached = stored
            log.i("device id reused ${stored.take(8)}")
            return stored
        }
        val minted = newId()
        prefs.edit().putString(KEY_ID, minted).apply()
        cached = minted
        log.i("device id minted ${minted.take(8)}")
        return minted
    }

    fun forget(context: Context) {
        cached = null
        SecureStore.edit(context).remove(KEY_ID).apply()
        log.i("device id dropped")
    }

    fun newId(): String {
        val bytes = ByteArray(20)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
