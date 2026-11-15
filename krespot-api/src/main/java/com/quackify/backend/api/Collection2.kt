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
 * Copyright (c) 2026 23fpsz (Quackify). All Rights Reserved.
 * Original repository: https://github.com/23fpsz/Quackify
 *
 * PROPRIETARY / SOURCE-AVAILABLE MODULE:
 * You may view and audit this source code for personal or educational inspection.
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.quackify.backend.api

import android.content.Context
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.log.Logs
import org.json.JSONArray
import org.json.JSONObject
import com.quackify.backend.core.net.ClientToken
import com.quackify.backend.core.net.Net

private val log = Logs.of(Logs.Module.COLLECTION2)

object Collection2 {

    const val IGNORE_IN_RECS = "ignoreinrecs"
    const val ARTIST_BAN = "artistban"
    const val MARKED_AS_FINISHED = "markedasfinished"
    const val NOT_INTERESTED = "notinterested"

    private const val BASE = "https://spclient.wg.spotify.com/collection/v2"

    private suspend fun username(context: Context): String = EncoreAuth.userId(context)

    private suspend fun headers(context: Context): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${EncoreAuth.token(context)}")
        put("Accept", "application/json")
        put("app-platform", "WebPlayer")
        put("User-Agent", Net.DESKTOP_UA)
        ClientToken.get(context).takeIf { it.isNotBlank() }?.let { put("client-token", it) }
    }

    private fun itemsArray(uris: List<String>, removed: Boolean = false): JSONArray {
        val array = JSONArray()
        uris.forEach { uri ->
            val item = JSONObject().put("uri", uri)
            if (removed) item.put("is_removed", true)
            array.put(item)
        }
        return array
    }

    private fun body(user: String, set: String, uris: List<String>, removed: Boolean): String =
        JSONObject()
            .put("username", user)
            .put("set", set)
            .put("items", itemsArray(uris, removed))
            .toString()

    suspend fun contains(context: Context, set: String, uris: List<String>): Set<String> {
        val wanted = uris.filter { it.isNotBlank() }.distinct()
        if (wanted.isEmpty()) return emptySet()
        val user = username(context)
        if (user.isBlank()) {
            log.w("contains: no username yet, cannot read '$set'")
            return emptySet()
        }
        val response = Net.postJson(
            "$BASE/contains",
            body(user, set, wanted, removed = false),
            headers(context),
            "collection2/$set/contains",
        ) ?: return emptySet()
        val found = response.json()?.optJSONArray("found")
        if (found == null) {
            log.w("contains '$set' http=${response.code} no found[] ${response.body.take(200)}")
            return emptySet()
        }
        val out = LinkedHashSet<String>()
        wanted.forEachIndexed { i, uri -> if (found.optBoolean(i, false)) out.add(uri) }
        log.i("contains '$set' ${out.size} of ${wanted.size} http=${response.code}")
        return out
    }

    suspend fun add(context: Context, set: String, uris: List<String>): Boolean =
        write(context, set, uris, removed = false)

    suspend fun remove(context: Context, set: String, uris: List<String>): Boolean =
        write(context, set, uris, removed = true)

    private suspend fun write(
        context: Context,
        set: String,
        uris: List<String>,
        removed: Boolean,
    ): Boolean {
        val wanted = uris.filter { it.isNotBlank() }.distinct()
        if (wanted.isEmpty()) return false
        val user = username(context)
        if (user.isBlank()) {
            log.w("write: no username yet, cannot touch '$set'")
            return false
        }
        val response = Net.postJson(
            "$BASE/write",
            body(user, set, wanted, removed),
            headers(context),
            "collection2/$set/${if (removed) "remove" else "add"}",
        )
        val ok = response?.ok == true
        log.i("${if (removed) "remove" else "add"} '$set' ${wanted.size} uri(s) ok=$ok " +
                "http=${response?.code ?: -1} ${response?.body?.take(160).orEmpty()}"
        )
        return ok
    }

    suspend fun items(context: Context, set: String): List<String> {
        val user = username(context)
        if (user.isBlank()) return emptyList()
        val payload = JSONObject()
            .put("username", user)
            .put("set", set)
            .toString()
        val response = Net.postJson(
            "$BASE/paging",
            payload,
            headers(context),
            "collection2/$set/paging",
        ) ?: return emptyList()
        val array = response.json()?.optJSONArray("items") ?: run {
            log.i("paging '$set' empty http=${response.code}")
            return emptyList()
        }
        val out = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.optString("uri")?.takeIf { it.isNotBlank() }?.let(out::add)
        }
        log.i("paging '$set' ${out.size} uri(s)")
        return out
    }

    suspend fun excludedFromTasteProfile(context: Context, uri: String): Boolean =
        contains(context, IGNORE_IN_RECS, listOf(uri)).contains(uri)

    suspend fun setExcludedFromTasteProfile(
        context: Context,
        uri: String,
        excluded: Boolean,
    ): Boolean {
        if (uri.isBlank()) return false
        val ok = if (excluded) {
            add(context, IGNORE_IN_RECS, listOf(uri))
        } else {
            remove(context, IGNORE_IN_RECS, listOf(uri))
        }
        log.i("taste profile $uri excluded=$excluded ok=$ok")
        return ok
    }

    suspend fun artistBanned(context: Context, uri: String): Boolean =
        contains(context, ARTIST_BAN, listOf(uri)).contains(uri)

    suspend fun setArtistBanned(context: Context, uri: String, banned: Boolean): Boolean {
        if (!uri.startsWith("spotify:artist:")) return false
        val ok = if (banned) {
            add(context, ARTIST_BAN, listOf(uri))
        } else {
            remove(context, ARTIST_BAN, listOf(uri))
        }
        log.i("artist ban $uri banned=$banned ok=$ok")
        return ok
    }
}
