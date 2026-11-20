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

package com.mwask.bat.backend.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.mwask.bat.backend.core.log.Logs
import kotlin.random.Random

private val log = Logs.of(Logs.Module.DRM)

private const val GATE_BASE_MS = 2_000L

private const val GATE_MAX_MS = 60_000L

private const val GATE_STEPS = 5

private const val GATE_MAX_STRIKES = 4

/**
 * The server's Retry-After is a real window: Spotify can ask for several
 * minutes. Clamping it to the local 60 s escalation made every retry land
 * inside the window and burn another strike, so it gets its own cap.
 */
private const val GATE_SERVER_CAP_MS = 10 * 60_000L

internal object LicenseGate {

    @Volatile
    private var until = 0L

    @Volatile
    private var strikes = 0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var timer: Job? = null

    @Volatile
    var onOpen: (() -> Unit)? = null

    @Volatile
    var rateLimited = false

    @Volatile
    var lastStatus = 0

    fun remainingMs(): Long = (until - System.currentTimeMillis()).coerceAtLeast(0L)

    fun hold(status: Int = 429, millis: Long = 0L) {
        strikes++
        lastStatus = status
        rateLimited = status == 429
        log.w("licence refused $status, strike $strikes")
        val escalated = (GATE_BASE_MS shl (strikes - 1).coerceAtMost(GATE_STEPS))
            .coerceAtMost(GATE_MAX_MS)
        val server = millis.coerceIn(0L, GATE_SERVER_CAP_MS)
        val base = maxOf(escalated, server)
        // Jitter keeps every account/device that hit the same window from
        // retrying on the same millisecond.
        val wait = base + Random.nextLong(0L, (base / 4L).coerceAtLeast(250L))
        val target = System.currentTimeMillis() + wait
        if (target > until) until = target
        log.w("licence gate closed for ${wait / 1000}s (strike $strikes)")
        timer?.cancel()
        if (strikes >= GATE_MAX_STRIKES) {
            log.w("licence keeps being refused, the user has to ask again")
            return
        }
        timer = scope.launch {
            delay(wait + 250L)
            if (remainingMs() > 0L) return@launch
            log.i("licence gate open, asking for the track that was refused")
            onOpen?.invoke()
        }
    }

    fun resetStrikes() {
        if (strikes == 0) return
        strikes = 0
        rateLimited = false
    }

    fun clear() {
        timer?.cancel()
        timer = null
        rateLimited = false
        if (until == 0L && strikes == 0) return
        until = 0L
        strikes = 0
        log.i("licence gate open")
    }
}
