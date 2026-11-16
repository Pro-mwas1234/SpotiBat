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

import android.webkit.CookieManager
import com.mwask.bat.backend.core.log.Logs

private val log = Logs.of(Logs.Module.WEBVIEW)

/**
 * The system WebView provider can disappear from under the app — the
 * com.google.android.webview package gets disabled, hidden or swapped
 * mid-update (seen on MIUI as "Package not found: com.google.android.webview"),
 * and `CookieManager.getInstance()` then throws an AndroidRuntimeException
 * that takes the whole process down when it happens on a composition path.
 *
 * Every cookie-jar access goes through here: a broken provider degrades to
 * "no cookies" (and the webview flows back off) instead of a crash loop.
 */
fun cookieManagerOrNull(): CookieManager? = try {
    CookieManager.getInstance()
} catch (t: Throwable) {
    log.w("cookie jar unavailable: ${t.cause?.message ?: t.message}")
    null
}
