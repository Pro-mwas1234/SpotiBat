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
 * You may view and audit this source code for personal and educational inspection.
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.mwask.bat.backend.api

import android.content.Context
import com.mwask.bat.backend.core.auth.EncoreAuth
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.net.ClientToken
import com.mwask.bat.backend.core.net.GraphQl
import com.mwask.bat.backend.core.net.Net
import com.mwask.bat.backend.core.net.Pathfinder
import com.mwask.bat.backend.model.ProfileArtist
import com.mwask.bat.backend.model.ProfileContext
import com.mwask.bat.backend.model.ProfilePlaylist
import com.mwask.bat.backend.model.ProfileTopContent
import com.mwask.bat.backend.model.ProfileTopTrack
import com.mwask.bat.backend.model.ProfileUser
import com.mwask.bat.backend.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject

private val log = Logs.of(Logs.Module.PROFILE)

object Profile {

    private const val BASE = "https://spclient.wg.spotify.com/user-profile-view/v3/profile"
    private const val RECENTLY_PLAYED = "https://spclient.wg.spotify.com/recently-played/v3/user"
    private const val OP_TOP_CONTENT = "userTopContent"
    private const val TOP_ARTIST_LIMIT = 10
    private const val TOP_TRACK_LIMIT = 5
    private const val RECENT_LIMIT = 12

    suspend fun profile(context: Context, userId: String = ""): UserProfile? {
        val id = normalizeUserId(userId).ifBlank { EncoreAuth.userId(context) }
        if (id.isBlank()) {
            log.w("profile: no user id")
            return null
        }
        val response = Net.get(
            "$BASE/$id?playlist_limit=10&artist_limit=10&episode_limit=10&market=from_token",
            headers(context),
            "profile/view",
        )
        if (response == null || !response.ok) {
            log.w("profile http=${response?.code ?: -1} ${response?.body?.take(160).orEmpty()}")
            return null
        }
        val root = response.json() ?: return null
        val artists = root.optJSONArray("recently_played_artists") ?: JSONArray()
        val parsed = (0 until artists.length()).mapNotNull { parseArtist(artists.optJSONObject(it)) }
        val playlists = root.optJSONArray("public_playlists") ?: JSONArray()
        val parsedPlaylists = (0 until playlists.length()).mapNotNull { parsePlaylist(playlists.optJSONObject(it)) }
        // The v3 endpoint only sends "is_current_user" sometimes (it is absent for
        // other users), so a missing field must never default to true — otherwise
        // someone else's profile would be treated as our own. The uri check makes
        // the decision reliable in both directions.
        val selfId = EncoreAuth.userId(context)
        val profileUri = root.optString("uri")
        val isSelf = root.optBoolean("is_current_user", false) ||
            (selfId.isNotBlank() && profileUri.endsWith(":$selfId"))
        val profile = UserProfile(
            uri = profileUri,
            name = root.optString("name"),
            followersCount = root.optLong("followers_count", 0L),
            followingCount = root.optLong("following_count", 0L),
            recentlyPlayedArtists = parsed,
            colorHex = colorHexOf(root.optInt("color", 0)),
            isCurrentUser = isSelf,
            avatarUrl = root.optString("image_url"),
            publicPlaylists = parsedPlaylists,
        )
        log.i("profile '${profile.name}' followers=${profile.followersCount} " +
                "following=${profile.followingCount} artists=${parsed.size} " +
                "playlists=${parsedPlaylists.size} self=${profile.isCurrentUser}"
        )
        return profile
    }

    suspend fun following(context: Context, userId: String = ""): List<ProfileUser>? =
        people(context, "following", userId)

    suspend fun followers(context: Context, userId: String = ""): List<ProfileUser>? =
        people(context, "followers", userId)

