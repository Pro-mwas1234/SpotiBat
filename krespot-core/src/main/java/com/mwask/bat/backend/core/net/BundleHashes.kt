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

package com.mwask.bat.backend.core.net

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.store.AppPrefs
import org.json.JSONObject

private val log = Logs.of(Logs.Module.HASHES)

object BundleHashes {

    private const val SHELL = "https://open.spotify.com/"
    private const val CDN = "https://open.spotifycdn.com/cdn/build/web-player/"
    private const val PREFS = "spotibat_hashes"
    private const val KEY_TABLE = "table"
    private const val KEY_AT = "at"
    private const val COOLDOWN_MS = 60_000L

    private val TRIPLE =
        Regex("\\(\"([A-Za-z][A-Za-z0-9_]*)\",\"(query|mutation)\",\"([0-9a-f]{64})\"")
    private val BUNDLE =
        Regex("https://open\\.spotifycdn\\.com/cdn/build/web-player/[A-Za-z0-9~._-]+\\.js")
    private val CHUNK_ENTRY = Regex("(\\d+):\"([a-z0-9~.-]+)\"")
    private val CHUNK_HEX = Regex("[0-9a-f]{8}")
    private val WANTED = Regex(
        "^(xpui-routes-(search|recent-searches|album|artist|playlist|your-library-x|home|show|episode)" +
            "|home-v2)",
    )

    private val lock = Mutex()
    private val table = LinkedHashMap<String, String>()
    private var loaded = false
    private var at = 0L

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = AppPrefs.prefs(context, PREFS)
        at = prefs.getLong(KEY_AT, 0L)
        val raw = prefs.getString(KEY_TABLE, null) ?: return
        runCatching {
            val root = JSONObject(raw)
            root.keys().asSequence().forEach { name ->
                val hash = root.optString(name)
                if (hash.isNotBlank()) table[name] = hash
            }
            log.i("loaded ${table.size} hashes, ${(System.currentTimeMillis() - at) / 1000}s old")
        }.onFailure { log.w("stored table unreadable", it) }
    }

    @Synchronized
    fun hash(context: Context, name: String): String? {
        load(context)
        return table[name]
    }

    @Synchronized
    fun refreshedAt(context: Context): Long {
        load(context)
        return at
    }

    suspend fun refresh(context: Context, reason: String): Map<String, String>? = lock.withLock {
        load(context)
        val now = System.currentTimeMillis()
        if (table.isNotEmpty() && now - at < COOLDOWN_MS) {
            log.i("not scraping for $reason, the table is ${(now - at) / 1000}s old")
            return@withLock table.toMap()
        }
        val found = scrape()
        if (found.isEmpty()) {
            log.w("scrape for $reason found nothing")
            return@withLock null
        }
        table.clear()
        table.putAll(found)
        at = System.currentTimeMillis()
        persist(context)
        log.i("scrape for $reason -> ${found.size} hashes in ${at - now}ms")
        table.toMap()
    }

    private suspend fun scrape(): Map<String, String> {
        val ua = mapOf("User-Agent" to Net.DESKTOP_UA)
        val shell = Net.get(SHELL, ua, "hash-shell")?.body
        if (shell.isNullOrBlank()) {
            log.w("no shell from $SHELL")
            return emptyMap()
        }
        val bundles = BUNDLE.findAll(shell)
            .map { it.value }
            .distinct()
            .sortedBy { it.substringAfterLast('/').contains('~') }
            .toList()
        if (bundles.isEmpty()) {
            log.w("the shell names no bundles")
            return emptyMap()
        }
        val found = LinkedHashMap<String, String>()
        var chunkMap: Map<String, String> = emptyMap()
        for (url in bundles) {
            val body = Net.get(url, ua, "hash-bundle")?.body
            if (body.isNullOrBlank()) continue
            harvest(body, found)
            if (chunkMap.isEmpty()) chunkMap = chunks(body)
            if (found.isNotEmpty() && chunkMap.isNotEmpty()) break
        }
        log.i("${found.size} hashes in the base bundles, ${chunkMap.size} chunks mapped")
        for ((name, hash) in chunkMap) {
            if (!WANTED.containsMatchIn(name)) continue
            val body = Net.get("$CDN$name.$hash.js", ua, "hash-chunk")?.body ?: continue
            harvest(body, found)
        }
        return found
    }

    private fun harvest(body: String, into: MutableMap<String, String>) {
        TRIPLE.findAll(body).forEach { match ->
            into[match.groupValues[1]] = match.groupValues[3]
        }
    }

    private fun chunks(source: String): Map<String, String> {
        val start = source.indexOf("u.u=e=>")
        if (start < 0) return emptyMap()
        val end = source.indexOf(".js", start)
        if (end < 0) return emptyMap()
        val names = LinkedHashMap<String, String>()
        val hashes = HashMap<String, String>()
        CHUNK_ENTRY.findAll(source.substring(start, end + 3)).forEach { match ->
            val id = match.groupValues[1]
            val literal = match.groupValues[2]
            if (CHUNK_HEX.matches(literal)) hashes[id] = literal else names.putIfAbsent(id, literal)
        }
        return names.mapNotNull { (id, name) -> hashes[id]?.let { name to it } }.toMap()
    }

    private fun persist(context: Context) {
        val root = JSONObject()
        table.forEach { (name, hash) -> root.put(name, hash) }
        AppPrefs.prefs(context, PREFS).edit()
            .putString(KEY_TABLE, root.toString())
            .putLong(KEY_AT, at)
            .apply()
    }
}
