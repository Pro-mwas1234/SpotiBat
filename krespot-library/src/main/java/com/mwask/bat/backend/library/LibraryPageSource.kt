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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.mwask.bat.backend.core.auth.EncoreAuth
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.net.GraphQl
import com.mwask.bat.backend.core.net.Pathfinder
import com.mwask.bat.backend.core.net.plainText
import com.mwask.bat.backend.model.LibraryPage
import com.mwask.bat.backend.model.ShelfItem
import com.mwask.bat.backend.model.ShelfKind
import org.json.JSONArray
import org.json.JSONObject
import com.mwask.bat.backend.core.net.plainText

private val log = Logs.of(Logs.Module.LIBRARY_PAGE)

internal const val PAGE = 50

object LibraryPageSource {

    private const val OP_PAGE = "libraryV3"
    private const val OP_PIN = "pinLibraryItem"
    private const val OP_UNPIN = "unpinLibraryItem"
    private const val CACHE_MS = 5 * 60_000L

    /** Search keystrokes mint cache keys without limit; stale ones are dropped, live ones capped. */
    private const val CACHE_KEYS = 16

    private data class Cached(val page: LibraryPage, val at: Long)

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Cached>()

    private val revisions = MutableStateFlow(0)

    val revision: StateFlow<Int> = revisions.asStateFlow()

    fun invalidate() {
        cache.clear()
        revisions.value += 1
        log.i("cache cleared, revision ${revisions.value}")
    }

    private fun cacheKey(order: String?, textFilter: String, folderUri: String?, flatten: Boolean): String =
        "${order.orEmpty()}|$textFilter|${folderUri.orEmpty()}|$flatten"

    private val FEATURES = JSONArray()
        .put("LIKED_SONGS")
        .put("YOUR_EPISODES_V2")
        .put("PRERELEASES")
        .put("PRERELEASES_V2")
        .put("CLIPS")
        .put("EVENTS")

    suspend fun page(
        context: Context,
        order: String? = null,
        textFilter: String = "",
        folderUri: String? = null,
        offset: Int = 0,
        flatten: Boolean = false,
    ): LibraryPage? {
        val key = cacheKey(order, textFilter, folderUri, flatten)
        val now = System.currentTimeMillis()
        if (offset == 0) {
            val hit = cache[key]
            if (hit != null) {
                if (now - hit.at < CACHE_MS) {
                    log.i("page cache hit '$key' -> ${hit.page.items.size}/${hit.page.total}")
                    return hit.page
                }
                cache.remove(key, hit)
            }
            if (cache.size >= CACHE_KEYS) {
                log.i("page cache at ${cache.size} keys, dropping the oldest ones")
                cache.entries.sortedBy { it.value.at }
                    .take(cache.size - CACHE_KEYS / 2)
                    .forEach { cache.remove(it.key, it.value) }
            }
        }
        val token = EncoreAuth.token(context)
        if (token.isBlank()) {
            log.w("page: no token")
            return null
        }
        val variables = JSONObject()
            .put("order", order ?: JSONObject.NULL)
            .put("textFilter", textFilter)
            .put("features", FEATURES)
            .put("limit", PAGE)
            .put("offset", offset)
            .put("flatten", flatten)
            .put("expandedFolders", JSONArray())
            .put("folderUri", folderUri ?: JSONObject.NULL)
            .put("includeFoldersWhenFlattening", true)
            .toString()
        val json = Pathfinder.queryPost(context, token, OP_PAGE, variables) ?: return null
        val lib = json.optJSONObject("data")
            ?.optJSONObject("me")
            ?.optJSONObject("libraryV3")
            ?: run {
                log.w("page: no data.me.libraryV3")
                return null
            }
        val items = lib.optJSONArray("items")
        val shelves = ArrayList<ShelfItem>(items?.length() ?: 0)
        for (i in 0 until (items?.length() ?: 0)) {
            shelf(items?.optJSONObject(i))?.let(shelves::add)
        }
        val filters = ArrayList<String>()
        val available = lib.optJSONArray("availableFilters")
        for (i in 0 until (available?.length() ?: 0)) {
            available?.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let(filters::add)
        }
        val page = LibraryPage(
            items = shelves,
            filters = filters,
            sort = lib.optJSONObject("selectedSortOrder")?.optString("id").orEmpty(),
            total = lib.optInt("totalCount", shelves.size),
            offset = offset,
            fetched = items?.length() ?: 0,
        )
        log.i("page offset=$offset filter='$textFilter' order=${page.sort} -> " +
                "${shelves.size}/${page.fetched} of ${page.total} filters=$filters " +
                "kinds=${shelves.groupingBy { it.kind }.eachCount()}"
        )
        if (offset == 0) cache[key] = Cached(page, System.currentTimeMillis())
        return page
    }

    suspend fun setPinned(context: Context, uri: String, pinned: Boolean): Boolean {
        if (uri.isBlank()) return false
        val token = EncoreAuth.token(context)
        if (token.isBlank()) {
            log.w("setPinned: no token")
            return false
        }
        val operation = if (pinned) OP_PIN else OP_UNPIN
        val variables = JSONObject().put("pinnableItemUri", uri).toString()
        val json = Pathfinder.queryPost(context, token, operation, variables, fallbackToGet = false)
        val key = if (pinned) "pinItemInLibrary" else "unpinItemInLibrary"
        val result = json?.optJSONObject("data")?.optJSONObject(key)
        log.i("$operation '$uri' -> ${result?.optString("pinResult").orEmpty()}")
        val ok = result != null
        if (ok) invalidate()
        return ok
    }

