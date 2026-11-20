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

package com.mwask.bat.backend.connect.state

import android.content.Context
import com.mwask.bat.backend.connect.ConnectHeaders
import com.mwask.bat.backend.connect.command.CommandTrack
import com.mwask.bat.backend.core.net.Net
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import com.mwask.bat.backend.core.log.Logs

private val log = Logs.of(Logs.Module.MELODY)

private const val SDK_ID = "harmony:4.81.1-1f3d48b7f"
private const val PLATFORM = "web_player windows 10;chrome 150.0.0.0;desktop"
private const val CLIENT_VERSION = "0.0.0"
private const val KEY_SYSTEM = "com.widevine.alpha"

object MediaEvent {
    const val START = 0
    const val PAUSED = 2
    const val PLAYING = 3
    const val UNKNOWN_4 = 4
    const val KEY_SYSTEM = 7
    const val AUDIO_PARAMS = 9
    const val POSITION = 10
}

class Melody(
    private val context: Context,
    private val spclient: () -> String,
    private val sessionId: () -> String,
) {

    private val sequence = AtomicInteger(0)

    fun reset() {
        sequence.set(0)
    }

    suspend fun playbackStart(track: CommandTrack, stateId: String, positionMs: Long): Boolean {
        val message = JSONObject()
            .put("play_track", track.uri)
            .put("file_id", track.best()?.fileId.orEmpty())
            .put("playback_id", stateId)
            .put("session_id", sessionId())
            .put("ms_start_position", positionMs)
            .put("initially_paused", false)
            .put("client_id", "")
            .put("correlation_id", "")
            .put("feature_identifier", "")
        return send("jssdk_playback_start", message)
    }

    /**
     * Closes a play session the way the web player does: a
     * track_stream_verification (which is what makes the play show up in
     * listening history on this account) plus the jssdk_playback_stats row.
     */
    suspend fun streamFinished(
        track: CommandTrack,
        stateId: String,
        nextPlaybackId: String,
        internalPlayId: Int,
        startPositionMs: Long,
        endPositionMs: Long,
        durationMs: Long,
        startWallClock: Long,
        reasonStart: String,
        reasonEnd: String,
        msPlayed: Long,
    ): Boolean {
        val verification = JSONObject()
            .put("play_track", track.uri)
            .put("playback_id", stateId)
            .put("ms_played", msPlayed)
            .put("ms_nominal_played", msPlayed)
            .put("session_id", sessionId())
            .put("sequence_id", 0)
            .put("playback_service", "track-playback")
        if (nextPlaybackId.isNotBlank()) verification.put("next_playback_id", nextPlaybackId)
        return sendBatch(
            "track_stream_verification" to verification,
            "jssdk_playback_stats" to statsMessage(
                track, stateId, internalPlayId, startPositionMs, endPositionMs,
                durationMs, startWallClock, reasonStart, reasonEnd, msPlayed,
            ),
        )
    }

    private fun statsMessage(
        track: CommandTrack,
        stateId: String,
        internalPlayId: Int,
        startPositionMs: Long,
        endPositionMs: Long,
        durationMs: Long,
        startWallClock: Long,
        reasonStart: String,
        reasonEnd: String,
        msPlayed: Long,
    ): JSONObject {
        val direct = track.isDirect
        val fileId = track.best()?.fileId.orEmpty()
        val bitrate = track.reportBitrate
        val audioFormat = if (direct) "mp3" else "mp4"
        return JSONObject()
            .put("play_track", track.uri)
            .put("file_id", fileId)
            .put("playback_id", stateId)
            .put("internal_play_id", internalPlayId.toString())
            .put("memory_cached", false)
            .put("persistent_cached", false)
            .put("audio_format", audioFormat)
            .put("video_format", "")
            .put("manifest_id", fileId)
            .put("protected", !direct)
            .put("key_system", if (direct) "" else KEY_SYSTEM)
            .put("key_system_impl", if (direct) "" else "native")
            .put("start_time", startWallClock)
            .put("end_time", System.currentTimeMillis())
            .put("ms_played", msPlayed)
            .put("ms_nominal_played", msPlayed)
            .put("ms_file_duration", durationMs)
            .put("ms_actual_duration", durationMs)
            .put("ms_metadata_duration", 0)
            .put("ms_start_position", startPositionMs)
            .put("ms_end_position", endPositionMs)
            .put("ms_initial_rebuffer", 0)
            .put("ms_seek_rebuffer", 0)
            .put("ms_stall_rebuffer", 0)
            .put("ms_played_per_surface", JSONObject())
            .put("ms_played_visible", 0)
            .put("n_stalls", 0)
            .put("n_rendition_upgrade", 0)
            .put("n_rendition_downgrade", 0)
            .put("bps_bandwidth_max", 0)
            .put("bps_bandwidth_min", 0)
            .put("bps_bandwidth_avg", 0)
            .put("n_seekback", 0)
            .put("n_seekforward", 0)
            .put("audio_start_bitrate", bitrate)
            .put("video_start_bitrate", JSONObject.NULL)
            .put("start_bitrate", bitrate)
            .put("time_weighted_bitrate", 0)
            .put("reason_start", reasonStart)
            .put("reason_end", reasonEnd)
            .put("initially_paused", false)
            .put("had_error", false)
            .put("n_warnings", 0)
            .put("n_navigator_offline", 0)
            .put("session_id", sessionId())
            .put("sequence_id", 0)
            .put("client_id", "")
            .put("correlation_id", "")
            .put("n_dropped_video_frames", 0)
            .put("n_total_video_frames", 0)
            .put("resolution_max", 0)
            .put("resolution_min", 0)
            .put("total_bytes", 0)
            .put("strategy", "MSE")
            .put("ms_played_per_audio_format", JSONObject().put("$audioFormat;$bitrate", msPlayed))
            .put("ms_played_per_video_format", JSONObject())
    }

    suspend fun start(track: CommandTrack, stateId: String, positionMs: Long, paused: Boolean): Boolean {
        reset()
        val media = JSONObject()
            .put("mu", mediaUrl(track))
            .put("pi", stateId)
            .put("mt", "audio")
        val state = JSONObject()
            .put("pt", "harmony")
            .put("md", 0)
            .put("ip", paused)
            .put("pp", positionMs)
            .put("sl", "unknown")
            .put("sp", if (paused) 0 else 1)
            .put("vo", 100)
            .put("ns", "4g")
            .put("br", 0)
            .put("pd", "device")
            .put("ao", true)
            .put("st", "unknown")
            .put("su", "unknown")
        val event = event(MediaEvent.START)
            .put("ms", media)
            .put("ps", state)
            .put("ur", track.uri)
            .put("pc", "")
            .put("xt", "audio")
        return send("media_playback_event_v2", events(event))
    }

    suspend fun keysystem(): Boolean {
        val event = event(MediaEvent.KEY_SYSTEM)
            .put("ks", JSONObject().put("nm", KEY_SYSTEM).put("im", "native"))
        return send("media_playback_event_v2", events(event))
    }

    suspend fun speed(playing: Boolean): Boolean {
        val event = event(if (playing) MediaEvent.PLAYING else MediaEvent.PAUSED)
            .put("ps", JSONObject().put("sp", if (playing) 1 else 0))
        return send("media_playback_event_v2", events(event))
    }

    suspend fun audioParams(track: CommandTrack, positionMs: Long): Boolean {
        val bitrate = track.reportBitrate
        val codec = if (track.isDirect) "audio/mpeg" else "audio/mp4; codecs=\"mp4a.40.2\""
        val event = event(MediaEvent.AUDIO_PARAMS)
            .put("ps", JSONObject().put("pp", positionMs).put("br", bitrate))
            .put("ap", JSONObject().put("qi", "0").put("cc", codec).put("ab", bitrate).put("xb", bitrate))
            .put("fr", 0)
            .put("ty", "audio")
            .put("rn", "abr")
        return send("media_playback_event_v2", events(event))
    }

    suspend fun position(positionMs: Long, reason: Int): Boolean {
        val event = event(MediaEvent.POSITION)
            .put("ps", JSONObject().put("pp", positionMs))
            .put("rn", reason)
            .put("bt", -1)
            .put("rf", 0)
            .put("df", 0)
        return send("media_playback_event_v2", events(event))
    }

    suspend fun ended(mediaDurationMs: Long, baselineMs: Long): Boolean {
        val event = event(MediaEvent.POSITION)
            .put("ps", JSONObject().put("md", mediaDurationMs))
            .put("rn", 0)
            .put("bt", baselineMs)
            .put("rf", 0)
            .put("df", 0)
        return send("media_playback_event_v2", events(event))
    }

    private fun mediaUrl(track: CommandTrack): String {
        val direct = track.bestDirect()?.url
        if (track.isDirect && !direct.isNullOrBlank()) return direct
        val fileId = track.best()?.fileId.orEmpty()
        return if (fileId.isBlank()) "" else "spotify-audio://$fileId"
    }

    private fun event(type: Int): JSONObject = JSONObject()
        .put("sn", sequence.getAndIncrement())
        .put("ct", System.currentTimeMillis())
        .put("ms", JSONObject())
        .put("et", type)

    private fun events(vararg events: JSONObject): JSONObject {
        val array = JSONArray()
        events.forEach { array.put(it) }
        val device = JSONObject()
            .put("manufacturer", "spotify")
            .put("model", "web_player")
            .put("type", "computer")
        val context = JSONObject()
            .put("device", device)
            .put("os", JSONObject().put("name", "").put("version", ""))
        return JSONObject().put("events", array).put("context", context)
    }

    private suspend fun send(type: String, message: JSONObject): Boolean =
        sendBatch(type to message)

    private suspend fun sendBatch(vararg messages: Pair<String, JSONObject>): Boolean {
        val host = spclient()
        if (host.isBlank()) {
            log.w("no spclient host")
            return false
        }
        val array = JSONArray()
        messages.forEach { (type, message) ->
            array.put(JSONObject().put("type", type).put("message", message))
        }
        val payload = JSONObject()
            .put("messages", array)
            .put("sdk_id", SDK_ID)
            .put("platform", PLATFORM)
            .put("client_version", CLIENT_VERSION)
        val url = "https://$host/melody/v1/msg/batch"
        val headers = ConnectHeaders.of(context) +
            mapOf("Content-Type" to "text/plain;charset=UTF-8")
        val response = Net.postJson(url, payload.toString(), headers, tag = "melody")
        val ok = response?.ok == true
        if (!ok) {
            val names = messages.joinToString(",") { it.first }
            log.w("melody $names failed ${response?.code ?: "no response"}")
        }
        return ok
    }
}
