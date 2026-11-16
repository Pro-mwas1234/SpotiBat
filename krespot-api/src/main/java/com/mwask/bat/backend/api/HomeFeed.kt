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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mwask.bat.backend.core.auth.EncoreAuth
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.model.*
import org.json.JSONArray
import org.json.JSONObject
import com.mwask.bat.backend.core.net.GraphQl
import com.mwask.bat.backend.core.net.Pathfinder
import com.mwask.bat.backend.core.net.plainText

private val log = Logs.of(Logs.Module.HOME)

object HomeFeed {

    const val SHORTS = "HomeShortsSectionData"
    const val GENERIC = "HomeGenericSectionData"
    const val BASELINE = "HomeFeedBaselineSectionData"
    const val RECENT = "HomeRecentlyPlayedSectionData"
    const val GRID = "HomeGridSectionData"
    const val YOUR_DJ = "HomeYourDJSectionData"
    const val WATCH_FEED = "HomeWatchFeedSectionData"
    const val MUSIC_VIDEOS = "HomeMusicVideosSectionData"
    const val PODCAST_VIDEOS = "HomePodcastVideosCarouselSectionData"

    private val SILENT = setOf(
        "HomeOnboardingSectionData",
        "HomeOnboardingSectionDataV2",
        "HomeFeatureActivationSectionData",
        "HomeFeatureActivationSectionDataV2",
        "HomeContentEvaluationListSectionData",
        "HomeHaloCarouselSectionData",
        "",
    )

