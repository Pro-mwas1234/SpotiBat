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
import java.util.UUID
import com.mwask.bat.backend.connect.ConnectHeaders
import com.mwask.bat.backend.connect.cluster.ClusterTrack
import com.mwask.bat.backend.connect.cluster.QUEUE_PROVIDER
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.net.Net
import org.json.JSONArray
import org.json.JSONObject

private val log = Logs.of(Logs.Module.COMMAND)

object CommandClient {

    suspend fun play(
        context: Context,
        spclient: String,
        deviceId: String,
        contextUri: String,
        trackUri: String,
        trackUid: String,
        trackIndex: Int,
        feature: String = "playlist",
        pageInstanceId: String = "",
        targetDeviceId: String = "",
    ): Net.Response? {
        val skipTo = JSONObject()
        if (trackUri.isNotBlank() && trackUri != contextUri) {
            skipTo.put("track_uri", trackUri)
            if (trackIndex >= 0) skipTo.put("track_index", trackIndex)
            if (trackUid.isNotBlank()) skipTo.put("track_uid", trackUid)
        }
        val options = JSONObject()
            .put("license", "tft")
            .put("skip_to", skipTo)
            .put("player_options_override", JSONObject())
        val pageIds = JSONArray()
        if (pageInstanceId.isNotBlank()) pageIds.put(pageInstanceId)
        val loggingParams = JSONObject()
            .put("page_instance_ids", pageIds)
            .put("interaction_ids", JSONArray().put(UUID.randomUUID().toString()))
            .put("command_id", UUID.randomUUID().toString().replace("-", ""))
        val command = JSONObject()
            .put(
                "context",
                JSONObject()
                    .put("uri", contextUri)
                    .put("url", "context://$contextUri")
                    .put("metadata", JSONObject()),
            )
            .put(
                "play_origin",
                JSONObject()
                    .put("feature_identifier", feature)
                    .put("referrer_identifier", "deeplink"),
            )
            .put("options", options)
            .put("logging_params", loggingParams)
            .put("endpoint", "play")

        val to = targetDeviceId.ifBlank { deviceId }
        val url = "https://$spclient/connect-state/v1/player/command/from/$deviceId/to/$to"
        val body = JSONObject().put("command", command).toString()
        val response = Net.postJson(url, body, ConnectHeaders.of(context), tag = "play")
        log.i("play '$trackUri' in '$contextUri' -> ${response?.code ?: "no response"}")
        return response
    }

    suspend fun addToQueue(
        context: Context,
        spclient: String,
        deviceId: String,
        trackUri: String,
    ): Net.Response? {
        val command = JSONObject()
            .put(
                "track",
                JSONObject()
                    .put("uri", trackUri)
                    .put("metadata", JSONObject().put("is_queued", "true"))
                    .put("provider", QUEUE_PROVIDER),
            )
            .put("logging_params", JSONObject().put("command_id", commandId()))
            .put("endpoint", "add_to_queue")
        val url = "https://$spclient/connect-state/v1/player/command/from/$deviceId/to/$deviceId"
        val body = JSONObject().put("command", command).toString()
        val response = Net.postJson(url, body, ConnectHeaders.of(context), tag = "add_to_queue")
        log.i("queue += '$trackUri' -> ${response?.code ?: "no response"}")
        return response
    }

    suspend fun setQueue(
        context: Context,
        spclient: String,
        deviceId: String,
        tracks: List<ClusterTrack>,
        queueRevision: String,
    ): Net.Response? {
        val next = JSONArray()
        tracks.forEach { next.put(trackNode(it)) }
        val command = JSONObject()
            .put("next_tracks", next)
            .put("prev_tracks", JSONArray())
            .put("queue_revision", queueRevision)
            .put("logging_params", JSONObject().put("command_id", commandId()))
            .put("endpoint", "set_queue")
        val url = "https://$spclient/connect-state/v1/player/command/from/$deviceId/to/$deviceId"
        val body = JSONObject().put("command", command).toString()
        val response = Net.postJson(url, body, ConnectHeaders.of(context), tag = "set_queue")
        log.i("queue = ${tracks.size} tracks rev=${queueRevision.take(12)} -> ${response?.code ?: "no response"}")
        return response
    }

