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

package com.mwask.bat.backend.connect.command

import android.content.Context
import com.mwask.bat.backend.connect.ConnectHeaders
import com.mwask.bat.backend.connect.cluster.Device
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.net.Net
import org.json.JSONArray
import org.json.JSONObject

private val log = Logs.of(Logs.Module.REGISTER)

private const val SDK_VERSION = "harmony:4.81.1-1f3d48b7f"

object Registration {

    fun observerId(deviceId: String): String = "hobs_$deviceId".take(40)

    suspend fun observer(
        context: Context,
        spclient: String,
        deviceId: String,
        connectionId: String,
    ): Net.Response? {
        val url = "https://$spclient/connect-state/v1/devices/${observerId(deviceId)}"
        val headers = ConnectHeaders.of(context) + mapOf(
            "Accept" to "application/json",
            "X-Spotify-Connection-Id" to connectionId,
        )
        val body = JSONObject()
            .put("member_type", "CONNECT_STATE")
            .put(
                "device",
                JSONObject().put(
                    "device_info",
                    JSONObject().put(
                        "capabilities",
                        JSONObject()
                            .put("can_be_player", false)
                            .put("hidden", true)
                            .put("needs_full_player_state", true),
                    ),
                ),
            )
            .toString()
        val response = Net.request("PUT", url, headers, body, tag = "observer")
        log.i("observer ${observerId(deviceId)} -> ${response?.code ?: "no response"} ${response?.bytes ?: 0}B"
        )
        return response
    }

    const val REASON_NEW_DEVICE = "NEW_DEVICE"

    const val REASON_STATE_CHANGED = "PLAYER_STATE_CHANGED"

    const val REASON_BECAME_INACTIVE = "BECAME_INACTIVE"

    /**
     * Reads the account's connect cluster outright, the way the web player
     * does with GET connect-state/v1/cluster: it needs no dealer connection
     * and no connection id, so it is the fastest way to learn who is playing
     * right after launch - the first dealer frame can take seconds to arrive.
     */
    suspend fun cluster(
        context: Context,
        spclient: String,
    ): Net.Response? {
        val url = "https://$spclient/connect-state/v1/cluster"
        val headers = ConnectHeaders.of(context) + mapOf("Accept" to "application/json")
        val response = Net.request("GET", url, headers, null, tag = "cluster-get")
        log.i("cluster read -> ${response?.code ?: "no response"} ${response?.bytes ?: 0}B")
        return response
    }

