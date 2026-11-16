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

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.net.Net
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private val log = Logs.of(Logs.Module.PLAYER)

private const val PROGRESS_MS = 500L

internal class EncoreVideoDrmCallback(
    private val context: Context,
    private val licenseUrl: String,
) : MediaDrmCallback {

    override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): MediaDrmCallback.Response {
        val response = runBlocking {
            Net.postBlob(
                licenseUrl,
                request.data,
                "application/octet-stream",
                PlayerHeaders.license(context),
                tag = "video-license",
            )
        }
        val body = response?.takeIf { it.ok } ?: throw IOException("video licence ${response?.code ?: "failed"}")
        log.i("video licence ${body.bytes.size}B")
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
}

internal object VideoDash {

    fun write(context: Context, manifest: VideoManifest, preferredHeight: Int): File? {
        val profile = manifest.bestVideoProfile(preferredHeight) ?: return null
        val dir = File(context.cacheDir, "video-manifests").apply { mkdirs() }
        val file = File(dir, "mv-${manifest.sourceGid.take(12)}.mpd")
        runCatching { file.writeText(build(manifest, profile)) }.getOrElse {
            log.w("mpd write failed ${it.message}")
            return null
        }
        log.i("mpd ${file.name} audio=${manifest.audioProfile?.id} video=${profile.id} " +
            "${profile.width}x${profile.height} segments=${manifest.segmentCount}")
        return file
    }

