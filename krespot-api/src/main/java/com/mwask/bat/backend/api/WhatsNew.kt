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
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.net.GraphQl
import com.mwask.bat.backend.core.net.Pathfinder
import com.mwask.bat.backend.core.net.plainText
import com.mwask.bat.backend.model.WhatsNewFeed
import com.mwask.bat.backend.model.WhatsNewItem
import org.json.JSONArray
import org.json.JSONObject

private val log = Logs.of(Logs.Module.WHATS_NEW)

object WhatsNew {

    private const val OP_FEED = "queryWhatsNewFeed"
    private const val OP_NEW = "whatsNewFeedNewItems"
    private const val OP_SEEN = "SetItemsStateInWhatsNewFeed"
    private const val PAGE = 50
    private const val NEW_WINDOW_MS = 3_600_000L
    private const val TYPE_ALBUM = "ALBUM"
    private const val TYPE_EPISODE = "EPISODE"

    suspend fun feed(context: Context, types: List<String> = emptyList()): WhatsNewFeed? {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) {
            log.w("feed: no token")
            return null
        }
        val variables = JSONObject()
            .put("offset", 0)
            .put("limit", PAGE)
            .put("onlyUnPlayedItems", false)
            .put("includedContentTypes", JSONArray(types))
            .put("includeEpisodeContentRatingsV2", true)
            .toString()
        val data = Pathfinder.queryPost(context, token, OP_FEED, variables) ?: return null
        val holder = data.optJSONObject("data")?.optJSONObject("whatsNewFeedItems")
        if (holder == null) {
            log.w("feed: no whatsNewFeedItems in the answer")
            return null
        }
        val raw = holder.optJSONArray("items") ?: JSONArray()
        val items = (0 until raw.length()).mapNotNull { index -> parse(raw.optJSONObject(index)) }
        val paging = holder.optJSONObject("pagingInfo")
        val feed = WhatsNewFeed(
            items = items,
            total = holder.optInt("totalCount", items.size),
            nextOffset = paging?.optInt("nextOffset", -1)?.takeIf { it >= 0 },
            seen = items.filter { it.isNew }.map { it.id },
        )
        log.i("feed ${items.size} item(s) of ${feed.total} new=${feed.newCount} " +
                "types=${types.ifEmpty { listOf("all") }}"
        )
        return feed
    }

    suspend fun badgeCount(context: Context): Int {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) return 0
        if (!hasNew(context)) return 0
        val feed = feed(context) ?: return 0
        return feed.newCount
    }

    suspend fun hasNew(context: Context): Boolean {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) return false
        val data = Pathfinder.queryPost(context, token, OP_NEW, "{}") ?: return false
        return data.optJSONObject("data")?.optBoolean("whatsNewFeedHasNewItems", false) ?: false
    }

    suspend fun markSeen(context: Context, ids: List<String>): Boolean {
        if (ids.isEmpty()) return true
        val token = EncoreAuth.token(context)
        if (token.isBlank()) return false
        val items = JSONArray()
        ids.forEach { id -> items.put(JSONObject().put("id", id).put("state", "SEEN")) }
        val variables = JSONObject().put("items", items).toString()
        val answer = Pathfinder.queryPost(context, token, OP_SEEN, variables) ?: return false
        val errors = answer.optJSONArray("errors")?.length() ?: 0
        log.i("markSeen ${ids.size} item(s), errors=$errors")
        return errors == 0
    }

    private fun parse(item: JSONObject?): WhatsNewItem? {
        if (item == null) return null
        val id = item.optString("id")
        val state = item.optJSONObject("state")
        val content = item.optJSONObject("content") ?: return null
        val wrapper = content.optString("__typename")
        val data = content.optJSONObject("data") ?: return null
        val inner = data.optString("__typename")
        val isMusic = inner == "Album"
        if (!isMusic && inner != "Episode") {
            log.v("item $wrapper/$inner is not drawn")
            return null
        }
        val stamp = state?.optJSONObject("timestamp")?.optString("isoString").orEmpty()
        val title = data.optString("name").plainText()
        val subtitle = if (isMusic) {
            artistsOf(data)
        } else {
            data.optJSONObject("podcastV2")?.optJSONObject("data")?.optString("name")
                .orEmpty().plainText()
        }
        return WhatsNewItem(
            id = id,
            uri = data.optString("uri"),
            title = title,
            subtitle = subtitle,
            imageUrl = if (isMusic) {
                GraphQl.entityImage(data)
            } else {
                GraphQl.largestSource(data.optJSONObject("coverArt"))
            },
            timestamp = stamp,
            isNew = isNew(stamp, state?.optString("state").orEmpty()),
            isMusic = isMusic,
            kind = if (isMusic) TYPE_ALBUM else TYPE_EPISODE,
        )
    }

    private fun artistsOf(album: JSONObject): String {
        val items = album.optJSONObject("artists")?.optJSONArray("items") ?: return ""
        val names = (0 until items.length()).mapNotNull { index ->
            items.optJSONObject(index)?.optJSONObject("profile")?.optString("name")
                ?.plainText()?.takeIf { it.isNotBlank() }
        }
        return names.joinToString(", ")
    }

    private fun isNew(timestamp: String, state: String): Boolean {
        if (timestamp.isBlank()) return state == "NEW"
        val at = runCatching { java.time.Instant.parse(timestamp).toEpochMilli() }.getOrNull()
            ?: return false
        return System.currentTimeMillis() - at <= NEW_WINDOW_MS
    }
}