    suspend fun folder(context: Context, uri: String): LibraryPage? {
        if (uri.isBlank()) return null
        val hash = FolderSource.folderHash(uri)
        if (hash.isBlank()) {
            log.w("folder: no hash in '$uri'")
            return null
        }
        val snap = Rootlist.snapshot(context)
        val start = snap?.groupIndex(hash, end = false) ?: -1
        val end = snap?.groupIndex(hash, end = true) ?: -1
        if (snap == null || start < 0 || end <= start) {
            log.w("folder $uri markers start=$start end=$end among ${snap?.entries?.size ?: 0} rootlist entries")
            return null
        }
        val inside = snap.entries.filter { it.index > start && it.index < end }
        val byUri = HashMap<String, ShelfItem>()
        val wanted = inside.mapTo(HashSet()) { it.uri }
        var offset = 0
        while (offset < PAGE * 4) {
            val flat = page(context, flatten = true, offset = offset) ?: break
            flat.items.forEach {
                if (it.uri.isNotBlank()) {
                    byUri[it.uri] = it
                    wanted.remove(it.uri)
                }
            }
            if (flat.fetched <= 0 || byUri.size >= flat.total || wanted.isEmpty()) break
            offset += flat.fetched
        }
        val items = inside.mapNotNull { entry -> byUri[entry.uri] }
        log.i("folder $uri -> ${items.size} of ${inside.size} row(s) from the rootlist, " +
                "flat page ${byUri.size} row(s), missing ${inside.size - items.size}"
        )
        return LibraryPage(
            items = items,
            filters = emptyList(),
            sort = "",
            total = items.size,
            offset = 0,
            fetched = items.size,
        )
    }

    private fun shelf(node: JSONObject?): ShelfItem? {
        if (node == null) return null
        val wrapper = node.optJSONObject("item") ?: return null
        val data = wrapper.optJSONObject("data") ?: return null
        val uri = data.optString("uri").ifBlank { wrapper.optString("_uri") }
        if (uri.isBlank()) return null
        val pinned = node.optBoolean("pinned", false)
        val pinnable = node.optBoolean("pinnable", false)
        val name = data.optString("name").plainText()
        return when (val type = data.optString("__typename")) {
            "PseudoPlaylist" -> ShelfItem(
                uri = uri,
                title = name,
                subtitle = "Playlist • ${data.optInt("count")} songs",
                kind = ShelfKind.LIKED,
                imageUrl = GraphQl.largestSource(data.optJSONObject("image")),
                colorHex = GraphQl.colorHex(data.optJSONObject("image")),
                pinned = pinned,
                pinnable = pinnable,
                count = data.optInt("count"),
            )
            "Playlist" -> ShelfItem(
                uri = uri,
                title = name,
                subtitle = "Playlist • " + (
                    data.optJSONObject("ownerV2")?.optJSONObject("data")?.optString("name").orEmpty()
                        .plainText().ifBlank { "Unknown" }
                    ),
                kind = ShelfKind.PLAYLIST,
                imageUrl = GraphQl.entityImage(data),
                colorHex = GraphQl.entityColor(data),
                pinned = pinned,
                pinnable = pinnable,
            )
            "Album" -> ShelfItem(
                uri = uri,
                title = name,
                subtitle = "Album • " + GraphQl.artistNames(data),
                kind = ShelfKind.ALBUM,
                imageUrl = GraphQl.entityImage(data),
                colorHex = GraphQl.entityColor(data),
                pinned = pinned,
                pinnable = pinnable,
            )
            "Artist" -> ShelfItem(
                uri = uri,
                title = name.ifBlank { data.optJSONObject("profile")?.optString("name").orEmpty() },
                subtitle = "Artist",
                kind = ShelfKind.ARTIST,
                imageUrl = GraphQl.entityImage(data),
                colorHex = GraphQl.entityColor(data),
                pinned = pinned,
                pinnable = pinnable,
            )
            "Folder" -> {
                val lists = data.optInt("playlistCount")
                val sub = data.optInt("folderCount")
                val parts = ArrayList<String>(2)
                if (lists > 0) parts += "$lists playlist" + if (lists == 1) "" else "s"
                if (sub > 0) parts += "$sub folder" + if (sub == 1) "" else "s"
                ShelfItem(
                    uri = uri,
                    title = name,
                    subtitle = if (parts.isEmpty()) "Folder" else parts.joinToString(", "),
                    kind = ShelfKind.FOLDER,
                    pinned = pinned,
                    pinnable = pinnable,
                    count = lists,
                )
            }
            "Show" -> ShelfItem(
                uri = uri,
                title = name,
                subtitle = "Podcast • " + data.optJSONObject("publisher")?.optString("name").orEmpty(),
                kind = ShelfKind.SHOW,
                imageUrl = GraphQl.entityImage(data),
                colorHex = GraphQl.entityColor(data),
                pinned = pinned,
                pinnable = pinnable,
            )
            else -> ShelfItem(
                uri = uri,
                title = name.ifBlank { type },
                subtitle = type,
                kind = ShelfKind.UNKNOWN,
                imageUrl = GraphQl.entityImage(data),
                colorHex = GraphQl.entityColor(data),
                pinned = pinned,
                pinnable = pinnable,
            )
        }
    }
}
