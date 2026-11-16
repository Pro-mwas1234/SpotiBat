package com.mwask.bat.backend.player

import android.content.Context
import android.os.SystemClock
import android.view.Surface
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.mwask.bat.backend.connect.command.AudioFile
import com.mwask.bat.backend.connect.command.CommandTrack
import com.mwask.bat.backend.core.log.Logs

private val log = Logs.of(Logs.Module.PLAYER)

private const val LICENCE_SETTLE_MS = 2_000L

private const val LICENCE_SKIP_WINDOW_MS = 4_000L

private const val MANIFEST_CACHE = 8

private const val FILE_ID_CACHE = 64

private const val SOURCE_CACHE = 16

private const val DEEP_LOOKAHEAD = 1

private const val DUCK_LEVEL = 0.2f

private const val PLAYER_PREFS = "spotibat.player"

private const val KEY_RESUME_URI = "resumeUri"

private const val KEY_RESUME_FILE = "resumeFile"

private const val KEY_RESUME_FORMAT = "resumeFormat"

private const val KEY_RESUME_AT = "resumeAt"

private const val RESUME_WRITE_MS = 5_000L

sealed interface PlayerEvent {
    data class Started(val uri: String = "") : PlayerEvent
    data class Ended(val uri: String = "") : PlayerEvent
    data class Progress(val positionMs: Long, val durationMs: Long) : PlayerEvent
    data class Seeked(val positionMs: Long) : PlayerEvent
    data class Failed(val message: String) : PlayerEvent
}

data class PlayerState(
    val playing: Boolean = false,
    val paused: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val fileId: String = "",
    val format: Int = 0,
    val bitrate: Int = 0,
    val quality: String = "",
    val trackUri: String = "",
    val isVideo: Boolean = false,
)

