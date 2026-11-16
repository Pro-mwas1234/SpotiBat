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
import android.util.Base64
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.net.Net
import com.mwask.bat.backend.core.net.Pathfinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val log = Logs.of(Logs.Module.LIBRARY)

data class SmartShuffleEligibility(
    val canSmartShuffle: Boolean,
    val lensUri: String,
)

/**
 * One row of the enhanced context answer. The server hands out the item id
 * and the ready-made signal payloads, so taps never need local crafting.
 * [distribution] carries the server's shuffle position for the row (from the
 * `shuffle.distribution` format attribute), shared by playlist tracks and
 * recommendations alike; sorted by it the two interleave like the official
 * queue does.
 */
data class SmartShuffleRow(
    val uri: String,
    val itemId: String,
    val enhanced: Boolean,
    val signals: Map<String, String>,
    val distribution: Double = -1.0,
)

object SmartShuffleSource {

    private const val LIKED_SONGS = "spotify:collection:tracks"
    private const val LENS_ENHANCE = "enhance"
    private const val BASE = "https://spclient.wg.spotify.com"
    private const val ENHANCED_PROVIDER = "enhanced_recommendation"

    suspend fun eligibility(context: Context, token: String, contextUri: String): SmartShuffleEligibility {
        val uri = lensCandidate(contextUri)
        val fallback = SmartShuffleEligibility(canSmartShuffle = false, lensUri = "")
        if (uri.isBlank()) return fallback
        val variables = JSONObject().put("uris", JSONArray().put(uri)).toString()
        val response = withContext(Dispatchers.IO) {
            runCatching { Pathfinder.queryPost(context, token, "smartShuffle", variables) }.getOrNull()
        } ?: return fallback
        val node = response
            .optJSONObject("data")
            ?.optJSONArray("lookup")?.optJSONObject(0)
            ?.optJSONObject("data")
            ?.optJSONObject("smartShuffle")
        if (node == null) {
            log.w("smartShuffle: no node for '${uri.takeLast(24)}'")
            return fallback
        }
        val lens = node.optString("uri").ifBlank { uri }
        val available = node.optBoolean("available", false)
        log.i("smartShuffle: '$uri' available=$available lens='${lens.takeLast(24)}'")
        return SmartShuffleEligibility(canSmartShuffle = available, lensUri = lens)
    }

    suspend fun reset(context: Context, token: String, spclient: String, lensUri: String): List<SmartShuffleRow> {
        val response = post(context, token, spclient, lensUri, "reset") ?: return emptyList()
        if (!response.ok) return emptyList()
        val rows = parseRows(response.bytes.toString(Charsets.UTF_8))
        log.i("signal 'reset': ${rows.size} row(s), ${rows.count { it.enhanced }} enhanced")
        return rows
    }

    suspend fun tap(
        context: Context,
        token: String,
        spclient: String,
        lensUri: String,
        row: SmartShuffleRow,
    ): List<SmartShuffleRow> = signal(context, token, spclient, lensUri, row, "tap")

    suspend fun plus(
        context: Context,
        token: String,
        spclient: String,
        lensUri: String,
        row: SmartShuffleRow,
    ): List<SmartShuffleRow> = signal(context, token, spclient, lensUri, row, "plus")

    suspend fun minus(
        context: Context,
        token: String,
        spclient: String,
        lensUri: String,
        row: SmartShuffleRow,
    ): List<SmartShuffleRow> = signal(context, token, spclient, lensUri, row, "minus")

    private suspend fun signal(
        context: Context,
        token: String,
        spclient: String,
        lensUri: String,
        row: SmartShuffleRow,
        identifier: String,
    ): List<SmartShuffleRow> {
        val data = row.signals[identifier]
        if (data.isNullOrBlank()) {
            log.w("signal '$identifier': the row carries no prebuilt payload")
            return emptyList()
        }
        val response = post(context, token, spclient, lensUri, identifier, data)
        val ok = response?.ok == true
        log.i("signal '$identifier' -> ${response?.code ?: "no response"} ok=$ok")
        if (!ok) return emptyList()
        return parseRows(response!!.bytes.toString(Charsets.UTF_8))
    }

    private suspend fun post(
        context: Context,
        token: String,
        spclient: String,
        lensUri: String,
        identifier: String,
        data: String = DEFAULT_RESET_DATA,
    ): Net.Blob? {
        if (spclient.isBlank() || lensUri.isBlank()) return null
        val type = lensUri.substringAfter("spotify:").substringBefore(":")
        val id = lensUri.substringAfterLast(":")
        if (type.isBlank() || id.isBlank()) return null
        val base = when (type) {
            "playlist" -> "$BASE/playlist/v2/playlist/$id"
            else -> "$BASE/$type/$id"
        }
        val url = "$base/signals?spotify-apply-lenses=$LENS_ENHANCE"
        val signal = JSONObject()
            .put("identifier", identifier)
            .put("data", data)
        val body = JSONObject()
            .put("emittedSignals", JSONArray().put(signal))
            .toString()
        val headers = mapOf(
            "Authorization" to "Bearer $token",
            "Accept" to "application/json",
        )
        return withContext(Dispatchers.IO) {
            Net.postBlob(url, body.toByteArray(), "application/json", headers, tag = "smart-shuffle/$identifier")
        }
    }

    private fun parseRows(body: String): List<SmartShuffleRow> {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: run {
            log.w("signal answer: not json, keeping the queue as it is")
            return emptyList()
        }
        val items = json.optJSONObject("contents")?.optJSONArray("items") ?: return emptyList()
        val rows = mutableListOf<SmartShuffleRow>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val uri = item.optString("uri")
            if (!uri.startsWith("spotify:track:")) continue
            val attrs = item.optJSONObject("attributes") ?: continue
            var provider = ""
            var distribution = -1.0
            val format = attrs.optJSONArray("formatAttributes")
            for (j in 0 until (format?.length() ?: 0)) {
                val field = format!!.optJSONObject(j) ?: continue
                when (field.optString("key")) {
                    "provider" -> provider = field.optString("value")
                    "shuffle.distribution" -> distribution =
                        field.optString("value").substringAfter("d(").substringBefore(")").toDoubleOrNull() ?: -1.0
                }
            }
            val signals = buildMap {
                val available = attrs.optJSONArray("availableSignals")
                for (j in 0 until (available?.length() ?: 0)) {
                    val signal = available!!.optJSONObject(j) ?: continue
                    val id = signal.optString("identifier")
                    val data = signal.optString("data")
                    if (id.isNotBlank() && data.isNotBlank()) put(id, data)
                }
            }
            rows.add(SmartShuffleRow(uri, attrs.optString("itemId"), provider == ENHANCED_PROVIDER, signals, distribution))
        }
        return rows
    }

    private fun lensCandidate(contextUri: String): String = when {
        contextUri.startsWith("spotify:playlist:") -> contextUri
        contextUri.startsWith("spotify:user:") && contextUri.endsWith(":collection") -> LIKED_SONGS
        contextUri == LIKED_SONGS -> LIKED_SONGS
        else -> ""
    }

    /** The reset signal the official player sends: lens 'enhance' in a tiny proto. */
    private const val DEFAULT_RESET_DATA = "CgdlbmhhbmNl"
}
