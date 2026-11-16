/*
 *  _   __                          _   
 * | | / /                         | |  
 * | |/ / _ __ ___  ___ _ __   ___ | |_ 
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
 * Copying, modifying, distributing, or integrating this module (into whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.mwask.bat.backend.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.net.Net
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch

private val log = Logs.of(Logs.Module.PLAYER)

private const val PROGRESS_MS = 500L

internal class AudioDrmCallback(
    private val context: Context,
    private val licenseUrl: suspend () -> String,
) : MediaDrmCallback {

    override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): MediaDrmCallback.Response {
        val url = runBlocking { licenseUrl() }
        val response = runBlocking {
            Net.postBlob(
                url,
                request.data,
                "application/octet-stream",
                PlayerHeaders.license(context),
                tag = "audio-license",
            )
        }
        val body = response?.takeIf { it.ok }
        if (body == null) {
            if (response?.code != 403) LicenseGate.hold(response?.retryAfterSeconds ?: 0L)
            throw IOException("audio licence ${response?.code ?: "failed"}")
        }
        LicenseGate.clear()
        log.i("audio licence ${body.bytes.size}B ${licencePolicy(body.bytes)}")
        return MediaDrmCallback.Response(body.bytes)
    }

    override fun executeProvisionRequest(uuid: UUID, request: ExoMediaDrm.ProvisionRequest): MediaDrmCallback.Response {
        val signed = String(request.data, Charsets.UTF_8)
        val separator = if (request.defaultUrl.contains("?")) "&" else "?"
        val url = request.defaultUrl + separator + "signedRequest=" + java.net.URLEncoder.encode(signed, "UTF-8")
        val response = runBlocking {
            Net.postBlob(url, ByteArray(0), "application/octet-stream", emptyMap(), tag = "provision")
        }
        val body = response?.takeIf { it.ok } ?: throw IOException("provision ${response?.code ?: "failed"}")
        return MediaDrmCallback.Response(body.bytes)
    }

    private fun licencePolicy(bytes: ByteArray): String = runCatching {
        var canPlay = -1
        var canPersist = -1
        var canRenew = -1
        var licenseDuration = -1L
        var playbackDuration = -1L
        var policy: ByteArray? = null
        val root = Reader(bytes)
        while (!root.done()) {
            val (field, wire) = root.tag()
            when {
                field == 2 && wire == 2 -> policy = root.bytes()
                else -> root.skip(wire)
            }
        }
        policy?.let { raw ->
            val reader = Reader(raw)
            while (!reader.done()) {
                val (field, wire) = reader.tag()
                when {
                    field == 1 && wire == 0 -> canPlay = reader.varint().toInt()
                    field == 2 && wire == 0 -> canPersist = reader.varint().toInt()
                    field == 3 && wire == 0 -> canRenew = reader.varint().toInt()
                    field == 5 && wire == 0 -> playbackDuration = reader.varint()
                    field == 6 && wire == 0 -> licenseDuration = reader.varint()
                    else -> reader.skip(wire)
                }
            }
        }
        "play=$canPlay persist=$canPersist renew=$canRenew " +
            "licenseDur=${licenseDuration}s playbackDur=${playbackDuration}s"
    }.getOrElse { "policy unreadable" }
}

internal class AudioEngine(
    private val context: Context,
    private val spclient: () -> String,
    private val scope: CoroutineScope,
    private val onEvent: (PlayerEvent) -> Unit,
) {

    private val main = Handler(Looper.getMainLooper())

    private var player: ExoPlayer? = null

    @Volatile
    private var started = false

    @Volatile
    private var announced = false

    @Volatile
    private var lastItemUri = ""

    private val itemDurations = java.util.concurrent.ConcurrentHashMap<String, Long>()

    @Volatile
    var durationMs: Long = 0L
        private set

    val currentUri: String
        get() = runOnMain { player?.currentMediaItem?.mediaId }.orEmpty()

    val hasQueuedNext: Boolean
        get() = runOnMain { player?.let { it.mediaItemCount > it.currentMediaItemIndex + 1 } } ?: false

    @Volatile
    private var progressJob: Job? = null

    @Volatile
    private var lastPositionMs = 0L

    @Volatile
    private var duckLevel = 1f

    @Volatile
    private var userLevel = 1f

    @Volatile
    private var gainFactor = 1f

    val currentPositionMs: Long
        get() = runOnMain { player?.currentPosition } ?: lastPositionMs

    val isPlaying: Boolean
        get() = runOnMain { player?.isPlaying } ?: false

    val isBuffering: Boolean
        get() = runOnMain { player?.playbackState == Player.STATE_BUFFERING } ?: false

    fun durationFor(uri: String): Long = itemDurations[uri] ?: 0L

    suspend fun prepare(
        source: AudioSource,
        uri: String,
        fromMs: Long,
        autoplay: Boolean,
        gainDb: Double,
    ): Boolean =
        withContext(Dispatchers.Main) {
            release()
            val totalMs = source.seektable.totalDuration * 1000L / source.seektable.timescale
            val encrypted = source.seektable.pssh.isNotBlank()
            val offline = OfflineAudio.isDownloaded(uri)
            val keySet = if (offline) OfflineAudio.licenceKeySet(context, uri) else null
            val dataSource = if (offline) {
                OfflineAudio.playbackDataSource(context)
            } else {
                DefaultDataSource.Factory(context)
            }
            val factory = DefaultMediaSourceFactory(dataSource)
            if (encrypted) {
                val callback = AudioDrmCallback(context) { LicenseEndpoint.of(context, spclient()) }
                var cachedManager: DrmSessionManager? = null
                factory.setDrmSessionManagerProvider(DrmSessionManagerProvider { _: MediaItem ->
                    val existing = cachedManager
                    if (existing != null) return@DrmSessionManagerProvider existing
                    DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .setMultiSession(true)
                        .build(callback)
                        .also { manager ->
                            if (keySet != null) {
                                manager.setMode(DefaultDrmSessionManager.MODE_PLAYBACK, keySet)
                                log.i("audio playing from the offline copy '${source.fileId.take(12)}'")
                            }
                            cachedManager = manager
                        }
                })
                log.i("audio drm session asked for '${source.fileId.take(12)}'")
            } else {
                log.i("clear content, no drm session asked")
            }
            gainFactor = Math.pow(10.0, gainDb / 20.0).toFloat().coerceIn(0f, 1f)
            durationMs = totalMs
            announced = false
            lastItemUri = uri
            if (uri.isNotBlank()) itemDurations[uri] = totalMs
            val created = ExoPlayer.Builder(context)
                .setMediaSourceFactory(factory)
                .build()
            created.addListener(listener)
            created.setMediaSource(factory.createMediaSource(itemFor(uri, source)))
            player = created
            created.prepare()
            created.seekTo(fromMs.coerceIn(0L, (totalMs - 1000L).coerceAtLeast(0L)))
            created.playWhenReady = autoplay
            started = autoplay
            log.i("audio engine ready '${source.fileId.take(12)}' ${totalMs}ms at ${fromMs}ms autoplay=$autoplay")
            true
        }

    fun queueNext(uri: String, source: AudioSource) {
        runOnMain {
            val p = player ?: return@runOnMain
            val current = p.currentMediaItemIndex
            if (p.mediaItemCount > current + 1) p.removeMediaItems(current + 1, p.mediaItemCount)
            p.addMediaItem(itemFor(uri, source))
        }
        log.i("audio queued next '$uri' '${source.fileId.take(12)}' " +
                if (source.seektable.pssh.isNotBlank()) "drm" else "clear"
        )
    }

    fun skipTo(uri: String): Boolean {
        val moved = runOnMain {
            val p = player ?: return@runOnMain false
            for (index in 0 until p.mediaItemCount) {
                if (p.getMediaItemAt(index).mediaId == uri) {
                    p.seekTo(index, 0L)
                    return@runOnMain true
                }
            }
            false
        } ?: false
        if (moved) log.i("audio skipped to '$uri'")
        return moved
    }

    private fun itemFor(uri: String, source: AudioSource): MediaItem {
        val encrypted = source.seektable.pssh.isNotBlank()
        return MediaItem.Builder()
            .setMediaId(uri)
            .setUri(Uri.parse(source.url))
            .setCustomCacheKey(uri)
            .apply {
                if (encrypted) {
                    setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .setForceSessionsForAudioAndVideoTracks(true)
                            .build(),
                    )
                }
            }
            .build()
    }

    fun play() {
        started = true
        runOnMain { player?.play() }
        startProgress()
    }

    fun pause() {
        started = false
        runOnMain { player?.pause() }
    }

    fun resume() {
        started = true
        runOnMain { player?.play() }
        startProgress()
    }

    fun seekTo(ms: Long) {
        lastPositionMs = ms
        runOnMain { player?.seekTo(ms) }
        onEvent(PlayerEvent.Seeked(ms))
    }

    fun duck(level: Float) {
        duckLevel = level.coerceIn(0f, 1f)
        applyVolume()
    }

    fun volume(level: Float) {
        userLevel = level.coerceIn(0f, 1f)
        applyVolume()
    }

    fun retune(gainDb: Double) {
        gainFactor = Math.pow(10.0, gainDb / 20.0).toFloat().coerceIn(0f, 1f)
        applyVolume()
    }

    fun rate(mult: Float) {
        runOnMain { player?.playbackParameters = PlaybackParameters(mult.coerceIn(0.5f, 3.5f)) }
    }

    fun stop() {
        started = false
        progressJob?.cancel()
        progressJob = null
        runOnMain {
            player?.stop()
            player?.release()
            player = null
        }
    }

    private fun applyVolume() {
        runOnMain { player?.volume = (duckLevel * userLevel * gainFactor).coerceIn(0f, 1f) }
    }

    private fun startProgress() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive && started) {
                val position = runOnMain { player?.currentPosition } ?: lastPositionMs
                if (position >= 0L) {
                    lastPositionMs = position
                    onEvent(PlayerEvent.Progress(position, durationMs))
                }
                delay(PROGRESS_MS)
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            val newUri = item?.mediaId.orEmpty()
            val auto = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            if (auto && lastItemUri.isNotBlank()) {
                onEvent(PlayerEvent.Ended(lastItemUri))
            }
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                announced = false
                lastPositionMs = 0L
                lastItemUri = newUri
                itemDurations[newUri]?.let { durationMs = it }
            }
            if (auto) {
                val ready = runOnMain { player?.playbackState == Player.STATE_READY } ?: false
                if (ready && !announced) {
                    announced = true
                    onEvent(PlayerEvent.Started(newUri))
                    if (started) startProgress()
                }
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> if (!announced) {
                    announced = true
                    onEvent(PlayerEvent.Started(lastItemUri))
                    if (started) startProgress()
                }
                Player.STATE_ENDED -> {
                    lastPositionMs = durationMs
                    onEvent(PlayerEvent.Ended(lastItemUri))
                    lastItemUri = ""
                }
                else -> Unit
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            if (playing) startProgress()
        }

        override fun onPlayerError(error: PlaybackException) {
            log.e("audio playback failed ${error.errorCodeName}: ${error.message}")
            onEvent(PlayerEvent.Failed("${error.errorCodeName}: ${error.message ?: "audio error"}"))
        }
    }

    private fun release() {
        progressJob?.cancel()
        progressJob = null
        runOnMain {
            player?.release()
            player = null
        }
    }

    private inline fun <T> runOnMain(crossinline block: () -> T?): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: T? = null
        val done = CountDownLatch(1)
        main.post {
            result = block()
            done.countDown()
        }
        if (!done.await(2500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            log.w("the main thread did not answer in 2500ms, giving up on the read")
        }
        return result
    }
}

private class Reader(private val data: ByteArray) {
    private var pos = 0

    fun done(): Boolean = pos >= data.size

    fun tag(): Pair<Int, Int> {
        val value = varint()
        return (value ushr 3).toInt() to (value and 7L).toInt()
    }

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (shift <= 63) {
            if (pos >= data.size) throw IllegalStateException("truncated varint")
            val byte = data[pos++].toInt() and 255
            result = result or ((byte and 127).toLong() shl shift)
            if (byte and 128 == 0) return result
            shift += 7
        }
        throw IllegalStateException("varint overflow")
    }

    fun bytes(): ByteArray {
        val length = varint().toInt()
        if (length < 0 || pos + length > data.size) throw IllegalStateException("truncated field")
        return data.copyOfRange(pos, pos + length).also { pos += length }
    }

    fun skip(wire: Int) {
        when (wire) {
            0 -> varint()
            1 -> pos += 8
            2 -> bytes()
            5 -> pos += 4
            else -> throw IllegalStateException("wire $wire")
        }
    }
}