class KrespotPlayer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<PlayerEvent> = _events

    @Volatile
    private var engine: AudioEngine? = null

    @Volatile
    private var mp3: Mp3Engine? = null

    private var job: Job? = null

    @Volatile
    private var generation = 0

    @Volatile
    private var lastPlayAt = 0L

    @Volatile
    private var licenceSettling = false

    @Volatile
    private var engineIdle = true

    @Volatile
    private var licencePending: CommandTrack? = null

    @Volatile
    private var licenceFrom = 0L

    @Volatile
    private var pausedByFocus = false

    @Volatile
    private var pendingPause = false

    /** A seek that arrived while the engine was still loading, applied at run(). */
    @Volatile
    private var pendingSeekMs = -1L

    @Volatile
    private var spclient: String = ""

    @Volatile
    private var refused: CommandTrack? = null

    @Volatile
    private var refusedFrom: Long = 0L

    private val focus = AudioFocus(context)

    private val video = VideoEngine(context, { spclient }, scope) { event ->
        if (videoActive && event !is PlayerEvent.Progress) {
            _events.tryEmit(event)
        }
        if (videoActive) {
            mirror(event, _state.value.durationMs)
        }
    }

    @Volatile
    private var videoActive = false

    @Volatile
    private var pendingVideoSurface: Surface? = null

    var onFocusPause: (() -> Unit)? = null

    var onFocusResume: (() -> Unit)? = null

    var onFocusDuck: ((Boolean) -> Unit)? = null

    @Volatile
    private var licenceRetrying = false

    init {
        LicenseGate.onOpen = {
            val track = licencePending ?: refused
            if (track != null) {
                refused = null
                licencePending = null
                licenceRetrying = true
                log.i("licence is back, retrying '${track.name.ifBlank { track.uri }}' " +
                        "from ${refusedFrom}ms"
                )
                play(track, spclient, if (licenceFrom > 0L) licenceFrom else refusedFrom)
                licenceRetrying = false
            }
        }
        OfflineAudio.load(context)
    }

    private val manifests = java.util.concurrent.ConcurrentHashMap<String, Deferred<List<AudioFile>>>()

    private val fileIds = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val fileIdOrder = java.util.ArrayDeque<String>()

    private val sources = java.util.LinkedHashMap<String, AudioSource>()

    private val resolving = java.util.concurrent.ConcurrentHashMap<String, Deferred<AudioSource?>>()

    @Volatile
    private var deepJob: Job? = null

    @Volatile
    private var deepTarget: String = ""

    private val deepWarmed = LinkedHashSet<String>()

    private var resumeWrittenUri = ""

    private var resumeWrittenMs = -1L

    fun rememberFile(trackUri: String, fileId: String) {
        if (trackUri.isBlank() || fileId.length < 32) return
        synchronized(fileIdOrder) {
            if (fileIds.put(trackUri, fileId) == null) fileIdOrder.addLast(trackUri)
            while (fileIdOrder.size > FILE_ID_CACHE) {
                fileIds.remove(fileIdOrder.removeFirst())
            }
        }
    }

    /** Counts a played or warmed track against the offline song cache limit. */
    fun cacheTouched(trackUri: String) {
        SongCache.touch(trackUri, context)
    }

    @Volatile
    var queuedNextUri: String = ""
        private set

    @Volatile
    private var queuedNextTrack: CommandTrack? = null

    @Volatile
    private var queuedGainDb = 0.0

    /**
     * Hands the engine the next track of the queue so ExoPlayer prebuffers it
     * while the current one plays and the transition carries no silence. A
     * warm source queues right away; a cold one resolves here and only queues
     * if the engine is still on the track that asked for it. The bridge checks
     * hasQueuedNext at the end of the track, so a late resolve can never leave
     * the transition hanging - the normal advance simply takes over.
     */
    fun queueNext(track: CommandTrack, spclient: String) {
        if (track.isDirect) return
        this.spclient = spclient
        val live = engine?.takeIf { it.hasMedia } ?: return
        val askedFor = _state.value.trackUri
        if (askedFor.isBlank() || live.currentUri != askedFor) return
        if (queuedNextUri == track.uri) return
        val file = track.best() ?: track.files.firstOrNull()
        val cached = sourceFor(track.uri)
        if (cached != null) {
            live.queueNext(track.uri, cached)
            SongCache.touch(track.uri, context)
            queuedNextUri = track.uri
            queuedNextTrack = track
            queuedGainDb = file?.gainDb ?: 0.0
            log.i("queued next '${track.name}' from the warm source")
            return
        }
        scope.launch {
            val source = warmSource(track.uri, spclient) ?: return@launch
            val still = engine
            if (still !== live || still.currentUri != askedFor || _state.value.trackUri != askedFor) {
                return@launch
            }
            still.queueNext(track.uri, source)
            queuedNextUri = track.uri
            queuedNextTrack = track
            queuedGainDb = file?.gainDb ?: 0.0
            log.i("queued next '${track.name}' after resolving")
        }
    }

    fun clearQueuedNext() {
        queuedNextUri = ""
        queuedNextTrack = null
    }

    fun hasSourceFor(trackUri: String): Boolean = sourceFor(trackUri) != null
    fun prefetch(trackUri: String, spclient: String) {
        if (trackUri.isBlank() || spclient.isBlank()) return
        if (manifests.containsKey(trackUri)) return
        if (manifests.size >= MANIFEST_CACHE) {
            manifests.entries.firstOrNull { it.key != trackUri }?.let { (key, pending) ->
                manifests.remove(key)
                pending.cancel()
            }
        }
        manifests[trackUri] = scope.async { SourceResolver.manifest(context, spclient, trackUri) }
    }

    /** Fire-and-forget full source warm for a visible row: manifest, seektable and CDN url. */
    fun warmTrack(trackUri: String, spclient: String) {
        if (trackUri.isBlank() || spclient.isBlank()) return
        if (!trackUri.startsWith("spotify:track:")) return
        if (sourceFor(trackUri) != null) return
        warmDeferred(trackUri, spclient)
    }

    fun prefetchAhead(
        tracks: List<com.mwask.bat.backend.connect.cluster.ClusterTrack>,
        spclient: String,
        nowPlaying: String = "",
    ) {
        val window = windowOf(tracks)
        if (tracks.isEmpty() || spclient.isBlank()) {
            if (window.isEmpty()) {
                deepJob?.cancel()
                deepTarget = ""
            }
            dropStaleWarmups(window, nowPlaying)
            return
        }
        tracks.forEach { track ->
            if (track.isAd || track.isMeta || !track.isVisible) return@forEach
            if (!track.uri.startsWith("spotify:track:")) return@forEach
            if (fileIds.containsKey(track.uri)) return@forEach
            prefetch(track.uri, spclient)
            scope.launch {
                val file = manifestFor(track.uri).firstOrNull() ?: return@launch
                rememberFile(track.uri, file.fileId)
            }
        }
        val target = window.firstOrNull().orEmpty()
        if (target.isBlank()) {
            deepJob?.cancel()
            deepTarget = ""
            dropStaleWarmups(window, nowPlaying)
            return
        }
        if (deepJob?.isActive == true && deepTarget == target) {
            dropStaleWarmups(window, nowPlaying)
            return
        }
        deepJob?.cancel()
        deepTarget = target
        dropStaleWarmups(window, nowPlaying)
        deepJob = scope.launch { deepWarm(tracks, spclient) }
    }

    private fun windowOf(
        tracks: List<com.mwask.bat.backend.connect.cluster.ClusterTrack>,
    ): List<String> = tracks.asSequence()
        .filter { !it.isAd && !it.isMeta && it.isVisible && it.uri.startsWith("spotify:track:") }
        .take(DEEP_LOOKAHEAD)
        .map { it.uri }
        .toList()

    private fun dropStaleWarmups(window: List<String>, nowPlaying: String) {
        val keep = window.toHashSet()
        val playing = _state.value.trackUri
        val stale = synchronized(deepWarmed) {
            val drop = deepWarmed.filter { it != playing && it != nowPlaying && it !in keep }
            deepWarmed.removeAll(drop)
            drop
        }
        if (stale.isEmpty()) return
        stale.forEach { uri ->
            sourceFor(uri) ?: fileIds[uri] ?: return@forEach
        }
        log.i("dropped ${stale.size} stale warmup(s): " +
                stale.joinToString { it.takeLast(12) })
    }

    /**
     * Resolves, licenses and preloads the next few tracks so advancing
     * the queue starts from warm bytes instead of a cold pipeline.
     */
    private suspend fun deepWarm(
        tracks: List<com.mwask.bat.backend.connect.cluster.ClusterTrack>,
        spclient: String,
    ) {
        tracks.asSequence()
            .filter { !it.isAd && !it.isMeta && it.isVisible && it.uri.startsWith("spotify:track:") }
            .take(DEEP_LOOKAHEAD)
            .forEach { track ->
                if (sourceFor(track.uri) != null) return@forEach
                val source = warmSource(track.uri, spclient) ?: return@forEach
                SongCache.touch(track.uri, context)
                synchronized(deepWarmed) { deepWarmed.add(track.uri) }
                log.i("deep warm '${track.title}' file=${source.fileId.take(8)}")
            }
    }

    private fun rememberSource(trackUri: String, source: AudioSource) {
        if (trackUri.isBlank()) return
        synchronized(sources) {
            sources.remove(trackUri)
            sources[trackUri] = source
            while (sources.size > SOURCE_CACHE) {
                val oldest = sources.keys.firstOrNull() ?: break
                sources.remove(oldest)
            }
        }
    }

    private fun sourceFor(trackUri: String): AudioSource? = synchronized(sources) { sources[trackUri] }

    private suspend fun warmSource(
        trackUri: String,
        spclient: String,
        hintFile: String = "",
        hintFormat: Int = 0,
    ): AudioSource? =
        runCatching { warmDeferred(trackUri, spclient, hintFile, hintFormat).await() }.getOrNull()

    private fun warmDeferred(
        trackUri: String,
        spclient: String,
        hintFile: String = "",
        hintFormat: Int = 0,
    ): Deferred<AudioSource?> {
        resolving[trackUri]?.let { return it }
        val deferred = scope.async {
            runCatching {
                val known = hintFile.ifBlank { fileIds[trackUri].orEmpty() }
                val fromKnown = known.takeIf { it.isNotBlank() }
                    ?.let { SourceResolver.resolve(context, spclient, trackUri, it, hintFormat) }
                fromKnown
                    ?: SourceResolver.resolve(context, spclient, trackUri, "", 0, manifestFor(trackUri))
            }.getOrNull()?.also { source ->
                rememberSource(trackUri, source)
                rememberFile(trackUri, source.fileId)
            }
        }
        deferred.invokeOnCompletion { resolving.remove(trackUri, deferred) }
        val racing = resolving.putIfAbsent(trackUri, deferred)
        if (racing != null) {
            deferred.cancel()
            return racing
        }
        return deferred
    }

    private suspend fun manifestFor(trackUri: String): List<AudioFile> {
        val pending = manifests[trackUri] ?: return emptyList()
        val files = runCatching { pending.await() }.getOrNull() ?: emptyList()
        if (files.isEmpty()) manifests.remove(trackUri, pending)
        return files
    }

    private fun forgetSource(trackUri: String) {
        synchronized(sources) { sources.remove(trackUri) }
    }

    init {
        focus.onPause = {
            log.i("the audio focus went to another app, pausing")
            pausedByFocus = true
            pauseInternal()
            onFocusPause?.invoke()
        }
        focus.onResume = {
            if (pausedByFocus) {
                log.i("the audio focus came back, resuming")
                pausedByFocus = false
                resumeInternal()
                onFocusResume?.invoke()
            } else {
                log.i("the audio focus came back, but the user paused in the meantime")
            }
        }
        focus.onDuck = { ducked ->
            duck(if (ducked) DUCK_LEVEL else 1f)
            onFocusDuck?.invoke(ducked)
        }
    }

    fun warm() {
        video.prewarmDrm()
    }

    fun resumeLast(spclient: String) {
        scope.launch {
            val prefs = com.mwask.bat.backend.core.store.AppPrefs.prefs(context, PLAYER_PREFS)
            val uri = prefs.getString(KEY_RESUME_URI, "").orEmpty()
            val file = prefs.getString(KEY_RESUME_FILE, "").orEmpty()
            val format = prefs.getInt(KEY_RESUME_FORMAT, 0)
            val at = prefs.getLong(KEY_RESUME_AT, 0L).coerceAtLeast(0L)
            if (!uri.startsWith("spotify:track:") || file.length < 32) {
                log.i("resume: no remembered track to prefetch")
                return@launch
            }
            if (sourceFor(uri) != null) return@launch
            val source = warmSource(uri, spclient, hintFile = file, hintFormat = format)
            if (source == null) {
                log.i("resume: '${uri.takeLast(12)}' did not resolve, autoplay takes the cold path")
                return@launch
            }
            log.i("resume: '${uri.takeLast(12)}' file=${source.fileId.take(8)} source warmed at ${at}ms")
        }
    }

    private fun persistResume() {
        val current = _state.value
        val file = current.fileId
        if (file.isBlank() || !current.trackUri.startsWith("spotify:track:")) return
        if (current.trackUri == resumeWrittenUri &&
            kotlin.math.abs(current.positionMs - resumeWrittenMs) < RESUME_WRITE_MS
        ) return
        resumeWrittenUri = current.trackUri
        resumeWrittenMs = current.positionMs
        com.mwask.bat.backend.core.store.AppPrefs.prefs(context, PLAYER_PREFS).edit()
            .putString(KEY_RESUME_URI, current.trackUri)
            .putString(KEY_RESUME_FILE, file)
            .putInt(KEY_RESUME_FORMAT, current.format)
            .putLong(KEY_RESUME_AT, current.positionMs)
            .apply()
    }

    val positionMs: Long
        get() = if (videoActive) video.positionMs else engine?.takeIf { it.hasMedia }?.currentPositionMs
            ?: mp3?.currentPositionMs ?: _state.value.positionMs

    val durationMs: Long get() = _state.value.durationMs

    fun durationForItem(uri: String): Long = engine?.durationFor(uri) ?: 0L

    val isPlaying: Boolean get() = if (videoActive) video.isPlaying else _state.value.playing

    val format: Int get() = _state.value.format

    val bitrate: Int get() = _state.value.bitrate

    val quality: String get() = _state.value.quality

    val hasSource: Boolean
        get() = videoActive || (engine?.hasMedia == true && !engineIdle) || mp3 != null || job?.isActive == true

    val hasQueuedNext: Boolean get() = !videoActive && mp3 == null && (engine?.takeIf { it.hasMedia }?.hasQueuedNext ?: false)

    val currentUri: String
        get() = engine?.takeIf { it.hasMedia }?.currentUri ?: _state.value.trackUri

    fun play(
        track: CommandTrack,
        spclient: String,
        fromMs: Long = 0L,
    ) {
        if (!licenceRetrying) LicenseGate.resetStrikes()
        val sinceLast = System.currentTimeMillis() - lastPlayAt
        licenceSettling = lastPlayAt > 0L && sinceLast < LICENCE_SKIP_WINDOW_MS
        lastPlayAt = System.currentTimeMillis()
        if (track.isDirect) {
            playDirect(track)
            return
        }
        val file = track.best() ?: track.files.firstOrNull()
        this.spclient = spclient
        stop()
        if (!focus.acquire()) {
            log.w("play refused, another app holds the audio focus")
            _events.tryEmit(PlayerEvent.Failed("focus"))
            return
        }
        val id = ++generation
        pendingPause = false
        queuedNextUri = ""
        queuedNextTrack = null
        _state.value = PlayerState(
            playing = true,
            buffering = true,
            durationMs = track.durationMs,
            trackUri = track.uri,
        )
        log.i("play '${track.name}' ${track.uri} " +
                "file=${file?.fileId ?: "by uri"} fmt=${file?.format ?: 0} ${file?.bitrate ?: 0}b"
        )
        job = scope.launch {
            var player: AudioEngine? = null
            try {
                // A downloaded song is a plain file on disk: play it directly, with no Spotify
                // source resolve and no DRM, so it works offline.
                val offlineFile = OfflineAudio.localFile(context, track.uri)
                val source = if (offlineFile != null) {
                    log.i("play '${track.name}' from the downloaded file")
                    AudioSource(
                        fileId = track.uri,
                        format = 0,
                        bitrate = 0,
                        quality = "yt",
                        url = android.net.Uri.fromFile(offlineFile).toString(),
                        seektable = Seektable(
                            offset = 0,
                            sizes = emptyList(),
                            durations = listOf(track.durationMs.coerceAtLeast(1L)),
                            timescale = 1000,
                            pssh = "",
                        ),
                    )
                } else {
                    sourceFor(track.uri) ?: warmSource(track.uri, spclient)
                }
                if (source == null) {
                    if (id == generation) settleStopped()
                    _events.emit(PlayerEvent.Failed("no source"))
                    return@launch
                }
                if (offlineFile == null) rememberFile(track.uri, source.fileId)
                SongCache.touch(track.uri, context)
                if (licenceSettling) {
                    log.i("licence deferred, the user is still skipping")
                    delay(LICENCE_SETTLE_MS)
                    if (id != generation) {
                        log.i("licence skipped, playback moved on before settling")
                        return@launch
                    }
                }
                if (id != generation) {
                    log.i("play was skipped while its source resolved")
                    return@launch
                }
                val totalMs = source.seektable.totalDuration * 1000L / source.seektable.timescale
                val sink: (PlayerEvent) -> Unit = { event ->
                    if (id == generation) {
                        val failed = (event as? PlayerEvent.Failed)?.message
                            ?.takeIf { it.contains("DRM_LICENSE_ACQUISITION_FAILED") }
                        if (failed != null) {
                            // A queued track may already have taken the slot by
                            // the time the licence for the previous one comes
                            // back refused; retrying the stale track would loop
                            // the same two rows, so whatever is waiting is the
                            // one to keep.
                            val movedTo = _state.value.trackUri == queuedNextUri
                            val waiting = if (movedTo) queuedNextTrack ?: track else track
                            val refusedSource = sourceFor(waiting.uri)
                            if (refusedSource != null && !LicenseGate.rateLimited) {
                                SourceResolver.blockFormat(refusedSource.format)
                                forgetSource(waiting.uri)
                                log.w("the licence refused format ${refusedSource.format}, trying the next one")
                                scope.launch {
                                    if (id == generation) play(waiting, spclient)
                                }
                            } else if (refusedSource != null) {
                                log.w("the licence is rate limited, keeping format ${refusedSource.format}")
                                licencePending = waiting
                                licenceFrom = if (movedTo) 0L else fromMs
                            } else if (LicenseGate.remainingMs() > 0L) {
                                refused = waiting
                                refusedFrom = if (movedTo) 0L else player?.currentPositionMs ?: 0L
                                log.w("the licence is closed for ${LicenseGate.remainingMs() / 1000}s, " +
                                        "keeping '${waiting.name.ifBlank { waiting.uri }}' for a retry"
                                )
                            } else {
                                mirror(event, player?.durationMs ?: totalMs)
                                if (event !is PlayerEvent.Progress) _events.tryEmit(event)
                            }
                        } else {
                            mirror(event, player?.durationMs ?: totalMs)
                            if (event !is PlayerEvent.Progress) _events.tryEmit(event)
                        }
                    }
                }
                player = engine?.also { it.onEvent = sink }
                    ?: AudioEngine(context, { spclient }, scope, sink).also { engine = it }
                val startAt = if (pendingSeekMs >= 0L) pendingSeekMs.also { pendingSeekMs = -1L } else fromMs
                val ready = player.prepare(source, track.uri, startAt, autoplay = !pendingPause, file?.gainDb ?: 0.0)
                if (!ready || id != generation) {
                    player.stop()
                    if (id == generation) {
                        settleStopped()
                        _events.emit(PlayerEvent.Failed("prepare"))
                    } else {
                        log.i("play was superseded before the engine came up")
                    }
                    return@launch
                }
                engine = player
                engineIdle = false
                _state.value = PlayerState(
                    playing = !pendingPause,
                    paused = pendingPause,
                    buffering = !pendingPause,
                    durationMs = totalMs,
                    fileId = source.fileId,
                    format = source.format,
                    bitrate = source.bitrate,
                    quality = source.quality,
                    trackUri = track.uri,
                )
                if (pendingPause) {
                    log.i("play was asked to stay paused, holding it at ${startAt}ms")
                }
                if (pendingSeekMs >= 0L) {
                    val parked = pendingSeekMs.also { pendingSeekMs = -1L }
                    log.i("a seek parked while the engine was loading, applying at ${parked}ms")
                    player.seekTo(parked)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                log.e("play failed ${failure.javaClass.simpleName}: ${failure.message}")
                player?.stop()
                if (player != null && engine === player) engineIdle = true
                val refusedSource = sourceFor(track.uri)
                if (refusedSource != null &&
                    failure.message.orEmpty().contains("DRM_LICENSE_ACQUISITION_FAILED") &&
                    !LicenseGate.rateLimited
                ) {
                    SourceResolver.blockFormat(refusedSource.format)
                    forgetSource(track.uri)
                    log.w("the licence refused format ${refusedSource.format}, trying the next one")
                    play(track, spclient)
                    return@launch
                }
                if (LicenseGate.remainingMs() > 0L) {
                    refused = track
                    refusedFrom = fromMs
                    licencePending = track
                    licenceFrom = fromMs
                    log.w("the licence is closed for ${LicenseGate.remainingMs() / 1000}s, " +
                            "keeping '${track.name.ifBlank { track.uri }}' for a retry"
                    )
                    return@launch
                }
                _events.emit(PlayerEvent.Failed(failure.message.orEmpty()))
            }
        }
    }

    /**
     * Hands the track to [OfflineAudio]. Nothing is resolved here: the downloader mints a fresh
     * stream url when it starts, the way Yuma does, so a song queued minutes ago still lands.
     */
    fun download(track: CommandTrack, spclient: String) {
        if (track.isDirect || track.isAd || track.uri.isBlank()) return
        this.spclient = spclient
        OfflineAudio.enqueue(
            context,
            OfflineRequest(
                uri = track.uri,
                spclient = spclient,
                title = track.name,
                artists = track.artists,
                album = track.album,
                imageUrl = track.imageUrl,
                durationMs = track.durationMs,
            ),
        )
    }

    fun removeDownload(trackUri: String) {
        OfflineAudio.remove(context, trackUri)
    }

    private fun playDirect(track: CommandTrack) {
        val file = track.bestDirect()
        val url = file?.url.orEmpty()
        if (url.isBlank()) {
            log.w("play '${track.uri}': no direct url in the manifest")
            _events.tryEmit(PlayerEvent.Failed("no direct url"))
            return
        }
        stop()
        if (!focus.acquire()) {
            log.w("play direct refused, another app holds the audio focus")
            _events.tryEmit(PlayerEvent.Failed("focus"))
            return
        }
        val id = ++generation
        pendingPause = false
        pendingSeekMs = -1L
        _state.value = PlayerState(playing = true, buffering = true, trackUri = track.uri)
        log.i("play direct '${track.name.ifBlank { track.uri }}' ${file?.bitrate ?: 0}b ${url.take(72)}")
        job = scope.launch {
            var player: Mp3Engine? = null
            try {
                player = Mp3Engine(url) { event ->
                    if (id == generation) {
                        mirror(event, _state.value.durationMs)
                        if (event !is PlayerEvent.Progress) _events.tryEmit(event)
                    }
                }
                mp3 = player
                _state.value = PlayerState(playing = true, buffering = true, trackUri = track.uri)
                player.run()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                log.e("play direct failed ${failure.javaClass.simpleName}: ${failure.message}")
                _events.emit(PlayerEvent.Failed(failure.message.orEmpty()))
            } finally {
                if (player != null && mp3 === player) mp3 = null
            }
        }
    }

    fun pause() {
        pausedByFocus = false
        pauseInternal()
        focus.abandon()
    }

    private fun pauseInternal() {
        pendingPause = true
        if (videoActive) video.pause()
        engine?.pause()
        mp3?.pause()
        _state.update { it.copy(playing = false, paused = true, buffering = false) }
    }

    fun resume() {
        pausedByFocus = false
        resumeInternal()
    }

    private fun resumeInternal() {
        if (!focus.acquire()) {
            log.w("resume refused, another app holds the audio focus")
            return
        }
        pendingPause = false
        if (videoActive) video.play()
        engine?.resume()
        mp3?.resume()
        _state.update { it.copy(playing = true, paused = false, buffering = false) }
    }

    fun duck(level: Float) {
        if (videoActive) video.duck(level)
        engine?.duck(level)
        mp3?.duck(level)
    }

    /** User volume on the local sink, kept separate from audio-focus ducking. */
    fun setVolume(level: Float) {
        if (videoActive) video.duck(level)
        engine?.volume(level)
        mp3?.volume(level)
    }

    fun seekTo(ms: Long) {
        if (videoActive) {
            log.i("video seek -> ${ms}ms")
            video.seekTo(ms)
            _state.update { it.copy(positionMs = ms) }
            return
        }
        val engine = engine?.takeIf { it.hasMedia }
        if (engine == null) {
            if (job?.isActive == true) {
                pendingSeekMs = ms
                _state.update { it.copy(positionMs = ms) }
                log.i("seek -> ${ms}ms parked, the engine is still loading")
            } else {
                log.w("seek -> ${ms}ms ignored, nothing is running")
            }
            return
        }
        log.i("seek -> ${ms}ms")
        engine.seekTo(ms)
        _state.update { it.copy(positionMs = engine.currentPositionMs) }
    }

    /** Nothing is running: the state must not keep claiming it plays. */
    private fun settleStopped() {
        _state.update { it.copy(playing = false, paused = false, buffering = false) }
    }

    fun stop() {
        generation++
        pendingPause = false
        pendingSeekMs = -1L
        queuedNextUri = ""
        queuedNextTrack = null
        refused = null
        stopVideo()
        deepJob?.cancel()
        deepTarget = ""
        engine?.stop()
        engineIdle = true
        mp3?.stop()
        mp3 = null
        job?.cancel()
        job = null
        focus.abandon()
        _state.update { it.copy(playing = false, paused = false, buffering = false) }
    }

    fun release() {
        stop()
        engine?.release()
        engine = null
        LicenseEndpoint.reset()
    }

    /** Hands the surface the video renders into; kept until the engine starts. */
    fun videoSurface(surface: Surface?) {
        pendingVideoSurface = surface
        video.setSurface(surface)
    }

    /** Constrains the video rendition by height; 0 or below restores auto. */
    fun selectVideoQuality(height: Int) {
        video.selectQuality(height)
    }

    fun playbackRate(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 3.5f)
        if (state.value.isVideo) {
            video.rate(clamped)
        } else {
            engine?.rate(clamped)
        }
        log.i("playback rate -> ${clamped}x")
    }

    /**
     * Plays a music video entity: resolves its source manifest, builds the DASH
     * session and takes over playback from the audio pipeline at fromMs.
     */
    fun playVideo(videoUri: String, audioTrackUri: String, durationMs: Long, fromMs: Long) {
        if (!videoUri.startsWith("spotify:track:") && !videoUri.startsWith("spotify:episode:")) return
        stopAudio()
        if (!focus.acquire()) {
            log.w("video play refused, another app holds the audio focus")
            _events.tryEmit(PlayerEvent.Failed("focus"))
            return
        }
        val id = ++generation
        pendingPause = false
        videoActive = true
        _state.value = PlayerState(
            playing = true,
            buffering = true,
            durationMs = durationMs,
            trackUri = audioTrackUri,
            isVideo = true,
        )
        log.i("play video '$videoUri' of '$audioTrackUri' from ${fromMs}ms")
        job = scope.launch {
            try {
                val source = VideoManifests.resolve(context, spclient, videoUri)
                if (source == null) {
                    log.w("video '$videoUri' did not resolve, falling back to audio")
                    videoActive = false
                    _events.emit(PlayerEvent.Failed("no video source"))
                    return@launch
                }
                if (id != generation) return@launch
                val ready = video.prepare(source.manifest, fromMs, autoplay = !pendingPause)
                if (!ready) {
                    videoActive = false
                    _events.emit(PlayerEvent.Failed("video engine failed"))
                    return@launch
                }
                if (id != generation) return@launch
                video.setSurface(pendingVideoSurface)
                _state.value = PlayerState(
                    playing = !pendingPause,
                    paused = pendingPause,
                    buffering = !pendingPause,
                    durationMs = source.manifest.durationMs,
                    trackUri = audioTrackUri,
                    isVideo = true,
                )
                if (pendingPause) video.pause()
                if (!pendingPause) video.play()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                log.e("video play failed ${failure.javaClass.simpleName}: ${failure.message}")
                videoActive = false
                _events.emit(PlayerEvent.Failed(failure.message.orEmpty()))
            }
        }
    }

    /** Leaves video mode; the caller resumes audio playback at fromMs. */
    fun stopVideo() {
        if (!videoActive) return
        videoActive = false
        video.stop()
        _state.update { it.copy(isVideo = false, playing = false, buffering = false) }
        log.i("video mode stopped")
    }

    private fun stopAudio() {
        generation++
        engine?.stop()
        mp3?.stop()
        mp3 = null
        job?.cancel()
        job = null
    }

    fun reset() {
        stop()
        engine?.release()
        engine = null
        _state.value = PlayerState()
        LicenseEndpoint.reset()
        log.i("reset, engine and state cleared")
    }

    private fun mirror(event: PlayerEvent, durationMs: Long) {
        _state.update { current ->
            when (event) {
                is PlayerEvent.Progress -> current.copy(positionMs = event.positionMs, durationMs = event.durationMs)
                is PlayerEvent.Seeked -> current.copy(positionMs = event.positionMs)
                is PlayerEvent.Started -> {
                    val itemMs = engine?.durationFor(event.uri) ?: 0L
                    val settled = itemMs.takeIf { it > 0L } ?: durationMs
                    val moved = event.uri.isNotBlank() && event.uri != current.trackUri
                    val base = if (moved) {
                        current.copy(trackUri = event.uri, positionMs = 0L, durationMs = settled)
                    } else {
                        current.copy(durationMs = settled)
                    }
                    if (event.uri == queuedNextUri) {
                        engine?.retune(queuedGainDb)
                    }
                    if (current.paused) {
                        base.copy(buffering = false)
                    } else {
                        base.copy(playing = true, paused = false, buffering = false)
                    }
                }
                is PlayerEvent.Ended -> current.copy(playing = false, buffering = false, positionMs = durationMs)
                is PlayerEvent.Failed -> current.copy(playing = false, buffering = false, trackUri = "")
            }
        }
        persistResume()
    }
}
