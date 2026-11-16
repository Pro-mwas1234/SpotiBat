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
import com.mwask.bat.backend.core.net.Net
import com.mwask.bat.backend.core.net.Pathfinder
import com.mwask.bat.backend.model.AccountAttributes
import com.mwask.bat.backend.model.ProfilePrivacy
import org.json.JSONObject

private val log = Logs.of(Logs.Module.SETTINGS)

object Settings {

    private const val OP_READ = "accountAttributes"
    private const val OP_WRITE = "changeAttributes"
    private const val PRIVACY_HOST = "https://spclient.wg.spotify.com"
    private const val PRIVACY_READ = "/profile-privacy/v2/read-settings"
    private const val PRIVACY_STORE = "/profile-privacy/v2/store-settings"

    suspend fun attributes(context: Context): AccountAttributes? {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) {
            log.w("attributes: no token")
            return null
        }
        val data = Pathfinder.queryPost(context, token, OP_READ, "{}") ?: return null
        val account = data.optJSONObject("data")?.optJSONObject("me")?.optJSONObject("account")
        if (account == null) {
            log.w("attributes: no me.account in the answer")
            return null
        }
        val a = account.optJSONObject("attributes") ?: JSONObject()
        val result = AccountAttributes(
            product = account.optString("product"),
            country = account.optString("country"),
            catalogue = a.optString("catalogue"),
            explicitFilter = a.optBoolean("filterExplicitContent", false),
            canvasDisabled = a.optBoolean("canvasDisabled", false),
            musicVideoDisabled = a.optBoolean("musicVideoDisabled", false),
            otherVideoDisabled = a.optBoolean("otherVideoDisabled", false),
            canvasLocked = !a.isNull("lockCanvasDisabled"),
            musicVideoLocked = !a.isNull("lockMusicVideoDisabled"),
            otherVideoLocked = !a.isNull("lockOtherVideoDisabled"),
            hasPodcasts = a.optBoolean("hasPodcastsAvailable", true),
        )
        log.i("attributes product=${result.product} country=${result.country} " +
                "canvas=${!result.canvasDisabled} music=${!result.musicVideoDisabled} " +
                "other=${!result.otherVideoDisabled} locks=${result.canvasLocked}/${result.musicVideoLocked}/${result.otherVideoLocked}"
        )
        return result
    }

    suspend fun setAttribute(context: Context, field: String, value: Boolean): Boolean {
        val token = EncoreAuth.token(context)
        if (token.isBlank()) return false
        val variables = JSONObject()
            .put("input", JSONObject().put(field, value))
            .toString()
        val data = Pathfinder.queryPost(context, token, OP_WRITE, variables) ?: return false
        val ok = data.optJSONObject("data")
            ?.optJSONObject("changeUserAccountAttributes")
            ?.optBoolean("success", false) == true
        log.i("$field=$value -> success=$ok")
        return ok
    }

    suspend fun privacy(context: Context, username: String): ProfilePrivacy? {
        val token = EncoreAuth.token(context)
        if (token.isBlank() || username.isBlank()) return null
        val body = writer().apply { tag(1, 2); bytes(username.toByteArray()) }.finish()
        val answer = Net.postBlob(
            PRIVACY_HOST + PRIVACY_READ,
            body,
            "application/x-protobuf",
            authHeader(token),
            "privacy-read",
        ) ?: return null
        if (!answer.ok) {
            log.w("privacy read http=${answer.code}, the settings are unknown")
            return null
        }
        val result = readPrivacy(answer)
        log.i("privacy showFollows=${result.showFollows} playlists=${result.showPublishedPlaylists} " +
                "allowFollows=${result.allowFollows}"
        )
        return result
    }

    suspend fun setPrivacy(
        context: Context,
        username: String,
        showFollows: Boolean? = null,
        showPublishedPlaylists: Boolean? = null,
        allowFollows: Boolean? = null,
    ): Boolean {
        val token = EncoreAuth.token(context)
        if (token.isBlank() || username.isBlank()) return false
        val mask = ArrayList<String>(3)
        if (showFollows != null) mask += "show_follows"
        if (showPublishedPlaylists != null) mask += "show_published_playlists"
        if (allowFollows != null) mask += "allow_follows"
        if (mask.isEmpty()) return false
        val body = writer().apply {
            tag(1, 2); bytes(username.toByteArray())
            val inner = writer()
            mask.forEach { name -> inner.tag(1, 2); inner.bytes(name.toByteArray()) }
            val packed = inner.finish()
            tag(3, 2); bytes(packed)
            showFollows?.let { tag(5, 0); varint(if (it) 1 else 0) }
            showPublishedPlaylists?.let { tag(6, 0); varint(if (it) 1 else 0) }
            allowFollows?.let { tag(4, 0); varint(if (it) 1 else 0) }
        }.finish()
        val response = Net.postBlob(
            PRIVACY_HOST + PRIVACY_STORE,
            body,
            "application/x-protobuf",
            authHeader(token),
            "privacy-store",
        )
        log.i("store ${mask.joinToString(",")} -> http=${response?.code ?: 0}")
        return response != null && response.code in 200..299
    }

    private fun authHeader(token: String): Map<String, String> = mapOf(
        "Authorization" to "Bearer $token",
        "Accept" to "application/x-protobuf",
    )

    private fun readPrivacy(answer: Net.Blob): ProfilePrivacy {
        var showFollows = false
        var playlists = false
        var allowFollows = false
        var index = 0
        while (index < answer.bytes.size) {
            val (key, afterKey) = readVarint(answer.bytes, index)
            val field = (key shr 3).toInt()
            val wire = (key and 7).toInt()
            index = afterKey
            when (wire) {
                0 -> {
                    val (value, after) = readVarint(answer.bytes, index)
                    index = after
                    when (field) {
                        3 -> allowFollows = value != 0L
                        4 -> showFollows = value != 0L
                        5 -> playlists = value != 0L
                    }
                }
                2 -> {
                    val (length, after) = readVarint(answer.bytes, index)
                    index = (after + length).toInt()
                }
                else -> return ProfilePrivacy(showFollows, playlists, allowFollows)
            }
        }
        return ProfilePrivacy(showFollows, playlists, allowFollows)
    }

    private fun writer() = Proto()

    private class Proto {
        private val out = ArrayList<Byte>(64)

        fun tag(field: Int, wire: Int): Proto {
            varint((field shl 3) or wire)
            return this
        }

        fun varint(value: Int) {
            var v = value.toLong()
            while (true) {
                val b = (v and 0x7F).toInt()
                v = v ushr 7
                if (v == 0L) {
                    out.add(b.toByte())
                    return
                }
                out.add((b or 0x80).toByte())
            }
        }

        fun bytes(value: ByteArray) {
            varint(value.size)
            value.forEach { out.add(it) }
        }

        fun finish(): ByteArray = out.toByteArray()
    }

    private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var index = start
        while (index < data.size) {
            val b = data[index].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            index++
            if (b and 0x80 == 0) return result to index
            shift += 7
        }
        return result to index
    }
}
