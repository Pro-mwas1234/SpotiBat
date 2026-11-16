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

package com.mwask.bat.backend.library

import android.content.Context
import com.mwask.bat.backend.core.auth.EncoreAuth
import com.mwask.bat.backend.core.log.Logs
import org.json.JSONArray
import org.json.JSONObject
import com.mwask.bat.backend.core.net.ClientToken
import com.mwask.bat.backend.core.net.Net

private val log = Logs.of(Logs.Module.ROOTLIST)

object Rootlist {

    private const val BASE = "https://spclient.wg.spotify.com/playlist/v2/user"

    /** Opening a playlist asks saved() and published() back to back; this keeps the second read free. */
    private const val READ_CACHE_MS = 5_000L

    @Volatile
    private var lastRead: Pair<Long, JSONObject>? = null

    data class Entry(val uri: String, val index: Int)

    data class Snapshot(
        val revision: String,
        val length: Int,
        val pos: Int,
        val truncated: Boolean,
        val entries: List<Entry>,
    ) {
        fun indexOf(uri: String): Int = entries.firstOrNull { it.uri == uri }?.index ?: -1

        fun groupIndex(hash: String, end: Boolean): Int {
            if (hash.isBlank()) return -1
            val tag = if (end) "spotify:end-group:$hash" else "spotify:start-group:$hash"
            return entries.firstOrNull { it.uri == tag || it.uri.startsWith("$tag:") }?.index ?: -1
        }
    }

    internal suspend fun username(context: Context): String = EncoreAuth.userId(context)

