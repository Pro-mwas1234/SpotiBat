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

package com.mwask.bat.backend.core.locale

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * Keeps the Spotify login page out of the app language.
 *
 * accounts.spotify.com negotiates the WebView's Accept-Language into its URLs:
 * a device set to Indonesian gets redirected to `/id/login`, and the flow then
 * polls a URL the code no longer recognises. The login webviews are therefore
 * built from an en-US [loginContext] while [pushLoginLocale] pins the process
 * locale, which is what some WebView builds actually read.
 */
object AppLocale {

    private const val LOGIN_LOCALE = "en-US"

    /** A few languages resolve on Android through their legacy ISO codes. */
    private val LEGACY_LANGUAGES = mapOf(
        "id" to "in",
        "he" to "iw",
        "yi" to "ji",
    )

    /**
     * A context pinned to en-US for the login webviews, whatever the device
     * language is. Does not touch the process-wide default — pair with
     * [pushLoginLocale] when a login webview is actually alive.
     */
    fun loginContext(base: Context): Context {
        val locale = localeFor(LOGIN_LOCALE) ?: return base
        val wrapped = Configuration(base.resources.configuration)
        wrapped.setLocale(locale)
        wrapped.setLocales(LocaleList(locale))
        return base.createConfigurationContext(wrapped)
    }

    private var loginDepth = 0
    private var savedLocales: LocaleList? = null

    /**
     * Pins the process locale to en-US while a login webview is alive: some
     * WebView builds derive Accept-Language from the process default rather
     * than from the creating context's configuration. Pairs with
     * [popLoginLocale]. Main thread only.
     */
    fun pushLoginLocale() {
        if (loginDepth++ == 0) {
            savedLocales = LocaleList.getDefault()
            val locale = localeFor(LOGIN_LOCALE) ?: return
            Locale.setDefault(locale)
            LocaleList.setDefault(LocaleList(locale))
        }
    }

    /** Restores what [pushLoginLocale] pinned. Safe to call unbalanced. */
    fun popLoginLocale() {
        if (loginDepth == 0) return
        if (--loginDepth == 0) {
            savedLocales?.let { LocaleList.setDefault(it) }
            savedLocales = null
        }
    }

    private fun localeFor(tag: String): Locale? {
        val parsed = runCatching { Locale.forLanguageTag(tag) }.getOrNull()
            ?.takeIf { it.language.isNotBlank() } ?: return null
        val legacy = LEGACY_LANGUAGES[parsed.language] ?: return parsed
        return runCatching {
            Locale.Builder()
                .setLanguage(legacy)
                .apply {
                    if (parsed.script.isNotBlank()) setScript(parsed.script)
                    if (parsed.country.isNotBlank()) setRegion(parsed.country)
                    if (parsed.variant.isNotBlank()) setVariant(parsed.variant)
                }
                .build()
        }.getOrNull() ?: parsed
    }
}
