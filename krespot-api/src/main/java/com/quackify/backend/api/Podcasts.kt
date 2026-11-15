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
import com.quackify.backend.model.*
import org.json.JSONObject
import com.quackify.backend.core.net.ClientToken
import com.quackify.backend.core.net.GraphQl
import com.quackify.backend.core.net.Net
import com.quackify.backend.core.net.Pathfinder
import com.quackify.backend.core.net.plainText

private val log = Logs.of(Logs.Module.PODCASTS)

object Podcasts {

    private const val OP_SHOW = "queryShowMetadataV2"
    private const val OP_EPISODES = "queryPodcastEpisodes"
    private const val OP_EPISODE = "getEpisodeOrChapter"
    private const val OP_RELATED_SHOWS = "internalLinkRecommenderShow"
    private const val PAGE = 50

    suspend fun show(context: Context, uri: String): ShowDetail? {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) {
            log.w("show: no token")
            return null
        }
        val union = showUnion(context, token, uri) ?: return null
        val page = episodesPage(context, token, uri, 0, PAGE)
        val rows = page?.rows.orEmpty().distinctBy { it.uri }
        val related = relatedShows(context, token, uri)
        val averageRating = union.optJSONObject("rating")?.optJSONObject("averageRating")
        val detail = ShowDetail(
            uri = uri,
            name = union.optString("name").plainText(),
            publisher = union.optJSONObject("publisher")?.optString("name").orEmpty().plainText(),
            description = union.optString("description").plainText(),
            imageUrl = GraphQl.entityImage(union),
            colorHex = GraphQl.visualColor(union).ifBlank { GraphQl.entityColor(union) },
            rating = averageRating?.optDouble("average", 0.0) ?: 0.0,
            ratingCount = averageRating?.optInt("totalRatings", 0) ?: 0,
            saved = union.optBoolean("saved", false),
            mediaType = union.optString("mediaType").plainText().ifBlank { "AUDIO" },
            topics = showTopics(union),
            episodes = rows,
            total = page?.total ?: 0,
            offset = 0,
            fetched = rows.size,
            nextOffset = page?.nextOffset,
            related = related,
        )
        log.i("show $uri '${detail.name}' by '${detail.publisher}' episodes=${detail.fetched}/${detail.total} " +
                "related=${related.size} rating=%.2f(%d) saved=%s media=%s".format(
                    detail.rating,
                    detail.ratingCount,
                    detail.saved,
                    detail.mediaType,
                )
        )
        return detail
    }

    suspend fun episodesMore(context: Context, uri: String, offset: Int): EpisodePage? {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) return null
        return episodesPage(context, token, uri, offset, PAGE)
    }

    suspend fun episode(context: Context, uri: String): EpisodeDetail? {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) {
            log.w("episode: no token")
            return null
        }
        val variables = JSONObject()
            .put("uri", uri)
            .put("includeEpisodeContentRatingsV2", true)
            .toString()
        val union = Pathfinder.queryPost(context, token, OP_EPISODE, variables)
            ?.optJSONObject("data")
            ?.optJSONObject("episodeUnionV2")
            ?: run {
                log.w("episode: no payload for $uri")
                return null
            }
        val show = union.optJSONObject("podcastV2")?.optJSONObject("data")
        val played = union.optJSONObject("playedState")
        val detail = EpisodeDetail(
            uri = uri,
            title = union.optString("name").plainText(),
            description = union.optString("description").plainText(),
            showName = show?.optString("name").orEmpty().plainText(),
            showUri = show?.optString("uri").orEmpty(),
            imageUrl = GraphQl.entityImage(union),
            wideImageUrl = wideImage(union),
            colorHex = GraphQl.visualColor(union).ifBlank { GraphQl.entityColor(union) },
            durationMs = union.optJSONObject("duration")?.optLong("totalMilliseconds", 0L) ?: 0L,
            releaseDate = releaseLabel(union),
            explicit = union.optJSONObject("contentRating")?.optString("label")
                .orEmpty().plainText() == "EXPLICIT",
            playable = union.optJSONObject("playability")?.optBoolean("playable", true) ?: true,
            video = mediaTypes(union).contains("VIDEO"),
            played = played?.optString("state").orEmpty() == "COMPLETED",
            positionMs = played?.optLong("playPositionMilliseconds", 0L) ?: 0L,
            shareUrl = union.optJSONObject("sharingInfo")?.optString("shareUrl").orEmpty(),
        )
        log.i("episode $uri '${detail.title}' show='${detail.showName}' ${detail.releaseDate} " +
                "${detail.durationMs}ms video=${detail.video} played=${detail.played} " +
                "position=${detail.positionMs} playable=${detail.playable}"
        )
        return detail
    }

    private suspend fun showUnion(context: Context, token: String, uri: String): JSONObject? {
        val variables = JSONObject()
            .put("uri", uri)
            .put("includeContentCapabilityTrait", true)
            .put("includeEpisodeContentRatingsV2", true)
            .toString()
        return Pathfinder.queryPost(context, token, OP_SHOW, variables)
            ?.optJSONObject("data")
            ?.optJSONObject("podcastUnionV2")
    }

    private suspend fun episodesPage(
        context: Context,
        token: String,
        uri: String,
        offset: Int,
        limit: Int,
    ): EpisodePage? {
        val variables = JSONObject()
            .put("uri", uri)
            .put("offset", offset)
            .put("limit", limit)
            .put("includeEpisodeContentRatingsV2", true)
            .toString()
        val union = Pathfinder.queryPost(context, token, OP_EPISODES, variables)
            ?.optJSONObject("data")
            ?.optJSONObject("podcastUnionV2")
            ?: return null
        val node = union.optJSONObject("episodesV2")
        val items = node?.optJSONArray("items")
        val rows = ArrayList<EpisodeRow>(items?.length() ?: 0)
        for (i in 0 until (items?.length() ?: 0)) {
            val item = items?.optJSONObject(i) ?: continue
            val data = item.optJSONObject("entity")?.optJSONObject("data")
                ?: item.optJSONObject("data")
                ?: continue
            row(data, union)?.let(rows::add)
        }
        val page = EpisodePage(
            rows = rows,
            total = node?.optInt("totalCount", 0) ?: 0,
            offset = offset,
            fetched = rows.size,
            nextOffset = node?.optJSONObject("pagingInfo")
                ?.optInt("nextOffset", -1)
                ?.takeIf { it > offset },
        )
        log.i("episodes $uri offset=$offset -> ${page.fetched}/${page.total} next=${page.nextOffset ?: "none"}"
        )
        return page
    }

    private fun showTopics(union: JSONObject): List<String> {
        val items = union.optJSONObject("topics")?.optJSONArray("items") ?: return emptyList()
        val out = LinkedHashSet<String>()
        for (i in 0 until items.length()) {
            val title = items.optJSONObject(i)?.optString("title").orEmpty().plainText()
            if (title.isNotBlank()) out += title
        }
        return out.toList()
    }

    private suspend fun relatedShows(context: Context, token: String, uri: String): List<ShowCard> {
        val variables = JSONObject().put("uri", uri).toString()
        val data = Pathfinder.queryPost(context, token, OP_RELATED_SHOWS, variables)
            ?.optJSONObject("data")
            ?: return emptyList()
        val node = data.optJSONObject("seoRecommendedPodcast")
            ?: data.optJSONObject("internalLinkRecommenderShow")
            ?: return emptyList()
        val items = node.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<ShowCard>(items.length())
        for (i in 0 until items.length()) {
            val show = items.optJSONObject(i)?.optJSONObject("data") ?: continue
            val cardUri = show.optString("uri")
            if (cardUri.isBlank()) continue
            out += ShowCard(
                uri = cardUri,
                name = show.optString("name").plainText(),
                publisher = show.optJSONObject("publisher")?.optString("name").orEmpty().plainText(),
                imageUrl = GraphQl.entityImage(show),
            )
        }
        return out
    }

    private fun row(episode: JSONObject, show: JSONObject?): EpisodeRow? {
        val uri = episode.optString("uri")
        if (uri.isBlank()) return null
        val played = episode.optJSONObject("playedState")
        return EpisodeRow(
            uri = uri,
            title = episode.optString("name").plainText(),
            description = episode.optString("description").plainText(),
            releaseDate = releaseLabel(episode),
            durationMs = episode.optJSONObject("duration")?.optLong("totalMilliseconds", 0L) ?: 0L,
            imageUrl = GraphQl.entityImage(episode),
            colorHex = GraphQl.visualColor(episode).ifBlank { GraphQl.entityColor(episode) },
            explicit = episode.optJSONObject("contentRating")?.optString("label")
                .orEmpty().plainText() == "EXPLICIT",
            playable = episode.optJSONObject("playability")?.optBoolean("playable", true) ?: true,
            video = mediaTypes(episode).contains("VIDEO"),
            played = played?.optString("state").orEmpty() == "COMPLETED",
            positionMs = played?.optLong("playPositionMilliseconds", 0L) ?: 0L,
            showName = show?.optString("name").orEmpty().plainText(),
            showUri = show?.optString("uri").orEmpty(),
        )
    }

    private fun mediaTypes(node: JSONObject): List<String> {
        val array = node.optJSONArray("mediaTypes") ?: return emptyList()
        val out = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val value = array.optString(i)
            if (value.isNotBlank()) out += value
        }
        return out
    }

    private fun releaseLabel(node: JSONObject): String {
        val date = node.optJSONObject("releaseDate") ?: return ""
        val iso = date.optString("isoString").plainText()
        if (iso.length < 10) return iso
        val precision = date.optString("precision").plainText()
        val year = iso.substring(0, 4)
        val month = iso.substring(5, 7).toIntOrNull()?.takeIf { it in 1..12 } ?: return year
        val day = iso.substring(8, 10).toIntOrNull()
        return when (precision) {
            "YEAR" -> year
            "MONTH" -> "${MONTH_NAMES[month - 1]} $year"
            else -> if (day == null) "${MONTH_NAMES[month - 1]} $year" else "${MONTH_NAMES[month - 1]} $day, $year"
        }
    }

    private fun wideImage(node: JSONObject): String {
        val visual = node.optJSONObject("visualIdentity") ?: return ""
        val wide = visual.optJSONObject("sixteenByNineCoverImage") ?: return ""
        val sources = (wide.optJSONObject("data") ?: wide).optJSONArray("sources") ?: return ""
        var best = ""
        var bestWidth = -1
        for (i in 0 until sources.length()) {
            val source = sources.optJSONObject(i) ?: continue
            val url = source.optString("url")
            if (url.isBlank()) continue
            val width = source.optInt("width", 0)
            if (width > bestWidth) {
                bestWidth = width
                best = url
            }
        }
        return best
    }

    private val MONTH_NAMES = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    private const val CHAPTERS_HOST = "https://spclient.wg.spotify.com"

    private suspend fun spHeaders(context: Context): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${EncoreAuth.token(context)}")
        put("Accept", "application/json")
        put("app-platform", "WebPlayer")
        put("User-Agent", Net.DESKTOP_UA)
        ClientToken.get(context).takeIf { it.isNotBlank() }?.let { put("client-token", it) }
    }

    /**
     * Chapter spans come from the episode's chapter playlist; the ml_generated
     * lists carry positions only, so titles are matched from the transcript's
     * titled sections, the first one landing inside the chapter's opening
     * seconds. Speaker labels are not chapter titles.
     */
    suspend fun chapters(context: Context, episodeUri: String): List<ChapterSpan> {
        if (!episodeUri.startsWith("spotify:episode:")) return emptyList()
        val encoded = java.net.URLEncoder.encode(episodeUri, "UTF-8")
        val list = Net.get(
            "$CHAPTERS_HOST/playlist/v2/list/podcast-chapters/$encoded" +
                "?decorate=revision,length,attributes,timestamp,owner,capabilities",
            spHeaders(context),
            tag = "chapters",
        )?.takeIf { it.ok }?.json() ?: return emptyList()
        val items = list.optJSONObject("contents")?.optJSONArray("items")
        if (items == null || items.length() == 0) return emptyList()
        val spans = ArrayList<ChapterSpan>(items.length())
        for (i in 0 until items.length()) {
            val node = items.optJSONObject(i) ?: continue
            val attrs = node.optJSONObject("attributes")?.optJSONArray("formatAttributes") ?: continue
            var startMs = -1L
            var endMs = -1L
            for (j in 0 until attrs.length()) {
                val attr = attrs.optJSONObject(j) ?: continue
                when (attr.optString("key")) {
                    "chapter.start_position_in_milliseconds" -> startMs = attr.optString("value").toLongOrNull() ?: -1L
                    "chapter.end_position_in_milliseconds" -> endMs = attr.optString("value").toLongOrNull() ?: -1L
                }
            }
            if (startMs < 0L || endMs <= startMs) continue
            spans += ChapterSpan(title = "", startMs = startMs, endMs = endMs)
        }
        if (spans.isEmpty()) return emptyList()
        val episodeId = episodeUri.removePrefix("spotify:episode:")
        val transcript = Net.get(
            "$CHAPTERS_HOST/transcript-read-along/v2/episode/$episodeId?format=json&maxSentenceLength=100&excludeCC=false",
            spHeaders(context),
            tag = "transcript",
        )?.takeIf { it.ok }?.json()
        val titled = ArrayList<Pair<Long, String>>()
        transcript?.optJSONArray("section")?.let { sections ->
            for (i in 0 until sections.length()) {
                val section = sections.optJSONObject(i) ?: continue
                val title = section.optJSONObject("title")?.optString("title").orEmpty().plainText()
                if (title.isBlank() || title.matches(Regex("Speaker \\d+"))) continue
                titled += section.optLong("startMs", 0L) to title
            }
        }
        titled.sortBy { it.first }
        val out = ArrayList<ChapterSpan>(spans.size)
        spans.forEachIndexed { index, span ->
            val title = titled.firstOrNull { (at, _) -> at >= span.startMs - 2000L && at <= span.startMs + 30_000L }
                ?.second
                .orEmpty()
                .ifBlank { "Chapter ${index + 1}" }
            out += span.copy(title = title)
        }
        log.i("chapters $episodeUri spans=${spans.size} titled=${titled.size}")
        return out
    }
}