    internal fun build(manifest: VideoManifest, videoProfile: VideoProfile): String {
        val baseUrl = manifest.baseUrls.first()
        val audio = manifest.audioProfile ?: return ""
        val segmentSec = manifest.segmentSeconds
        val full = manifest.durationMs / (segmentSec * 1000L)
        val remainderMs = manifest.durationMs % (segmentSec * 1000L)
        val timeline = buildString {
            if (full > 0) append("<S t=\"0\" d=\"$segmentSec\" r=\"${full - 1}\"/>")
            if (remainderMs > 0) append("<S t=\"${full * segmentSec}\" d=\"${(remainderMs + 999) / 1000}\"/>")
        }
        val protection = manifest.encryption?.let { enc ->
            val kid = runCatching { kidUuid(enc.keyId.ifBlank { audio.keyId }) }.getOrNull().orEmpty()
            buildString {
                append("<ContentProtection schemeIdUri=\"urn:mpeg:dash:mp4-protection:2011\" value=\"cenc\"")
                if (kid.isNotBlank()) append(" cenc:default_KID=\"$kid\"")
                append("><cenc:pssh>").append(enc.pssh).append("</cenc:pssh></ContentProtection>")
                append("<ContentProtection schemeIdUri=\"urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed\">")
                append("<cenc:pssh>").append(enc.pssh).append("</cenc:pssh></ContentProtection>")
            }
        }.orEmpty()
        val audioSet = adaptation(
            id = 0,
            mime = audio.mime,
            type = "audio",
            protection = protection,
            baseUrl = baseUrl,
            manifest = manifest,
            timeline = timeline,
            representations = listOf(representation(audio.id, audio.codecs, audio.bitrate, 0, 0)),
        )
        val videoSet = adaptation(
            id = 1,
            mime = videoProfile.mime,
            type = "video",
            protection = protection,
            baseUrl = baseUrl,
            manifest = manifest,
            timeline = timeline,
            representations = manifest.videoProfiles.map {
                representation(it.id, it.codecs, it.bitrate, it.width, it.height)
            },
        )
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" xmlns:cenc=\"urn:mpeg:cenc:2013\" ")
            append("type=\"static\" mediaPresentationDuration=\"PT")
            append(manifest.durationMs / 1000.0)
            append("S\" minBufferTime=\"PT2S\" ")
            append("profiles=\"urn:mpeg:dash:profile:isoff-live:2011\">")
            append("<Period><BaseURL>").append(escape(baseUrl)).append("</BaseURL>")
            append(audioSet)
            append(videoSet)
            append("</Period></MPD>")
        }
    }

    private fun adaptation(
        id: Int,
        mime: String,
        type: String,
        protection: String,
        baseUrl: String,
        manifest: VideoManifest,
        timeline: String,
        representations: List<String>,
    ): String = buildString {
        append("<AdaptationSet id=\"$id\" mimeType=\"$mime\" contentType=\"$type\">")
        append(protection)
        append("<SegmentTemplate timescale=\"1\" duration=\"${manifest.segmentSeconds}\" ")
        append("initialization=\"").append(escape(baseUrl + template(manifest.initTemplate))).append("\" ")
        append("media=\"").append(escape(baseUrl + template(manifest.segmentTemplate))).append("\">")
        append("<SegmentTimeline>").append(timeline).append("</SegmentTimeline>")
        append("</SegmentTemplate>")
        representations.forEach { append(it) }
        append("</AdaptationSet>")
    }

    private fun template(raw: String): String = raw
        .replace("{{profile_id}}", "\$RepresentationID\$")
        .replace("{{file_type}}", "mp4")
        .replace("{{segment_timestamp}}", "\$Time\$")

    private fun representation(
        id: Int,
        codecs: String,
        bitrate: Int,
        width: Int,
        height: Int,
    ): String = buildString {
        append("<Representation id=\"$id\" codecs=\"$codecs\" bandwidth=\"${bitrate.coerceAtLeast(1)}\"")
        if (width > 0 && height > 0) append(" width=\"$width\" height=\"$height\"")
        append("/>")
    }

    private fun kidUuid(keyIdBase64: String): String {
        val bytes = android.util.Base64.decode(keyIdBase64, android.util.Base64.DEFAULT)
        if (bytes.size < 16) return ""
        val hex = bytes.copyOfRange(0, 16).joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }

    private fun escape(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

internal class VideoEngine(
    private val context: Context,
    private val spclient: () -> String,
    private val scope: CoroutineScope,
    private val onEvent: (PlayerEvent) -> Unit,
) {

    private val main = Handler(Looper.getMainLooper())

    private var player: ExoPlayer? = null

    @Volatile
    private var surface: Surface? = null

    @Volatile
    private var started = false

    @Volatile
    private var announced = false

    @Volatile
    private var durationMs = 0L

    @Volatile
    private var progressJob: Job? = null

    @Volatile
    private var lastPositionMs = 0L

    @Volatile
    private var qualityHeight = 720

    val positionMs: Long
        get() = runOnMain { player?.currentPosition } ?: lastPositionMs

    val isPlaying: Boolean
        get() = runOnMain { player?.isPlaying } ?: false

    val isBuffering: Boolean
        get() = runOnMain { player?.playbackState == Player.STATE_BUFFERING } ?: false

    fun setSurface(newSurface: Surface?) {
        surface = newSurface
        runOnMain { player?.setVideoSurface(newSurface) }
    }

    /**
     * Opens a throwaway MediaDrm session at startup so the device is
     * provisioned before the first video switch; provisioning is per device
     * and persists, so later switches skip it entirely.
     */
    fun prewarmDrm() {
        scope.launch(Dispatchers.IO) {
            val drm = runCatching { android.media.MediaDrm(C.WIDEVINE_UUID) }.getOrElse {
                log.w("video drm prewarm unavailable ${it.javaClass.simpleName}")
                return@launch
            }
            runCatching { drm.setPropertyString("securityLevel", "L3") }
            try {
                var session: ByteArray? = try {
                    drm.openSession()
                } catch (missing: android.media.NotProvisionedException) {
                    val bytes = provisionDevice(drm)
                    if (bytes == null) {
                        log.w("video drm prewarm failed to provision")
                        return@launch
                    }
                    runCatching { drm.provideProvisionResponse(bytes) }
                        .onFailure { log.w("video drm prewarm rejected ${it.javaClass.simpleName}") }
                        .getOrThrow()
                    drm.openSession()
                }
                if (session != null) {
                    runCatching { drm.closeSession(session) }
                    log.i("video drm prewarmed, the device is provisioned")
                }
            } catch (failure: Throwable) {
                log.w("video drm prewarm failed ${failure.javaClass.simpleName}: ${failure.message}")
            } finally {
                runCatching { drm.release() }
            }
        }
    }

    /**
     * Sessions at the default level make this device pick the secure decoder,
     * whose protected buffers abort the RenderThread inside a TextureView.
     * Forcing L3 (what Chrome does) keeps the decoder non-secure so the
     * frames stay normal and the TextureView survives.
     */
    private fun l3Provider(): ExoMediaDrm.Provider = ExoMediaDrm.Provider { uuid ->
        FrameworkMediaDrm.DEFAULT_PROVIDER.acquireExoMediaDrm(uuid).apply {
            runCatching { setPropertyString("securityLevel", "L3") }
                .onSuccess { log.i("video drm securityLevel forced to L3") }
                .onFailure { log.w("video drm securityLevel refused ${it.message}") }
        }
    }

    fun selectQuality(height: Int) {
        qualityHeight = if (height <= 0) Int.MAX_VALUE else height
        runOnMain {
            val current = player ?: return@runOnMain
            current.trackSelectionParameters = current.trackSelectionParameters.buildUpon()
                .setMaxVideoSize(Int.MAX_VALUE, qualityHeight)
                .build()
        }
    }

    suspend fun prepare(manifest: VideoManifest, fromMs: Long, autoplay: Boolean): Boolean =
        withContext(Dispatchers.Main) {
            release()
            val file = VideoDash.write(context, manifest, qualityHeight) ?: return@withContext false
            val encryption = manifest.encryption
            val factory = DefaultMediaSourceFactory(DefaultDataSource.Factory(context))
            val mediaItem: MediaItem
            if (encryption == null) {
                mediaItem = MediaItem.Builder().setUri(Uri.fromFile(file)).build()
            } else {
                val license = VideoManifests.licenseUrl(spclient(), encryption.licenseEndpoint)
                val drmCallback = EncoreVideoDrmCallback(context, license)
                var cachedManager: DrmSessionManager? = null
                factory.setDrmSessionManagerProvider(DrmSessionManagerProvider { _: MediaItem ->
                    val existing = cachedManager
                    if (existing != null) return@DrmSessionManagerProvider existing
                    DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, l3Provider())
                        .build(drmCallback)
                        .also { cachedManager = it }
                })
                mediaItem = MediaItem.Builder()
                    .setUri(Uri.fromFile(file))
                    .setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .setForceSessionsForAudioAndVideoTracks(true)
                            .build(),
                    )
                    .build()
            }
            val created = ExoPlayer.Builder(context)
                .setMediaSourceFactory(factory)
                .build()
            created.addListener(listener)
            created.setVideoSurface(surface)
            created.trackSelectionParameters = created.trackSelectionParameters.buildUpon()
                .setMaxVideoSize(Int.MAX_VALUE, qualityHeight)
                .build()
            created.setMediaSource(factory.createMediaSource(mediaItem))
            player = created
            durationMs = manifest.durationMs
            lastPositionMs = fromMs
            announced = false
            created.prepare()
            created.seekTo(fromMs.coerceIn(0L, (manifest.durationMs - 1000L).coerceAtLeast(0L)))
            created.playWhenReady = autoplay
            started = autoplay
            log.i("video engine ready '${manifest.sourceGid.take(12)}' at ${fromMs}ms autoplay=$autoplay")
            true
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

    fun seekTo(ms: Long) {
        lastPositionMs = ms
        runOnMain { player?.seekTo(ms) }
        onEvent(PlayerEvent.Seeked(ms))
    }

    fun duck(level: Float) {
        runOnMain { player?.volume = level }
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
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> if (!announced) {
                    announced = true
                    onEvent(PlayerEvent.Started())
                    if (started) startProgress()
                }
                Player.STATE_ENDED -> {
                    lastPositionMs = durationMs
                    onEvent(PlayerEvent.Ended())
                }
                else -> Unit
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            if (playing) startProgress()
        }

        override fun onPlayerError(error: PlaybackException) {
            log.e("video playback failed ${error.errorCodeName}: ${error.message}")
            onEvent(PlayerEvent.Failed(error.message ?: "video error"))
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
        done.await()
        return result
    }
}

internal suspend fun provisionDevice(instance: android.media.MediaDrm): ByteArray? {
    val request = runCatching { instance.getProvisionRequest() }.getOrNull() ?: return null
    val signed = String(request.data, Charsets.UTF_8)
    val separator = if (request.defaultUrl.contains("?")) "&" else "?"
    val url = request.defaultUrl + separator + "signedRequest=" + java.net.URLEncoder.encode(signed, "UTF-8")
    val response = Net.postBlob(url, ByteArray(0), "application/octet-stream", tag = "provision")
    return response?.takeIf { it.ok }?.bytes
}
