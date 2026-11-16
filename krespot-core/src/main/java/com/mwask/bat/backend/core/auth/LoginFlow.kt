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

package com.mwask.bat.backend.core.auth

import android.content.Context
import android.os.SystemClock
import com.mwask.bat.backend.core.net.Pathfinder
import kotlinx.coroutines.delay
import com.mwask.bat.backend.core.log.Logs

private val log = Logs.of(Logs.Module.LOGIN_FLOW)

object LoginFlow {

    fun ops(): Int = Pathfinder.count()

    fun markAttempt(context: Context) {
        Pathfinder.markAttempt(context)
    }

    suspend fun harvest(
        context: Context,
        log: (String) -> Unit,
        timeoutMs: Long = 20_000,
        requiredOp: String = "home",
        ready: () -> Boolean = { true },
    ): Boolean {
        Pathfinder.load(context)
        val done = { Pathfinder.hashOf(requiredOp) != null && ready() }
        if (done()) {
            log("pathfinder cached (${Pathfinder.count()} ops, '$requiredOp' + credentials ready)")
            return true
        }
        log("harvesting until '$requiredOp' and credentials, ${timeoutMs}ms cap")
        val started = SystemClock.elapsedRealtime()
        var lastLogAt = 0L
        while (SystemClock.elapsedRealtime() - started < timeoutMs) {
            if (done()) break
            val elapsed = SystemClock.elapsedRealtime() - started
            if (elapsed - lastLogAt >= 3000) {
                lastLogAt = elapsed
                log(
                    "harvest ${elapsed}ms ops=${Pathfinder.count()} " +
                        "token=${EncoreAuth.token(context).isNotBlank()}",
                )
            }
            delay(250)
        }
        val ok = done()
        log(
            "harvest finished ops=${Pathfinder.count()} ok=$ok " +
                "in ${SystemClock.elapsedRealtime() - started}ms",
        )
        return ok
    }
}
