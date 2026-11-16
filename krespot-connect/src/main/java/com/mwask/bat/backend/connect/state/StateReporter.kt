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
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.mwask.bat.backend.connect.ConnectHeaders
import com.mwask.bat.backend.connect.command.ContentType
import com.mwask.bat.backend.connect.command.PlaybackCommand
import com.mwask.bat.backend.connect.command.CommandParser
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.net.Net
import org.json.JSONObject

private val log = Logs.of(Logs.Module.STATE)

object DebugSource {
    const val TRACK_DATA_FINALIZED = "track_data_finalized"
    const val BEFORE_TRACK_LOAD = "before_track_load"
    const val STARTED_PLAYING = "started_playing"
    const val PLAYED_THRESHOLD_REACHED = "played_threshold_reached"
    const val SEEK = "seek"
    const val PAUSE = "pause"
    const val RESUME = "resume"
}

data class Snapshot(
    val stateMachineId: String,
    val stateId: String,
    val paused: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val speed: Double,
    val previousPositionMs: Long,
    val debugSource: String,
    val mediaType: String,
    val format: String,
    val bitrate: Int,
    val quality: String,
    val videoOn: Boolean = false,
) {
    private val formatValue: Any get() = format.toIntOrNull() ?: format

    companion object {

        fun of(
            command: PlaybackCommand,
            debugSource: String,
            positionMs: Long,
            previousPositionMs: Long = positionMs,
            speed: Double = 1.0,
            paused: Boolean = false,
            durationMs: Long = 0L,
            videoOn: Boolean = false,
            format: String = "",
            bitrate: Int = 0,
            quality: String = "",
        ): Snapshot? {
            val track = command.currentTrack ?: return null
            val state = command.currentState ?: return null
            val direct = track.isDirect
            return Snapshot(
                stateMachineId = command.stateMachineId,
                stateId = state.stateId,
                paused = paused,
                positionMs = positionMs,
                durationMs = if (durationMs > 0L) durationMs else track.durationMs,
                speed = speed,
                previousPositionMs = previousPositionMs,
                debugSource = debugSource,
                mediaType = "AUDIO",
                format = format.ifBlank { track.reportFormat },
                bitrate = bitrate.takeIf { it > 0 } ?: track.reportBitrate,
                quality = quality.ifBlank { track.reportQuality },
                videoOn = videoOn && !direct,
            )
        }
    }
}

class StateReporter(
    private val context: Context,
    private val deviceId: String,
    private val spclient: () -> String,
) {

    private val sequence = AtomicInteger(0)

    private val gate = Mutex()

    fun reset() {
        sequence.set(0)
    }

    fun seed(seq: Int) {
        if (seq <= 0) return
        sequence.set(seq)
        log.i("seq seeded at $seq, the next report is ${seq + 1}")
    }

    suspend fun report(snapshot: Snapshot): PlaybackCommand? = gate.withLock { send(snapshot) }

    private suspend fun send(snapshot: Snapshot): PlaybackCommand? {
        val host = spclient()
        if (host.isBlank()) {
            log.w("no spclient host")
            return null
        }
        val formatValue: Any = snapshot.format.toIntOrNull() ?: snapshot.format
        val subState = JSONObject()
            .put("playback_speed", snapshot.speed)
            .put("position", snapshot.positionMs)
            .put("duration", snapshot.durationMs)
            .put("media_type", snapshot.mediaType)
            .put("bitrate", snapshot.bitrate)
            .put("audio_quality", snapshot.quality)
            .put("format", formatValue)
            .put("is_video_on", snapshot.videoOn)

        val seq = sequence.incrementAndGet()
        val body = JSONObject()
            .put("seq_num", seq)
            .put(
                "state_ref",
                JSONObject()
                    .put("state_machine_id", snapshot.stateMachineId)
                    .put("state_id", snapshot.stateId)
                    .put("paused", snapshot.paused),
            )
            .put("sub_state", subState)
            .put("previous_position", snapshot.previousPositionMs)
            .put("debug_source", snapshot.debugSource)

        val url = "https://$host/track-playback/v1/devices/$deviceId/state"
        val response = Net.request("PUT", url, ConnectHeaders.of(context), body.toString(), tag = "state")
        if (response?.ok != true) {
            log.w("state failed ${response?.code ?: "no response"} ${response?.body?.take(200).orEmpty()}")
            return null
        }
        val next = response.body
            .takeIf { it.trimStart().startsWith("{") }
            ?.let { CommandParser.parse(it) }
        val ad = if (next?.currentTrack?.contentType == ContentType.AD) " AD" else ""
        log.i("state seq=$seq ${snapshot.debugSource} pos=${snapshot.positionMs} " +
                "paused=${snapshot.paused} next=${next?.stateMachineId?.take(12) ?: "none"} " +
                "ref=${next?.stateIndex ?: -1}/${next?.states?.size ?: 0} " +
                "'${next?.currentTrack?.name.orEmpty()}'$ad"
        )
        return next
    }
}
