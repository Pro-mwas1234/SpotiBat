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

package com.mwask.bat.backend.api

import android.content.Context
import com.mwask.bat.backend.core.auth.EncoreAuth
import java.util.UUID
import com.mwask.bat.backend.core.log.Logs
import org.json.JSONArray
import org.json.JSONObject
import com.mwask.bat.backend.core.net.Pathfinder

private val log = Logs.of(Logs.Module.LIBRARY)

object Library {

    const val OP_SAVED = "areEntitiesInLibrary"
    const val OP_ADD = "addToLibrary"
    const val OP_REMOVE = "removeFromLibrary"

    private const val BATCH = 50

    var onLibraryChanged: (() -> Unit)? = null

    private val WRITABLE = setOf(
        "album", "artist", "author", "clip", "concert", "concert_campaign",
        "episode", "kallax", "show", "track", "venue",
    )

    private fun typeOf(uri: String): String =
        uri.removePrefix("spotify:").substringBefore(":")

    suspend fun saved(context: Context, uris: List<String>): Set<String> {
        val unique = uris.filter { it.isNotBlank() }.distinct()
        if (unique.isEmpty()) return emptySet()
        val token = EncoreAuth.token(context)
        if (token.isBlank()) {
            log.w("saved: no token")
            return emptySet()
        }
        val out = LinkedHashSet<String>()
        unique.chunked(BATCH).forEach { chunk ->
            val variables = JSONObject().put("uris", JSONArray(chunk)).toString()
            val json = Pathfinder.queryPost(context, token, OP_SAVED, variables) ?: return@forEach
            val lookup = json.optJSONObject("data")?.optJSONArray("lookup") ?: return@forEach
            chunk.forEachIndexed { i, uri ->
                val entry = lookup.optJSONObject(i)?.optJSONObject("data") ?: return@forEachIndexed
                if (entry.optBoolean("saved", false)) out.add(uri)
            }
        }
        log.i("saved: ${out.size} of ${unique.size} (${unique.size / BATCH + 1} batch max)")
        return out
    }

    suspend fun setSaved(context: Context, uris: List<String>, saved: Boolean): Boolean {
        val unique = uris.filter { it.isNotBlank() }.distinct()
        if (unique.isEmpty()) return true
        val writable = unique.filter { typeOf(it) in WRITABLE }
        if (writable.size != unique.size) {
            log.w("setSaved: dropped ${unique.size - writable.size} uri(s) the library does not accept " +
                    "(playlists have no library state)"
            )
        }
        if (writable.isEmpty()) return false
        val token = EncoreAuth.token(context)
        if (token.isBlank()) {
            log.w("setSaved: no token")
            return false
        }
        val operation = if (saved) OP_ADD else OP_REMOVE
        var allOk = true
        writable.chunked(BATCH).forEach { chunk ->
            val variables = JSONObject().put("libraryItemUris", JSONArray(chunk))
            if (chunk.all { typeOf(it) == "show" }) {
                variables.put("interactionId", JSONObject.NULL)
            } else {
                variables.put("interactionId", UUID.randomUUID().toString())
            }
            val json = Pathfinder.queryPost(context, token, operation, variables.toString(), fallbackToGet = false)
            val key = if (saved) "addLibraryItems" else "removeLibraryItems"
            val ok = json?.optJSONObject("data")?.optJSONObject(key) != null
            if (!ok) allOk = false
            log.i("$operation ${chunk.size} uri(s) ok=$ok")
        }
        if (allOk) onLibraryChanged?.invoke()
        return allOk
    }
}