    suspend fun transfer(
        context: Context,
        spclient: String,
        localDeviceId: String,
        targetDeviceId: String,
        restorePaused: String = RESTORE,
    ): Net.Response? {
        if (localDeviceId.isBlank() || targetDeviceId.isBlank()) {
            log.w("transfer: local='$localDeviceId' target='$targetDeviceId'")
            return null
        }
        val body = JSONObject()
            .put("transfer_options", JSONObject().put("restore_paused", restorePaused))
            .put("command_id", commandId())
        val url = "https://$spclient/connect-state/v1/connect/transfer/from/$localDeviceId/to/$targetDeviceId"
        val response = Net.postJson(url, body.toString(), ConnectHeaders.of(context), tag = "transfer")
        log.i("transfer to ${targetDeviceId.take(8)} restore=$restorePaused -> ${response?.code ?: "no response"}")
        return response
    }

    suspend fun volume(
        context: Context,
        spclient: String,
        localDeviceId: String,
        targetDeviceId: String,
        level: Int,
    ): Net.Response? {
        if (localDeviceId.isBlank() || targetDeviceId.isBlank()) return null
        val clamped = level.coerceIn(0, VOLUME_MAX)
        val body = JSONObject().put("volume", clamped)
        val url = "https://$spclient/connect-state/v1/connect/volume/from/$localDeviceId/to/$targetDeviceId"
        val response = Net.putJson(url, body.toString(), ConnectHeaders.of(context), tag = "volume")
        log.i("volume ${clamped}/$VOLUME_MAX on ${targetDeviceId.take(8)} -> ${response?.code ?: "no response"}")
        return response
    }

    suspend fun setPaused(
        context: Context,
        spclient: String,
        localDeviceId: String,
        targetDeviceId: String,
        paused: Boolean,
    ): Net.Response? {
        val endpoint = if (paused) "pause" else "resume"
        val command = JSONObject()
            .put("logging_params", JSONObject().put("command_id", commandId()))
            .put("endpoint", endpoint)
        val url = "https://$spclient/connect-state/v1/player/command/from/$localDeviceId/to/$targetDeviceId"
        val body = JSONObject().put("command", command).toString()
        val response = Net.postJson(url, body, ConnectHeaders.of(context), tag = endpoint)
        log.i("$endpoint on ${targetDeviceId.take(8)} -> ${response?.code ?: "no response"}")
        return response
    }

    suspend fun seekTo(
        context: Context,
        spclient: String,
        localDeviceId: String,
        targetDeviceId: String,
        positionMs: Long,
    ): Net.Response? {
        val command = JSONObject()
            .put("value", positionMs.coerceAtLeast(0L))
            .put("logging_params", JSONObject().put("command_id", commandId()))
            .put("endpoint", "seek_to")
        val url = "https://$spclient/connect-state/v1/player/command/from/$localDeviceId/to/$targetDeviceId"
        val body = JSONObject().put("command", command).toString()
        val response = Net.postJson(url, body, ConnectHeaders.of(context), tag = "seek_to")
        log.i("seek_to ${positionMs}ms on ${targetDeviceId.take(8)} -> ${response?.code ?: "no response"}")
        return response
    }

    suspend fun skip(
        context: Context,
        spclient: String,
        localDeviceId: String,
        targetDeviceId: String,
        next: Boolean,
    ): Net.Response? {
        val endpoint = if (next) "skip_next" else "skip_prev"
        val command = JSONObject()
            .put("logging_params", JSONObject().put("command_id", commandId()))
            .put("endpoint", endpoint)
        val url = "https://$spclient/connect-state/v1/player/command/from/$localDeviceId/to/$targetDeviceId"
        val body = JSONObject().put("command", command).toString()
        val response = Net.postJson(url, body, ConnectHeaders.of(context), tag = endpoint)
        log.i("$endpoint on ${targetDeviceId.take(8)} -> ${response?.code ?: "no response"}")
        return response
    }

    /**
     * Flips one of the player options on the target device: shuffling_context,
     * repeating_context or repeating_track. Sent as a player command so the
     * cluster state (and every listener) follows.
     */
    suspend fun setPlayerOption(
        context: Context,
        spclient: String,
        localDeviceId: String,
        targetDeviceId: String,
        endpoint: String,
        value: Boolean,
    ): Net.Response? {
        val command = JSONObject()
            .put("value", value)
            .put("logging_params", JSONObject().put("command_id", commandId()))
            .put("endpoint", endpoint)
        val url = "https://$spclient/connect-state/v1/player/command/from/$localDeviceId/to/$targetDeviceId"
        val body = JSONObject().put("command", command).toString()
        val response = Net.postJson(url, body, ConnectHeaders.of(context), tag = endpoint)
        log.i("$endpoint=$value on ${targetDeviceId.take(8)} -> ${response?.code ?: "no response"}")
        return response
    }