    suspend fun load(context: Context, facet: String = ""): HomeFeedPayload {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) {
            log.w("no token, cannot load the home feed")
            return HomeFeedPayload()
        }
        val captured = Pathfinder.variablesOf("home")
        val base = runCatching { JSONObject(captured) }.getOrNull()
        if (base == null && captured.isNotBlank()) {
            log.w("the captured home variables do not parse, running with the defaults")
        }
        val variables = (base ?: JSONObject())
            .put("sp_t", Pathfinder.sessionToken())
            .put("facet", facet)
            .put("sectionItemsLimit", 10)
            .toString()
        val json = Pathfinder.query(
            context = context,
            token = token,
            operation = "home",
            variables = variables,
        ) ?: return HomeFeedPayload()
        val payload = withContext(Dispatchers.Default) { parse(json) }
        val colored = payload.sections.sumOf { section -> section.items.count { it.colorHex.isNotBlank() } }
        log.i("facet='$facet' parsed ${payload.sections.size} sections, " +
                "${payload.sections.sumOf { it.items.size }} items, $colored with extracted color, " +
                "${payload.chips.size} chips")
        payload.sections.take(6).forEach { s ->
            log.i("  ${s.kind} '${s.title}' -> ${s.items.size} items")
            s.items.take(2).forEach { i ->
                log.i("     ${i.kind} '${i.title}' / '${i.subtitle}' img=${i.imageUrl.isNotBlank()} color=${i.colorHex.ifBlank { "-" }}")
            }
        }
        return payload
    }

    fun parse(json: JSONObject): HomeFeedPayload {
        val home = json.optJSONObject("data")?.optJSONObject("home") ?: return HomeFeedPayload()
        val greeting = home.optJSONObject("greeting")?.let { label(it) }.orEmpty()
        val chips = chips(home.optJSONArray("homeChips"))
        val array = home.optJSONObject("sectionContainer")
            ?.optJSONObject("sections")
            ?.optJSONArray("items")
            ?: return HomeFeedPayload(greeting = greeting, chips = chips)
        val out = ArrayList<HomeSection>(array.length())
        for (i in 0 until array.length()) {
            val section = array.optJSONObject(i) ?: continue
            val data = section.optJSONObject("data")
            val kind = data?.optString("__typename").orEmpty()
            if (kind in SILENT) continue
            val title = label(data?.optJSONObject("title"))
                ?: label(data?.optJSONObject("sectionTitle"))
                ?: label(section.optJSONObject("sectionTitle"))
                ?: ""
            val items = section.optJSONObject("sectionItems")?.optJSONArray("items")
                ?: continue
            val parsed = (0 until items.length()).mapNotNull { item(items.optJSONObject(it), data) }
            if (parsed.isEmpty()) continue
            out += HomeSection(
                id = section.optString("uri"),
                title = title,
                kind = kind,
                items = parsed,
            )
        }
        return HomeFeedPayload(greeting = greeting, chips = chips, sections = out)
    }

    private fun chips(array: JSONArray?): List<HomeChip> {
        if (array == null) return emptyList()
        val out = ArrayList<HomeChip>(array.length())
        for (i in 0 until array.length()) {
            val node = array.optJSONObject(i) ?: continue
            val id = node.optString("id")
            val name = label(node.optJSONObject("label")).orEmpty()
            if (id.isBlank() || name.isBlank()) continue
            if (id in DISABLED_CHIPS) continue
            out += HomeChip(id = id, label = name, subChips = chips(node.optJSONArray("subChips")))
        }
        return out
    }

    private val DISABLED_CHIPS = setOf("audiobooks-chip")

    private val DISABLED_KINDS = setOf("Audiobook", "Chapter", "AudiobookResponseWrapper")

    private fun item(node: JSONObject?, sectionData: JSONObject?): HomeItem? {
        if (node == null) return null
        val content = node.optJSONObject("content") ?: node
        val entity = content.optJSONObject("data") ?: node.optJSONObject("data")
        val uri = content.optString("uri").ifBlank { node.optString("uri") }
        if (uri.isBlank()) return null
        val kind = entity?.optString("__typename")
            ?: content.optString("__typename").takeIf { it != "UnknownType" }
            ?: ""
        if (kind == "GenericError" || kind == "NotFound" || kind == "RestrictedContent") return null
        if (kind in DISABLED_KINDS) return null
        val title = nameOf(entity, uri)
        if (title.isBlank() && uri !in COLLECTION_URIS) return null
        return HomeItem(
            uri = uri,
            title = title.ifBlank { collectionLabel(uri) },
            subtitle = subtitleOf(entity),
            imageUrl = imageOf(entity),
            kind = kind,
            colorHex = colorOf(entity),
            caption = captionOf(sectionData, entity),
        )
    }

    private fun captionOf(sectionData: JSONObject?, entity: JSONObject?): String {
        val raw = label(sectionData?.optJSONObject("title")) ?: return ""
        if (!raw.contains("{0}")) return raw
        val show = entity?.optJSONObject("podcastV2")?.optString("name").orEmpty()
        return if (show.isBlank()) raw.substringBefore(" {0}").trim() else raw.replace("{0}", show)
    }

    private fun nameOf(entity: JSONObject?, uri: String): String {
        if (entity == null) return collectionLabel(uri)
        val raw = entity.optString("name").takeIf { it.isNotBlank() }
            ?: entity.optString("title").takeIf { it.isNotBlank() }
            ?: entity.optJSONObject("profile")?.optString("name")?.takeIf { it.isNotBlank() }
            ?: return ""
        return raw.plainText()
    }

    private fun subtitleOf(entity: JSONObject?): String {
        if (entity == null) return ""
        val raw = artists(entity)?.takeIf { it.isNotBlank() }
            ?: entity.optJSONObject("owner")?.optString("name")?.takeIf { it.isNotBlank() }
            ?: entity.optJSONObject("publisher")?.optString("name")?.takeIf { it.isNotBlank() }
            ?: entity.optJSONObject("podcast")?.optString("name")?.takeIf { it.isNotBlank() }
            ?: entity.optString("description").takeIf { it.isNotBlank() }
            ?: return ""
        return raw.plainText().take(80)
    }

    private fun artists(entity: JSONObject): String? {
        val items = entity.optJSONObject("artists")?.optJSONArray("items") ?: return null
        val names = ArrayList<String>(items.length())
        for (i in 0 until items.length()) {
            val a = items.optJSONObject(i) ?: continue
            val name = a.optString("name").ifBlank {
                a.optJSONObject("profile")?.optString("name").orEmpty()
            }
            if (name.isNotBlank()) names += name
        }
        return names.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun imageOf(entity: JSONObject?): String = GraphQl.entityImage(entity)

    private fun colorOf(entity: JSONObject?): String = GraphQl.entityColor(entity)

    private fun label(obj: JSONObject?): String? {
        if (obj == null) return null
        val raw = obj.optString("transformedLabel").takeIf { it.isNotBlank() }
            ?: obj.optString("translatedBaseText").takeIf { it.isNotBlank() }
            ?: obj.optString("text").takeIf { it.isNotBlank() }
            ?: obj.optString("title").takeIf { it.isNotBlank() }
            ?: return null
        return raw.plainText()
    }

    private val COLLECTION_URIS = setOf(
        "spotify:user:@:collection",
        "spotify:collection:tracks",
    )

    private fun collectionLabel(uri: String): String =
        if (uri in COLLECTION_URIS) "Liked Songs" else ""
}
