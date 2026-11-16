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

package com.mwask.bat.backend.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.mwask.bat.backend.core.log.Logs

private val log = Logs.of(Logs.Module.SINK)

internal class AudioSink(
    private val sampleRate: Int,
    private val channels: Int,
) {

    @Volatile
    private var track: AudioTrack = build()

    @Volatile
    private var held = false

    @Volatile
    private var rate = 1f

    /** Audio-focus ducking factor, combined with [userLevel] on the track. */
    @Volatile
    private var duckLevel = 1f

    /** User volume factor, combined with [duckLevel] on the track. */
    @Volatile
    private var userLevel = 1f

    private fun build(): AudioTrack {
        val channelMask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val min = AudioTrack.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        val bytes = if (min > 0) min * 8 else 65_536
        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build(),
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        log.i("sink ${sampleRate}Hz ${channels}ch buffer=${bytes}B min=${min}B state=${created.state}")
        return created
    }

    fun start() {
        held = false
        applyVolume()
        val played = runCatching { track.play() }
        val failure = played.exceptionOrNull()
        if (failure == null) {
            log.i("sink play state=${track.playState} underruns=${underruns()}")
        } else {
            log.w("sink play refused ${failure.javaClass.simpleName}: ${failure.message} state=${track.state}")
        }
    }

    fun pause() {
        held = true
        runCatching { track.pause() }
    }

    fun resume() {
        held = false
        runCatching { track.play() }
    }

    fun duck(level: Float) {
        duckLevel = level.coerceIn(0f, 1f)
        applyVolume()
    }

    fun volume(level: Float) {
        userLevel = level.coerceIn(0f, 1f)
        applyVolume()
    }

    private fun applyVolume() {
        runCatching { track.setVolume(duckLevel * userLevel) }
    }

    fun flush() {
        runCatching {
            track.pause()
            track.flush()
            if (!held) track.play()
        }
    }

    fun write(pcm: ShortArray, count: Int): Int = runCatching { track.write(pcm, 0, count) }.getOrElse {
        log.w("sink write threw ${it.javaClass.simpleName}: ${it.message}")
        -1
    }

    fun alive(): Boolean = track.playState == AudioTrack.PLAYSTATE_PLAYING

    fun underruns(): Int = runCatching { track.underrunCount }.getOrDefault(-1)

    fun rate(mult: Float) {
        rate = mult.coerceIn(0.5f, 3.5f)
        runCatching { track.playbackRate = (sampleRate * rate).toInt().coerceAtLeast(1) }
    }

    fun rebuild() {
        runCatching {
            track.pause()
            track.flush()
            track.release()
        }
        track = build()
        applyVolume()
        if (rate != 1f) rate(rate)
        if (!held) runCatching { track.play() }
        log.i("sink rebuilt state=${track.state} play=${track.playState}")
    }

    fun release() {
        runCatching {
            track.pause()
            track.flush()
            track.release()
        }
    }
}