    private suspend fun headers(context: Context): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${EncoreAuth.token(context)}")
        put("Accept", "application/json")
        put("app-platform", "WebPlayer")
        put("User-Agent", Net.DESKTOP_UA)
        ClientToken.get(context).takeIf { it.isNotBlank() }?.let { put("client-token", it) }
    }

    private fun info(): JSONObject = JSONObject().put("source", JSONObject().put("client", "WEBPLAYER"))

    private fun body(op: JSONObject, revision: String? = null): String =
        bodyOf(JSONArray().put(op), revision)

    private fun bodyOf(ops: JSONArray, revision: String? = null): String {
        val json = JSONObject()
        if (revision != null) json.put("baseRevision", revision)
        json.put(
            "deltas",
            JSONArray().put(JSONObject().put("ops", ops).put("info", info())),
        )
        if (revision != null) {
            json.put("wantResultingRevisions", false)
            json.put("wantSyncResult", false)
            json.put("nonces", JSONArray())
        }
        return json.toString()
    }

    private fun change(uri: String, add: Boolean): String {
        val op = if (add) {
            JSONObject().put("kind", "ADD").put(
                "add",
                JSONObject()
                    .put("items", JSONArray().put(item(uri)))
                    .put("addFirst", true),
            )
        } else {
            JSONObject().put("kind", "REM").put(
                "rem",
                JSONObject()
                    .put("items", JSONArray().put(JSONObject().put("uri", uri)))
                    .put("itemsAsKey", true),
            )
        }
        return body(op)
    }

    private fun item(uri: String): JSONObject = JSONObject()
        .put("uri", uri)
        .put("attributes", JSONObject().put("timestamp", System.currentTimeMillis().toString()))

    private suspend fun write(context: Context, uri: String, add: Boolean): Boolean {
        if (uri.isBlank()) return false
        val user = username(context)
        if (user.isBlank()) {
            log.w("no username yet, cannot ${if (add) "add" else "remove"} $uri")
            return false
        }
        lastRead = null
        val response = Net.postJson(
            "$BASE/$user/rootlist/changes",
            change(uri, add),
            headers(context),
            "rootlist/${if (add) "add" else "remove"}",
        )
        val ok = response?.ok == true
        log.i("${if (add) "add" else "remove"} $uri ok=$ok http=${response?.code ?: -1} " +
                response?.body?.take(160).orEmpty()
        )
        if (ok) LibraryPageSource.invalidate()
        return ok
    }

    suspend fun add(context: Context, uri: String): Boolean = write(context, uri, add = true)

    suspend fun remove(context: Context, uri: String): Boolean = write(context, uri, add = false)

    suspend fun setSaved(context: Context, uri: String, saved: Boolean): Boolean =
        if (saved) add(context, uri) else remove(context, uri)

    suspend fun snapshot(context: Context): Snapshot? {
        val user = username(context)
        if (user.isBlank()) {
            log.w("snapshot: no username yet")
            return null
        }
        val response = Net.get(
            "$BASE/$user/rootlist?decorate=revision,length,attributes,timestamp",
            headers(context),
            "rootlist/read",
        ) ?: return null
        val json = response.json()
        if (json == null) {
            log.w("snapshot: http=${response.code} not-json ${response.body.take(200)}")
            return null
        }
        val contents = json.optJSONObject("contents")
        val items = contents?.optJSONArray("items") ?: JSONArray()
        val entries = ArrayList<Entry>(items.length())
        for (i in 0 until items.length()) {
            val uri = items.optJSONObject(i)?.optString("uri").orEmpty()
            if (uri.isNotBlank()) entries += Entry(uri, i)
        }
        val snap = Snapshot(
            revision = json.optString("revision"),
            length = json.optInt("length", entries.size),
            pos = contents?.optInt("pos", 0) ?: 0,
            truncated = contents?.optBoolean("truncated", false) ?: false,
            entries = entries,
        )
        log.i("snapshot revision=${snap.revision} length=${snap.length} pos=${snap.pos} " +
                "truncated=${snap.truncated} entries=${entries.size}"
        )
        return snap
    }

    suspend fun addUris(context: Context, uris: List<String>, atTop: Boolean = true): Boolean {
        if (uris.isEmpty()) return false
        val user = username(context)
        if (user.isBlank()) {
            log.w("addUris: no username yet")
            return false
        }
        val items = JSONArray()
        uris.forEach { items.put(item(it)) }
        val add = JSONObject().put("items", items)
        if (atTop) add.put("addFirst", true) else add.put("addLast", true)
        val op = JSONObject().put("kind", "ADD").put("add", add)
        lastRead = null
        val response = Net.postJson(
            "$BASE/$user/rootlist/changes",
            body(op),
            headers(context),
            "rootlist/addItems",
        )
        val ok = response?.ok == true
        log.i("addItems ${uris.size} atTop=$atTop ok=$ok http=${response?.code ?: -1} " +
                response?.body?.take(160).orEmpty()
        )
        if (ok) LibraryPageSource.invalidate()
        return ok
    }

    suspend fun move(
        context: Context,
        from: Int,
        to: Int,
        length: Int = 1,
        revision: String? = null,
    ): Boolean {
        if (from < 0 || to < 0 || length < 1 || from == to) {
            log.w("move: bad indices from=$from to=$to length=$length")
            return false
        }
        val user = username(context)
        if (user.isBlank()) {
            log.w("move: no username yet")
            return false
        }
        val base = revision ?: snapshot(context)?.revision.orEmpty()
        if (base.isBlank()) {
            log.w("move: no baseRevision, the server rejects an index op without it")
            return false
        }
        val op = JSONObject().put("kind", "MOV").put(
            "mov",
            JSONObject()
                .put("fromIndex", from)
                .put("toIndex", to)
                .put("length", length)
                .put("items", JSONArray()),
        )
        lastRead = null
        val response = Net.postJson(
            "$BASE/$user/rootlist/changes",
            body(op, base),
            headers(context),
            "rootlist/move",
        )
        val ok = response?.ok == true
        log.i("move $from -> $to length=$length ok=$ok http=${response?.code ?: -1} " +
                response?.body?.take(160).orEmpty()
        )
        if (ok) LibraryPageSource.invalidate()
        return ok
    }

    suspend fun moveAfter(context: Context, uri: String, afterUri: String): Boolean {
        if (uri.isBlank() || afterUri.isBlank()) {
            log.w("moveAfter: bad input '$uri' '$afterUri'")
            return false
        }
        val user = username(context)
        if (user.isBlank()) {
            log.w("moveAfter: no username yet")
            return false
        }
        val op = JSONObject().put("kind", "MOV").put(
            "mov",
            JSONObject()
                .put("items", JSONArray().put(JSONObject().put("uri", uri)))
                .put("addAfterItem", JSONObject().put("uri", afterUri)),
        )
        lastRead = null
        val response = Net.postJson(
            "$BASE/$user/rootlist/changes",
            body(op),
            headers(context),
            "rootlist/moveAfter",
        )
        val ok = response?.ok == true
        log.i("moveAfter '$uri' after '$afterUri' ok=$ok http=${response?.code ?: -1} " +
                response?.body?.take(160).orEmpty()
        )
        if (ok) LibraryPageSource.invalidate()
        return ok
    }

    suspend fun moveFirst(context: Context, uri: String): Boolean {
        if (uri.isBlank()) {
            log.w("moveFirst: bad input '$uri'")
            return false
        }
        val user = username(context)
        if (user.isBlank()) {
            log.w("moveFirst: no username yet")
            return false
        }
        val op = JSONObject().put("kind", "MOV").put(
            "mov",
            JSONObject()
                .put("items", JSONArray().put(JSONObject().put("uri", uri)))
                .put("addFirst", true),
        )
        lastRead = null
        val response = Net.postJson(
            "$BASE/$user/rootlist/changes",
            body(op),
            headers(context),
            "rootlist/moveFirst",
        )
        val ok = response?.ok == true
        log.i("moveFirst '$uri' ok=$ok http=${response?.code ?: -1} " +
                response?.body?.take(160).orEmpty()
        )
        if (ok) LibraryPageSource.invalidate()
        return ok
    }

    internal suspend fun writeOps(
        context: Context,
        ops: JSONArray,
        revision: String?,
        label: String,
    ): Boolean {
        val user = username(context)
        if (user.isBlank()) {
            log.w("$label: no username yet")
            return false
        }
        lastRead = null
        val response = Net.postJson(
            "$BASE/$user/rootlist/changes",
            bodyOf(ops, revision),
            headers(context),
            "rootlist/$label",
        )
        val ok = response?.ok == true
        log.i("$label ops=${ops.length()} ok=$ok http=${response?.code ?: -1} " +
                response?.body?.take(160).orEmpty()
        )
        if (ok) LibraryPageSource.invalidate()
        return ok
    }

    internal fun remOp(uri: String): JSONObject = JSONObject().put("kind", "REM").put(
        "rem",
        JSONObject()
            .put("items", JSONArray().put(JSONObject().put("uri", uri)))
            .put("itemsAsKey", true),
    )

    internal fun addAtOp(uri: String, index: Int): JSONObject = JSONObject()
        .put("kind", "ADD")
        .put(
            "add",
            JSONObject()
                .put("items", JSONArray().put(item(uri)))
                .put("fromIndex", index),
        )


    /** The rootlist document, served from a short-lived cache when it was just read. */
    private suspend fun read(context: Context, tag: String): JSONObject? {
        lastRead?.takeIf { System.currentTimeMillis() - it.first < READ_CACHE_MS }?.let { return it.second }
        val user = username(context)
        if (user.isBlank()) {
            log.w("$tag: no username yet")
            return null
        }
        val response = Net.get(
            "$BASE/$user/rootlist?decorate=revision,length,attributes,timestamp",
            headers(context),
            tag,
        ) ?: return null
        val json = response.json() ?: return null
        lastRead = System.currentTimeMillis() to json
        return json
    }

    suspend fun saved(context: Context, uri: String): Boolean? {
        if (uri.isBlank()) return null
        val json = read(context, "rootlist/read") ?: return null
        val items = json.optJSONObject("contents")?.optJSONArray("items")
        var count = 0
        for (i in 0 until (items?.length() ?: 0)) {
            val node = items?.optJSONObject(i) ?: continue
            if (node.optString("uri").isNotBlank()) count++
            if (node.optString("uri") == uri) {
                log.i("saved: $count+ uri(s) in the rootlist, $uri inside=true")
                return true
            }
        }
        log.i("saved: $count uri(s) in the rootlist, $uri inside=false")
        return false
    }
}