    /**
     * Registers this device as a real connect-state player by PUTting its
     * device state, the same way native clients (librespot & friends) do.
     * The hidden hobs_ observer never shows up in the account's device list
     * and the track-playback register is what the web player uses, so this
     * PUT is what actually makes the phone selectable in "Listening on" and
     * dealer connection the server routes commands over. The answer is the
     * cluster, like the observer PUT.
     *
     * While this device plays, the same PUT carries `is_active: true` and a
     * full `player_state` inside the device block - the shape librespot
     * publishes - so the server makes this phone the account's active player
     * and every other client follows it.
     */
    suspend fun connectState(
        context: Context,
        spclient: String,
        deviceId: String,
        connectionId: String,
        reason: String = REASON_NEW_DEVICE,
        isActive: Boolean = false,
        playerState: JSONObject? = null,
        startedPlayingAt: Long = 0L,
    ): Net.Response? {
        val url = "https://$spclient/connect-state/v1/devices/$deviceId"
        val headers = ConnectHeaders.of(context) + mapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "X-Spotify-Connection-Id" to connectionId,
        )
        val device = JSONObject().put("device_info", deviceInfo(deviceId))
        if (playerState != null) device.put("player_state", playerState)
        val body = JSONObject()
            .put("member_type", "CONNECT_STATE")
            .put("put_state_reason", reason)
            .put("is_active", isActive)
            .put("client_side_timestamp", System.currentTimeMillis())
        if (startedPlayingAt > 0L) body.put("started_playing_at", startedPlayingAt)
        body.put("device", device)
        val response = Net.request("PUT", url, headers, body.toString(), tag = "member")
        log.i("member $deviceId -> ${response?.code ?: "no response"} ${response?.bytes ?: 0}B " +
                "active=$isActive reason=$reason state=${playerState != null}"
        )
        return response
    }

    /**
     * Removes this device from the connect cluster, the way librespot does
     * on shutdown, so "Listening on" on the other clients drops the phone
     * instead of showing a ghost that never plays again.
     */
    suspend fun unregister(
        context: Context,
        spclient: String,
        deviceId: String,
    ): Net.Response? {
        val url = "https://$spclient/connect-state/v1/devices/$deviceId"
        val response = Net.request(
            "DELETE",
            url,
            ConnectHeaders.of(context) + mapOf("Accept" to "application/json"),
            null,
            tag = "member",
        )
        log.i("member delete $deviceId -> ${response?.code ?: "no response"}")
        return response
    }

    suspend fun player(
        context: Context,
        spclient: String,
        deviceId: String,
        connectionId: String,
    ): Net.Response? {
        val url = "https://$spclient/track-playback/v1/devices"
        val info = trackDevice(deviceId)
        val body = JSONObject()
            .put("device", info)
            .put("outro_endcontent_snooping", false)
            .put("connection_id", connectionId)
            .put("client_version", SDK_VERSION)
            .put("volume", 65535)
            .toString()
        val headers = ConnectHeaders.of(context) + mapOf(
            "Accept" to "application/json",
            "Origin" to "https://open.spotify.com",
            "Referer" to "https://open.spotify.com/",
            "User-Agent" to Net.DESKTOP_UA,
        )
        val response = Net.postJson(url, body, headers, tag = "register")
        val parsed = response?.json()
        log.i("player $deviceId -> ${response?.code ?: "no response"} " +
                "seq=${parsed?.optInt("initial_seq_num") ?: -1} " +
                "license=${parsed?.optString("effective_license") ?: "-"}"
        )
        if (response != null && !response.ok) {
            log.w("player register failed, connectionId(${connectionId.length}B)=" +
                    "'${connectionId.takeLast(10)}' body=${response.body.take(180)}"
            )
        }
        if (response?.ok == true) {
            val updated = Net.request(
                "PUT",
                "$url/$deviceId",
                ConnectHeaders.of(context) + mapOf("Content-Type" to "application/json"),
                info.toString(),
                tag = "devinfo",
            )
            log.i("device info -> ${updated?.code ?: "no response"}")
        }
        return response
    }

    fun initialSeq(response: Net.Response?): Int =
        response?.json()?.optInt("initial_seq_num") ?: -1

    /** The device block of the track-playback register, verbatim web-player shape. */
    private fun trackDevice(deviceId: String): JSONObject = JSONObject()
        .put("brand", "spotify")
        .put("model", "web_player")
        .put("name", "SpotiBat")
        .put("device_id", deviceId)
        .put("device_type", "COMPUTER")
        .put("platform_name", "web_player")
        .put("platform_identifier", "web_player windows 10;chrome 150.0.0.0;desktop")
        .put("is_group", false)
        .put("is_public", false)
        .put("correlation_id", deviceId)
        .put("client_version", SDK_VERSION)
        .put("metadata", JSONObject())
        .put("capabilities", capabilities())

    /** The DeviceInfo block of a connect-state member, the way native clients publish it. */
    private fun deviceInfo(deviceId: String): JSONObject = JSONObject()
        .put("can_play", true)
        .put("volume", 65535)
        .put("name", "SpotiBat")
        .put("device_id", deviceId)
        .put("device_type", "COMPUTER")
        .put("device_software_version", ConnectHeaders.APP_VERSION)
        .put("client_id", Device.CLIENT_ID)
        .put("brand", "spotify")
        .put("model", "web_player")
        .put("capabilities", capabilities())

    private fun capabilities(): JSONObject = JSONObject()
        .put("can_be_player", true)
        .put("is_controllable", true)
        .put("is_observable", true)
        .put("needs_full_player_state", true)
        .put("supported_types", supportedTypes())

    private fun supportedTypes(): JSONArray = JSONArray()
        .put("audio/track")
        .put("video/track")
        .put("audio/episode")
        .put("audio/ad")
        .put("audio/chapter")
}