    /**
     * Top artists/tracks of a profile. Without [userUri] the persisted query
     * answers for the signed-in user (`me`); with it we ask for another user's
     * public top content — when that user keeps it private (or the query has
     * nothing to say about them) this returns null and the caller should say so
     * instead of falling back to our own data.
     */
    suspend fun topContent(context: Context, userUri: String = ""): ProfileTopContent? {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) {
            log.w("topContent: no token")
            return null
        }
        val target = userUri.trim().takeIf { it.isNotBlank() }
            ?.let { uri ->
                if (uri.startsWith("spotify:user:")) uri else "spotify:user:$uri"
            }
        val variables = JSONObject()
            .put(
                "topArtistsInput",
                JSONObject()
                    .put("offset", 0)
                    .put("limit", TOP_ARTIST_LIMIT)
                    .put("sortBy", "AFFINITY")
                    .put("timeRange", "SHORT_TERM"),
            )
            .put(
                "topTracksInput",
                JSONObject()
                    .put("offset", 0)
                    .put("limit", TOP_TRACK_LIMIT)
                    .put("sortBy", "AFFINITY")
                    .put("timeRange", "SHORT_TERM"),
            )
            .put("includeTopArtists", true)
            .put("includeTopTracks", true)
        if (target != null) {
            variables.put("userUri", target)
        }
        val data = Pathfinder.queryPost(context, token, OP_TOP_CONTENT, variables.toString()) ?: return null
        val dataNode = data.optJSONObject("data") ?: return null
        val profile = if (target == null) {
            dataNode.optJSONObject("me")?.optJSONObject("profile")
        } else {
            dataNode.optJSONObject("user")?.optJSONObject("profile")
                ?: dataNode.optJSONObject("user")
        }
        if (profile == null) {
            log.w("topContent: no profile node${if (target != null) " for $target" else ""}")
            return null
        }
        val artists = profile.optJSONObject("topArtists")?.optJSONArray("items") ?: JSONArray()
        val tracks = profile.optJSONObject("topTracks")?.optJSONArray("items") ?: JSONArray()
        val topArtists = (0 until artists.length()).mapNotNull { parseTopArtist(artists.optJSONObject(it)) }
        val topTracks = (0 until tracks.length()).mapNotNull { parseTopTrack(tracks.optJSONObject(it)) }
        if (topArtists.isEmpty() && topTracks.isEmpty()) {
            log.w("topContent: no items")
            return null
        }
        log.i("topContent artists=${topArtists.size} tracks=${topTracks.size}")
        return ProfileTopContent(topArtists = topArtists, topTracks = topTracks)
    }

    suspend fun recentlyPlayed(context: Context): List<ProfileContext>? {
        val userId = EncoreAuth.userId(context)
        if (userId.isBlank()) {
            log.w("recentlyPlayed: no user id")
            return null
        }
        val response = Net.get(
            "$RECENTLY_PLAYED/$userId/recently-played" +
                "?format=json&offset=0&limit=$RECENT_LIMIT" +
                "&filter=default%2Ccollection-new-episodes&market=from_token",
            headers(context),
            "profile/recently-played",
        )
        if (response == null || !response.ok) {
            log.w("recentlyPlayed http=${response?.code ?: -1} ${response?.body?.take(160).orEmpty()}")
            return null
        }
        val root = response.json() ?: return null
        val contexts = root.optJSONArray("playContexts") ?: return emptyList()
        val parsed = (0 until contexts.length()).mapNotNull { index ->
            val node = contexts.optJSONObject(index) ?: return@mapNotNull null
            val uri = node.optString("uri")
            if (uri.isBlank()) return@mapNotNull null
            ProfileContext(uri = uri, lastPlayedTimeMs = node.optLong("lastPlayedTime", 0L))
        }.distinctBy { it.uri }
        log.i("recentlyPlayed ${parsed.size} context(s)")
        return parsed
    }

    private suspend fun people(context: Context, kind: String, userId: String): List<ProfileUser>? {
        val id = normalizeUserId(userId).ifBlank { EncoreAuth.userId(context) }
        if (id.isBlank()) {
            log.w("$kind: no user id")
            return null
        }
        val response = Net.get(
            "$BASE/$id/$kind?market=from_token",
            headers(context),
            "profile/$kind",
        )
        if (response == null || !response.ok) {
            log.w("$kind http=${response?.code ?: -1} ${response?.body?.take(160).orEmpty()}")
            return null
        }
        val root = response.json() ?: return null
        val profiles = root.optJSONArray("profiles") ?: return emptyList()
        val parsed = (0 until profiles.length()).mapNotNull { parsePerson(profiles.optJSONObject(it)) }
        log.i("$kind ${parsed.size} profile(s)")
        return parsed
    }

    private fun parseArtist(node: JSONObject?): ProfileArtist? {
        if (node == null) return null
        val uri = node.optString("uri")
        val name = node.optString("name")
        if (uri.isBlank() || name.isBlank()) return null
        return ProfileArtist(
            uri = uri,
            name = name,
            imageUrl = node.optString("image_url"),
            followersCount = node.optLong("followers_count", 0L),
            isFollowing = node.optBoolean("is_following", false),
        )
    }

    private fun parseTopArtist(item: JSONObject?): ProfileArtist? {
        val data = item?.optJSONObject("data") ?: return null
        val uri = data.optString("uri")
        val name = data.optJSONObject("profile")?.optString("name").orEmpty()
        if (uri.isBlank() || name.isBlank()) return null
        return ProfileArtist(
            uri = uri,
            name = name,
            imageUrl = GraphQl.entityImage(data),
        )
    }

    private fun parseTopTrack(item: JSONObject?): ProfileTopTrack? {
        val data = item?.optJSONObject("data") ?: return null
        val uri = data.optString("uri")
        val title = data.optString("name")
        if (uri.isBlank() || title.isBlank()) return null
        val album = data.optJSONObject("albumOfTrack")
        return ProfileTopTrack(
            uri = uri,
            title = title,
            artists = GraphQl.artistNames(data),
            artistUri = GraphQl.artistUri(data),
            albumUri = album?.optString("uri").orEmpty(),
            albumName = album?.optString("name").orEmpty(),
            imageUrl = GraphQl.largestSource(album?.optJSONObject("coverArt")),
            durationMs = data.optJSONObject("duration")?.optLong("totalMilliseconds", 0L) ?: 0L,
            explicit = data.optJSONObject("contentRating")?.optString("label") == "EXPLICIT",
        )
    }

    private fun parsePlaylist(node: JSONObject?): ProfilePlaylist? {
        if (node == null) return null
        val uri = node.optString("uri")
        val name = node.optString("name")
        if (uri.isBlank() || name.isBlank()) return null
        val image = node.optString("image_url").ifBlank {
            node.optJSONArray("images")?.optJSONObject(0)?.optString("url").orEmpty()
        }
        return ProfilePlaylist(uri = uri, name = name, imageUrl = image)
    }

    private fun parsePerson(node: JSONObject?): ProfileUser? {
        if (node == null) return null
        val uri = node.optString("uri")
        val name = node.optString("name")
        if (uri.isBlank() || name.isBlank()) return null
        return ProfileUser(
            uri = uri,
            name = name,
            imageUrl = node.optString("image_url"),
            followersCount = node.optLong("followers_count", 0L),
            colorHex = colorHexOf(node.optInt("color", 0)),
        )
    }

    private fun normalizeUserId(value: String): String {
        val id = value.trim().removePrefix("spotify:user:").substringBefore(':')
        return id.takeUnless { it.isBlank() || it == "me" }.orEmpty()
    }

    private fun colorHexOf(color: Int): String =
        if (color != 0) "%06X".format(color).let { "#$it" } else ""

    private suspend fun headers(context: Context): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${EncoreAuth.token(context)}")
        put("Accept", "application/json")
        put("accept-language", "en")
        put("app-platform", "WebPlayer")
        put("User-Agent", Net.DESKTOP_UA)
        ClientToken.get(context).takeIf { it.isNotBlank() }?.let { put("client-token", it) }
    }
}
