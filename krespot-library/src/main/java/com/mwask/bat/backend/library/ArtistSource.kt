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
import com.mwask.bat.backend.core.net.ClientToken
import com.mwask.bat.backend.core.net.GraphQl
import com.mwask.bat.backend.core.net.Net
import com.mwask.bat.backend.core.net.Pathfinder
import com.mwask.bat.backend.core.net.plainText
import com.mwask.bat.backend.model.ArtistDetail
import com.mwask.bat.backend.model.ArtistLink
import com.mwask.bat.backend.model.CityStat
import com.mwask.bat.backend.model.ConcertItem
import com.mwask.bat.backend.model.ReleasePage
import com.mwask.bat.backend.model.ShelfItem
import com.mwask.bat.backend.model.TrackRow
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mwask.bat.backend.core.net.plainText

private val log = Logs.of(Logs.Module.ARTIST)

object ArtistSource {

    private const val OP_ARTIST = "queryArtistOverview"
    private const val OP_DISCOGRAPHY_COUNTS = "queryArtistDiscographyOverview"
    private const val OP_DISCOVERED_ON = "queryArtistDiscoveredOn"

    private const val SPCLIENT = "https://spclient.wg.spotify.com"
    private const val DISCOGRAPHY_PAGE = 20

    suspend fun artist(context: Context, uri: String): ArtistDetail? {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) return null
        val variables = JSONObject()
            .put("uri", uri)
            .put("locale", "")
            .put("preReleaseV2", true)
            .toString()
        return withContext(Dispatchers.Default) {
            val json = Pathfinder.queryPost(context, token, OP_ARTIST, variables) ?: return@withContext null
            val union = json.optJSONObject("data")?.optJSONObject("artistUnion") ?: return@withContext null
            val profile = union.optJSONObject("profile")
            val discography = union.optJSONObject("discography")
            val top = discography?.optJSONObject("topTracks")?.optJSONArray("items")
            val rows = ArrayList<TrackRow>(top?.length() ?: 0)
            for (i in 0 until (top?.length() ?: 0)) {
                val item = top?.optJSONObject(i) ?: continue
                val trackNode = item.optJSONObject("track") ?: item
                val row = track(trackNode, "") ?: continue
                val playcount = item.optString("playcount").ifBlank {
                    item.optLong("playcount", 0L).takeIf { it > 0L }?.toString()
                } ?: trackNode.optString("playcount").ifBlank {
                    trackNode.optLong("playcount", 0L).takeIf { it > 0L }?.toString()
                }
                val plays = playcount?.toLongOrNull() ?: row.plays
                rows.add(if (row.plays == 0L && plays > 0L) row.copy(plays = plays) else row)
            }
            val stats = union.optJSONObject("stats")
            val related = union.optJSONObject("relatedContent")
            val pinnedNode = profile?.optJSONObject("pinnedItem")
            val discovered = related?.optJSONObject("discoveredOnV2")
                ?.let { discoveredShelf(context, token, uri) }
                .orEmpty()
            val detail = ArtistDetail(
                uri = uri,
                name = profile?.optString("name").orEmpty().plainText(),
                imageUrl = GraphQl.entityImage(union.optJSONObject("visuals")),
                headerImageUrl = GraphQl.headerImage(union),
                gallery = GraphQl.gallery(union),
                colorHex = GraphQl.visualColor(union).ifBlank {
                    GraphQl.colorHex(union.optJSONObject("visuals")?.optJSONObject("avatarImage"))
                },
                monthlyListeners = stats?.optLong("monthlyListeners", 0L) ?: 0L,
                followers = stats?.optLong("followers", 0L) ?: 0L,
                biography = profile?.optJSONObject("biography")?.optString("text").orEmpty().plainText(),
                verified = union.optJSONObject("onPlatformReputationTrait")
                    ?.optJSONObject("verification")
                    ?.optBoolean("isVerified", false) == true ||
                    profile?.optBoolean("verified", false) == true,
                saved = union.optBoolean("saved", false),
                topTracks = withSaved(context, rows),
                releases = releases(discography),
                pinned = pinned(pinnedNode),
                pinnedComment = pinnedNode?.optString("comment").orEmpty().plainText(),
                albums = releaseGroup(discography?.optJSONObject("albums")),
                singles = releaseGroup(discography?.optJSONObject("singles")),
                compilations = releaseGroup(discography?.optJSONObject("compilations")),
                featuring = dataShelf(related?.optJSONObject("featuringV2")),
                appearsOn = releaseGroup(related?.optJSONObject("appearsOn")),
                artistPlaylists = dataShelf(profile?.optJSONObject("playlistsV2")),
                relatedArtists = artistNodes(related?.optJSONObject("relatedArtists")),
                videos = videoShelf(union.optJSONObject("relatedMusicVideos")),
                topCities = stats?.optJSONObject("topCities")?.optJSONArray("items")?.let { cities ->
                    (0 until cities.length()).mapNotNull { i ->
                        val node = cities.optJSONObject(i) ?: return@mapNotNull null
                        val city = node.optString("city").plainText()
                        if (city.isBlank()) null
                        else CityStat(city, node.optLong("numberOfListeners", 0L))
                    }
                }.orEmpty(),
                worldRank = stats?.optInt("worldRank", 0) ?: 0,
                merch = merch(union.optJSONObject("goods")?.optJSONObject("merch")),
                externalLinks = links(profile?.optJSONObject("externalLinks")),
                discoveredOn = discovered,
                concerts = concerts(union.optJSONObject("goods")?.optJSONObject("concerts")),
            )
            log.i("artist $uri '${detail.name}' top=${detail.topTracks.size} releases=${detail.releases.size} " +
                    "listeners=${detail.monthlyListeners} followers=${detail.followers} " +
                    "banner=${detail.headerImageUrl.isNotBlank()} gallery=${detail.gallery.size} " +
                    "verified=${detail.verified} saved=${detail.saved} " +
                    "albums=${detail.albums.size} singles=${detail.singles.size} " +
                    "compilations=${detail.compilations.size} featuring=${detail.featuring.size} " +
                    "appearsOn=${detail.appearsOn.size} playlists=${detail.artistPlaylists.size} " +
                    "related=${detail.relatedArtists.size} videos=${detail.videos.size} " +
                    "pinned=${detail.pinned != null} rank=${detail.worldRank} " +
                    "bio=${detail.biography.length} cities=${detail.topCities.size} " +
                    "merch=${detail.merch.size} links=${detail.externalLinks.size} " +
                    "discovered=${detail.discoveredOn.size} concerts=${detail.concerts.size}"
            )
            detail
        }
    }

    suspend fun discographyCounts(context: Context, uri: String): Map<String, Int> {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) return emptyMap()
        val variables = JSONObject().put("uri", uri).toString()
        val json = Pathfinder.queryPost(context, token, OP_DISCOGRAPHY_COUNTS, variables) ?: return emptyMap()
        val node = json.optJSONObject("data")?.optJSONObject("artistUnion")?.optJSONObject("discography")
            ?: return emptyMap()
        val out = LinkedHashMap<String, Int>()
        listOf("all", "albums", "singles", "compilations").forEach { key ->
            node.optJSONObject(key)?.let { out[key] = it.optInt("totalCount", 0) }
        }
        log.i("discography counts $uri $out")
        return out
    }

    suspend fun discography(
        context: Context,
        uri: String,
        section: String,
        offset: Int = 0,
        order: String = "DATE_DESC",
        limit: Int = DISCOGRAPHY_PAGE,
    ): ReleasePage? {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) return null
        val operation = when (section) {
            "albums" -> "queryArtistDiscographyAlbums"
            "singles" -> "queryArtistDiscographySingles"
            "compilations" -> "queryArtistDiscographyCompilations"
            else -> "queryArtistDiscographyAll"
        }
        val variables = JSONObject()
            .put("uri", uri)
            .put("offset", offset)
            .put("limit", limit)
            .put("order", order)
            .toString()
        val json = Pathfinder.queryPost(context, token, operation, variables) ?: return null
        val node = json.optJSONObject("data")?.optJSONObject("artistUnion")
            ?.optJSONObject("discography")?.optJSONObject(section) ?: return null
        val items = node.optJSONArray("items") ?: JSONArray()
        val rows = ArrayList<ShelfItem>(items.length())
        for (i in 0 until items.length()) {
            val nested = items.optJSONObject(i)?.optJSONObject("releases")?.optJSONArray("items") ?: continue
            release(nested.optJSONObject(0))?.let(rows::add)
        }
        val page = ReleasePage(rows, node.optInt("totalCount", 0), offset, items.length())
        log.i("discography $uri $section order=$order offset=$offset -> ${rows.size}/${page.total} " +
                "hasMore=${page.hasMore}"
        )
        return page
    }

    suspend fun artistShelf(
        context: Context,
        uri: String,
        section: String,
        offset: Int = 0,
        limit: Int = DISCOGRAPHY_PAGE,
    ): ReleasePage? {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) return null
        val operation = when (section) {
            "featuring" -> "queryArtistFeaturing"
            "appearsOn" -> "queryArtistAppearsOn"
            "playlists" -> "queryArtistPlaylists"
            "related" -> "queryArtistRelated"
            else -> return null
        }
        val variables = JSONObject()
            .put("uri", uri)
            .put("offset", offset)
            .put("limit", limit)
            .toString()
        val json = Pathfinder.queryPost(context, token, operation, variables) ?: return null
        val union = json.optJSONObject("data")?.optJSONObject("artistUnion") ?: return null
        val node = when (section) {
            "playlists" -> union.optJSONObject("profile")?.optJSONObject("playlistsV2")
            "featuring" -> union.optJSONObject("relatedContent")?.optJSONObject("featuringV2")
            "appearsOn" -> union.optJSONObject("relatedContent")?.optJSONObject("appearsOn")
            else -> union.optJSONObject("relatedContent")?.optJSONObject("relatedArtists")
        } ?: return null
        val fetched = node.optJSONArray("items")?.length() ?: 0
        val rows = when (section) {
            "appearsOn" -> releaseGroup(node, fetched)
            "related" -> artistNodes(node, fetched)
            else -> dataShelf(node, fetched)
        }
        val reported = node.optInt("totalCount", rows.size)
        val total = if (section == "appearsOn") rows.size else reported
        val page = ReleasePage(rows, total, offset, fetched)
        log.i("artistShelf $uri $section offset=$offset -> ${rows.size} of ${node.optInt("totalCount", 0)} " +
                "hasMore=${page.hasMore}"
        )
        return page
    }

    suspend fun artistRadio(context: Context, uri: String): String? {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) return null
        val url = "$SPCLIENT/inspiredby-mix/v2/seed_to_playlist/$uri?response-format=json"
        val response = Net.get(url, spHeaders(context), "artist/radio") ?: return null
        if (!response.ok) return null
        val items = response.json()?.optJSONArray("mediaItems") ?: return null
        val first = items.optJSONObject(0)?.optString("uri").orEmpty()
        log.i("artist radio $uri -> '$first' (${items.length()} items)")
        return first.takeIf { it.startsWith("spotify:") }
    }

    suspend fun popularTrackUris(context: Context, uri: String): List<String> {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) return emptyList()
        val id = uri.removePrefix("spotify:artist:")
        if (id.isBlank()) return emptyList()
        val url = "$SPCLIENT/playlist/v2/list/popular-release-segments-main-roles/$id" +
            "?decorate=revision%2Clength%2Cattributes%2Ctimestamp%2Cowner%2Ccapabilities"
        val response = Net.get(url, spHeaders(context), "artist/popular") ?: return emptyList()
        if (!response.ok) return emptyList()
        val items = response.json()?.optJSONObject("contents")?.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<String>(items.length())
        for (i in 0 until items.length()) {
            val trackUri = items.optJSONObject(i)?.optString("uri").orEmpty()
            if (trackUri.startsWith("spotify:track:")) out += trackUri
        }
        log.i("popular segments $uri -> ${out.size} track(s)")
        return out
    }

    private suspend fun spHeaders(context: Context): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${EncoreAuth.token(context)}")
        put("Accept", "application/json")
        put("app-platform", "WebPlayer")
        put("User-Agent", Net.DESKTOP_UA)
        ClientToken.get(context).takeIf { it.isNotBlank() }?.let { put("client-token", it) }
    }

    private suspend fun discoveredShelf(context: Context, token: String, uri: String): List<ShelfItem> {
        val variables = JSONObject().put("uri", uri).toString()
        val json = Pathfinder.queryPost(context, token, OP_DISCOVERED_ON, variables) ?: return emptyList()
        val node = json.optJSONObject("data")?.optJSONObject("artistUnion")
            ?.optJSONObject("relatedContent")?.optJSONObject("discoveredOnV2") ?: return emptyList()
        val items = dataShelf(node)
        log.i("discoveredOn $uri -> ${items.size} playlists of ${node.optJSONArray("items")?.length() ?: 0}")
        return items
    }

    private fun concerts(node: JSONObject?, limit: Int = 10): List<ConcertItem> {
        val items = node?.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<ConcertItem>(limit)
        for (i in 0 until items.length()) {
            if (out.size >= limit) break
            val item = items.optJSONObject(i) ?: continue
            val data = item.optJSONObject("data") ?: item
            val title = data.optString("title").plainText()
            val iso = data.optString("startDateIsoString").plainText()
            if (title.isBlank() || iso.length < 10) continue
            val location = data.optJSONObject("location")
            out += ConcertItem(
                title = title,
                venue = location?.optString("name").orEmpty().plainText(),
                city = location?.optString("city").orEmpty().plainText(),
                dateLabel = formatDate(iso.take(10), "DAY"),
                festival = data.optBoolean("festival", false),
                uri = data.optString("uri").orEmpty(),
            )
        }
        return out
    }

    private fun links(node: JSONObject?): List<ArtistLink> {
        val items = node?.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<ArtistLink>(items.length())
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val url = item.optString("url").plainText()
            if (!url.startsWith("http")) continue
            val name = item.optString("name").plainText().ifBlank { "Link" }
            out += ArtistLink(name = name, url = url)
        }
        return out
    }
}
