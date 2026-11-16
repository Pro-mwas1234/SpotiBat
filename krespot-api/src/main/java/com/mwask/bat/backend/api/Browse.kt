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
import com.mwask.bat.backend.model.BrowseCard
import com.mwask.bat.backend.model.BrowsePage
import com.mwask.bat.backend.model.BrowseSection
import org.json.JSONArray
import org.json.JSONObject
import com.mwask.bat.backend.core.net.GraphQl
import com.mwask.bat.backend.core.net.Pathfinder
import com.mwask.bat.backend.core.net.plainText

private val log = Logs.of(Logs.Module.BROWSE)

object Browse {

    const val OP_ALL = "browseAll"
    const val OP_PAGE = "browsePage"

    private const val INTEGRATION = "INTEGRATION_WEB_PLAYER"
    const val PAGE_SECTIONS = 10
    private const val PAGE_ITEMS = 10

    suspend fun all(context: Context): List<BrowseCard> {
        val variables = JSONObject()
            .put("pagePagination", JSONObject().put("offset", 0).put("limit", PAGE_SECTIONS))
            .put("sectionPagination", JSONObject().put("offset", 0).put("limit", 99))
            .put("browseEndUserIntegration", INTEGRATION)
            .toString()
        val json = call(context, OP_ALL, variables) ?: return emptyList()
        val sections = json.optJSONObject("data")
            ?.optJSONObject("browseStart")
            ?.optJSONObject("sections")
            ?.optJSONArray("items")
            ?: return emptyList()
        val out = ArrayList<BrowseCard>()
        for (i in 0 until sections.length()) {
            val node = sections.optJSONObject(i) ?: continue
            val nested = node.optJSONObject("sectionItems")?.optJSONArray("items")
            if (nested != null) {
                for (j in 0 until nested.length()) {
                    card(nested.optJSONObject(j))?.let(out::add)
                }
            } else {
                card(node)?.let(out::add)
            }
        }
        log.i("browseAll -> ${out.size} categories ${out.map { it.title }.take(6)}")
        return out
    }

    suspend fun page(context: Context, uri: String, offset: Int = 0): BrowsePage? {
        if (uri.isBlank()) return null
        val variables = JSONObject()
            .put("pagePagination", JSONObject().put("offset", offset).put("limit", PAGE_SECTIONS))
            .put("sectionPagination", JSONObject().put("offset", 0).put("limit", PAGE_ITEMS))
            .put("uri", uri)
            .put("browseEndUserIntegration", INTEGRATION)
            .put("includeEpisodeContentRatingsV2", true)
            .toString()
        val json = call(context, OP_PAGE, variables) ?: return null
        val browse = json.optJSONObject("data")?.optJSONObject("browse")
        if (browse == null) {
            log.w("browsePage $uri: no data.browse, keys=" +
                    json.optJSONObject("data")?.keys()?.asSequence()?.toList()
            )
            return null
        }
        val header = browse.optJSONObject("header")
        val container = browse.optJSONObject("sections")
        val items = container?.optJSONArray("items") ?: JSONArray()
        val sections = ArrayList<BrowseSection>()
        var skipped = 0
        for (i in 0 until items.length()) {
            val node = items.optJSONObject(i) ?: continue
            val content = node.optJSONObject("sectionItems")?.optJSONArray("items")
            if (content == null) {
                card(node)?.let { sections += BrowseSection(title = "", cards = listOf(it)) }
                continue
            }
            val label = node.optJSONObject("data")?.optJSONObject("title")
                ?: node.optJSONObject("title")
            val title = label?.optString("transformedLabel").orEmpty().plainText()
            val cards = ArrayList<BrowseCard>(content.length())
            for (j in 0 until content.length()) {
                card(content.optJSONObject(j))?.let(cards::add)
            }
            if (cards.isEmpty()) {
                skipped += 1
                continue
            }
            sections += BrowseSection(title = title, cards = cards)
        }
        val next = container?.optJSONObject("pagingInfo")?.optInt("nextOffset", -1) ?: -1
        val page = BrowsePage(
            uri = browse.optString("uri").takeIf { it.startsWith("spotify:") } ?: uri,
            title = header?.optJSONObject("title")?.optString("transformedLabel")
                .orEmpty().plainText(),
            subtitle = header?.optJSONObject("subtitle")?.optString("transformedLabel")
                .orEmpty().plainText(),
            colorHex = header?.optJSONObject("color")?.optString("hex").orEmpty(),
            imageUrl = GraphQl.largestSource(header?.optJSONObject("backgroundImage")),
            sections = sections,
            nextOffset = next.takeIf { it >= 0 },
        )
        log.i("browsePage $uri offset=$offset '${page.title}' -> ${sections.size} sections " +
                "(${sections.sumOf { it.cards.size }} cards, $skipped skipped) next=${page.nextOffset}"
        )
        return page
    }

    private fun card(node: JSONObject?): BrowseCard? {
        if (node == null) return null
        val content = node.optJSONObject("content") ?: return null
        val data = content.optJSONObject("data") ?: return null
        val nodeUri = node.optString("uri")
        if (data.optString("__typename") == "BrowseClientFeature") {
            val title = label(data.optJSONObject("title"))
            val target = data.optString("featureUri").takeIf { it.startsWith("spotify:") }
            if (title.isBlank() || target == null) return null
            return BrowseCard(
                uri = target,
                title = title,
                subtitle = "",
                imageUrl = GraphQl.largestSource(data.optJSONObject("artwork")),
                colorHex = data.optJSONObject("backgroundColor")?.optString("hex").orEmpty(),
            )
        }
        if (data.optString("__typename") == "BrowseSectionContainer") {
            val rep = data.optJSONObject("data")?.optJSONObject("cardRepresentation")
            val title = label(rep?.optJSONObject("title"))
            val uri = nodeUri.takeIf { it.startsWith("spotify:") } ?: data.optString("uri")
            if (title.isBlank() || !uri.startsWith("spotify:")) return null
            return BrowseCard(
                uri = uri,
                title = title,
                subtitle = "",
                imageUrl = GraphQl.largestSource(rep?.optJSONObject("artwork")),
                colorHex = rep?.optJSONObject("backgroundColor")?.optString("hex").orEmpty(),
            )
        }
        val uri = data.optString("uri").takeIf { it.startsWith("spotify:") } ?: nodeUri
        if (!uri.startsWith("spotify:")) return null
        val title = data.optString("name").ifBlank {
            data.optJSONObject("profile")?.optString("name").orEmpty()
        }.plainText()
        if (title.isBlank()) return null
        val subtitle = data.optJSONObject("ownerV2")
            ?.optJSONObject("data")
            ?.optString("name")
            .orEmpty()
            .ifBlank { GraphQl.artistNames(data) }
            .plainText()
        return BrowseCard(
            uri = uri,
            title = title,
            subtitle = subtitle,
            imageUrl = GraphQl.entityImage(data),
            colorHex = GraphQl.entityColor(data),
        )
    }

    private fun label(obj: JSONObject?): String {
        if (obj == null) return ""
        val raw = obj.optString("transformedLabel").takeIf { it.isNotBlank() }
            ?: obj.optString("translatedBaseText").takeIf { it.isNotBlank() }
            ?: obj.optString("text").takeIf { it.isNotBlank() }
            ?: return ""
        return raw.plainText()
    }

    private suspend fun call(context: Context, operation: String, variables: String): JSONObject? {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) {
            log.w("$operation: no token")
            return null
        }
        return Pathfinder.queryPost(context, token, operation, variables)
    }
}
