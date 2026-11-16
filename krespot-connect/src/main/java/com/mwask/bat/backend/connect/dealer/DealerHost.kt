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

package com.mwask.bat.backend.connect.dealer

import android.content.Context
import com.mwask.bat.backend.core.store.SecureStore
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.net.Net

private val log = Logs.of(Logs.Module.DEALER_HOST)

private const val APRESOLVE = "https://apresolve.spotify.com/?type=dealer-g2&type=spclient"

private const val KEY_DEALER = "apresolve_dealer"

private const val KEY_SPCLIENT = "apresolve_spclient"

private const val KEY_AT = "apresolve_at"

private const val TTL_MS = 6 * 60 * 60 * 1000L


object DealerHost {

    /** dealer host, spclient host, and when the pair was resolved. */
    @Volatile
    private var cached: Triple<String, String, Long>? = null

    suspend fun resolve(context: Context): Pair<String, String>? {
        cached?.takeIf { System.currentTimeMillis() - it.third <= TTL_MS }?.let {
            return it.first to it.second
        }
        persisted(context)?.let {
            cached = Triple(it.first, it.second, System.currentTimeMillis())
            log.i("dealer=${it.first} spclient=${it.second} cached")
            return it
        }
        val body = Net.get(APRESOLVE, tag = "apresolve")?.json() ?: run {
            log.w("apresolve: no body, keeping the last known hosts")
            return persisted(context, ignoreAge = true)
        }
        val dealer = body.optJSONArray("dealer-g2")?.optString(0)?.substringBefore(':').orEmpty()
        val spclient = body.optJSONArray("spclient")?.optString(0)?.substringBefore(':').orEmpty()
        if (dealer.isBlank() || spclient.isBlank()) {
            log.w("apresolve: dealer='$dealer' spclient='$spclient'")
            return persisted(context, ignoreAge = true)
        }
        val pair = dealer to spclient
        cached = Triple(dealer, spclient, System.currentTimeMillis())
        store(context, pair)
        log.i("dealer=$dealer spclient=$spclient")
        return pair
    }

    fun forget(context: Context) {
        cached = null
        SecureStore.edit(context)
            .remove(KEY_DEALER)
            .remove(KEY_SPCLIENT)
            .remove(KEY_AT)
            .apply()
    }

    private fun persisted(context: Context, ignoreAge: Boolean = false): Pair<String, String>? {
        val prefs = SecureStore.prefs(context)
        val dealer = prefs.getString(KEY_DEALER, "").orEmpty()
        val spclient = prefs.getString(KEY_SPCLIENT, "").orEmpty()
        if (dealer.isBlank() || spclient.isBlank()) return null
        if (ignoreAge) return dealer to spclient
        val at = prefs.getLong(KEY_AT, 0L)
        if (at <= 0L || System.currentTimeMillis() - at > TTL_MS) return null
        return dealer to spclient
    }

    private fun store(context: Context, pair: Pair<String, String>) {
        SecureStore.edit(context)
            .putString(KEY_DEALER, pair.first)
            .putString(KEY_SPCLIENT, pair.second)
            .putLong(KEY_AT, System.currentTimeMillis())
            .apply()
    }
}
