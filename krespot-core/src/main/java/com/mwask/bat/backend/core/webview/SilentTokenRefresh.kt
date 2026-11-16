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

package com.mwask.bat.backend.core.webview

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import com.mwask.bat.backend.core.auth.EncoreAuth
import com.mwask.bat.backend.core.auth.WebToken
import com.mwask.bat.backend.core.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.mwask.bat.backend.core.log.Logs

private val log = Logs.of(Logs.Module.SILENT)
private const val START_URL = "https://open.spotify.com/"
private const val TIMEOUT_MS = 25_000L
private const val POLL_MS = 200L
private const val RELOAD_AFTER_MS = 8_000L
private const val COOKIE_TRIES = 40
private const val COOKIE_POLL_MS = 50L
private const val PROACTIVE_AFTER_MS = 50 * 60 * 1000L

object SilentTokenRefresh {

    private val lock = Mutex()

    suspend fun maybeRefresh(context: Context): Boolean {
        if (!EncoreAuth.isLoggedIn(context)) return false
        val age = EncoreAuth.tokenAgeMs(context)
        if (age in 0 until PROACTIVE_AFTER_MS) return false
        log.i("token is ${age / 1000}s old, refreshing before it 401s")
        return refresh(context, "proactive")
    }

    suspend fun refresh(context: Context, reason: String): Boolean {
        if (!EncoreAuth.isLoggedIn(context)) return false
        if (WebToken.mint(context) != null) {
            log.i("token minted natively from /api/token, no webview needed ($reason)")
            return true
        }
        val askedAt = System.currentTimeMillis()
        return lock.withLock {
            if (freshSince(context, askedAt)) {
                log.i("a refresh in flight already delivered a token while waiting ($reason)")
                return@withLock true
            }
            withContext(Dispatchers.Main) { harvest(context, reason) }
        }
    }

    private suspend fun harvest(context: Context, reason: String): Boolean {
        val started = System.currentTimeMillis()
        val host = (context as? Activity)?.takeIf { !it.isDestroyed }
        var web: WebView? = null
        var holder: FrameLayout? = null
        try {
            val cookies = waitForCookies(context)
            if (cookies == null) {
                log.w("no sp_dc in the cookie jar, a refresh cannot work ($reason)")
                return false
            }
            log.i("cookies ready (${cookies.length}B names=${cookieNames(cookies)}) ($reason)")
            web = createWebView(context)
            holder = attach(host, web)
            cookieManagerOrNull()?.setAcceptThirdPartyCookies(web, true)
            web.loadUrl(START_URL)
            log.i("started ($reason) attached=${holder != null}")
            var reloaded = false
            while (System.currentTimeMillis() - started < TIMEOUT_MS) {
                if (freshSince(context, started)) {
                    log.i("fresh token in ${System.currentTimeMillis() - started}ms ($reason)")
                    return true
                }
                if (!reloaded && System.currentTimeMillis() - started > RELOAD_AFTER_MS) {
                    reloaded = true
                    log.i("no credentials yet, reloading once ($reason)")
                    web.reload()
                }
                delay(POLL_MS)
            }
            log.w("no fresh token after ${TIMEOUT_MS}ms ($reason)")
            return false
        } catch (t: Throwable) {
            log.w("refresh failed ($reason)", t)
            return false
        } finally {
            holder?.let { (it.parent as? ViewGroup)?.removeView(it) }
            web?.let {
                runCatching { it.stopLoading() }
                runCatching { it.destroy() }
            }
        }
    }

    private suspend fun waitForCookies(context: Context): String? {
        EncoreAuth.applySession(context)
        val manager = cookieManagerOrNull() ?: run {
            log.w("cookie jar unavailable, cannot seed a refresh")
            return null
        }
        manager.setAcceptCookie(true)
        repeat(COOKIE_TRIES) {
            val raw = manager.getCookie(START_URL).orEmpty()
            if (raw.contains("sp_dc=")) {
                manager.flush()
                return raw
            }
            delay(COOKIE_POLL_MS)
        }
        return null
    }

    private fun cookieNames(raw: String): String = raw.split(";")
        .map { it.substringBefore("=").trim() }
        .filter { it.isNotBlank() }
        .joinToString(",")

    private fun freshSince(context: Context, startedAt: Long): Boolean {
        val age = EncoreAuth.tokenAgeMs(context)
        if (age < 0) return false
        return System.currentTimeMillis() - age >= startedAt
    }

    private fun attach(host: Activity?, web: WebView): FrameLayout? {
        if (host == null) return null
        val decor = host.window?.decorView as? ViewGroup ?: return null
        val holder = FrameLayout(host).apply {
            alpha = 0f
            isClickable = false
            isFocusable = false
        }
        holder.addView(web, ViewGroup.LayoutParams(-1, -1))
        return runCatching {
            decor.addView(holder, ViewGroup.LayoutParams(1, 1))
            holder
        }.getOrElse {
            log.w("could not attach the refresher, running detached", it)
            null
        }
    }

    private fun createWebView(context: Context): WebView {
        val web = WebView(context)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            userAgentString = Net.DESKTOP_UA
        }
        NetworkTap.install(web, context)
        return web
    }
}
