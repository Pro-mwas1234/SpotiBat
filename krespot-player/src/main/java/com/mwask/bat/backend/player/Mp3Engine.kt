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

import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.net.Net

private val log = Logs.of(Logs.Module.MP3)

private const val MIME_PREFIX = "audio/"
private const val PROGRESS_STEP_MS = 250L

private class BytesSource(private val data: ByteArray) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= data.size) return -1
        val count = minOf(size.toLong(), data.size - position).toInt()
        System.arraycopy(data, position.toInt(), buffer, offset, count)
        return count
    }

    override fun getSize(): Long = data.size.toLong()

    override fun close() = Unit
}

internal class Mp3Engine(
    private val url: String,
    private val emit: (PlayerEvent) -> Unit,
) {

    @Volatile
    private var stopped = false

    @Volatile
    private var paused = false

    @Volatile
    private var positionMs = 0L

    @Volatile
    private var durationMs = 0L

    private var sink: AudioSink? = null

    val currentPositionMs: Long get() = positionMs

    fun stop() {
        stopped = true
        paused = false
    }

    fun pause() {
        paused = true
        sink?.pause()
    }

    fun resume() {
        paused = false
        sink?.resume()
    }

    fun duck(level: Float) {
        sink?.duck(level)
    }

    fun volume(level: Float) {
        sink?.volume(level)
    }

    fun seekTo(ms: Long) {
        log.i("seek ignored on a direct url, ${ms}ms requested")
    }

    suspend fun run() {
        stopped = false
        val bytes = withContext(Dispatchers.IO) {
            Net.blob(url, mapOf("referer" to "https://open.spotify.com/"), tag = "ad").also {
                log.i("ad bytes ${it?.code ?: "no response"} ${it?.size ?: 0}B")
            }
        }?.takeIf { it.ok }?.bytes
        if (bytes == null) {
            emit(PlayerEvent.Failed("ad download failed"))
            return
        }
        val extractor = MediaExtractor()
        val prepared = runCatching {
            extractor.setDataSource(BytesSource(bytes))
            val index = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty().startsWith(MIME_PREFIX)
            } ?: error("no audio track")
            extractor.selectTrack(index)
            extractor.getTrackFormat(index)
        }
        val format = prepared.getOrElse {
            log.e("extractor ${it.javaClass.simpleName}: ${it.message}")
            extractor.release()
            emit(PlayerEvent.Failed("ad container unreadable"))
            return
        }
        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        durationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION) / 1000L
        } else {
            0L
        }
        log.i("ad $mime ${sampleRate}Hz ${channels}ch ${durationMs}ms ${bytes.size}B")

        val codec = runCatching {
            MediaCodec.createDecoderByType(mime).also {
                it.configure(format, null, null, 0)
                it.start()
            }
        }.getOrElse {
            log.e("decoder ${it.javaClass.simpleName}: ${it.message}")
            extractor.release()
            emit(PlayerEvent.Failed("ad decoder unavailable"))
            return
        }
        val audio = AudioSink(sampleRate, channels)
        sink = audio
        audio.start()
        emit(PlayerEvent.Started())

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var reported = 0L
        try {
            while (!stopped && !outputDone) {
                while (paused && !stopped) delay(50)
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)
                        val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                while (true) {
                    val index = codec.dequeueOutputBuffer(info, 10_000)
                    if (index < 0) break
                    if (info.size > 0) write(codec, index, audio, info)
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                        break
                    }
                }
                if (positionMs - reported >= PROGRESS_STEP_MS) {
                    reported = positionMs
                    emit(PlayerEvent.Progress(positionMs, durationMs))
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            log.e("ad run failed ${failure.javaClass.simpleName}: ${failure.message}")
            emit(PlayerEvent.Failed(failure.message.orEmpty()))
        } finally {
            audio.release()
            sink = null
            runCatching {
                codec.stop()
                codec.release()
            }
            extractor.release()
        }

        if (!stopped && outputDone) {
            log.i("ad ended at ${positionMs}ms of ${durationMs}ms")
            emit(PlayerEvent.Ended())
        }
    }

    private fun write(codec: MediaCodec, index: Int, audio: AudioSink, info: MediaCodec.BufferInfo) {
        val out = codec.getOutputBuffer(index) ?: return
        out.position(info.offset)
        out.limit(info.offset + info.size)
        val pcm = ShortArray(info.size / 2)
        out.asShortBuffer().get(pcm)
        if (pcm.isEmpty()) return
        audio.write(pcm, pcm.size)
        positionMs = info.presentationTimeUs / 1000L
    }
}
