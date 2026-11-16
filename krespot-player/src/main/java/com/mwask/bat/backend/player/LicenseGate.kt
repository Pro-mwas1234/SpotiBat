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

private val log = Logs.of(Logs.Module.DRM)

private const val GATE_BASE_MS = 600_000L

private const val GATE_MAX_MS = 1_200_000L

private const val GATE_STEPS = 1

internal object LicenseGate {

    @Volatile
    private var until = 0L

    @Volatile
    private var strikes = 0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var timer: Job? = null

    @Volatile
    var onOpen: (() -> Unit)? = null

    fun remainingMs(): Long = (until - System.currentTimeMillis()).coerceAtLeast(0L)

    fun hold(millis: Long) {
        strikes++
        val escalated = (GATE_BASE_MS shl (strikes - 1).coerceAtMost(GATE_STEPS))
            .coerceAtMost(GATE_MAX_MS)
        val wait = if (millis > 0L) maxOf(millis, escalated).coerceAtMost(GATE_MAX_MS) else escalated
        val target = System.currentTimeMillis() + wait
        if (target > until) until = target
        log.w("licence gate closed for ${wait / 1000}s (strike $strikes)")
        timer?.cancel()
        timer = scope.launch {
            delay(wait + 250L)
            if (remainingMs() > 0L) return@launch
            log.i("licence gate open, asking for the track that was refused")
            onOpen?.invoke()
        }
    }

    fun clear() {
        timer?.cancel()
        timer = null
        if (until == 0L && strikes == 0) return
        until = 0L
        strikes = 0
        log.i("licence gate open")
    }
}