    const val ENDPOINT_SHUFFLE = "set_shuffling_context"
    const val ENDPOINT_REPEAT_CONTEXT = "set_repeating_context"
    const val ENDPOINT_REPEAT_TRACK = "set_repeating_track"
    const val ENDPOINT_SET_OPTIONS = "set_options"
    const val ENDPOINT_UPDATE_CONTEXT = "update_context"
    const val ENHANCEMENT_NONE = "NONE"
    const val ENHANCEMENT_SMART = "RECOMMENDATION"

    /**
     * One set_options command carrying every mode that changed: shuffling_context,
     * repeating_context, repeating_track and the context_enhancement mode that
     * stands for smart shuffle. Blank booleans stay out of the payload.
     */
    suspend fun setOptions(
        context: Context,
        spclient: String,
        localDeviceId: String,
        targetDeviceId: String,
        shuffle: Boolean? = null,
        enhancement: String? = null,
        repeatContext: Boolean? = null,
        repeatTrack: Boolean? = null,
    ): Net.Response? {
        val command = JSONObject()
        if (shuffle != null) command.put("shuffling_context", shuffle)
        if (enhancement != null || shuffle != null) {
            val modes = JSONObject()
            if (enhancement != null) modes.put("context_enhancement", enhancement)
            if (modes.length() > 0) command.put("modes", modes)
        }
        if (repeatContext != null) command.put("repeating_context", repeatContext)
        if (repeatTrack != null) command.put("repeating_track", repeatTrack)
        command
            .put("logging_params", JSONObject().put("command_id", commandId()))
            .put("endpoint", ENDPOINT_SET_OPTIONS)
        val to = targetDeviceId.ifBlank { localDeviceId }
        val url = "https://$spclient/connect-state/v1/player/command/from/$localDeviceId/to/$to"
        val body = JSONObject().put("command", command).toString()
        val response = Net.postJson(url, body, ConnectHeaders.of(context), tag = ENDPOINT_SET_OPTIONS)
        log.i(
            "set_options shuffle=$shuffle enhancement=$enhancement " +
                "repeatContext=$repeatContext repeatTrack=$repeatTrack on ${to.take(8)} -> ${response?.code ?: "no response"}"
        )
        return response
    }

    /**
     * Tells the server to (re)read the playback context through a lens: the
     * smart shuffle flow points the context url at
     * enhanced rows the lens adds.
     */
    suspend fun updateContext(
        context: Context,
        spclient: String,
        localDeviceId: String,
        targetDeviceId: String,
        contextUri: String,
        lens: String? = null,
    ): Net.Response? {
        val url = if (lens.isNullOrBlank()) "context://$contextUri"
        else "context://$contextUri?spotify-apply-lenses=$lens"
        val command = JSONObject()
            .put("context", JSONObject().put("url", url))
            .put("logging_params", JSONObject().put("command_id", commandId()))
            .put("endpoint", ENDPOINT_UPDATE_CONTEXT)
        val to = targetDeviceId.ifBlank { localDeviceId }
        val endpoint = "https://$spclient/connect-state/v1/player/command/from/$localDeviceId/to/$to"
        val body = JSONObject().put("command", command).toString()
        val response = Net.postJson(endpoint, body, ConnectHeaders.of(context), tag = ENDPOINT_UPDATE_CONTEXT)
        log.i("update_context '${contextUri.takeLast(20)}' lens=$lens on ${to.take(8)} -> ${response?.code ?: "no response"}")
        return response
    }

    const val RESTORE = "restore"
    const val VOLUME_MAX = 65535

    private fun commandId(): String = UUID.randomUUID().toString().replace("-", "")

    private fun trackNode(track: ClusterTrack): JSONObject {
        val metadata = runCatching { JSONObject(track.raw) }.getOrElse { JSONObject() }
        if (metadata.length() == 0) {
            metadata
                .put("title", track.title)
                .put("artist_name", track.artistName)
                .put("artist_uri", track.artistUri)
                .put("album_title", track.albumTitle)
                .put("album_uri", track.albumUri)
                .put("image_url", track.imageUrl)
                .put("duration", track.durationMs.toString())
                .put("is_explicit", track.explicit.toString())
        }
        if (track.isQueued) metadata.put("is_queued", "true")
        return JSONObject()
            .put("uri", track.uri)
            .put("uid", track.uid)
            .put("metadata", metadata)
            .put("provider", track.provider.ifBlank { QUEUE_PROVIDER })
    }
}
