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

package com.mwask.bat.backend.core.net

import org.json.JSONArray
import org.json.JSONObject

object GraphQl {

    fun largestSource(holder: JSONObject?): String {
        if (holder == null) return ""
        holder.optJSONArray("sources")?.let { return largestOf(it) }
        holder.optJSONArray("items")?.optJSONObject(0)?.let { return largestSource(it) }
        return ""
    }

    fun colorHex(holder: JSONObject?): String {
        if (holder == null) return ""
        val colors = holder.optJSONObject("extractedColors")
        if (colors != null) {
            val dark = colors.optJSONObject("colorDark") ?: return ""
            if (dark.optBoolean("isFallback", false)) return ""
            val hex = dark.optString("hex")
            return if (hex.length == 7 && hex[0] == '#') hex else ""
        }
        holder.optJSONArray("items")?.optJSONObject(0)?.let { return colorHex(it) }
        return ""
    }

    private fun rgbOf(node: JSONObject?): String {
        if (node == null) return ""
        val r = node.optInt("red", -1)
        val g = node.optInt("green", -1)
        val b = node.optInt("blue", -1)
        if (r < 0 || g < 0 || b < 0) return ""
        return "#%02X%02X%02X".format(r, g, b)
    }

    fun visualColor(entity: JSONObject?): String {
        if (entity == null) return ""
        val set = entity.optJSONObject("visualIdentity")
            ?.optJSONObject("squareCoverImage")
            ?.optJSONObject("extractedColorSet")
            ?: return ""
        val contrast = set.optJSONObject("highContrast") ?: set.optJSONObject("minContrast") ?: return ""
        rgbOf(contrast.optJSONObject("backgroundBase")).takeIf { it.isNotEmpty() }?.let { return it }
        return rgbOf(contrast.optJSONObject("backgroundTintedBase"))
    }

    fun headerImage(entity: JSONObject?): String {
        val image = entity?.optJSONObject("headerImage") ?: return ""
        val data = image.optJSONObject("data") ?: image
        return largestSource(data)
    }

    fun gallery(entity: JSONObject?): List<String> {
        val items = entity?.optJSONObject("visuals")?.optJSONObject("gallery")?.optJSONArray("items")
            ?: return emptyList()
        val out = ArrayList<String>(items.length())
        for (i in 0 until items.length()) {
            largestSource(items.optJSONObject(i)).takeIf { it.isNotEmpty() }?.let(out::add)
        }
        return out
    }

    fun userAvatar(owner: JSONObject?): String = largestSource(owner?.optJSONObject("avatar"))

    private fun holders(entity: JSONObject): List<JSONObject> = listOfNotNull(
        entity.optJSONObject("coverArt"),
        entity.optJSONObject("images"),
        entity.optJSONObject("visuals")?.optJSONObject("avatarImage"),
        entity.optJSONObject("avatarImage"),
    )

    fun entityImage(entity: JSONObject?): String {
        if (entity == null) return ""
        holders(entity).forEach { holder ->
            largestSource(holder).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return ""
    }

    fun entityColor(entity: JSONObject?): String {
        if (entity == null) return ""
        holders(entity).forEach { holder ->
            colorHex(holder).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return ""
    }

    fun artistNames(entity: JSONObject?, field: String = "artists"): String {
        val items = entity?.optJSONObject(field)?.optJSONArray("items") ?: return ""
        val names = ArrayList<String>(items.length())
        for (i in 0 until items.length()) {
            val artist = items.optJSONObject(i) ?: continue
            val name = artist.optString("name").ifBlank {
                artist.optJSONObject("profile")?.optString("name").orEmpty()
            }
            if (name.isNotBlank()) names += name
        }
        return names.joinToString(", ")
    }

    fun artistUri(entity: JSONObject?, field: String = "artists"): String {
        val items = entity?.optJSONObject(field)?.optJSONArray("items") ?: return ""
        for (i in 0 until items.length()) {
            val artist = items.optJSONObject(i) ?: continue
            val uri = artist.optString("uri")
            if (uri.startsWith("spotify:artist:")) return uri
        }
        return ""
    }

    private fun largestOf(sources: JSONArray): String {
        var best = ""
        var width = -1
        for (i in 0 until sources.length()) {
            val source = sources.optJSONObject(i) ?: continue
            val w = source.optInt("width", 0).coerceAtLeast(source.optInt("maxWidth", 0))
            if (w > width) {
                width = w
                best = source.optString("url")
            }
        }
        return best
    }
}
