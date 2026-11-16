/*
 *  _   __                          _   
 * | | / /                         | |  
 * | |/ / _ __ ___  ___ _ __   ___ | |_ 
 * |    \| '__/ _ \/ __/ '_ \ / _ \| __|
 * |\  \ | |  __/\__ \ |_) | (_) | |_ 
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
import com.mwask.bat.backend.core.net.ClientToken
import com.mwask.bat.backend.core.net.Net
import com.mwask.bat.backend.core.net.Pathfinder
import com.mwask.bat.backend.model.ActivityFeed
import com.mwask.bat.backend.model.ActivityNotification
import com.mwask.bat.backend.model.SelfProfile
import org.json.JSONArray
import org.json.JSONObject

private val log = Logs.of(Logs.Module.NOTIFICATIONS)

object Notifications {

    private const val GANDER = "https://spclient.wg.spotify.com/gander/v2"
    private const val OP_PROFILE_ATTRIBUTES = "profileAttributes"
    private const val LIMIT = 20
    private const val APP_VERSION = "9.1.92.3058"

    /** Latest entries of the feed; null means the call failed. */
    suspend fun feed(context: Context): ActivityFeed? {
        val response = Net.get(
            "$GANDER/GetNotifications?locale=&limit=$LIMIT",
            headers(context),
            "notifications/feed",
        )
        if (response == null || !response.ok) {
            log.w("feed http=${response?.code ?: -1} ${response?.body?.take(160).orEmpty()}")
            return null
        }
        val root = response.json() ?: return null
        val raw = root.optJSONArray("notifications") ?: JSONArray()
        val items = (0 until raw.length()).mapNotNull { index -> parse(raw.optJSONObject(index)) }
        val feed = ActivityFeed(
            items = items,
            latestCursor = root.optString("latestCursor"),
        )
        log.i("feed ${items.size} item(s) new=${items.count { it.isNew }} " +
                "cursor=${feed.latestCursor.ifBlank { "-" }}"
        )
        return feed
    }

    /** True when the user has notifications they have not opened yet. */
    suspend fun hasUnread(context: Context): Boolean {
        val response = Net.get(
            "$GANDER/GetUserHasUnreadNotification?postFix=a",
            headers(context),
            "notifications/hasUnread",
        )
        if (response == null || !response.ok) {
            log.w("hasUnread http=${response?.code ?: -1} ${response?.body?.take(160).orEmpty()}")
            return false
        }
        val unread = response.json()?.optBoolean("userHasUnreadNotification", false) ?: false
        log.i("hasUnread -> $unread")
        return unread
    }

    /** Marks everything up to [cursor] as seen; called when the user leaves the feed. */
    suspend fun resetCursor(context: Context, cursor: String): Boolean {
        if (cursor.isBlank()) {
            log.w("resetCursor: no cursor")
            return false
        }
        val response = Net.postJson(
            "$GANDER/ResetLatestCursor",
            JSONObject().put("cursor", cursor).toString(),
            headers(context) + mapOf("Content-Type" to "application/json;charset=UTF-8"),
            "notifications/resetCursor",
        )
        val ok = response?.ok == true
        log.i("resetCursor cursor=$cursor ok=$ok http=${response?.code ?: -1} " +
                response?.body?.take(120).orEmpty()
        )
        return ok
    }

    /** The current user's own profile basics from the profileAttributes query. */
    suspend fun selfProfile(context: Context): SelfProfile? {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) {
            log.w("selfProfile: no token")
            return null
        }
        val data = Pathfinder.queryPost(context, token, OP_PROFILE_ATTRIBUTES, "{}") ?: return null
        val profile = data.optJSONObject("data")?.optJSONObject("me")?.optJSONObject("profile")
        if (profile == null) {
            log.w("selfProfile: no data.me.profile")
            return null
        }
        val parsed = SelfProfile(
            accountId = profile.optString("accountId"),
            name = profile.optString("name"),
            username = profile.optString("username"),
            uri = profile.optString("uri"),
            avatarUrl = profile.optString("avatar"),
            avatarBackgroundColor = profile.optInt("avatarBackgroundColor", 0),
            socialHandle = profile.optString("socialHandle"),
        )
        log.i("selfProfile '${parsed.name}' account=${parsed.accountId} avatar=" +
                parsed.avatarUrl.take(32).ifBlank { "-" }
        )
        return parsed
    }

    private fun parse(node: JSONObject?): ActivityNotification? {
        if (node == null) return null
        val id = node.optString("id")
        val title = node.optString("title")
        if (id.isBlank() || title.isBlank()) return null
        val action = node.optJSONObject("action")
        val images = node.optJSONObject("userAndEntityImage")
        val actors = ArrayList<String>(2)
        images?.optJSONObject("userImage")?.optString("userDisplayName")
            ?.takeIf { it.isNotBlank() }?.let(actors::add)
        node.optJSONObject("multiUserImage")?.optJSONArray("userImages")?.let { users ->
            (0 until users.length()).forEach { index ->
                users.optJSONObject(index)?.optString("userDisplayName")
                    ?.takeIf { it.isNotBlank() }?.let(actors::add)
            }
        }
        val entity = images?.optJSONObject("entityImage")
        return ActivityNotification(
            id = id,
            title = title,
            createdTimestamp = node.optString("createdTimestamp"),
            actionUri = action?.optString("uri").orEmpty()
                .ifBlank { entity?.optString("entityUrl").orEmpty() },
            actionType = action?.optString("type").orEmpty(),
            actorNames = actors,
            entityImageUrl = entity?.optString("imageUrl").orEmpty(),
            entityUri = entity?.optString("entityUrl").orEmpty(),
            isNew = node.optBoolean("isNew", false),
        )
    }

    private suspend fun headers(context: Context): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${EncoreAuth.token(context)}")
        put("Accept", "application/json")
        put("accept-language", "en")
        put("app-platform", "WebPlayer")
        put("spotify-app-version", APP_VERSION)
        put("Origin", "https://open.spotify.com")
        put("Referer", "https://open.spotify.com/")
        put("User-Agent", Net.DESKTOP_UA)
        ClientToken.get(context).takeIf { it.isNotBlank() }?.let { put("client-token", it) }
    }
}
