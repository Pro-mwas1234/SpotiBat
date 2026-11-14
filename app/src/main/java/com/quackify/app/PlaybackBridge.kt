package com.quackify.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quackify.backend.api.ArtistBanStore
import com.quackify.app.bridge.QueueNavigatorState
import com.quackify.app.bridge.QueueTarget
import com.quackify.app.bridge.UriSources
import com.quackify.app.bridge.albumUriOf
import com.quackify.app.bridge.artistUriOf
import com.quackify.app.bridge.asCommandTrack
import com.quackify.app.bridge.asTrackItem
import com.quackify.app.bridge.playingUri
import com.quackify.app.bridge.targetFrom
import com.quackify.compose.playlist.TrackItem
import com.quackify.compose.components.RepeatMode
import com.quackify.compose.components.ShuffleMode
import com.quackify.compose.shell.ChapterMarker
import com.quackify.compose.shell.ComposePlaybackState
import com.quackify.compose.shell.ComposePlayerListener
import com.quackify.compose.shell.PlaybackProgress
import com.quackify.compose.sheets.SleepTimerOption
import com.quackify.backend.connect.KrespotConnect
import com.quackify.backend.connect.cluster.ClusterState
import com.quackify.backend.connect.cluster.ClusterTrack
import com.quackify.backend.model.ConnectDevice
import com.quackify.backend.model.DeviceKind
import android.media.AudioDeviceCallback
import android.os.Handler
import android.os.Looper
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.quackify.backend.connect.command.CommandClient
import com.quackify.backend.connect.command.CommandTrack
import com.quackify.backend.connect.command.ContentType
import com.quackify.backend.connect.command.PlaybackCommand
import com.quackify.backend.connect.command.RemoteCommand
import com.quackify.backend.connect.command.TrackUid
import com.quackify.backend.connect.state.DebugSource
import com.quackify.backend.connect.state.Snapshot
import com.quackify.backend.player.KrespotPlayer
import com.quackify.backend.player.PlayerEvent
import com.quackify.backend.player.PlayerState
import com.quackify.backend.player.VideoManifests
import com.quackify.backend.api.Podcasts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.snapshotFlow
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.library.Krespot
import com.quackify.backend.library.SmartShuffleRow
import com.quackify.backend.library.SmartShuffleSource
import com.quackify.backend.library.TrackSource
import com.quackify.backend.api.Library
import android.os.SystemClock
import org.json.JSONObject

/** Mirrors the seek bar's coarse view of the engine: position and duration only. */
data class PlaybackProgressSnapshot(
    val positionMs: Long,
    val durationMs: Long,
)

private val log = Logs.of(Logs.Module.BRIDGE)

/** Shared local prefs file the client keeps its UI flags in. */
const val CLIENT_PREFS = "quackify.client"

/** Settings toggle: start playback as soon as the session's track loads. */
const val AUTOPLAY_ON_STARTUP_PREF = "autoplayOnStartup"
const val LAST_TRACK_ARTIST_PREF = "lastTrackArtist"

/** Settings toggle: keep playing from a station when the context runs out. */
const val AUTOPLAY_ON_END_PREF = "autoplayOnEnd"

/** Settings toggle: prebuffer the next track so transitions carry no silence. */
const val GAPLESS_PREF = "gaplessPlayback"

/** Last context this phone played, so a cold start can rebuild its row list. */
const val CHAIN_CONTEXT_PREF = "chainContext"

/** The rows of that context, so the rebuild survives a cold start offline. */
const val CHAIN_ROWS_PREF = "chainRows"

private const val QUEUE_RETRY_MS = 1200L

class PlaybackBridge(private val context: Context) {

    private val connect = KrespotConnect(context)

    private val player = KrespotPlayer(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var dealer: Job? = null

    private var deviceWatch: Job? = null

    private var remoteTick: Job? = null

    @Volatile
    var deviceSheetOpen: Boolean = false

    /** Settings "Autoplay on startup": read from the local prefs at start(). */
    private var autoplayOnStartup: Boolean = false

    /** Settings "Gapless playback": read on demand, so the toggle applies live. */
    private fun gaplessEnabled(): Boolean =
        com.quackify.backend.core.store.AppPrefs.prefs(context, CLIENT_PREFS)
            .getBoolean(GAPLESS_PREF, false)

    /** The track the engine is prebuffering to take over without a restart. */
    @Volatile
    private var gaplessQueuedUri: String = ""

    private var sleepTimerJob: Job? = null

    @Volatile
    private var sleepEndOfTrackArmed = false

    @Volatile
    private var sleepArmedTrackUri = ""

    private var adoptDuration: Long = 0L

    /**
     * The next track when the local queue decides it deterministically: repeat
     * one, the smart-shuffle picks, the user queue, the play order and the
     * context chain. Server-driven or randomised picks return null and the
     * normal advance handles them, because guessing wrong here would skip a
     * track the listener expected.
     */
    private fun peekNextTrack(ended: String): CommandTrack? {
        if (!gaplessEnabled()) return null
        if (ended.isBlank() || ended.startsWith(AD_PREFIX)) return null
        val server = connect.cluster.value
        val context = chainContext.ifBlank { server?.contextUri.orEmpty() }
        val rows = chainFor()
        if (repeatTrackOn(server)) return commandFor(ended, context)
        val upcoming = enhancedUpcoming(context, ended)
        if (upcoming.isNotEmpty()) {
            val pick = upcoming.map { it.uri }.firstOrNull { it != ended && !bannedCachedUri(it) }
            if (!pick.isNullOrBlank() && pick != ended) return commandFor(pick, context)
        }
        queuedTail.firstOrNull { it.uri != ended && !bannedCachedUri(it.uri) }?.let { queued ->
            return CommandTrack(
                uri = queued.uri,
                name = queued.title,
                artists = queued.artist,
                album = "",
                albumUri = queued.albumUri.orEmpty(),
                durationMs = queued.durationMs,
                imageUrl = queued.imageUrl.orEmpty(),
                explicit = queued.isExplicit,
                contentType = if (queued.uri.startsWith("spotify:episode:")) ContentType.EPISODE else ContentType.TRACK,
                files = emptyList(),
                dualFiles = emptyList(),
                directFiles = emptyList(),
                contextUri = context,
            )
        }
        if (sessionQueueContext == context && sessionQueue.isNotEmpty()) {
            val rest = if (sessionQueue.firstOrNull() == ended) sessionQueue.drop(1) else sessionQueue
            rest.firstOrNull { it != ended && !bannedCachedUri(it) }?.let { return commandFor(it, context) }
        }
        if (shuffleOn(server) && !smartOn(server)) return null
        val at = rowOf(rows, ended)
        if (at >= 0) {
            var next = at + 1
            while (next in rows.indices && bannedCachedUri(rows[next])) next++
            if (next in rows.indices) return commandFor(rows[next], context)
        }
        return null
    }

    private fun commandFor(uri: String, context: String): CommandTrack {
        connect.cluster.value?.window()?.firstOrNull { it.uri == uri }?.let { return it.asCommandTrack() }
        decorated[uri]?.let { item ->
            return CommandTrack(
                uri = uri,
                name = item.title,
                artists = item.artist,
                album = "",
                albumUri = item.albumUri.orEmpty(),
                durationMs = item.durationMs,
                imageUrl = item.imageUrl.orEmpty(),
                explicit = item.isExplicit,
                contentType = if (uri.startsWith("spotify:episode:")) ContentType.EPISODE else ContentType.TRACK,
                files = emptyList(),
                dualFiles = emptyList(),
                directFiles = emptyList(),
                contextUri = context,
            )
        }
        return CommandTrack(
            uri = uri,
            name = "",
            artists = "",
            album = "",
            albumUri = "",
            durationMs = 0L,
            imageUrl = "",
            explicit = false,
            contentType = if (uri.startsWith("spotify:episode:")) ContentType.EPISODE else ContentType.TRACK,
            files = emptyList(),
            dualFiles = emptyList(),
            directFiles = emptyList(),
            contextUri = context,
        )
    }

    /** Queues the next track into the live engine so the handoff is seamless. */
    private fun queueGaplessNext(currentUri: String) {
        if (!gaplessEnabled()) return
        if (sleepEndOfTrackArmed) return
        val next = peekNextTrack(currentUri) ?: return
        if (!next.uri.startsWith("spotify:track:")) return
        if (!player.hasSourceFor(next.uri)) player.prefetch(next.uri, connect.spclientHost)
        player.queueNext(next, connect.spclientHost)
        gaplessQueuedUri = next.uri
        log.i("gapless: '${next.uri.takeLast(12)}' handed to the engine behind the current track")
    }
    private var autoplayPending: Boolean = false
    private var autoplayArmedAt: Long = 0L

    /** Canvas loop enabled by the user; rides along every playback state report as is_video_on. */
    @Volatile
    var canvasOn: Boolean = false
        private set

    private var videoFlagsUri = ""

    /** The track whose video source is already being prefetched. */
    private var videoPrefetchUri = ""
    private val videoAutoSwitched = HashSet<String>()

    /** Account switches: music videos for tracks, other videos for episode video. */
    @Volatile
    var musicVideosAllowed: Boolean = true
        private set

    @Volatile
    var otherVideosAllowed: Boolean = true
        private set

    private var mirrors: Job? = null

    private var thresholdTrack = ""

    @Volatile
    private var screen: List<String> = emptyList()

    @Volatile
    private var chainRows: List<String> = emptyList()

    @Volatile
    private var chainContext: String = ""

    @Volatile
    private var rememberedChain: String = ""

    @Volatile
    private var rememberedRows: List<String> = emptyList()

    private var restoreJob: Job? = null

    private var primeJob: Job? = null

    @Volatile
    private var primedStationFor: String = ""

    private val restoreTried = HashSet<String>()

    @Volatile
    private var held: CommandTrack? = null
    private var blankSeen = ""

    private val hydrateTried = HashSet<String>()
    private var machineLearned = ""

    @Volatile
    private var pendingTrack = ""

    @Volatile
    private var pendingContext = ""

    @Volatile
    private var pendingIndex = -1

    @Volatile
    private var pendingAt = 0L

    @Volatile
    private var resumeUri = ""

    @Volatile
    private var resumeAt = 0L

    @Volatile
    private var queuedTail: List<TrackItem> = emptyList()

    @Volatile
    private var pendingShuffle: Boolean? = null

    @Volatile
    private var shuffledContext: String = ""

    @Volatile
    private var sessionQueue: List<String> = emptyList()

    @Volatile
    private var sessionQueueContext: String = ""

    @Volatile
    private var pendingRepeatContext: Boolean? = null

    @Volatile
    private var pendingRepeatTrack: Boolean? = null

    @Volatile
    private var pendingModeAt = 0L

    @Volatile
    private var pendingSmart: Boolean? = null

    @Volatile
    private var enhanceLens = ""

    @Volatile
    private var enhanceLensFor = ""

    @Volatile
    private var enhancedRows: List<SmartShuffleRow> = emptyList()
    private var playedSmart = mutableSetOf<String>()

    @Volatile
    private var enhancedFor = ""

    @Volatile
    private var enhanceInFlight = false

    private val decorated = java.util.concurrent.ConcurrentHashMap<String, TrackItem>()

    private val undecoratable = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )

    private val playlistNames = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val playlistNameTried = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )

    private var decorateJob: Job? = null

    @Volatile
    private var decoratePending: List<String> = emptyList()

    @Volatile
    private var resumeRow = -1

    @Volatile
    private var advancing = false

    @Volatile
    private var clusterSeen = ""

    @Volatile
    private var activeSeen = ""

    @Volatile
    private var engineSeen = ""

    val deviceId: String get() = connect.deviceId

    init {
        connect.accepts = accept@{ command ->
            if (command.isControl) return@accept true
            if (command.isReplaceState) return@accept true
            val track = command.currentTrack
            val named = track?.uri.orEmpty()
            val waiting = pendingActive()
            val sameContext = waiting &&
                pendingContext.isNotBlank() &&
                track?.contextUri == pendingContext
            val accepted = command.isAd ||
                    (waiting && (named == pendingTrack || sameContext)) ||
                    (!waiting && (!player.hasSource || player.state.value.trackUri == named))
            if (!accepted) {
                log.i("command dropped '${named.takeLast(12)}' " +
                        "pending='${pendingTrack.takeLast(12)}' waiting=$waiting " +
                        "ctx='${track?.contextUri?.takeLast(12).orEmpty()}' " +
                        "engine='${player.state.value.trackUri.takeLast(12)}'"
                )
            } else if (waiting && sameContext && named != pendingTrack) {
                log.i("command accepted: the server started '${track?.name}' from the context we " +
                        "asked for instead of the row we named"
                )
            }
            accepted
        }
    }

    var state by mutableStateOf(ComposePlaybackState())
        private set

    val progress = PlaybackProgress()

    val progressSnapshot: PlaybackProgressSnapshot
        get() = PlaybackProgressSnapshot(progress.positionMs, progress.durationMs)

    var cluster: ClusterState? by mutableStateOf(null)
        private set

    val listener = object : ComposePlayerListener {
        override fun onPlayPause() {
            scope.launch { toggle() }
        }

        override fun onSleepTimer(option: SleepTimerOption) {
            sleepTimer(option)
        }

        override fun onSkipNext() {
            scope.launch { skipTo(next = true) }
        }

        override fun onSkipPrevious() {
            scope.launch { skipTo(next = false) }
        }

        override fun onSeek(positionMs: Long) {
            val cluster = connect.cluster.value
            val remoteActive = cluster?.activeDevice?.let { it.id != deviceId } == true
            if (remoteActive) {
                scope.launch { seekRemote(positionMs) }
                return
            }
            if (!player.hasSource) {
                log.w("seek $positionMs ignored, nothing loaded")
                return
            }
            val command = connect.playback
            if (command != null && (command.isAd || command.disallowSeeking)) {
                val why = if (command.isAd) "an ad is playing" else "the state disallows seeking"
                log.w("seek $positionMs ignored, $why")
                return
            }
            val duration = player.durationMs.takeIf { it > 0L } ?: progress.durationMs
            val target = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs
            log.i("seek $positionMs -> ${target}ms of ${duration}ms")
            progress.set(target, duration)
            player.seekTo(target)
            scope.launch { publishLocalFromEngine(target) }
        }

        override fun onShuffleToggle() {
            scope.launch { toggleShuffle() }
        }

        override fun onRepeatToggle() {
            scope.launch { toggleRepeat() }
        }

        override fun onSwitchVideo(toVideo: Boolean) {
            switchVideo(toVideo)
        }

        override fun onPlaybackSpeed(speed: Float) {
            val clamped = speed.coerceIn(0.5f, 3.5f)
            player.playbackRate(clamped)
            state = state.copy(playbackSpeed = clamped)
        }

        override fun onSkipSeek(deltaMs: Long) {
            val duration = player.durationMs.takeIf { it > 0L } ?: progress.durationMs
            val target = (progress.positionMs + deltaMs).coerceIn(0L, duration)
            onSeek(target)
        }

        override fun onPlayEpisode(uri: String, contextUri: String, hasVideo: Boolean) {
            if (hasVideo) {
                decorated[uri] = (decorated[uri] ?: TrackItem(title = "", artist = "", uri = uri))
                    .copy(hasVideo = true)
            }
            captureChain(contextUri)
            scope.launch { playTrack(uri, contextUri, -1) }
        }

        override fun onVideoQuality(height: Int) {
            player.selectVideoQuality(height)
        }

        override fun onVideoSurface(surface: android.view.Surface?) {
            player.videoSurface(surface)
        }

        override fun onPlayUri(uri: String) {
            val enhanced = enhancedQueue(chainContext)
            val at = enhanced.indexOfFirst { it.uri == uri }
            if (at >= 0) {
                scope.launch { playTrack(uri, chainContext, at) }
                return
            }
            val context = screenContext(uri)
            captureChain(context)
            scope.launch {
                val first = connect.cluster.value
                    ?.takeIf { it.contextUri == uri }
                    ?.window()
                    ?.firstOrNull { it.isVisible }
                if (first != null) playTrack(first.uri, uri, 0) else playTrack(uri, context, -1)
            }
        }

        override fun onPlayTrack(uri: String, contextUri: String, index: Int) {
            captureChain(contextUri)
            sessionQueue = emptyList()
            sessionQueueContext = ""
            scope.launch {
                playTrack(uri, contextUri, index)
                if (shuffleOn(connect.cluster.value) && !smartOn(connect.cluster.value)) {
                    applyShuffleOrder(true, contextUri, uri)
                }
            }
        }

        override fun onListRendered(uris: List<String>) {
            if (uris == screen) return
            screen = uris
            log.d("screen list now ${uris.size} rows, head=${uris.firstOrNull()?.takeLast(12)}")
        }

        override fun onLoadPlaylist(uri: String) {
            if (uri.isBlank()) return
            log.i("load playlist '${uri.takeLast(12)}'")
            scope.launch { connect.refreshCluster() }
        }

        override fun onLoadAlbum(uri: String, albumName: String, artist: String) {
            if (uri.isBlank()) return
            log.i("load album '${uri.takeLast(12)}' by '$artist'")
            scope.launch { connect.refreshCluster() }
        }

        override fun onLoadArtist(uri: String, artistName: String) {
            if (uri.isBlank()) return
            log.i("load artist '${uri.takeLast(12)}'")
            scope.launch { connect.refreshCluster() }
        }

        override fun onDeviceSheetVisible(visible: Boolean) {
            deviceSheetOpen = visible
            if (visible) scope.launch { connect.refreshCluster() }
        }

        override fun onSelectDevice(targetId: String) {
            scope.launch {
                val target = connect.cluster.value?.devices?.firstOrNull { it.id == targetId }
                    ?: if (targetId == deviceId) localDeviceRow(active = false) else null
                if (target == null) {
                    log.w("device select: '" + targetId + "' is not in the cluster any more")
                    return@launch
                }
                if (!target.canTransfer) {
                    log.w("device select: '" + target.name + "' cannot take the stream")
                    return@launch
                }
                val backHome = target.id == deviceId
                log.i("device select '" + target.name + "' -> " +
                        if (backHome) "taking the stream back here" else "transfer"
                )
                if (backHome) {
                    state = state.copy(
                        controllingRemote = false,
                        activeDeviceName = "Quackify",
                        activeDeviceId = deviceId,
                        connectedDevice = localOutputLabel(),
                        devices = state.devices.map { it.copy(active = it.id == deviceId) }
                    )
                    takeBackHere()
                    return@launch
                }
                val ok = connect.transferTo(target.id)
                if (!ok) {
                    log.w("device select: the transfer to '" + target.name + "' was refused")
                    return@launch
                }
                connect.holdsStream = false
                state = state.copy(
                    controllingRemote = true,
                    activeDeviceName = target.name,
                    activeDeviceId = target.id,
                    connectedDevice = target.name,
                    devices = state.devices.map { it.copy(active = it.id == target.id) }
                )
            }
        }

        override fun onLikeToggle() {
            val uri = state.trackUri
            if (!uri.startsWith("spotify:track:")) {
                log.w("like ignored, the state has no track uri")
                return
            }
            val next = !state.isLiked
            state = state.copy(isLiked = next)
            scope.launch {
                val ok = Library.setSaved(context, listOf(uri), next)
                log.i("like '${uri.takeLast(12)}' -> $next ok=$ok")
                if (!ok) state = state.copy(isLiked = !next)
            }
        }

        override fun onQueueAdd(uri: String) {
            queueAdd(uri)
        }

        override fun onQueueSet(uris: List<String>) {
            scope.launch {
                val cluster = connect.cluster.value
                if (cluster == null) {
                    log.w("queue set ignored, no cluster yet")
                    return@launch
                }
                val allowed = uris.filter { uri ->
                    val artist = artistUriOf(uri)
                    artist.isBlank() || !ArtistBanStore.banned(context, artist)
                }
                if (allowed.size < uris.size) {
                    log.i("queue set: dropped ${uris.size - allowed.size} banned row(s)")
                }
                if (allowed.isEmpty()) return@launch
                if (!writeQueue(allowed, cluster)) return@launch
                val local = queuedTail.associateBy { it.uri }
                queuedTail = allowed.map { local[it] ?: TrackItem(title = "", artist = "", uri = it) }
                publishQueue(cluster)
                connect.refreshCluster()
            }
        }
    }

    private fun queueTailUris(uri: String): List<String> {
        val queued = state.queue.map { it.uri }.filter { it.isNotBlank() && it != uri }
        val context = state.queueFromContext.map { it.uri }.filter { it.isNotBlank() }
        return (queued + uri + context).distinct()
    }

    private suspend fun writeQueue(uris: List<String>, cluster: ClusterState): Boolean {
        val known = (cluster.queued + cluster.fromContext).associateBy { it.uri }
        val missing = uris.filter { uri ->
            known[uri] == null &&
                (decorated[uri] ?: queuedTail.firstOrNull { it.uri == uri })?.title.isNullOrBlank()
        }
        if (missing.isNotEmpty()) {
            val rows = runCatching { TrackSource.decorate(context, missing) }.getOrDefault(emptyMap())
            rows.forEach { (uri, track) ->
                decorated[uri] = TrackItem(
                    title = track.title,
                    artist = track.artists,
                    uri = track.uri,
                    imageUrl = track.imageUrl.takeIf { it.isNotBlank() },
                    durationMs = track.durationMs,
                    isExplicit = track.explicit,
                    albumUri = track.albumUri,
                    artistUri = track.artistUri,
                    hasVideo = track.video,
                )
            }
        }
        val tracks = uris.mapNotNull { uri -> known[uri] ?: clusterRowFor(uri) }
        if (tracks.size != uris.size) {
            log.w("queue set: ${uris.size - tracks.size} of ${uris.size} rows have no metadata")
        }
        val ok = connect.setQueue(tracks)
        log.i("queue = ${tracks.size} rows ok=$ok")
        return ok
    }

    private fun clusterRowFor(uri: String): ClusterTrack? {
        val row = decorated[uri] ?: queuedTail.firstOrNull { it.uri == uri } ?: return null
        return ClusterTrack(
            uri = uri,
            uid = "",
            title = row.title,
            artistName = row.artist,
            artistUri = row.artistUri,
            imageUrl = row.imageUrl.orEmpty(),
            albumTitle = "",
            albumUri = row.albumUri,
            durationMs = row.durationMs,
            explicit = row.isExplicit,
            creativeId = "",
            provider = "queue",
        )
    }

    fun start() {
        if (dealer != null) return
        log.i("start device=" + deviceId)
        (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            ?.registerAudioDeviceCallback(localOutputWatcher, Handler(Looper.getMainLooper()))
        autoplayOnStartup = com.quackify.backend.core.store.AppPrefs.prefs(context, CLIENT_PREFS)
            .getBoolean(AUTOPLAY_ON_STARTUP_PREF, false)
        autoplayPending = true
        autoplayArmedAt = SystemClock.elapsedRealtime()
        if (autoplayOnStartup) log.i("autoplay on startup is armed")
        player.onFocusPause = { scope.launch { reportPaused() } }
        player.onFocusResume = { scope.launch { reportResumed() } }
        player.onFocusDuck = { ducked ->
            if (ducked) log.i("another app is talking over us, the volume is down") else
                log.i("the other app is done, the volume is back")
        }
        player.warm()
        if (autoplayOnStartup) {
            scope.launch {
                ArtistBanStore.warm(context)
                val savedArtist = com.quackify.backend.core.store.AppPrefs.prefs(context, CLIENT_PREFS)
                    .getString(LAST_TRACK_ARTIST_PREF, "").orEmpty()
                if (savedArtist.isNotBlank() && ArtistBanStore.bannedCached(savedArtist)) {
                    log.i("resume: the saved track's artist '$savedArtist' is banned, not warming it")
                } else {
                    player.resumeLast(connect.spclientHost)
                }
            }
        }
        connect.holdsStream = false
        deviceWatch = scope.launch {
            while (true) {
                delay(if (deviceSheetOpen || state.controllingRemote) DEVICE_WATCH_MS else DEVICE_POLL_MS)
                when {
                    player.hasSource && remoteTarget().isNotBlank() -> {
                        connect.holdsStream = false
                        connect.refreshCluster()
                    }
                    player.hasSource -> {
                        val other = connect.cluster.value?.activeDeviceId
                            ?.takeIf { it.isNotBlank() && it != deviceId }
                        if (other != null && !player.isPlaying) {
                            connect.holdsStream = false
                            connect.refreshCluster()
                        } else {
                            connect.holdsStream = true
                            publishLocalFromEngine()
                        }
                    }
                    else -> {
                        connect.holdsStream = false
                        connect.refreshCluster()
                    }
                }
            }
        }
        remoteTick = scope.launch {
            while (true) {
                delay(REMOTE_TICK_MS)
                advanceRemote()
            }
        }
        dealer = scope.launch {
            runCatching { connect.frames().collect { } }
                .onFailure { log.i("dealer stopped ${it.javaClass.simpleName}: ${it.message}") }
        }
        mirrors = scope.launch {
            launch { connect.cluster.collect(::mirror) }
            launch { connect.command.collect(::mirrorState) }
            launch { connect.pushes.collect(::smokeReport) }
            launch {
                connect.transfers.collect {
                    log.i("a transfer landed here, taking the stream back")
                    connect.refreshCluster()
                    takeBackHere()
                }
            }
            launch { connect.remoteCommands.collect(::onRemoteCommand) }
            launch { connect.registered.collect { log.i("registered=$it") } }
            launch {
                connect.connectionId.collect {
                    if (it.isNotBlank()) log.i("connection=${it.take(24)}")
                }
            }
            launch { player.events.collect(::onPlayerEvent) }
            launch {
                snapshotFlow { state.trackUri }
                    .filter { it.startsWith("spotify:track:") }
                    .collect { uri ->
                        val saved = Library.saved(context, listOf(uri))
                        if (state.trackUri == uri) {
                            state = state.copy(isLiked = uri in saved)
                        }
                    }
            }
            launch {
                snapshotFlow { state.trackUri }.collect { uri ->
                    if (!sleepEndOfTrackArmed) return@collect
                    if (uri.isBlank()) {
                        sleepEndOfTrackArmed = false
                        state = state.copy(sleepTimer = null)
                        return@collect
                    }
                    if (uri == sleepArmedTrackUri) return@collect
                    sleepEndOfTrackArmed = false
                    log.i("sleep timer: the armed track gave way to '${uri.takeLast(12)}', stopping")
                    fireSleepTimer()
                    state = state.copy(sleepTimer = null)
                }
            }
            launch {
                player.state.collect { playing ->
                    if (state.controllingRemote) return@collect
                    val waiting = held
                    val engineUri = playing.trackUri
                    val engineOwns = player.hasSource && engineUri.isNotBlank()
                    val local = engineOwns && (waiting == null || engineUri == waiting.uri)
                    val position = if (engineOwns) playing.positionMs else progress.positionMs
                    val duration = if (engineOwns) {
                        playing.durationMs.takeIf { it > 0L } ?: progress.durationMs
                    } else {
                        progress.durationMs
                    }
                    val next = state.copy(
                        title = if (local && waiting != null) waiting.name else state.title,
                        artist = if (local && waiting != null) waiting.artists else state.artist,
                        coverUrl = if (local && waiting != null) waiting.imageUrl else state.coverUrl,
                        trackUri = if (engineOwns) engineUri else state.trackUri,
                        artistUri = if (engineOwns && engineUri != state.trackUri) artistUriOf(engineUri)
                        else state.artistUri,
                        albumUri = if (engineOwns && engineUri != state.trackUri) albumUriOf(engineUri)
                        else state.albumUri,
                        isPlaying = if (engineOwns) playing.playing else state.isPlaying,
                        isBuffering = if (engineOwns) playing.buffering else false,
                    )
                    val identityChanged = next.trackUri != state.trackUri ||
                        next.title != state.title || next.artist != state.artist ||
                        next.coverUrl != state.coverUrl || next.artistUri != state.artistUri ||
                        next.albumUri != state.albumUri || next.isPlaying != state.isPlaying ||
                        next.isBuffering != state.isBuffering
                    progress.set(position, duration)
                    if (!identityChanged && state == next) {
                        threshold(playing)
                        return@collect
                    }
                    state = next
                    if (next.trackUri.startsWith("spotify:track:") && next.artistUri.isNotBlank()) {
                        com.quackify.backend.core.store.AppPrefs.prefs(context, CLIENT_PREFS).edit()
                            .putString(LAST_TRACK_ARTIST_PREF, next.artistUri)
                            .apply()
                    }
                    if (local && waiting != null) {
                        held = null
                        log.i("held '${waiting.name}' took the engine")
                    }
                    if (engineUri.isNotBlank() && engineUri != engineSeen) {
                        engineSeen = engineUri
                        publishQueue(connect.cluster.value)
                    }
                    threshold(playing)
                }
            }
        }
    }

    fun handOff() {
        pauseRemoteOnExit()
    }

    fun stop() {
        pauseRemoteOnExit()
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepEndOfTrackArmed = false
        sleepArmedTrackUri = ""
        connect.holdsStream = false
        runCatching {
            runBlocking { withTimeoutOrNull(2_000L) { connect.unregister() } }
        }.onFailure { log.w("unregister on stop failed ${it.javaClass.simpleName}") }
        dealer?.cancel()
        dealer = null
        deviceWatch?.cancel()
        deviceWatch = null
        remoteTick?.cancel()
        remoteTick = null
        mirrors?.cancel()
        mirrors = null
        player.release()
        connect.reset()
        thresholdTrack = ""
        held = null
        clearPending()
        chainRows = emptyList()
        chainContext = ""
        rememberedChain = ""
        rememberedRows = emptyList()
        restoreJob?.cancel()
        restoreJob = null
        restoreTried.clear()
        queuedTail = emptyList()
        decorated.clear()
        undecoratable.clear()
        resumeRow = -1
        advancing = false
        clusterSeen = ""
        activeSeen = ""
        engineSeen = ""
        cluster = null
        state = ComposePlaybackState()
        progress.reset()
        scope.cancel()
        hydrateTried.clear()
        machineLearned = ""
        log.i("bridge stopped, every collector and the socket are gone")
    }

    fun reset() {
        log.i("reset, dropping the device state of the previous session")
        dealer?.cancel()
        dealer = null
        deviceWatch?.cancel()
        deviceWatch = null
        remoteTick?.cancel()
        remoteTick = null
        mirrors?.cancel()
        mirrors = null
        scope.cancel()
        if (player.isPlaying) player.pause()
        runCatching {
            runBlocking {
                withTimeoutOrNull(2_000L) {
                    if (connect.holdsStream && player.hasSource) {
                        publishLocalFromEngine(playing = false)
                    }
                    connect.unregister()
                }
            }
        }.onFailure { log.w("reset cleanup failed ${it.javaClass.simpleName}") }
        player.reset()
        connect.reset()
        thresholdTrack = ""
        held = null
        clearPending()
        chainRows = emptyList()
        chainContext = ""
        queuedTail = emptyList()
        decorated.clear()
        undecoratable.clear()
        resumeRow = -1
        advancing = false
        clusterSeen = ""
        activeSeen = ""
        engineSeen = ""
        hydrateTried.clear()
        machineLearned = ""
        cluster = null
        state = ComposePlaybackState()
    }

    /**
     * Swaps the current track for its music video variant (or back), all
     * local: the audio media manifest already names the video entity
     * (video_version_uri), so no server round trip or cluster wait applies.
     * The track identity stays the audio track the whole time.
     */
    fun switchVideo(toVideo: Boolean) {
        scope.launch {
            state = state.copy(videoSwitching = true)
            if (toVideo) {
                if (videoActive()) {
                    state = state.copy(videoSwitching = false)
                    return@launch
                }
                val audioUri = state.trackUri
                val episode = audioUri.startsWith("spotify:episode:")
                val allowed = if (episode) otherVideosAllowed else musicVideosAllowed
                if (!allowed) {
                    log.i("video switch blocked, the ${if (episode) "other videos" else "music videos"} switch is off")
                    state = state.copy(videoSwitching = false)
                    return@launch
                }
                val hasVideo = if (episode) {
                    VideoManifests.episodeVideoKnown(audioUri) &&
                            VideoManifests.knownEpisodeVideoId(audioUri) != null
                } else {
                    decorated[audioUri]?.hasVideo == true
                }
                if ((!episode && !audioUri.startsWith("spotify:track:")) || !hasVideo) {
                    log.i("video switch ignored, '${audioUri.takeLast(12)}' has no music video")
                    return@launch
                }
                val at = player.positionMs
                val videoUri = if (episode) audioUri else runCatching {
                    VideoManifests.videoVersionUri(context, connect.spclientHost, audioUri)
                }.getOrNull()
                if (videoUri == null) {
                    log.w("video switch dropped, no video_version_uri for '${audioUri.takeLast(12)}'")
                    state = state.copy(videoSwitching = false)
                    return@launch
                }
                val source = runCatching {
                    VideoManifests.resolve(context, connect.spclientHost, videoUri)
                }.getOrNull()
                if (source == null) {
                    log.w("video switch dropped, '${videoUri.takeLast(12)}' did not resolve")
                    state = state.copy(videoSwitching = false)
                    return@launch
                }
                log.i("video switch -> video at ${at}ms")
                state = state.copy(
                    videoActive = true,
                    musicVideoAvailable = true,
                    videoQualities = source.manifest.videoProfiles.map { it.height }.distinct().sortedDescending(),
                    videoSwitching = false,
                )
                player.playVideo(videoUri, audioUri, source.manifest.durationMs, at)
            } else {
                if (!videoActive()) {
                    state = state.copy(videoSwitching = false)
                    return@launch
                }
                val audioUri = state.trackUri
                val at = player.positionMs
                val wasPlaying = player.isPlaying
                log.i("video switch -> audio at ${at}ms")
                state = state.copy(videoActive = false, videoQualities = emptyList())
                player.stopVideo()
                val cluster = connect.cluster.value
                val row = cluster?.window()?.firstOrNull { it.uri == audioUri }
                    ?: cluster?.track?.takeIf { it.uri == audioUri }
                    ?: clusterRowFor(audioUri)
                if (row == null) {
                    log.w("video switch back dropped, no row for '${audioUri.takeLast(12)}'")
                    state = state.copy(videoSwitching = false)
                    return@launch
                }
                val index = cluster?.window()?.indexOfFirst { it.uri == audioUri }?.takeIf { it >= 0 } ?: -1
                startLocal(row, state.contextUri, index, at, playing = wasPlaying)
                state = state.copy(videoSwitching = false)
            }
        }
    }

    /** The video engine gave up: back to the audio track at the last known position. */
    private fun fallbackFromVideo() {
        log.w("the video engine failed, falling back to audio")
        state = state.copy(videoActive = false, videoQualities = emptyList(), videoSwitching = false)
        player.stopVideo()
        scope.launch {
            val audioUri = state.trackUri
            val cluster = connect.cluster.value
            val row = cluster?.window()?.firstOrNull { it.uri == audioUri }
                ?: cluster?.track?.takeIf { it.uri == audioUri }
                ?: clusterRowFor(audioUri)
            if (row == null) {
                log.w("video fallback dropped, no row for '${audioUri.takeLast(12)}'")
                return@launch
            }
            val index = cluster?.window()?.indexOfFirst { it.uri == audioUri }?.takeIf { it >= 0 } ?: -1
            startLocal(row, state.contextUri, index, progress.positionMs, playing = state.isPlaying)
        }
    }

    private fun refreshVideoFlags() {
        val uri = state.trackUri
        val active = videoActive()
        val episode = uri.startsWith("spotify:episode:")
        val available = active || when {
            episode -> otherVideosAllowed && VideoManifests.episodeVideoKnown(uri) &&
                    VideoManifests.knownEpisodeVideoId(uri) != null
            else -> musicVideosAllowed && decorated[uri]?.hasVideo == true
        }
        if (uri == videoFlagsUri && state.musicVideoAvailable == available && state.videoActive == active) return
        videoFlagsUri = uri
        state = state.copy(
            musicVideoAvailable = available,
            videoActive = active,
            videoQualities = if (active) state.videoQualities else emptyList(),
        )
        // The web player starts video episodes in video mode; mirror that here
        // once we own the stream and the episode's video file is confirmed.
        if (episode && available && !active &&
            uri !in videoAutoSwitched && player.state.value.trackUri == uri
        ) {
            videoAutoSwitched += uri
            log.i("episode '$uri' has video, switching to video automatically")
            switchVideo(true)
            return
        }
        if (episode && otherVideosAllowed && !VideoManifests.episodeVideoKnown(uri) && uri != videoPrefetchUri) {
            videoPrefetchUri = uri
            scope.launch {
                runCatching { VideoManifests.episodeVideoId(context, connect.spclientHost, uri) }
                    .onFailure { log.w("episode video probe failed ${it.message}") }
                refreshVideoFlags()
            }
            return
        }
        if (available && !active && uri != videoPrefetchUri) {
            videoPrefetchUri = uri
            scope.launch {
                runCatching { VideoManifests.resolve(context, connect.spclientHost, uri) }
                    .onFailure { log.w("video prefetch failed ${it.message}") }
            }
        }
    }

    private fun videoActive(): Boolean = player.state.value.isVideo

    private fun markPending(uri: String, index: Int, context: String = "") {
        pendingTrack = uri
        pendingIndex = index
        pendingContext = context
        pendingAt = System.currentTimeMillis()
    }

    private fun clearPending() {
        pendingTrack = ""
        pendingIndex = -1
        pendingContext = ""
        pendingAt = 0L
    }

    private fun pendingActive(): Boolean =
        pendingTrack.isNotBlank() && System.currentTimeMillis() - pendingAt < PENDING_MS

    /** Speculative manifests for playlist rows, so a tap skips that round trip. */
    fun prefetchUris(uris: List<String>) {
        if (uris.isEmpty()) return
        var wanted = 0
        uris.forEach { uri ->
            if ((uri.startsWith("spotify:track:") || uri.startsWith("spotify:episode:")) && !bannedCachedUri(uri)) {
                wanted++
                player.prefetch(uri, connect.spclientHost)
            }
        }
        if (wanted > 0) log.i("prefetch $wanted playlist row(s)")
    }

    suspend fun playFromCluster(offset: Int = 0): Boolean {
        val cluster = connect.cluster.value
        if (cluster == null) {
            log.w("play: no cluster yet, registered=${connect.registered.value}")
            return false
        }
        val target = targetFrom(cluster, offset)
        val track = target?.track ?: cluster.track
        val index = target?.index ?: cluster.contextIndex
        log.i("playFromCluster offset=$offset anchor=${target?.anchor ?: -1} pivot=${cluster.pivot()} " +
                "ctxIdx=$index '${track.title}' uid=${track.uid.take(12)} context='${cluster.contextUri}'"
        )
        if (track.uri.startsWith("spotify:track:") || track.uri.startsWith("spotify:episode:")) {
            val artist = track.artistUri.ifBlank { artistUriOf(track.uri) }
            if (artist.isNotBlank() && ArtistBanStore.bannedCached(artist)) {
                log.i("play: '${track.uri.takeLast(12)}' blocked, artist '$artist' is banned")
                return false
            }
            player.prefetch(track.uri, connect.spclientHost)
        }
        markPending(track.uri, index, cluster.contextUri)
        val active = cluster.activeDevice
        val to = if (active != null && active.id != deviceId) active.id else ""
        if (to.isNotBlank()) {
            log.i("play: the stream stays on the remote device '${active!!.name}'")
        }
        val ok = connect.play(cluster.contextUri, track.uri, track.uid, index, targetDeviceId = to)
        if (!ok) {
            clearPending()
            log.w("playFromCluster: the server refused '${track.title}', dropping the wait")
        }
        return ok
    }

    private fun modeOverrideFresh(): Boolean =
        System.currentTimeMillis() - pendingModeAt < MODE_OVERRIDE_MS

    private fun shuffleOn(cluster: ClusterState?): Boolean {
        if (modeOverrideFresh()) pendingShuffle?.let { return it }
        return cluster?.shuffle == true
    }

    private fun repeatContextOn(cluster: ClusterState?): Boolean {
        if (modeOverrideFresh()) pendingRepeatContext?.let { return it }
        return cluster?.repeatContext == true
    }

    private fun repeatTrackOn(cluster: ClusterState?): Boolean {
        if (modeOverrideFresh()) pendingRepeatTrack?.let { return it }
        return cluster?.repeatTrack == true
    }

    private fun smartOn(cluster: ClusterState?): Boolean {
        if (modeOverrideFresh()) pendingSmart?.let { return it }
        return cluster?.enhancement == ENHANCEMENT_SMART
    }

    private fun rememberModes(
        shuffle: Boolean?,
        smart: Boolean?,
        repeatContext: Boolean?,
        repeatTrack: Boolean?,
    ) {
        if (shuffle != null) pendingShuffle = shuffle
        if (smart != null) pendingSmart = smart
        if (repeatContext != null) pendingRepeatContext = repeatContext
        if (repeatTrack != null) pendingRepeatTrack = repeatTrack
        pendingModeAt = System.currentTimeMillis()
    }

    private fun smartShuffleContext(contextUri: String): Boolean =
        contextUri.startsWith("spotify:playlist:") ||
            (contextUri.startsWith("spotify:user:") && contextUri.endsWith(":collection")) ||
            contextUri == "spotify:collection:tracks"

    private fun smartAvailable(cluster: ClusterState?): Boolean =
        cluster?.canSmartShuffle == true &&
            smartShuffleContext(cluster.contextUri.ifBlank { state.contextUri })

    private fun publishModes() {
        val cluster = connect.cluster.value
        val shuffle = shuffleOn(cluster)
        val smart = smartOn(cluster)
        val repeatContext = repeatContextOn(cluster)
        val repeatTrack = repeatTrackOn(cluster)
        val shuffleMode = when {
            smart -> ShuffleMode.SMART_SHUFFLE
            shuffle -> ShuffleMode.SHUFFLE
            else -> ShuffleMode.OFF
        }
        val repeatMode = when {
            repeatTrack -> RepeatMode.REPEAT_ONE
            repeatContext -> RepeatMode.REPEAT_ALL
            else -> RepeatMode.OFF
        }
        val canShuffle = cluster?.canShuffle ?: true
        val canSmartShuffle = smartAvailable(cluster)
        val canRepeat = (cluster?.canRepeatContext ?: true) || (cluster?.canRepeatTrack ?: true)
        val currentEnhanced = if (cluster?.track?.uri == state.trackUri && state.trackUri.isNotBlank()) {
            state.isEnhanced
        } else {
            currentTrackEnhanced(cluster)
        }
        if (state.shuffleMode != shuffleMode || state.repeatMode != repeatMode ||
            state.canShuffle != canShuffle || state.canSmartShuffle != canSmartShuffle ||
            state.canRepeat != canRepeat || state.isEnhanced != currentEnhanced
        ) {
            log.i(
                "modes -> shuffle=$shuffleMode repeat=$repeatMode enhancement='${cluster?.enhancement.orEmpty()}' " +
                    "canShuffle=$canShuffle canSmart=$canSmartShuffle canRepeat=$canRepeat enhanced=$currentEnhanced"
            )
            state = state.copy(
                shuffleMode = shuffleMode,
                repeatMode = repeatMode,
                canShuffle = canShuffle,
                canSmartShuffle = canSmartShuffle,
                canRepeat = canRepeat,
                isEnhanced = currentEnhanced,
            )
        }
    }

    /**
     * The sparkle badge belongs to the track, not to the mode: only the rows
     * the enhance lens added carry 'enhanced_recommendation' as their provider.
     */
    private fun currentTrackEnhanced(cluster: ClusterState?): Boolean {
        if (cluster == null) return false
        val uri = cluster.track.uri
        if (uri.isBlank()) return false
        if (cluster.track.isEnhanced) return true
        if (enhancedQueue(cluster.contextUri.ifBlank { chainContext }).any { it.uri == uri && it.enhanced }) {
            return true
        }
        return cluster.window().any { it.uri == uri && it.isEnhanced } ||
            cluster.nextTracks.any { it.uri == uri && it.isEnhanced } ||
            cluster.queued.any { it.uri == uri && it.isEnhanced }
    }

    private suspend fun toggleShuffle() {
        val cluster = connect.cluster.value
        val target = cluster?.activeDevice?.id?.takeIf { it.isNotBlank() } ?: deviceId
        val now = shuffleOn(cluster)
        val nowSmart = smartOn(cluster)
        val smartAllowed = smartAvailable(cluster)
        val enhanceWasOn = enhancedFor.isNotBlank() || cluster?.enhancement == ENHANCEMENT_SMART
        val next: Boolean
        val nextSmart: Boolean
        when {
            !now -> {
                next = true
                nextSmart = false
            }
            now && !nowSmart && smartAllowed -> {
                if (resolveLens(cluster?.contextUri.orEmpty().ifBlank { chainContext }) == null) {
                    next = false
                    nextSmart = false
                } else {
                    next = true
                    nextSmart = true
                }
            }
            else -> {
                next = false
                nextSmart = false
            }
        }
        log.i("shuffle toggle on '${target.take(8)}' -> $next smart=$nextSmart")
        if (!connect.setShuffleOn(target, next, nextSmart)) {
            log.w("shuffle: '${target.take(8)}' refused the option, applying it locally anyway")
        }
        rememberModes(shuffle = next, smart = nextSmart, repeatContext = null, repeatTrack = null)
        if (!nextSmart) {
            enhancedRows = emptyList()
            enhancedFor = ""
            playedSmart.clear()
            publishQueue(connect.cluster.value)
            if (enhanceWasOn) {
                val contextUri = cluster?.contextUri.orEmpty().ifBlank { chainContext }
                if (contextUri.isNotBlank()) {
                    val dropped = connect.clearContextLens(target, contextUri)
                    log.i("shuffle: dropped the enhance lens from '${contextUri.takeLast(16)}' ok=$dropped")
                }
            }
            applyShuffleOrder(next)
        }
        publishModes()
        if (nextSmart) enhanceReset(cluster)
        delay(MODE_SETTLE_MS)
        runCatching { connect.refreshCluster() }
        publishModes()
    }

    private suspend fun applyShuffleOrder(
        turnOn: Boolean,
        contextUri: String = "",
        anchorUri: String = "",
    ) {
        val cluster = connect.cluster.value
        val context = contextUri.ifBlank { cluster?.contextUri.orEmpty() }
        if (!usableContext(context)) return
        if (context.contains(":station") || context.contains(":radio")) return
        if (remoteTarget().isNotBlank()) return
        val rows = chainFor().ifEmpty { cluster?.window()?.filter { it.isVisible }?.map { it.uri }.orEmpty() }
        if (rows.size < 2) {
            log.i("shuffle order: '${context.takeLast(16)}' has ${rows.size} row(s), nothing to reorder")
            return
        }
        val anchor = anchorUri.ifBlank {
            pendingTrack.takeIf { pendingActive() } ?: (cluster?.let { playingUri(it) } ?: "")
        }
        if (anchor.isBlank() || anchor !in rows) {
            log.i("shuffle order: the playing row is outside the context list, skipping")
            return
        }
        sessionQueue = if (turnOn) {
            rows.filterNot { it == anchor }.shuffled()
        } else {
            rows.drop(rows.indexOf(anchor) + 1)
        }
        sessionQueueContext = context
        shuffledContext = context
        log.i("shuffle order '${context.takeLast(16)}' on=$turnOn -> ${sessionQueue.size} row(s) ahead of '${anchor.takeLast(12)}'")
        publishQueue(cluster)
    }

    /**
     * Tells the enhance lens that a recommendation was actually consumed: the
     * server only keeps feeding rows while the taps keep coming.
     */
    private fun sendSmartTap(uri: String, uid: String) {
        val cluster = connect.cluster.value
        val contextUri = cluster?.contextUri.orEmpty().ifBlank { chainContext }
        if (contextUri.isBlank()) return
        val row = enhancedQueue(contextUri).firstOrNull { it.uri == uri && it.enhanced }
        if (row == null) return
        scope.launch {
            val lens = resolveLens(contextUri) ?: return@launch
            val rows = SmartShuffleSource.tap(
                context,
                EncoreAuth.token(context),
                connect.spclientHost,
                lens,
                row,
            )
            log.i("smart shuffle tap on '${uri.takeLast(12)}' ok=${rows.isNotEmpty()} rows=${rows.size}")
            if (rows.isNotEmpty()) {
                val known = enhancedRows.mapTo(HashSet()) { it.uri }
                enhancedRows = enhancedRows + rows.filter { it.uri !in known }
                publishQueue(connect.cluster.value)
            }
        }
    }

    private suspend fun resolveLens(contextUri: String): String? {
        if (enhanceLens.isNotBlank() && enhanceLensFor == contextUri) return enhanceLens
        val eligibility = SmartShuffleSource.eligibility(context, EncoreAuth.token(context), contextUri)
        if (!eligibility.canSmartShuffle || eligibility.lensUri.isBlank()) {
            log.i("smart shuffle: '$contextUri' is not eligible")
            return null
        }
        enhanceLens = eligibility.lensUri
        enhanceLensFor = contextUri
        return enhanceLens
    }

    private suspend fun enhanceReset(cluster: ClusterState?): Boolean {
        val contextUri = cluster?.contextUri.orEmpty().ifBlank { chainContext }
        if (contextUri.isBlank()) return false
        val lens = resolveLens(contextUri) ?: return false
        enhanceInFlight = true
        scope.launch {
            try {
                val rows = SmartShuffleSource.reset(context, EncoreAuth.token(context), connect.spclientHost, lens)
                if (!smartOn(connect.cluster.value)) return@launch
                enhancedRows = rows
                enhancedFor = contextUri
                playedSmart.clear()
                log.i("smart shuffle reset on '${lens.takeLast(24)}' rows=${rows.size} " +
                    "enhanced=${rows.count { it.enhanced }}")
                publishQueue(connect.cluster.value)
            } finally {
                enhanceInFlight = false
            }
        }
        return true
    }

    private fun enhancedQueue(contextUri: String): List<SmartShuffleRow> =
        if (contextUri.isNotBlank() &&
            contextUri.substringAfterLast(':') == enhancedFor.substringAfterLast(':')
        ) enhancedRows else emptyList()

    /**
     * The next candidates are the enhanced rows we have not played in this
     * session, in local insertion order: the reset sets the order and each
     * tap appends the server's new feed at the end. The local play history
     * is what prevents repeats; when the pool runs out it resets and the
     * server's view takes over again.
     */
    private fun enhancedUpcoming(contextUri: String, current: String): List<SmartShuffleRow> {
        val rows = enhancedQueue(contextUri)
        if (rows.isEmpty()) return emptyList()
        val fresh = rows
            .filter { it.enhanced && it.uri != current && it.uri !in playedSmart }
        if (fresh.isNotEmpty()) return fresh
        playedSmart.clear()
        return rows.filter { it.enhanced && it.uri != current }
    }

    /**
     * A cold start restores the modes from the cluster but not the enhance
     * session: the first play in a smart context re-arms the lens and pulls
     * a fresh enhanced queue.
     */
    private fun rearmSmart(contextUri: String) {
        if (enhanceInFlight || contextUri.isBlank() || !smartShuffleContext(contextUri)) return
        if (enhancedQueue(contextUri).isNotEmpty()) return
        if (!smartOn(connect.cluster.value)) return
        enhanceInFlight = true
        scope.launch {
            try {
                val lens = resolveLens(contextUri) ?: return@launch
                val target = connect.cluster.value?.activeDevice?.id?.takeIf { it.isNotBlank() } ?: deviceId
                val ok = connect.setShuffleOn(target, true, true)
                if (!ok) {
                    log.w("smart shuffle re-arm: the server refused the options")
                    return@launch
                }
                val rows = SmartShuffleSource.reset(context, EncoreAuth.token(context), connect.spclientHost, lens)
                if (!smartOn(connect.cluster.value)) return@launch
                enhancedRows = rows
                enhancedFor = contextUri
                playedSmart.clear()
                log.i("smart shuffle re-armed on '${contextUri.takeLast(16)}' " +
                    "rows=${rows.size} enhanced=${rows.count { it.enhanced }}"
                )
                publishQueue(connect.cluster.value)
            } finally {
                enhanceInFlight = false
            }
        }
    }

    private suspend fun toggleRepeat() {
        val cluster = connect.cluster.value
        val target = cluster?.activeDevice?.id?.takeIf { it.isNotBlank() } ?: deviceId
        val nowContext = repeatContextOn(cluster)
        val nowTrack = repeatTrackOn(cluster)
        val nextContext: Boolean
        val nextTrack: Boolean
        when {
            !nowContext && !nowTrack -> {
                nextContext = true
                nextTrack = false
            }
            nowContext -> {
                nextContext = false
                nextTrack = true
            }
            else -> {
                nextContext = false
                nextTrack = false
            }
        }
        log.i("repeat toggle on '${target.take(8)}' -> context=$nextContext track=$nextTrack")
        if (!connect.setRepeatOn(target, nextContext, nextTrack)) {
            log.w("repeat: '${target.take(8)}' refused the option, applying it locally anyway")
        }
        rememberModes(shuffle = null, smart = null, repeatContext = nextContext, repeatTrack = nextTrack)
        publishModes()
        delay(MODE_SETTLE_MS)
        runCatching { connect.refreshCluster() }
        publishModes()
    }

    private suspend fun toggle() {
        val cluster = connect.cluster.value
        val active = cluster?.activeDevice
        if (active != null && active.id != deviceId) {
            toggleRemote(cluster, active)
            return
        }
        val command = connect.playback
        if (command == null) {
            toggleLocal()
            return
        }
        if (!player.hasSource) {
            val track = command.currentTrack
            if (track == null) {
                log.w("toggle: no track to start")
                return
            }
            if (remoteTarget().isNotBlank()) {
                log.i("toggle: another device is playing, this one stays quiet")
                return
            }
            val from = command.currentState?.initialPositionMs ?: 0L
            log.i("toggle: nothing loaded, starting '${track.name}' from=${from}ms")
            player.play(track, connect.spclientHost, from)
            return
        }
        val wasPlaying = player.isPlaying
        if (wasPlaying) player.pause() else player.resume()
        val source = if (wasPlaying) DebugSource.PAUSE else DebugSource.RESUME
        val snapshot = Snapshot.of(
            command,
            source,
            positionMs = player.positionMs,
            speed = if (wasPlaying) 0.0 else 1.0,
            paused = wasPlaying,
            videoOn = canvasOn,
        )
        if (snapshot == null) {
            log.w("toggle: no snapshot")
            return
        }
        log.i("toggle -> paused=$wasPlaying source=$source pos=${player.positionMs}")
        connect.report(snapshot)
    }

    /** Arms, re-arms or cancels the sleep timer; the bridge owns the countdown
     * so it survives recomposition and keeps ticking while the app idles. */
    private fun sleepTimer(option: SleepTimerOption) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepEndOfTrackArmed = false
        sleepArmedTrackUri = ""
        when (option) {
            SleepTimerOption.OFF -> {
                state = if (state.sleepTimer == null) state else state.copy(sleepTimer = null)
                log.i("sleep timer off")
            }
            SleepTimerOption.END_OF_TRACK -> {
                sleepEndOfTrackArmed = true
                sleepArmedTrackUri = state.trackUri
                state = state.copy(sleepTimer = option)
                log.i("sleep timer armed at the end of '${state.trackUri.takeLast(12)}'")
            }
            else -> {
                state = state.copy(sleepTimer = option)
                log.i("sleep timer armed for ${option.minutes} minutes")
                sleepTimerJob = scope.launch {
                    delay(option.minutes * 60_000L)
                    log.i("sleep timer fired after ${option.minutes}m")
                    fireSleepTimer()
                    state = state.copy(sleepTimer = null)
                }
            }
        }
    }

    private suspend fun fireSleepTimer() {
        if (!state.isPlaying) return
        val active = connect.cluster.value?.activeDevice
        if (active != null && active.id != deviceId) {
            toggle()
            return
        }
        if (player.hasSource) {
            player.pause()
            reportPaused()
        }
    }

    private suspend fun toggleRemote(cluster: ClusterState, active: com.quackify.backend.model.ConnectDevice) {
        connect.refreshCluster()
        val fresh = connect.cluster.value ?: cluster
        val playing = fresh.isPlaying
        log.i("toggle on the remote device '" + active.name + "', the cluster says it is " +
                (if (playing) "playing" else "paused")
        )
        val ok = connect.setPausedOn(active.id, playing)
        if (!ok) {
            log.w("toggle: '" + active.name + "' refused the command")
            return
        }
        rememberRemoteIntent(!playing)
        val at = fresh.positionNow()
        val duration = fresh.durationMs.takeIf { it > 0L } ?: progress.durationMs
        state = state.copy(isPlaying = !playing)
        progress.set(at, duration)
        delay(SKIP_SETTLE_MS)
        connect.refreshCluster()
    }

    private fun queueAdd(uri: String) {
        scope.launch {
            if (uri.isBlank()) return@launch
            val bannedArtist = artistUriOf(uri).takeIf { it.isNotBlank() }
            if (bannedArtist != null && ArtistBanStore.banned(context, bannedArtist)) {
                log.i("queue: '$uri' refused, artist '$bannedArtist' is banned")
                return@launch
            }
            if (queuedTail.none { it.uri == uri }) {
                queuedTail = queuedTail + TrackItem(title = "", artist = "", uri = uri)
            }
            publishQueue(connect.cluster.value)
            var added = connect.addToQueue(uri)
            if (!added) {
                connect.refreshCluster()
                delay(QUEUE_RETRY_MS)
                added = connect.addToQueue(uri)
            }
            log.i("queue += '$uri' ok=$added")
            val cluster = connect.cluster.value
            val written = added || (cluster != null && writeQueue(queueTailUris(uri), cluster))
            if (!added && cluster != null) log.i("queue += '$uri' through set_queue ok=$written")
            if (!written) {
                queuedTail = queuedTail.filterNot { it.uri == uri }
                publishQueue(cluster)
                return@launch
            }
            publishQueue(cluster)
            connect.refreshCluster()
            scheduleDecorate(listOf(uri))
        }
    }

    private suspend fun onRemoteCommand(command: RemoteCommand) {
        if (command.isTransfer) return
        when (command.endpoint) {
            "pause" -> {
                if (!player.hasSource) return
                if (player.isPlaying) player.pause()
                reportPaused()
            }
            "resume" -> {
                if (!player.hasSource) return
                if (!player.isPlaying) player.resume()
                reportResumed()
            }
            "skip_next" -> skipTo(true)
            "skip_prev" -> skipTo(false)
            "seek_to" -> {
                if (command.seekToMs < 0L || !player.hasSource) return
                val duration = player.durationMs.takeIf { it > 0L } ?: progress.durationMs
                val target = if (duration > 0L) command.seekToMs.coerceIn(0L, duration) else command.seekToMs
                progress.set(target, duration)
                player.seekTo(target)
                publishLocalFromEngine(target)
            }
            "set_shuffling_context" -> {
                val value = command.value ?: return
                if (shuffleOn(connect.cluster.value) != value) toggleShuffle()
            }
            "set_repeating_context" -> {
                val value = command.value ?: return
                setRepeatExplicit(context = value, track = null)
            }
            "set_repeating_track" -> {
                val value = command.value ?: return
                setRepeatExplicit(context = null, track = value)
            }
            "set_options" -> {
                command.shuffle?.let { if (shuffleOn(connect.cluster.value) != it) toggleShuffle() }
                if (command.repeatContext != null || command.repeatTrack != null) {
                    setRepeatExplicit(context = command.repeatContext, track = command.repeatTrack)
                }
            }
            "add_to_queue" -> {
                val uri = command.trackUri
                if (uri.isNotBlank()) queueAdd(uri)
            }
            "set_queue" -> scope.launch { runCatching { connect.refreshCluster() } }
            else -> log.i("remote command '${command.endpoint}' is not handled here yet")
        }
    }

    private suspend fun setRepeatExplicit(context: Boolean?, track: Boolean?) {
        if (context == null && track == null) return
        val cluster = connect.cluster.value
        val target = cluster?.activeDevice?.id?.takeIf { it.isNotBlank() } ?: deviceId
        val nextContext = context ?: repeatContextOn(cluster)
        val nextTrack = track ?: repeatTrackOn(cluster)
        if (nextContext == repeatContextOn(cluster) && nextTrack == repeatTrackOn(cluster)) return
        log.i("repeat set on '${target.take(8)}' -> context=$nextContext track=$nextTrack")
        if (!connect.setRepeatOn(target, nextContext, nextTrack)) {
            log.w("repeat: '${target.take(8)}' refused the option, applying it locally anyway")
        }
        rememberModes(shuffle = null, smart = null, repeatContext = nextContext, repeatTrack = nextTrack)
        publishModes()
        delay(MODE_SETTLE_MS)
        runCatching { connect.refreshCluster() }
        publishModes()
    }

    /** Apply the account video switches; an active video of a switched-off kind drops back to audio. */
    fun setVideoPolicy(musicVideos: Boolean, otherVideos: Boolean) {
        if (musicVideosAllowed == musicVideos && otherVideosAllowed == otherVideos) return
        musicVideosAllowed = musicVideos
        otherVideosAllowed = otherVideos
        log.i("video policy music=$musicVideos other=$otherVideos")
        val episode = state.trackUri.startsWith("spotify:episode:")
        if (videoActive() && (if (episode) !otherVideosAllowed else !musicVideosAllowed)) {
            scope.launch {
                log.i("video policy drops the active video to audio")
                switchVideo(false)
            }
        } else {
            refreshVideoFlags()
        }
    }

    /** Toggle the canvas loop and push it into the next playback state report. */
    fun setCanvas(on: Boolean) {
        canvasOn = on
        log.i("canvas $on")
        scope.launch {
            val command = connect.playback ?: return@launch
            if (!player.hasSource) {
                log.i("canvas $on noted, this device is not the one playing")
                return@launch
            }
            val at = player.positionMs
            val snapshot = Snapshot.of(
                command,
                DebugSource.SEEK,
                positionMs = at,
                previousPositionMs = at,
                speed = if (player.isPlaying) 1.0 else 0.0,
                paused = !player.isPlaying,
                videoOn = on,
            ) ?: return@launch
            val accepted = connect.report(snapshot)
            log.i("canvas $on reported accepted=$accepted")
        }
    }

    private suspend fun reportPaused() {
        val command = connect.playback
        if (command == null) {
            if (player.hasSource && remoteTarget().isBlank()) {
                log.i("focus pause: no command, publishing the local state")
                publishLocalFromEngine(playing = false)
            } else {
                log.i("focus pause: no command, nothing to report")
            }
            return
        }
        val snapshot = Snapshot.of(
            command,
            DebugSource.PAUSE,
            positionMs = player.positionMs,
            speed = 0.0,
            paused = true,
            videoOn = canvasOn,
        )
        if (snapshot == null) {
            log.w("focus pause: no snapshot")
            return
        }
        val accepted = connect.report(snapshot)
        log.i("focus pause reported accepted=$accepted pos=${player.positionMs}")
    }

    private suspend fun reportResumed() {
        val command = connect.playback
        if (command == null) {
            if (player.hasSource && remoteTarget().isBlank()) {
                log.i("focus resume: no command, publishing the local state")
                publishLocalFromEngine(playing = true)
            } else {
                log.i("focus resume: no command, nothing to report")
            }
            return
        }
        val snapshot = Snapshot.of(
            command,
            DebugSource.RESUME,
            positionMs = player.positionMs,
            speed = 1.0,
            paused = false,
            videoOn = canvasOn,
        )
        if (snapshot == null) {
            log.w("focus resume: no snapshot")
            return
        }
        val accepted = connect.report(snapshot)
        log.i("focus resume reported accepted=$accepted pos=${player.positionMs}")
    }

    /** What we last told a remote device to do, so a stale cluster read cannot undo it. */
    @Volatile
    private var remoteIntentPlaying: Boolean? = null

    @Volatile
    private var remoteIntentAt = 0L

    private fun rememberRemoteIntent(playing: Boolean) {
        remoteIntentPlaying = playing
        remoteIntentAt = System.currentTimeMillis()
    }

    private fun remoteIntent(): Boolean? =
        if (System.currentTimeMillis() - remoteIntentAt < REMOTE_INTENT_MS) remoteIntentPlaying else null

    /**
     * No server state machine, no remote target: this phone just plays. The
     * cluster and the engine between them carry everything needed - the
     * track, the queue around it and where to come in - and publishing the
     * member state makes the server side follow instead of the other way
     * around.
     */
    private suspend fun toggleLocal() {
        if (player.hasSource) {
            if (player.isPlaying) {
                log.i("toggle: pausing the engine here")
                player.pause()
                publishLocalFromEngine(playing = false)
            } else {
                val loadedUri = player.state.value.trackUri
                val loadedArtist = artistUriOf(loadedUri)
                if (loadedArtist.isNotBlank() && ArtistBanStore.banned(context, loadedArtist)) {
                    log.i("toggle: resume of '$loadedUri' blocked, artist '$loadedArtist' is banned")
                    player.stop()
                    return
                }
                log.i("toggle: resuming the engine here")
                player.resume()
                publishLocalFromEngine(playing = true)
            }
            return
        }
        val cluster = connect.cluster.value
        if (cluster == null) {
            log.w("toggle: no command and no cluster, nothing to play")
            return
        }
        val track = cluster.track
        if (track.uri.isBlank()) {
            log.w("toggle: the cluster has no track, nothing to play")
            return
        }
        val clusterArtist = track.artistUri.ifBlank { artistUriOf(track.uri) }
        if (clusterArtist.isNotBlank() && ArtistBanStore.banned(context, clusterArtist)) {
            log.i("toggle: '${track.uri}' blocked, artist '$clusterArtist' is banned")
            return
        }
        val at = cluster.positionNow()
        log.i("toggle: starting '${track.title}' here at ${at}ms, this phone takes the stream")
        startLocal(track, cluster.contextUri, cluster.contextIndex, at, playing = true)
    }

    /**
     * Starts the engine on a track and claims the account's stream: the UI
     * moves first so the screen answers instantly, the engine follows and
     * the published member state makes every other client follow too.
     */
    private suspend fun startLocal(
        track: ClusterTrack,
        contextUri: String,
        contextIndex: Int,
        positionMs: Long,
        playing: Boolean,
        startEngine: Boolean = true,
    ) {
        var row = track
        if (row.title.isBlank() &&
            (row.uri.startsWith("spotify:track:") || row.uri.startsWith("spotify:episode:"))
        ) {
            runCatching { TrackSource.decorate(context, listOf(row.uri))[row.uri] }.getOrNull()?.let { found ->
                row = row.copy(
                    title = found.title,
                    artistName = found.artists,
                    artistUri = found.artistUri,
                    imageUrl = found.imageUrl.orEmpty(),
                    albumUri = found.albumUri,
                    durationMs = if (found.durationMs > 0L) found.durationMs else row.durationMs,
                )
                decorated[row.uri] = TrackItem(
                    title = found.title,
                    artist = found.artists,
                    uri = row.uri,
                    imageUrl = found.imageUrl.takeIf { it.isNotBlank() },
                    durationMs = found.durationMs,
                    isExplicit = found.explicit,
                    albumUri = found.albumUri,
                    artistUri = found.artistUri,
                    hasVideo = found.video,
                )
            }
        }
        val duration = adoptDuration.takeIf { it > 0L }
            ?: row.durationMs.takeIf { it > 0L }
            ?: progress.durationMs
        state = state.copy(
            title = row.title,
            artist = row.artistName,
            coverUrl = row.imageUrl,
            trackUri = row.uri,
            artistUri = row.artistUri.ifBlank { state.artistUri.takeIf { state.trackUri == row.uri }.orEmpty() },
            albumUri = row.albumUri.ifBlank { state.albumUri.takeIf { state.trackUri == row.uri }.orEmpty() },
            isPlaying = playing,
            isBuffering = playing,
            contextUri = contextUri,
            controllingRemote = false,
            activeDeviceName = "Quackify",
            activeDeviceId = deviceId,
            connectedDevice = localOutputLabel(),
            devices = state.devices.map { it.copy(active = it.id == deviceId) },
        )
        progress.set(positionMs, duration)
        if (startEngine) {
            player.play(row.asCommandTrack(), connect.spclientHost, positionMs)
            if (!playing) player.pause()
        }
        connect.holdsStream = true
        publishLocalFromEngine(positionMs, playing)
        publishQueue(connect.cluster.value)
        loadChapters(row.uri)
        if (row.uri.startsWith("spotify:track:") || row.uri.startsWith("spotify:episode:")) {
            if (contextUri.isBlank() || contextUri == row.uri) primeStation(row.uri)
        }
    }

    /**
     * The engine moved to the queued track on its own: adopt it instead of
     * restarting. The bridge state moves first, the member PUT tells the
     * cluster, and the started report goes out once the pushed command names
     * the same track.
     */
    private suspend fun adoptEngineAdvance(uri: String) {
        gaplessQueuedUri = ""
        player.clearQueuedNext()
        val rows = chainFor()
        val at = rowOf(rows, uri)
        if (at >= 0) resumeRow = at + 1
        val server = connect.cluster.value
        val contextUri = chainContext.ifBlank { server?.contextUri.orEmpty() }
        val window = server?.window().orEmpty()
        val windowAt = window.indexOfFirst { it.uri == uri }
        var row = windowAt.takeIf { it >= 0 }?.let { window[it] }
        if (row == null) {
            val found = runCatching { TrackSource.decorate(this.context, listOf(uri))[uri] }.getOrNull()
            row = ClusterTrack(
                uri = uri,
                uid = TrackUid.get(uri),
                title = found?.title.orEmpty(),
                artistName = found?.artists.orEmpty(),
                artistUri = found?.artistUri.orEmpty(),
                imageUrl = found?.imageUrl.orEmpty(),
                albumTitle = "",
                albumUri = found?.albumUri.orEmpty(),
                durationMs = found?.durationMs ?: 0L,
                explicit = found?.explicit ?: false,
                creativeId = "",
            )
        }
        val index = when {
            windowAt >= 0 -> server?.indexOf(windowAt) ?: -1
            at >= 0 -> at
            else -> -1
        }
        log.i("gapless: adopting '${row.title.ifBlank { uri.takeLast(12) }}' ctxIdx=$index")
        adoptDuration = player.durationForItem(uri).takeIf { it > 0L } ?: 0L
        startLocal(row, contextUri, index, 0L, playing = true, startEngine = false)
        adoptDuration = 0L
        val deadline = SystemClock.elapsedRealtime() + 3000L
        while (connect.playback?.currentTrack?.uri != uri && SystemClock.elapsedRealtime() < deadline) {
            delay(100)
        }
        val command = connect.playback
        if (command?.currentTrack?.uri != uri) {
            log.w("gapless: the command never caught up with '${uri.takeLast(12)}', started report skipped")
            return
        }
        val duration = player.durationForItem(uri).takeIf { it > 0L }
            ?: player.durationMs.takeIf { it > 0L }
            ?: row.durationMs
        progress.set(0L, duration)
        publishLocalFromEngine(0L, true)
        val snapshot = Snapshot.of(
            command,
            DebugSource.STARTED_PLAYING,
            positionMs = 0L,
            previousPositionMs = 0L,
            speed = 1.0,
            durationMs = duration,
            format = player.format.takeIf { it > 0 }?.toString().orEmpty(),
            bitrate = player.bitrate,
            quality = player.quality,
            videoOn = canvasOn,
        )
        if (snapshot == null) {
            log.w("gapless: no snapshot for the adopted track")
            return
        }
        val accepted = connect.report(snapshot)
        command.currentTrack?.let { connect.melody.audioParams(it, 0L) }
        log.i("report started_playing accepted=$accepted pos=0 (gapless) fmt=${snapshot.format}")
        queueGaplessNext(uri)
    }

    private fun loadChapters(uri: String) {
        if (!uri.startsWith("spotify:episode:")) {
            if (state.chapters.isNotEmpty()) state = state.copy(chapters = emptyList())
            return
        }
        if (state.chapters.isNotEmpty() && state.trackUri == uri) return
        state = state.copy(chapters = emptyList())
        scope.launch {
            val spans = runCatching { Podcasts.chapters(context, uri) }.getOrDefault(emptyList())
            if (spans.isEmpty() || player.state.value.trackUri != uri) return@launch
            state = state.copy(chapters = spans.map {
                ChapterMarker(title = it.title, startMs = it.startMs, endMs = it.endMs)
            })
        }
    }

    private fun primeStation(seed: String) {
        if (seed.isBlank()) return
        if (chainContext == primedStationFor && chainRows.isNotEmpty()) return
        if (primedStationFor == seed && primeJob?.isActive == true) return
        if (chainContext.isNotBlank() && chainRows.isNotEmpty()) return
        primedStationFor = seed
        primeJob?.cancel()
        primeJob = scope.launch {
            val station = runCatching { Krespot.artists.artistRadio(context, seed) }.getOrNull()
            if (station.isNullOrBlank()) return@launch
            val rows = runCatching { Krespot.collections.collection(context, station) }.getOrNull()
                ?.tracks
                ?.map { it.uri }
                ?.filter { it.startsWith("spotify:track:") || it.startsWith("spotify:episode:") }
                .orEmpty()
            if (rows.isEmpty()) return@launch
            if (chainContext.isNotBlank() && chainContext != station) return@launch
            if (player.state.value.trackUri != seed) return@launch
            val start = rows.indexOfFirst { it != seed }.coerceAtLeast(0)
            chainRows = rows
            chainContext = station
            resumeRow = start
            log.i("station '$station' primed from '${seed.takeLast(12)}', " +
                    "${rows.size} rows at row $start"
            )
            publishQueue(connect.cluster.value)
        }
    }

    /** The cluster row for what the engine plays, thin when nothing knows it yet. */
    private fun localTrackRow(): ClusterTrack? {
        val uri = player.state.value.trackUri.takeIf { it.isNotBlank() } ?: return null
        connect.cluster.value?.window()?.firstOrNull { it.uri == uri }?.let { return it }
        return clusterTrackFor(uri)
    }

    private fun clusterTrackFor(uri: String): ClusterTrack {
        val shown = decorated[uri]
        val sameShown = state.trackUri == uri
        return ClusterTrack(
            uri = uri,
            uid = TrackUid.get(uri),
            title = shown?.title?.takeIf { it.isNotBlank() }
                ?: state.title.takeIf { it.isNotBlank() && sameShown }.orEmpty(),
            artistName = shown?.artist?.takeIf { it.isNotBlank() }
                ?: state.artist.takeIf { it.isNotBlank() && sameShown }.orEmpty(),
            artistUri = shown?.artistUri?.takeIf { it.isNotBlank() }
                ?: state.artistUri.takeIf { sameShown }.orEmpty(),
            imageUrl = shown?.imageUrl?.takeIf { it.isNotBlank() }
                ?: state.coverUrl.takeIf { sameShown }.orEmpty(),
            albumTitle = "",
            albumUri = shown?.albumUri?.takeIf { it.isNotBlank() }.orEmpty(),
            durationMs = shown?.durationMs?.takeIf { it > 0L }
                ?: progress.durationMs.takeIf { it > 0L } ?: 0L,
            explicit = shown?.isExplicit ?: false,
            creativeId = "",
        )
    }

    /** Publishes the engine's current state as this phone's player state. */
    private suspend fun publishLocalFromEngine(positionMs: Long = -1L, playing: Boolean? = null) {
        val track = localTrackRow() ?: return
        val at = connect.cluster.value
        val uri = track.uri
        val index = at?.takeIf { it.track.uri == uri }?.contextIndex
            ?: rowOf(chainFor(), uri).takeIf { it >= 0 }
            ?: at?.window()?.indexOfFirst { it.uri == uri }?.takeIf { it >= 0 }?.let { at.indexOf(it) }
            ?: 0
        val contextUri = state.contextUri.takeIf { it.isNotBlank() }
            ?: chainContext.takeIf { it.isNotBlank() }
            ?: at?.contextUri.orEmpty()
        connect.publishLocalState(
            track = track,
            contextUri = contextUri,
            contextIndex = index,
            positionMs = if (positionMs >= 0L) positionMs else player.positionMs,
            playing = playing ?: player.isPlaying,
            durationMs = player.durationMs.takeIf { it > 0L } ?: track.durationMs,
            shuffle = state.shuffleMode != ShuffleMode.OFF,
            repeatContext = state.repeatMode == RepeatMode.REPEAT_ALL,
            repeatTrack = state.repeatMode == RepeatMode.REPEAT_ONE,
            enhancement = if (state.shuffleMode == ShuffleMode.SMART_SHUFFLE) ENHANCEMENT_SMART else "NONE",
            queueRevision = at?.queueRevision.orEmpty(),
            contextDescription = state.queueContextLabel
                .takeIf { it.isNotBlank() && state.queueContext == contextUri }
                .orEmpty(),
            nextRows = queueTailRows(contextUri),
        )
        refreshVideoFlags()
    }

    private fun queueTailRows(contextUri: String): List<ClusterTrack> {
        val enhanced = enhancedQueue(contextUri)
        if (enhanced.isNotEmpty()) {
            return enhanced.take(20).map { row ->
                clusterTrackFor(row.uri)
                    .copy(provider = if (row.enhanced) "enhanced_recommendation" else "")
            }
        }
        val at = connect.cluster.value
        val queued = queuedTail.map { row ->
            val known = at?.queued?.firstOrNull { it.uri == row.uri }
                ?: at?.window()?.firstOrNull { it.uri == row.uri && it.isQueued }
            known ?: asQueued(clusterTrackFor(row.uri))
        }
        val follows = at?.contextUri.orEmpty().isNotBlank() && at?.contextUri == contextUri
        val context = if (sessionQueueContext == contextUri && sessionQueue.isNotEmpty()) {
            sessionQueue.map { uri -> clusterTrackFor(uri) }
        } else if (follows) {
            at!!.fromContext.filterNot { row -> queued.any { it.uri == row.uri } }
        } else {
            emptyList()
        }
        return queued + context
    }

    private fun asQueued(track: ClusterTrack): ClusterTrack {
        val metadata = runCatching { JSONObject(track.raw) }.getOrElse { JSONObject() }
        metadata.put("is_queued", "true")
        return track.copy(provider = "queue", queued = true, raw = metadata.toString())
    }

    private fun resumePositionFor(uri: String): Long? {
        if (uri.isBlank() || uri != resumeUri) return null
        val at = resumeAt
        resumeUri = ""
        resumeAt = 0L
        return at.takeIf { it > 0L }
    }

    private suspend fun skipTo(next: Boolean) {
        val cluster = connect.cluster.value
        val active = cluster?.activeDevice
        val remote = active != null && active.id != deviceId
        if (remote) {
            val what = if (next) "next" else "previous"
            log.i("skip $what on the remote device '${active!!.name}'")
            if (connect.skipOn(active.id, next)) {
                delay(SKIP_SETTLE_MS)
                settleSkip(active.id)
                return
            }
            log.w("skip: '${active.name}' refused the $what, leaving the stream where it is")
            return
        }
        skipLocal(forward = next)
    }

    private fun applyReplaceState(command: PlaybackCommand) {
        val track = command.currentTrack ?: run {
            if (command.isTransfer) {
                log.i("transfer pushed with no machine, taking the stream from the cluster")
                scope.launch {
                    connect.refreshCluster()
                    takeBackHere()
                }
            } else {
                log.w("replace_state: it names no track")
            }
            return
        }
        log.i("replace_state: '${track.name}' idx=${command.stateIndex} " +
                "paused=${command.paused} sm=${command.stateMachineId.take(10)}"
        )
        connect.learn(command)
        TrackUid.learn(command)
        val loaded = player.state.value.trackUri
        val moved = command.seekToMs >= 0L
        val position = if (moved) command.seekToMs else progress.positionMs
        val nextArtistUri = if (track.uri != state.trackUri) artistUriOf(track.uri) else state.artistUri
        val nextAlbumUri = if (track.uri != state.trackUri) albumUriOf(track.uri) else state.albumUri
        state = state.copy(
            title = track.name,
            artist = track.artists,
            coverUrl = track.imageUrl,
            trackUri = track.uri,
            artistUri = nextArtistUri,
            albumUri = nextAlbumUri,
            isPlaying = if (loaded == track.uri && player.hasSource) player.isPlaying
            else !command.paused,
            isBuffering = if (loaded == track.uri && player.hasSource) state.isBuffering else false,
            controllingRemote = false,
            activeDeviceName = "Quackify",
            activeDeviceId = deviceId,
            connectedDevice = localOutputLabel(),
            devices = state.devices.map { it.copy(active = it.id == deviceId) },
        )
        progress.set(position, progress.durationMs)
        val from = command.startAtMs
        if (loaded == track.uri) {
            val at = if (moved) from else player.positionMs
            log.i("replace_state: the engine already holds it, following paused=${command.paused} " +
                    "seek_to=${command.seekToMs} at ${at}ms"
            )
            if (moved) player.seekTo(from)
            if (command.paused) {
                player.pause()
            } else {
                player.resume()
                if (moved) player.seekTo(from)
            }
            return
        }
        log.i("replace_state: loading '${track.name}' from ${from}ms paused=${command.paused} " +
                "seek_to=${command.seekToMs}"
        )
        player.play(track, connect.spclientHost, from)
        if (command.paused) player.pause()
    }

    private fun applyControl(command: PlaybackCommand) {
        val what = command.endpoint
        log.i("control '$what' from the server, applying it here")
        when (what) {
            "pause" -> {
                if (player.hasSource && player.isPlaying) player.pause()
                state = state.copy(isPlaying = false, isBuffering = false)
            }
            "resume" -> {
                if (player.hasSource && !player.isPlaying) player.resume()
                state = state.copy(isPlaying = true, isBuffering = false)
            }
            "skip_next" -> scope.launch { skipLocal(forward = true) }
            "skip_prev" -> scope.launch { skipLocal(forward = false) }
            "seek_to" -> {
                val at = command.startAtMs
                scope.launch { player.seekTo(at) }
            }
            else -> log.w("control '$what' is not handled here yet")
        }
    }

    private suspend fun skipLocal(forward: Boolean) {
        if (!forward) {
            previous()
            return
        }
        val cluster = connect.cluster.value ?: run {
            log.w("skip: no cluster to move through")
            return
        }
        val here = playingUri(cluster)
        if (forward) {
            val queuedUris = queuedTail.map { it.uri } + cluster.queued.map { it.uri }
            val queuedIdx = queuedUris.indexOfFirst { it != here && !bannedTrack(it).also { banned ->
                if (banned) log.i("skip: bypassing banned queued '${it.takeLast(12)}'")
            } }
            if (queuedIdx >= 0) {
                val queuedUri = queuedUris[queuedIdx]
                log.i("skip next to the queued '${queuedUri.takeLast(12)}' of " +
                    "${queuedTail.size + cluster.queued.size} queued"
                )
                queuedTail = queuedTail.drop((queuedIdx + 1).coerceAtMost(queuedTail.size))
                playTrack(queuedUri, chainContext.ifBlank { cluster.contextUri }, -1)
                return
            }
            val upcoming = enhancedUpcoming(chainContext.ifBlank { cluster.contextUri }, here)
            val pick = upcoming.map { it.uri }.firstOrNull { it != here && !bannedTrack(it).also { banned ->
                if (banned) log.i("skip: bypassing banned upcoming '${it.takeLast(12)}'")
            } }
            if (!pick.isNullOrBlank() && pick != here) {
                log.i("skip next from the enhanced queue: '${pick.takeLast(12)}' " +
                    "of ${upcoming.size} upcoming"
                )
                playTrack(pick, chainContext.ifBlank { cluster.contextUri }, -1)
                return
            }
        }
        if (sessionQueueContext == chainContext.ifBlank { cluster.contextUri } && sessionQueue.isNotEmpty()) {
            if (sessionQueue.firstOrNull() == here) sessionQueue = sessionQueue.drop(1)
            var pick: String? = null
            while (sessionQueue.isNotEmpty()) {
                val candidate = sessionQueue.first()
                if (candidate == here) {
                    sessionQueue = sessionQueue.drop(1)
                    continue
                }
                if (bannedTrack(candidate)) {
                    log.i("skip: bypassing banned play-order '${candidate.takeLast(12)}'")
                    sessionQueue = sessionQueue.drop(1)
                    continue
                }
                pick = candidate
                break
            }
            if (pick != null) {
                log.i("skip next from the play order: '${pick.takeLast(12)}' of ${sessionQueue.size} ahead")
                playTrack(pick, chainContext.ifBlank { cluster.contextUri }, -1)
                return
            }
        }
        val chain = chainFor()
        val chainAt = rowOf(chain, here)
        if (chainAt >= 0 && chainAt + 1 < chain.size) {
            var row = chainAt + 1
            while (row < chain.size && bannedTrack(chain[row]).also { banned ->
                if (banned) log.i("skip: bypassing banned chain row $row '${chain[row].takeLast(12)}'")
            }) {
                row++
            }
            if (row < chain.size) {
                log.i("skip next from the chain: row $row of ${chain.size} '${chain[row].takeLast(12)}'")
                resumeRow = row
                playTrack(chain[row], chainContext, row)
                return
            }
        }
        val window = cluster.window()
        val at = window.indexOfFirst { it.uri == here }
        if (at < 0) {
            log.w("skip: '${here.takeLast(12)}' is not in the cluster window")
            return
        }
        val step = if (forward) 1 else -1
        var cursor = at + step
        while (cursor in window.indices && (!window[cursor].isVisible || untitled(window[cursor]) || bannedRow(window[cursor]))) cursor += step
        if (cursor !in window.indices) {
            if (repeatContextOn(cluster)) {
                cursor = if (forward) {
                    window.indexOfFirst { it.isVisible }
                } else {
                    window.indexOfLast { it.isVisible }
                }
            }
            if (cursor !in window.indices) {
                log.w("skip: there is no ${if (forward) "next" else "previous"} row to move to")
                return
            }
        }
        if (forward && shuffleOn(cluster) && !smartOn(cluster)) {
            val pool = window.indices.filter { i ->
                window[i].isVisible && window[i].uri != here && !untitled(window[i]) && !bannedRow(window[i])
            }
            if (pool.isNotEmpty()) cursor = pool.random()
        }
        val row = window[cursor]
        val index = if (row.isQueued) -1 else cluster.indexOf(cursor)
        log.i("skip ${if (forward) "next" else "previous"} here: '${row.title}' idx=$index")
        markPending(row.uri, index, cluster.contextUri)
        if (connect.playback != null && connect.play(cluster.contextUri, row.uri, row.uid, index)) {
            state = state.copy(
                title = row.title,
                artist = row.artistName,
                coverUrl = row.imageUrl,
                trackUri = row.uri,
                artistUri = row.artistUri,
                albumUri = row.albumUri,
            )
            progress.set(0L, progress.durationMs)
            return
        }
        log.i("skip: the server has no say here, starting '${row.title}' on this phone")
        startLocal(row, cluster.contextUri, index, 0L, playing = true)
        clearPending()
    }

    /**
     * The user picked this phone in "Listening on": take the stream back
     * without asking the server for permission - becoming the active member
     * is what transfer means for the device that ends up with the audio.
     */
    private suspend fun takeBackHere() {
        val at = connect.cluster.value
        val track = at?.track
        if (at == null || track == null || track.uri.isBlank()) {
            if (player.hasSource) {
                log.i("back home: nothing to take over, claiming the engine's stream")
                connect.holdsStream = true
                publishLocalFromEngine()
                state = state.copy(
                    controllingRemote = false,
                    activeDeviceName = "Quackify",
                    activeDeviceId = deviceId,
                    connectedDevice = localOutputLabel(),
                    devices = state.devices.map { it.copy(active = it.id == deviceId) }
                )
            } else {
                log.w("back home: nothing to take over yet")
            }
            return
        }
        val old = at.activeDevice?.takeIf { it.id != deviceId }
        if (old != null && at.isPlaying) {
            log.i("back home: pausing '${old.name}' before the move")
            connect.setPausedOn(old.id, playing = true)
            rememberRemoteIntent(false)
        }
        if (player.hasSource && player.state.value.trackUri == track.uri) {
            log.i("back home: the engine already holds '${track.title}', claiming the stream")
            connect.holdsStream = true
            val where = at.positionNow()
            val playing = at.isPlaying
            if (kotlin.math.abs(player.positionMs - where) > RESYNC_MS) player.seekTo(where)
            if (playing && !player.isPlaying) player.resume()
            if (!playing && player.isPlaying) player.pause()
            publishLocalFromEngine(where, playing)
            state = state.copy(
                controllingRemote = false,
                activeDeviceName = "Quackify",
                activeDeviceId = deviceId,
                connectedDevice = localOutputLabel(),
                devices = state.devices.map { it.copy(active = it.id == deviceId) }
            )
            return
        }
        log.i("back home: taking the stream at ${at.positionNow()}ms of '${track.title}' " +
                "paused=${!at.isPlaying}"
        )
        startLocal(track, at.contextUri, at.contextIndex, at.positionNow(), playing = at.isPlaying)
    }

    private suspend fun settleSkip(remoteId: String) {
        val before = state.trackUri
        repeat(SKIP_TRIES) { attempt ->
            connect.refreshCluster()
            val at = connect.cluster.value
            if (at != null && at.track.uri != before && at.activeDevice?.id == remoteId) {
                log.i("skip landed on '${at.track.uri.takeLast(12)}' at attempt ${attempt + 1}")
                hydrateCurrent(at)
                return
            }
            delay(SKIP_RETRY_MS)
        }
        log.w("skip: the cluster still names '${state.trackUri.takeLast(12)}' after ${SKIP_TRIES} reads")
    }

    private suspend fun previous() {
        val loaded = player.positionMs
        if (player.hasSource && loaded > RESTART_MS) {
            log.i("previous: at ${loaded}ms, restarting the track instead")
            player.seekTo(0L)
            return
        }
        val current = state.trackUri
        val rows = chainFor()
        val at = rowOf(rows, current)
        val row = if (at > 0) rows[at - 1] else null
        if (row != null) {
            log.i("previous: row ${at - 1} of ${rows.size} from '${current.takeLast(12)}'")
            resumeRow = at - 1
            playTrack(row, chainContext, at - 1)
            return
        }
        val cluster = connect.cluster.value
        if (cluster == null) {
            log.w("previous: nothing to go back to")
            return
        }
        val window = cluster.window()
        val anchor = cluster.anchorOf(current)
        var back = anchor - 1
        while (back >= 0 && !window[back].isVisible) back--
        val track = window.getOrNull(back)
        if (track == null) {
            log.w("previous: '${current.takeLast(12)}' is at the start of the window")
            return
        }
        log.i("previous: window $back ctxIdx=${cluster.indexOf(back)} '${track.title}'")
        resumeRow = -1
        playTrack(track.uri, cluster.contextUri, cluster.indexOf(back))
    }

    private suspend fun bannedTrack(uri: String): Boolean {
        val artist = artistUriOf(uri)
        return artist.isNotBlank() && ArtistBanStore.banned(context, artist)
    }

    private fun bannedCachedUri(uri: String): Boolean {
        val artist = artistUriOf(uri)
        return artist.isNotBlank() && ArtistBanStore.bannedCached(artist)
    }

    private fun bannedRow(track: ClusterTrack): Boolean {
        val artist = track.artistUri.ifBlank { artistUriOf(track.uri) }
        val banned = artist.isNotBlank() && ArtistBanStore.bannedCached(artist)
        if (banned) log.i("skip: bypassing banned window row '${track.uri.takeLast(12)}'")
        return banned
    }

    private fun bannedAheadTrack(track: ClusterTrack): Boolean {
        val artist = track.artistUri.ifBlank { artistUriOf(track.uri) }
        val banned = artist.isNotBlank() && ArtistBanStore.bannedCached(artist)
        if (banned) log.i("prefetch: skipping banned lookahead '${track.uri.takeLast(12)}'")
        return banned
    }

    private suspend fun playTrack(uri: String, contextUri: String, hint: Int) {
        val banCandidates = buildList {
            if (contextUri.startsWith("spotify:artist:")) add(contextUri)
            artistUriOf(uri).takeIf { it.isNotBlank() }?.let(::add)
        }.distinct()
        val bannedArtist = banCandidates.firstOrNull { ArtistBanStore.banned(context, it) }
        if (bannedArtist != null) {
            log.i("playTrack '$uri' blocked, artist '$bannedArtist' is banned")
            return
        }
        val cluster = connect.cluster.value
        if (cluster != null && contextUri.isNotBlank()) {
            val window = cluster.window()
            val at = window.indexOfFirst { it.uri == uri }
            if (at >= 0 && contextUri == cluster.contextUri) {
                val track = window[at]
                val index = cluster.indexOf(at)
                log.i("playTrack '$uri' from window[$at] ctxIdx=$index")
                playConfirmed(cluster.contextUri, uri, track.uid, index)
                return
            }
        }
        val fallback = contextUri.ifBlank { if (uri.startsWith("spotify:")) uri else "spotify:track:$uri" }
        val enhanced = if (fallback.startsWith("spotify:track:")) enhancedQueue(chainContext) else emptyList()
        val at = enhanced.indexOfFirst { it.uri == uri }
        val context = if (at >= 0 && chainContext.isNotBlank()) chainContext else fallback
        val single = context.startsWith("spotify:track:") || context.startsWith("spotify:episode:")
        val known = if (chainContext == context) chainRows.indexOfFirst { it == uri } else -1
        val index = when {
            single -> -1
            at >= 0 -> at
            known >= 0 -> known
            hint >= 0 -> hint
            else -> 0
        }
        val uid = TrackUid.get(uri)
        log.i("playTrack '$uri' outside the cluster window, '$context' ctxIdx=$index " +
                "chain=$known hint=$hint uid=${uid.take(12).ifBlank { "none" }}"
        )
        playConfirmed(context, uri, uid, index)
    }

    private fun remoteTarget(): String {
        val active = connect.cluster.value?.activeDevice ?: return ""
        return if (active.id != deviceId) active.id else ""
    }

    private suspend fun playConfirmed(context: String, uri: String, uid: String, index: Int) {
        if (uri.startsWith("spotify:track:") || uri.startsWith("spotify:episode:")) {
            player.prefetch(uri, connect.spclientHost)
        }
        sendSmartTap(uri, uid)
        rearmSmart(chainContext.ifBlank { context })
        val enhancedNow = enhancedQueue(context).any { it.uri == uri && it.enhanced }
        if (enhancedNow) {
            val current = connect.cluster.value?.let { playingUri(it) }.orEmpty()
            val upcoming = enhancedUpcoming(chainContext.ifBlank { context }, current)
            val ahead = upcoming.indexOfFirst { it.uri == uri }
            if (ahead > 0) upcoming.take(ahead).forEach { playedSmart.add(it.uri) }
            playedSmart.add(uri)
        }
        if (state.isEnhanced != enhancedNow) state = state.copy(isEnhanced = enhancedNow)
        val remote = remoteTarget()
        if (remote.isNotBlank()) {
            markPending(uri, index, context)
            if (!connect.play(context, uri, uid, index, targetDeviceId = remote)) {
                clearPending()
                log.w("playFromCluster: the server refused '${uri.takeLast(12)}', dropping the wait")
            }
            return
        }
        if (connect.playback == null) {
            startLocalConfirmed(context, uri, index)
            return
        }
        markPending(uri, index, context)
        if (!connect.play(context, uri, uid, index, targetDeviceId = "")) {
            clearPending()
            startLocalConfirmed(context, uri, index)
            return
        }
        val confirmed = awaitTrack(uri, context, CONFIRM_MS)
        if (confirmed && correctRow(context, uri, index)) return
        connect.refreshCluster()
        val live = connect.playback
        if (live != null && live.isAd) {
            log.i("play '$uri': an ad break is in the way, the track follows it")
            return
        }
        val settled = connect.cluster.value ?: run {
            clearPending()
            return
        }
        if (settled.track.uri == uri) {
            log.i("play '$uri': the cluster already names it at ctxIdx=${settled.contextIndex}")
            if (remoteTarget().isNotBlank()) {
                log.i("play '$uri': another device is playing it, this one stays quiet")
                return
            }
            val engine = player.state.value.trackUri
            if (!player.hasSource || engine != uri) {
                log.i("play '$uri': no push came, starting the engine from the cluster")
                player.play(settled.track.asCommandTrack(), connect.spclientHost, 0L)
            }
            return
        }
        val picked = live?.currentTrack?.uri.orEmpty()
        val at = settled.window().indexOfFirst { it.uri == uri }
        if (at < 0) {
            log.w("play '$uri': the cluster still describes '${settled.contextUri.take(32)}', " +
                    "not the context we asked for; retrying in '${context.take(32)}' at idx=$index"
            )
            if (connect.play(context, uri, uid, index, targetDeviceId = remoteTarget())) {
                awaitTrack(uri, context, CONFIRM_MS)
            } else {
                clearPending()
                startLocalConfirmed(context, uri, index)
            }
            return
        }
        log.w("play '$uri': the server picked '$picked' instead, retrying at ctxIdx=${settled.indexOf(at)}")
        markPending(uri, settled.indexOf(at), settled.contextUri)
        connect.play(
            settled.contextUri,
            uri,
            settled.window()[at].uid,
            settled.indexOf(at),
            targetDeviceId = remoteTarget(),
        )
    }

    /** Starts a row on this phone when the server cannot be asked to do it. */
    private suspend fun startLocalConfirmed(context: String, uri: String, index: Int) {
        val cluster = connect.cluster.value
        val row = cluster?.window()?.firstOrNull { it.uri == uri && it.isVisible }
            ?: cluster?.takeIf { it.track.uri == uri }?.track
            ?: clusterTrackFor(uri)
        val resume = resumePositionFor(uri) ?: 0L
        log.i("play '$uri': starting it here at ${resume}ms ctxIdx=$index ctx='${context.takeLast(24)}'")
        startLocal(row, context, index, resume, playing = true)
        clearPending()
    }

    private suspend fun correctRow(context: String, uri: String, index: Int): Boolean {
        val started = connect.playback?.currentTrack ?: return true
        if (started.uri == uri) return true
        if (started.contextUri != context) return true
        if (uri == context) return true
        var learned = TrackUid.get(uri)
        if (learned.isBlank()) {
            connect.refreshCluster()
            learned = TrackUid.get(uri)
        }
        if (learned.isBlank()) {
            log.w("play '$uri': the server started '${started.name}' and the uid of the row we wanted " +
                    "is still unknown, leaving it"
            )
            return true
        }
        log.i("play '$uri': the server started '${started.name}', asking again with the uid it just " +
                "taught us (${learned.take(12)}) at idx=$index"
        )
        markPending(uri, index, context)
        if (!connect.play(context, uri, learned, index, targetDeviceId = remoteTarget())) {
            clearPending()
            return true
        }
        return awaitTrack(uri, context, CONFIRM_MS)
    }

    private suspend fun awaitTrack(uri: String, context: String, millis: Long): Boolean {
        fun settled(): Boolean {
            val track = connect.playback?.currentTrack ?: return false
            if (track.uri == uri) return true
            return context.isNotBlank() && track.contextUri == context
        }
        val until = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < until) {
            if (settled()) return true
            delay(120L)
        }
        return settled()
    }

    private fun flushHeld() {
        val track = held ?: return
        held = null
        log.w("held '${track.name}' never reached the engine, showing it anyway")
        val duration = track.durationMs.takeIf { it > 0L } ?: progress.durationMs
        state = state.copy(
            title = track.name,
            artist = track.artists,
            coverUrl = track.imageUrl,
            trackUri = track.uri,
            isPlaying = false,
            isBuffering = false,
        )
        progress.set(0L, duration)
    }

    private suspend fun settleCluster(uri: String, index: Int) {
        repeat(CLUSTER_TRIES) { attempt ->
            if (attempt > 0) delay(CLUSTER_RETRY_MS)
            if (!connect.refreshCluster()) return
            val at = connect.cluster.value ?: return
            if (at.track.uri != uri) {
                log.w("cluster: the server names '${at.track.title}' instead of '$uri'")
                return
            }
            if (index <= 0 || at.contextIndex != 0) return
            log.w("cluster: idx=${at.contextIndex} but the row asked for $index, trying again")
        }
    }

    private suspend fun seekRemote(positionMs: Long) {
        val cluster = connect.cluster.value ?: return
        val active = cluster.activeDevice ?: return
        if (active.id == deviceId) return
        if (cluster.disallowSeeking) {
            log.w("seek $positionMs ignored, the cluster disallows seeking")
            return
        }
        val duration = cluster.durationMs.takeIf { it > 0L } ?: progress.durationMs
        val target = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs
        log.i("seek $positionMs -> ${target}ms on '${active.name}'")
        progress.set(target, duration)
        if (!connect.seekOn(active.id, target)) {
            log.w("seek: '${active.name}' refused it")
            return
        }
        delay(SKIP_SETTLE_MS)
        connect.refreshCluster()
    }

    private fun pauseRemoteOnExit() {
        val active = connect.cluster.value?.activeDevice ?: return
        if (active.id == deviceId) {
            log.i("leaving, this device holds the stream, nothing to hand over")
            return
        }
        if (!state.isPlaying) {
            log.i("leaving, '${active.name}' is already paused")
            return
        }
        log.i("leaving, pausing '${active.name}'")
        runCatching {
            runBlocking { withTimeoutOrNull(2_000L) { connect.setPausedOn(active.id, playing = true) } }
        }.onFailure { log.w("pause on exit failed ${it.javaClass.simpleName}") }
        state = state.copy(isPlaying = false, isBuffering = false)
    }

    private fun advanceRemote() {
        if (!state.controllingRemote || !state.isPlaying) return
        if (player.hasSource) return
        val duration = progress.durationMs
        if (duration <= 0L) return
        val next = (progress.positionMs + REMOTE_TICK_MS).coerceAtMost(duration)
        if (next == progress.positionMs) return
        progress.set(next, duration)
    }

    /**
     * Settings "Autoplay on startup": the first track the cluster names after
     * the app opens starts playing here right away — exactly what pressing
     * play on the loaded track does. It never steals the stream another
     * device is holding, skips ads, and only waits a short window before
     * giving up so a late cluster never surprises anyone.
     */
    private fun maybeAutoplayStartup(cluster: ClusterState) {
        if (!autoplayPending) return
        autoplayPending = false
        if (!autoplayOnStartup) return
        if (SystemClock.elapsedRealtime() - autoplayArmedAt > AUTOPLAY_WINDOW_MS) {
            log.i("autoplay: the session track came too late, staying quiet")
            return
        }
        if (cluster.track.uri.startsWith(AD_PREFIX)) return
        if (player.hasSource) return
        val remote = remoteTarget()
        if (remote.isNotBlank()) {
            log.i("autoplay: '${remote.take(12)}' owns the stream, not taking it")
            return
        }
        log.i("autoplay: the loaded track is here, starting playback")
        scope.launch { toggleLocal() }
    }

    private fun hydrateCurrent(cluster: ClusterState) {
        val uri = cluster.track.uri
        if (uri.isBlank() || uri in hydrateTried) return
        if (cluster.track.title.isNotBlank() && cluster.track.artistName.isNotBlank()) return
        if (uri.startsWith(AD_PREFIX)) {
            hydrateTried += uri
            log.i("hydrate: an ad is playing, it carries no title")
            state = state.copy(
                title = "Advertisement",
                artist = "",
                trackUri = uri,
                isPlaying = if (player.hasSource) player.isPlaying else cluster.isPlaying,
                isBuffering = false,
            )
            progress.set(cluster.positionMs, cluster.durationMs)
            return
        }
        hydrateTried += uri
        scope.launch {
            val row = runCatching { TrackSource.decorate(context, listOf(uri))[uri] }.getOrNull()
            if (row == null) {
                hydrateTried -= uri
                log.w("hydrate: nothing came back for '${uri.takeLast(12)}', will retry")
                return@launch
            }
            val at = connect.cluster.value ?: return@launch
            if (at.track.uri != uri) return@launch
            log.i("hydrate: '${uri.takeLast(12)}' is '${row.title}' by '${row.artists}'")
            decorated[uri] = TrackItem(
                title = row.title,
                artist = row.artists,
                uri = uri,
                imageUrl = row.imageUrl.takeIf { it.isNotBlank() },
                durationMs = row.durationMs,
                isExplicit = row.explicit,
                albumUri = row.albumUri,
                artistUri = row.artistUri,
                hasVideo = row.video,
            )
            state = state.copy(
                title = row.title,
                artist = row.artists,
                coverUrl = row.imageUrl.orEmpty(),
                trackUri = uri,
                artistUri = row.artistUri.ifBlank { state.artistUri.takeIf { state.trackUri == uri }.orEmpty() },
            )
        }
    }

    private fun artistUriOf(uri: String): String = artistUriOf(uri, uriSources())

    private fun albumUriOf(uri: String): String = albumUriOf(uri, uriSources())

    private fun uriSources(): UriSources = UriSources(
        cluster = cluster,
        decorated = decorated,
        queue = state.queue,
        queueFromContext = state.queueFromContext,
    )

    private fun mirror(cluster: ClusterState?) {
        if (cluster == null) return
        watchForIncomingTransfer(cluster)
        if (cluster.track.uri.isBlank()) {
            blankSeen = ""
            this.cluster = cluster
            publishDevices(cluster)
            publishQueue(cluster)
            return
        }
        maybeAutoplayStartup(cluster)
        restoreChain(cluster)
        if (cluster.track.title.isBlank() && cluster.track.artistName.isBlank()) {
            if (blankSeen != BLANK) {
                blankSeen = BLANK
                log.i("the cluster names '${cluster.track.uri.takeLast(12)}' with no metadata, " +
                        "keeping the track block as it is")
            }
            hydrateCurrent(cluster)
            this.cluster = cluster
            publishDevices(cluster)
            publishQueue(cluster)
            val remoteNow = cluster.activeDevice?.let { it.id != deviceId } == true
            if (!remoteNow && player.hasSource) {
                state = state.copy(isPlaying = player.isPlaying, isBuffering = false)
            }
            if (remoteNow) {
                val duration = cluster.durationMs.takeIf { it > 0L } ?: progress.durationMs
                val changed = cluster.track.uri != state.trackUri
                val intent = remoteIntent()
                val effPlaying = intent ?: cluster.isPlaying
                val live = when {
                    intent != null && intent != cluster.isPlaying -> progress.positionMs
                    intent == false -> cluster.positionMs
                    else -> cluster.positionNow()
                }
                val resync = changed || state.isPlaying != effPlaying ||
                        kotlin.math.abs(live - progress.positionMs) > RESYNC_MS
                val position = if (resync) live else progress.positionMs
                state = state.copy(
                    title = if (changed) "" else state.title,
                    artist = if (changed) "" else state.artist,
                    coverUrl = if (changed) "" else state.coverUrl,
                    trackUri = cluster.track.uri,
                    isPlaying = effPlaying,
                )
                progress.set(position, duration)
            }
            return
        }
        blankSeen = ""
        this.cluster = cluster
        publishDevices(cluster)
        hydrateCurrent(cluster)
        TrackUid.learn(cluster)
        if (cluster.activeDevice?.id != deviceId) {
            log.v("prefetch skipped, another device owns the stream")
        } else {
            player.prefetchAhead(
                cluster.nextTracks.filter { !bannedAheadTrack(it) }.take(KEY_LOOKAHEAD),
                connect.spclientHost,
                cluster.track.uri
            )
        }
        if (cluster.activeDeviceId.isNotBlank() && cluster.activeDeviceId != activeSeen) {
            activeSeen = cluster.activeDeviceId
            log.i("the account's active device is " +
                    if (cluster.activeDeviceId == deviceId) "this one" else cluster.activeDeviceId.take(12)
            )
        }
        if (chainRows.isEmpty() && chainContext.isBlank() &&
            cluster.contextUri.isNotBlank() && cluster.nextTracks.isNotEmpty()
        ) {
            chainRows = cluster.window().filter { it.isVisible }.map { it.uri }
            chainContext = cluster.contextUri
            rememberChain(chainContext)
            log.i("chain from the cluster: ${chainRows.size} rows in " +
                    "'${chainContext.take(32)}' at '${cluster.track.uri.takeLast(12)}' " +
                    "queued=${cluster.queued.size}"
            )
        }
        val loaded = player.state.value.trackUri
        val stale = player.hasSource && loaded.isNotBlank() && cluster.track.uri != loaded
        val signature = "${cluster.track.uri}|${cluster.isPlaying}|${cluster.contextIndex}|" +
                "${cluster.nextTracks.size}|${cluster.prevTracks.size}|${cluster.contextUri}|" +
                "${cluster.track.title}|$stale"
        val moved = signature != clusterSeen
        if (moved) {
            clusterSeen = signature
            if (stale) {
                log.w("queue kept: the cluster names '${cluster.track.uri.takeLast(12)}' at " +
                        "idx=${cluster.contextIndex}, the engine has '${loaded.takeLast(12)}'"
                )
            }
            log.i("cluster ${cluster.updateReason} '${cluster.track.title}' by " +
                    "'${cluster.track.artistName}' playing=${cluster.isPlaying} " +
                    "pos=${cluster.positionMs}/${cluster.durationMs} " +
                    "ctxIdx=${cluster.contextIndex} next=${cluster.nextTracks.size} " +
                    "prev=${cluster.prevTracks.size} uid=${cluster.track.uid.take(12)}"
            )
        }
        publishQueue(cluster)
        publishModes()
        if (moved) {
            if (sessionQueue.firstOrNull() == cluster.track.uri) sessionQueue = sessionQueue.drop(1)
            if (cluster.contextUri != shuffledContext && shuffleOn(cluster) && !smartOn(cluster)) {
                scope.launch { applyShuffleOrder(true, cluster.contextUri) }
            }
        }
        val clusterArtist = cluster.track.artistUri
        if (cluster.track.uri == state.trackUri && clusterArtist.isNotBlank() && state.artistUri != clusterArtist) {
            state = state.copy(artistUri = clusterArtist)
        }
        val remoteActive = cluster.activeDevice?.let { it.id != deviceId } == true
        val nothingActive = cluster.activeDeviceId.isBlank() && cluster.activeDevice == null &&
                !player.hasSource
        val following = remoteActive || nothingActive
        if (connect.playback != null && !following) return
        if (remoteActive) hydrateCurrent(cluster)
        if (!following && player.hasSource) return
        val local = player.hasSource && !following
        val intent = remoteIntent()
        val fresh = cluster.stateAtMs > 0L &&
                System.currentTimeMillis() - cluster.stateAtMs <= LIVE_STATE_MS
        val followPlaying = if (nothingActive || !player.hasSource) {
            cluster.isPlaying && fresh
        } else {
            cluster.isPlaying
        }
        val position = when {
            local -> progress.positionMs
            intent != null && intent != cluster.isPlaying -> progress.positionMs
            followPlaying -> cluster.positionNow()
            else -> cluster.positionMs
        }
        val duration = cluster.durationMs.takeIf { it > 0L } ?: cluster.track.durationMs
        val sameTrack = cluster.track.uri == state.trackUri
        state = state.copy(
            title = cluster.track.title.ifBlank { if (sameTrack) state.title else "" },
            artist = cluster.track.artistName.ifBlank { if (sameTrack) state.artist else "" },
            coverUrl = cluster.track.imageUrl.ifBlank { if (sameTrack) state.coverUrl else "" },
            trackUri = cluster.track.uri,
            artistUri = cluster.track.artistUri
                .ifBlank { if (sameTrack) state.artistUri else "" }
                .ifBlank { artistUriOf(cluster.track.uri) },
            isPlaying = if (local) player.isPlaying else (intent ?: followPlaying),
            isBuffering = if (local) player.state.value.buffering else false,
            explicit = if (sameTrack) state.explicit else cluster.track.explicit,
            contextUri = cluster.contextUri,
            contextLabel = contextLabelOf(cluster),
        )
        progress.set(position, duration)
        watchForVanishedActive(cluster)
        refreshVideoFlags()
    }

    private fun contextLabelOf(cluster: ClusterState): String {
        if (cluster.fromContext.isNotEmpty() && cluster.fromContext.all { it.isAutoplay }) return ""
        return cluster.contextDescription.trim()
    }

    /** Consecutive cluster reads that no longer name the device that held the stream. */
    @Volatile
    private var goneReads = 0

    /** The context+track the last takeover was fired for, to avoid repeats. */
    @Volatile
    private var takeOverTried = ""

    /**
     * Watches for the device that held the stream dropping out of the
     * cluster: once it is gone from the device list and nothing became
     * active in its place, this phone brings the session home instead of
     * leaving "Listening on" without a current device. A single blank read
     * is not enough - the server briefly unsets the active device while a
     * remote re-publishes its state - so it takes two in a row, and the
     * device has to really be missing, not just idle.
     */
    private fun watchForVanishedActive(cluster: ClusterState) {
        val holder = activeSeen
        val gone = holder.isNotBlank() &&
                cluster.activeDeviceId.isBlank() &&
                cluster.devices.isNotEmpty() &&
                cluster.devices.none { it.id == holder }
        if (!gone || player.hasSource || pendingActive()) {
            goneReads = 0
            return
        }
        val track = cluster.track
        if (track.uri.isBlank() || track.isAd) {
            goneReads = 0
            return
        }
        goneReads++
        if (goneReads < GONE_READS) return
        goneReads = 0
        val stamp = "${cluster.contextUri}|${track.uid.ifBlank { track.uri }}"
        if (stamp == takeOverTried) return
        takeOverTried = stamp
        scope.launch { takeOverFromGoneDevice(cluster) }
    }

    private suspend fun takeOverFromGoneDevice(cluster: ClusterState) {
        val track = cluster.track
        val at = cluster.positionNow()
        log.i("the device that held the stream left the cluster, " +
                "taking it back here at ${at}ms of '${track.title}' " +
                "paused=${!cluster.isPlaying}"
        )
        resumeUri = ""
        resumeAt = 0L
        startLocal(track, cluster.contextUri, cluster.contextIndex, at, playing = cluster.isPlaying)
    }

    /**
     * Another client moved the account's stream onto this phone from its own
     * device picker: the cluster flips the active device to this one long
     * before any command lands here. If nothing picks the stream up the phone
     * sits silent while the server waits for it, and once it gives up the
     * phone vanishes from every "Listening on" list. Watch for that flip
     * while nothing is loaded here and take the stream over.
     */
    private fun watchForIncomingTransfer(cluster: ClusterState) {
        if (cluster.activeDeviceId != deviceId) return
        if (activeSeen.isBlank() || activeSeen == deviceId) return
        if (player.hasSource || pendingActive()) return
        val track = cluster.track
        if (track.uri.isBlank() || track.isAd) return
        val age = System.currentTimeMillis() - cluster.stateAtMs
        if (cluster.stateAtMs <= 0L || age > TRANSFER_FRESH_MS) return
        val stamp = "${cluster.contextUri}|${track.uid.ifBlank { track.uri }}"
        if (stamp == takeOverTried) return
        takeOverTried = stamp
        log.i("the stream was moved here, taking it at ${cluster.positionNow()}ms " +
                "of '${track.title}' paused=${!cluster.isPlaying}"
        )
        scope.launch { takeBackHere() }
    }

    private fun publishDevices(cluster: ClusterState) {
        val active = cluster.activeDevice
        val localPlaybackActive = connect.holdsStream || (player.hasSource && !state.controllingRemote)
        val mine = (active != null && active.id == deviceId) || localPlaybackActive
        val label = if (localPlaybackActive) "Quackify" else active?.name.orEmpty()
        val effectiveActiveId = if (localPlaybackActive) deviceId else active?.id.orEmpty()
        val remote = cluster.remote.map { it.name }
        val rawDevices = withLocalDevice(cluster)
        val devices = if (localPlaybackActive) {
            rawDevices.map { it.copy(active = it.id == deviceId) }
        } else {
            rawDevices
        }
        if (state.devices != devices || state.activeDeviceName != label ||
            state.controllingRemote != !mine
        ) {
            log.i("devices=${devices.size} active='$label' ours=$mine " +
                    "injected=${cluster.devices.none { it.id == deviceId }} " +
                    "inferred=${cluster.activeInferred} remote=${remote.joinToString("|")}"
            )
        }
        val noneActive = active == null && !player.hasSource && !localPlaybackActive
        val connectedLabel = when {
            localPlaybackActive -> localOutputLabel()
            label.isBlank() -> if (mine || player.hasSource || noneActive) localOutputLabel() else ""
            else -> label
        }
        val remoteActive = (!mine || noneActive) && !player.hasSource && !localPlaybackActive
        state = state.copy(
            devices = devices,
            activeDeviceName = label,
            activeDeviceId = effectiveActiveId,
            controllingRemote = remoteActive,
            remoteCanSeek = !remoteActive || !cluster.disallowSeeking,
            remoteCanSkipPrev = !remoteActive || cluster.canSkipPrev,
            remoteCanShuffle = !remoteActive || cluster.canShuffle,
            remoteCanRepeat = !remoteActive || (cluster.canRepeatContext || cluster.canRepeatTrack),
            connectedDevice = connectedLabel,
        )
        if (active != null && !mine && player.hasSource && held == null) {
            log.i("the stream moved to '$label', releasing this device's engine")
            player.stop()
        }
    }

    /**
     * The account's device list only names devices the server knows about, so
     * while the player registration has not landed (or failed) this phone is
     * invisible in "Listening on". Synthesize the local row so it can always
     * be picked; the server row replaces it once the registration answers.
     */
    private fun withLocalDevice(cluster: ClusterState): List<ConnectDevice> {
        val fromServer = cluster.devices
        val isMineActive = cluster.activeDeviceId == deviceId ||
                connect.holdsStream ||
                (player.hasSource && !state.controllingRemote)
        if (fromServer.any { it.id == deviceId }) {
            return fromServer.map { dev ->
                if (dev.id == deviceId) {
                    dev.copy(kind = localOutputKind(), local = true, active = isMineActive)
                } else {
                    if (isMineActive) dev.copy(active = false) else dev
                }
            }
        }
        return fromServer + localDeviceRow(active = isMineActive)
    }

    private fun localOutputLabel(): String = LocalOutput.label(context)

    private fun localOutputKind(): DeviceKind =
        if (localOutputLabel().isBlank()) DeviceKind.PHONE else DeviceKind.HEADPHONES

    private val localOutputWatcher = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refreshLocalOutput()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refreshLocalOutput()
        }
    }

    private fun refreshLocalOutput() {
        if (state.controllingRemote) return
        val wasExternal = state.connectedDevice.isNotBlank()
        val label = localOutputLabel()
        val kind = localOutputKind()
        if (state.connectedDevice == label &&
            state.devices.firstOrNull { it.local }?.kind == kind
        ) return
        state = state.copy(
            connectedDevice = label,
            devices = state.devices.map {
                if (it.local) it.copy(kind = kind) else it
            }
        )
        if (wasExternal && label.isBlank()) scope.launch { pauseOnOutputLost() }
    }

    private suspend fun pauseOnOutputLost() {
        if (!player.hasSource || !player.isPlaying) return
        log.i("output lost: the bluetooth or wired device is gone, pausing locally")
        player.pause()
        state = state.copy(isPlaying = false, isBuffering = false)
        reportPaused()
    }

    private fun localDeviceRow(active: Boolean): ConnectDevice = ConnectDevice(
        id = deviceId,
        name = "Quackify",
        kind = localOutputKind(),
        canPlay = true,
        volume = 0,
        volumeSteps = 64,
        isControllable = true,
        disableVolume = false,
        supportsTransfer = true,
        supportsLogout = false,
        supportsCommandRequest = false,
        supportsSleepTimer = false,
        isObservable = true,
        audioQuality = "",
        brand = "spotify",
        model = "web_player",
        softwareVersion = "",
        license = "",
        active = active,
        local = true,
    )

    private fun publishQueue(cluster: ClusterState?) {
        val loaded = player.state.value.trackUri
        val anchor = (if (pendingActive()) pendingTrack else "")
            .ifBlank { loaded }
            .ifBlank { cluster?.track?.uri.orEmpty() }
        if (anchor.isNotBlank() && queuedTail.any { it.uri == anchor }) {
            queuedTail = queuedTail.filterNot { it.uri == anchor }
        }
        val server = cluster != null
        val live = connect.playback?.currentTrack?.contextUri.orEmpty()
        val clusterContext = cluster?.contextUri.orEmpty()
        val chain = chainFor()
        val context = live.takeIf { it.isNotBlank() && !it.startsWith("spotify:track:") }
            ?: chainContext.takeIf { it.isNotBlank() && chain.isNotEmpty() }
            ?: clusterContext.takeIf { it.isNotBlank() && !it.startsWith("spotify:track:") }
                .orEmpty()
        val single = clusterContext.startsWith("spotify:track:") ||
                clusterContext.startsWith("spotify:episode:")
        val follows = clusterContext.isNotBlank() &&
                (clusterContext == context || (single && context.isBlank()))
        val smartActive = smartOn(cluster)
        val serverTracks = if (server && follows) {
            if (smartActive) cluster!!.fromContext else cluster!!.fromContext
                .filterNot { it.isEnhanced }
                .filterNot { bannedCachedUri(it.uri) }
        } else emptyList()
        val serverRows = serverTracks.map { it.asTrackItem() }
        val localRows = sessionQueue
            .takeIf { sessionQueueContext == context && it.isNotEmpty() }
            ?.map { uri -> decorated[uri] ?: TrackItem(title = "", artist = "", uri = uri) }
            ?.filterNot { bannedCachedUri(it.uri) }
            .orEmpty()
        val contextRows = localRows.ifEmpty { serverRows }
        val radio = server && follows &&
                cluster!!.fromContext.let { it.isNotEmpty() && it.all { row -> row.isAutoplay } }
        val label = if (server && follows && !radio && !single) {
            cluster!!.contextDescription.ifBlank {
                state.queueContextLabel.takeIf { state.queueContext == context }.orEmpty()
            }.ifBlank { playlistLabelOf(context) }
        } else ""
        val queued = if (server) {
            val onServer = cluster!!.queued.map { it.asTrackItem() }.filterNot { bannedCachedUri(it.uri) }
            val known = onServer.mapTo(HashSet()) { it.uri }
            (onServer + queuedTail.filterNot { it.uri in known })
                .filterNot { bannedCachedUri(it.uri) }
                .map { if (it.title.isBlank()) decorated[it.uri] ?: it else it }
        } else {
            queuedTail.filterNot { bannedCachedUri(it.uri) }
        }
        val queuedUris = buildSet {
            if (server) cluster!!.queued.forEach { add(it.uri) }
            queued.forEach { add(it.uri) }
        }
        val chainFits = chainContext.isNotBlank() && (context.isBlank() || chainContext == context)
        val chainTail = if (anchor.isNotBlank() && chain.isNotEmpty() && chainFits) {
            val at = chain.indexOf(anchor)
            val from = when {
                at >= 0 -> at + 1
                resumeRow in chain.indices -> resumeRow
                else -> -1
            }
            if (from >= 0) {
                chain.drop(from).filterNot { it in queuedUris }.filterNot { bannedCachedUri(it) }.take(20)
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
        val knownUpcoming = contextRows.mapTo(HashSet()) { it.uri }
        val enhanced = if (chainFits && smartActive) enhancedQueue(context) else emptyList()
        val enhancedTail = if (enhanced.isNotEmpty()) {
            enhancedUpcoming(context, anchor)
                .asSequence()
                .filterNot { it.uri in queuedUris }
                .filterNot { bannedCachedUri(it.uri) }
                .map { it.uri }
                .distinct()
                .take(20)
                .toList()
        } else {
            emptyList()
        }
        val tailUris = if (enhancedTail.isNotEmpty()) enhancedTail else if (contextRows.isEmpty()) chainTail else emptyList()
        val sparse = ((if (enhancedTail.isNotEmpty()) emptyList() else contextRows) + queued)
            .filter { it.title.isBlank() }.map { it.uri }
        val missing = (sparse + tailUris.filterNot { it in knownUpcoming })
            .filterNot { decorated.containsKey(it) || it in undecoratable }
            .distinct()
        if (missing.isNotEmpty()) scheduleDecorate(missing)
        val tailRowsFilled = tailUris.map { uri ->
            (decorated[uri] ?: TrackItem(title = "", artist = "", uri = uri))
                .copy(isEnhanced = enhanced.firstOrNull { it.uri == uri }?.enhanced == true)
        }
        val upcoming = if (enhancedTail.isNotEmpty()) {
            tailRowsFilled
        } else {
            (contextRows.map { if (it.title.isBlank()) decorated[it.uri] ?: it else it } +
                tailRowsFilled)
                .filterNot { it.uri == anchor }
        }
        if (queued == state.queue && upcoming == state.queueFromContext &&
            context == state.queueContext && label == state.queueContextLabel
        ) {
            return
        }
        state = state.copy(
            queue = queued,
            queueFromContext = upcoming,
            queueContext = context,
            queueContextLabel = label,
        )
        log.i("queue ${queued.size} queued + ${upcoming.size} next from " +
                "'${label.ifBlank { context.ifBlank { "?" } }}' " +
                "${if (server) "the server" else "the list on screen"}"
        )
    }

    private fun playlistLabelOf(contextUri: String): String {
        if (contextUri == "spotify:collection:tracks" || contextUri == "spotify:user:@me:collection") {
            return "Liked Songs"
        }
        if (!contextUri.startsWith("spotify:playlist:")) return ""
        playlistNames[contextUri]?.let { return it }
        if (playlistNameTried.add(contextUri)) {
            scope.launch {
                val name = runCatching {
                    Krespot.collections.collection(context, contextUri)?.title
                }.getOrNull()?.trim().orEmpty()
                if (name.isBlank()) return@launch
                playlistNames[contextUri] = name
                log.i("context '$name' resolved for '${contextUri.takeLast(24)}'")
                publishQueue(connect.cluster.value)
                if (connect.holdsStream && player.hasSource) publishLocalFromEngine()
            }
        }
        return ""
    }

    private fun scheduleDecorate(uris: List<String>) {
        if (uris.isEmpty()) return
        if (uris == decoratePending && decorateJob?.isActive == true) return
        decoratePending = uris
        decorateJob?.cancel()
        decorateJob = scope.launch {
            val rows = runCatching { TrackSource.decorate(context, uris) }.getOrDefault(emptyMap())
            decoratePending = emptyList()
            if (!isActive) return@launch
            uris.forEach { uri -> if (rows[uri] == null) undecoratable += uri }
            rows.forEach { (uri, track) ->
                decorated[uri] = TrackItem(
                    title = track.title,
                    artist = track.artists,
                    uri = track.uri,
                    imageUrl = track.imageUrl.takeIf { it.isNotBlank() },
                    durationMs = track.durationMs,
                    isExplicit = track.explicit,
                    albumUri = track.albumUri,
                    artistUri = track.artistUri,
                    hasVideo = track.video,
                )
            }
            log.i("decorated ${rows.size} of ${uris.size} upcoming row(s)")
            publishQueue(connect.cluster.value)
        }
    }

    /** Lens-added rows reach the cluster without metadata; never play those blind. */
    private fun untitled(row: ClusterTrack): Boolean =
        row.title.isBlank() && decorated[row.uri] == null

    private fun screenContext(uri: String): String {
        val cluster = connect.cluster.value
        val known = connect.playback?.currentTrack?.contextUri.orEmpty()
        if (known.isNotBlank() && !known.startsWith("spotify:track:") && !known.startsWith("spotify:episode:")) {
            val inWindow = cluster?.takeIf { it.contextUri == known }?.window()?.any { it.uri == uri } ?: false
            val inChain = chainContext == known && chainRows.any { it == uri }
            if (inWindow || inChain) return known
            log.i("screen: '${uri.takeLast(12)}' is not part of the live context " +
                "'${known.takeLast(16)}', playing it on its own"
            )
        }
        if (screen.size >= 2 && screen.any { it == uri } && uri in chainRows) {
            return chainContext.takeIf { it.isNotBlank() && it != uri }.orEmpty()
        }
        return ""
    }

    /**
     * The context this phone last played is the only clue a cold start has:
     * the cluster comes back with the track, the position and the context uri
     * but with an empty next_tracks list, so the queue and both skips have
     * nothing to walk through. Persisting the context lets the next launch
     * fetch its row list from the API instead of showing an empty queue.
     */
    private fun rememberChain(contextUri: String) {
        if (!usableContext(contextUri)) return
        val rows = chainRows
        if (contextUri == rememberedChain && rows == rememberedRows) return
        rememberedChain = contextUri
        rememberedRows = rows
        com.quackify.backend.core.store.AppPrefs.prefs(context, CLIENT_PREFS)
            .edit()
            .putString(CHAIN_CONTEXT_PREF, contextUri)
            .putString(CHAIN_ROWS_PREF, rows.joinToString("\n"))
            .apply()
    }

    private fun restoredRows(): List<String> = com.quackify.backend.core.store.AppPrefs
        .prefs(context, CLIENT_PREFS)
        .getString(CHAIN_ROWS_PREF, "").orEmpty()
        .split("\n")
        .filter { it.startsWith("spotify:") }

    private fun restoreChain(cluster: ClusterState) {
        if (chainRows.isNotEmpty() || chainContext.isNotBlank()) return
        if (restoreJob?.isActive == true) return
        val fromCluster = cluster.contextUri.takeIf { usableContext(it) }
        val contextUri = fromCluster
            ?: rememberedChain.takeIf { usableContext(it) }
            ?: com.quackify.backend.core.store.AppPrefs.prefs(context, CLIENT_PREFS)
                .getString(CHAIN_CONTEXT_PREF, "").orEmpty().takeIf { usableContext(it) }
            ?: return
        if (cluster.nextTracks.isNotEmpty()) return
        val anchor = cluster.track.uri
        if (anchor.isBlank()) return
        if (anchor in restoreTried) return
        restoreTried += anchor
        val keptFor = com.quackify.backend.core.store.AppPrefs.prefs(context, CLIENT_PREFS)
            .getString(CHAIN_CONTEXT_PREF, "").orEmpty()
        val remembered = if (keptFor == contextUri) restoredRows() else emptyList()
        if (remembered.isNotEmpty() && anchor in remembered) {
            chainRows = remembered
            chainContext = contextUri
            val at = remembered.indexOf(anchor)
            resumeRow = if (at >= 0) at else -1
            log.i("restore: ${remembered.size} rows kept from the last session in " +
                    "'${contextUri.take(32)}' row=$at"
            )
            publishQueue(connect.cluster.value)
            return
        }
        if (remembered.isNotEmpty()) {
            log.i("restore: '${contextUri.take(32)}' does not hold '${anchor.takeLast(12)}', " +
                    "the track is its own context"
            )
            return
        }
        restoreJob = scope.launch {
            val rows = runCatching { rowsOf(contextUri) }.getOrNull().orEmpty()
            if (rows.isEmpty()) {
                log.w("restore: '${contextUri.take(32)}' gave no rows back")
                return@launch
            }
            if (chainRows.isNotEmpty() || chainContext.isNotBlank()) return@launch
            if (anchor !in rows) {
                log.i("restore: '${contextUri.take(32)}' does not hold '${anchor.takeLast(12)}', " +
                        "the track is its own context"
                )
                return@launch
            }
            chainRows = rows
            chainContext = contextUri
            rememberChain(contextUri)
            val at = rows.indexOf(anchor)
            resumeRow = if (at >= 0) at else -1
            log.i("restore: ${rows.size} rows in '${contextUri.take(32)}' at " +
                    "'${anchor.takeLast(12)}' row=$at"
            )
            publishQueue(connect.cluster.value)
        }
    }

    private fun usableContext(uri: String): Boolean =
        uri.isNotBlank() &&
            !uri.startsWith("spotify:track:") &&
            !uri.startsWith("spotify:episode:") &&
            !uri.startsWith("spotify:ad:")

    private suspend fun rowsOf(contextUri: String): List<String> = when {
        contextUri.startsWith("spotify:collection:") ||
            contextUri.startsWith("spotify:user:") -> {
            val detail = Krespot.collections.collection(context, contextUri) ?: return emptyList()
            detail.tracks.map { it.uri }
        }
        contextUri.startsWith("spotify:album:") -> {
            val detail = Krespot.albums.album(context, contextUri) ?: return emptyList()
            detail.tracks.map { it.uri }
        }
        contextUri.startsWith("spotify:artist:") -> {
            val detail = Krespot.artists.artist(context, contextUri) ?: return emptyList()
            detail.topTracks.map { it.uri }
        }
        contextUri.startsWith("spotify:playlist:") -> {
            val detail = Krespot.collections.collection(context, contextUri) ?: return emptyList()
            detail.tracks.map { it.uri }
        }
        contextUri.startsWith("spotify:show:") -> {
            val detail = Podcasts.show(context, contextUri) ?: return emptyList()
            detail.episodes.map { it.uri }
        }
        else -> emptyList()
    }

    private fun captureChain(contextUri: String) {
        if (contextUri.isBlank()) {
            if (chainRows.isNotEmpty() || chainContext.isNotBlank()) {
                chainRows = emptyList()
                chainContext = ""
                resumeRow = -1
                log.i("chain: reset, this play carries no context")
            }
            return
        }
        if (contextUri.startsWith("spotify:track:") || contextUri.startsWith("spotify:episode:")) {
            log.d("chain: a single track carries no chain, keeping '${chainContext.takeLast(16)}'")
            return
        }
        if (contextUri != chainContext) {
            sessionQueue = emptyList()
            sessionQueueContext = ""
            shuffledContext = ""
        }
        val rows = screen
        if (rows.size < 2) {
            if (chainRows.isNotEmpty() || chainContext != contextUri) {
                chainRows = emptyList()
                chainContext = contextUri
                rememberChain(contextUri)
                resumeRow = -1
                log.i("chain: '${contextUri.takeLast(16)}' holds ${rows.size} row(s), " +
                    "the old chain does not follow"
                )
            }
            if (chainRows.isEmpty()) {
                scope.launch {
                    val fetched = runCatching { rowsOf(contextUri) }.getOrNull().orEmpty()
                    if (fetched.size < 2 || chainContext != contextUri || chainRows.isNotEmpty()) return@launch
                    chainRows = fetched
                    rememberChain(contextUri)
                    log.i("chain: ${fetched.size} rows fetched for '${contextUri.takeLast(16)}'")
                    publishQueue(connect.cluster.value)
                }
            }
            return
        }
        chainRows = rows
        chainContext = contextUri
        rememberChain(contextUri)
        resumeRow = -1
        log.i("chain: ${rows.size} rows in '${chainContext.ifBlank { "one track per row" }}' " +
                "from '${rows.first().takeLast(12)}'")
    }

    private fun mirrorState(command: PlaybackCommand?) {
        if (command == null) return
        if (command.isControl) return
        learnMachineQueue(command)
        val track = command.currentTrack ?: return
        val loaded = player.state.value.trackUri
        val bothAds = loaded.startsWith(AD_PREFIX) && track.uri.startsWith(AD_PREFIX)
        if (!bothAds && player.hasSource && loaded.isNotBlank() && loaded != track.uri) {
            if (held?.uri != track.uri) {
                log.i("hold '${track.name}' until the engine leaves '${loaded.takeLast(12)}'")
            }
            held = track
            return
        }
        held = null
        val local = player.hasSource
        val position = if (local) {
            player.positionMs
        } else {
            cluster?.takeIf { it.track.uri == track.uri }?.positionMs
                ?: command.currentState?.initialPositionMs
                ?: progress.positionMs
        }
        val duration = track.durationMs.takeIf { it > 0L } ?: progress.durationMs
        val nextArtistUri = if (track.uri != state.trackUri) artistUriOf(track.uri) else state.artistUri
        val nextAlbumUri = if (track.uri != state.trackUri) albumUriOf(track.uri) else state.albumUri
        state = state.copy(
            title = track.name,
            artist = track.artists,
            coverUrl = track.imageUrl,
            trackUri = track.uri,
            artistUri = nextArtistUri,
            albumUri = nextAlbumUri,
            isPlaying = if (local) player.isPlaying else !command.paused,
            isBuffering = if (local) player.state.value.buffering else false,
            explicit = track.explicit,
            contextUri = track.contextUri.ifBlank { state.contextUri },
            contextLabel = command.contextDescription.trim(),
        )
        progress.set(position, duration)
    }

    /**
     * The state machine response carries the full context queue. Shows have
     * machine's episode list becomes the chain and decorates the queue.
     */
    private fun learnMachineQueue(command: PlaybackCommand) {
        if (command.tracks.size < 2) return
        if (machineLearned == command.stateMachineId) return
        val track = command.currentTrack ?: return
        val ctx = track.contextUri
        if (!ctx.startsWith("spotify:show:")) return
        machineLearned = command.stateMachineId
        val at = command.currentState?.trackIndex ?: -1
        command.tracks.forEach { row ->
            if (row.uri.isBlank()) return@forEach
            if (decorated[row.uri]?.title.isNullOrBlank()) {
                decorated[row.uri] = TrackItem(
                    title = row.name,
                    artist = row.artists,
                    uri = row.uri,
                    imageUrl = row.imageUrl.takeIf { it.isNotBlank() },
                    durationMs = row.durationMs,
                    isExplicit = row.explicit,
                    albumUri = row.albumUri,
                    artistUri = if (row.uri.startsWith("spotify:episode:")) row.albumUri else "",
                )
            }
        }
        if (chainContext != ctx || chainRows.size < command.tracks.size) {
            chainContext = ctx
            chainRows = command.tracks.map { it.uri }
            if (at in chainRows.indices) resumeRow = at
            log.i("machine queue: ${chainRows.size} rows in '$ctx' at row $at")
            publishQueue(connect.cluster.value)
        }
    }

    private fun smokeReport(command: PlaybackCommand) {
        if (command.isControl) {
            applyControl(command)
            return
        }
        if (command.isReplaceState) {
            applyReplaceState(command)
            return
        }
        val track = command.currentTrack ?: return
        TrackUid.learn(command)
        log.i("push sm=${command.stateMachineId.take(10)} idx=${command.stateIndex} " +
                "state=${command.stateId.take(8)} tracks=${command.tracks.size} states=${command.states.size} " +
                "'${track.name}' ad=${command.isAd}"
        )
        if (remoteTarget().isNotBlank()) {
            log.i("push belongs to the remote device, this one stays quiet")
            return
        }
        val loaded = player.state.value.trackUri
        val bothAds = loaded.startsWith(AD_PREFIX) && track.uri.startsWith(AD_PREFIX)
        if (!command.isAd && !bothAds && !pendingActive() && player.hasSource && loaded != track.uri) {
            log.w("push ignored: the engine owns '${loaded.takeLast(12)}', the server names " +
                    "'${track.uri.takeLast(12)}' at idx=${command.stateIndex} " +
                    "sm=${command.stateMachineId.take(10)}"
            )
            return
        }
        if (!track.isDirect) player.prefetch(track.uri, connect.spclientHost)
        scope.launch {
            val snapshot = Snapshot.of(command, DebugSource.BEFORE_TRACK_LOAD, 0L, 0L, speed = 0.0)
            if (snapshot == null) {
                log.w("report: no snapshot")
                flushHeld()
                return@launch
            }
            val accepted = connect.report(snapshot)
            log.i("report before_track_load state=${command.stateId.take(8)} accepted=$accepted " +
                    "next=${connect.playback?.stateId?.take(8)}"
            )
            if (accepted && pendingActive() && track.uri == pendingTrack) {
                val index = pendingIndex
                clearPending()
                scope.launch { settleCluster(track.uri, index) }
            }
            val resume = resumePositionFor(track.uri)
                ?: command.currentState?.initialPositionMs?.takeIf { it > 0L }
                ?: cluster?.takeIf { it.track.uri == track.uri }?.positionMs?.takeIf { it > 0L }
                ?: 0L
            log.i("audio '${track.name}' from=${resume}ms direct=${track.isDirect} files=${track.files.size}"
            )
            val loaded = player.state.value.trackUri
            if (player.hasSource && loaded == track.uri) {
                log.i("audio already on '$loaded', not restarting")
                return@launch
            }
            player.play(track, connect.spclientHost, resume)
        }
    }

    private fun onPlayerEvent(event: PlayerEvent) {
        val command = connect.playback
        if (command == null) {
            when (event) {
                is PlayerEvent.Started -> {
                    if (event.uri.isNotBlank() && event.uri == player.queuedNextUri) {
                        log.i("gapless: the engine moved to '${event.uri.takeLast(12)}' on its own")
                        scope.launch { adoptEngineAdvance(event.uri) }
                        return
                    }
                    scope.launch {
                        publishLocalFromEngine()
                        queueGaplessNext(event.uri.ifBlank { state.trackUri })
                    }
                }
                is PlayerEvent.Seeked -> scope.launch { publishLocalFromEngine(event.positionMs) }
                is PlayerEvent.Ended -> {
                    val ended = event.uri.ifBlank { player.state.value.trackUri }
                    if (gaplessEnabled() && player.currentUri != ended) {
                        log.i("player ended '$ended', the queued track takes over without a restart")
                        return
                    }
                    log.i("player ended '$ended' with no command, advancing on this phone")
                    scope.launch { advance(ended) }
                }
                is PlayerEvent.Failed -> {
                    gaplessQueuedUri = ""
                    if (videoActive()) {
                        fallbackFromVideo()
                        return
                    }
                    flushHeld()
                }
                is PlayerEvent.Progress -> Unit
            }
            return
        }
        val track = command.currentTrack
        val ad = command.isAd
        val raw = if (ad) AD_THRESHOLD_MS else player.positionMs
        val duration = if (ad) AD_THRESHOLD_MS else player.durationMs
        val position = if (duration > 0L) raw.coerceAtMost(duration) else raw
        when (event) {
            is PlayerEvent.Started -> {
                if (event.uri.isNotBlank() && event.uri == player.queuedNextUri) {
                    log.i("gapless: the engine moved to '${event.uri.takeLast(12)}' on its own")
                    scope.launch { adoptEngineAdvance(event.uri) }
                    return
                }
                log.i("player started pos=$position dur=${player.durationMs}ms ad=$ad")
                scope.launch {
                    val snapshot = Snapshot.of(
                        command,
                        DebugSource.STARTED_PLAYING,
                        positionMs = position,
                        previousPositionMs = 0L,
                        speed = if (player.isPlaying) 1.0 else 0.0,
                        paused = !player.isPlaying,
                        durationMs = duration,
                        format = player.format.takeIf { it > 0 }?.toString().orEmpty(),
                        bitrate = player.bitrate,
                        quality = player.quality,
                        videoOn = canvasOn,
                    )
                    if (snapshot == null) {
                        log.w("started: no snapshot")
                        return@launch
                    }
                    val accepted = connect.report(snapshot)
                    if (track != null) connect.melody.audioParams(track, position)
                    log.i("report started_playing accepted=$accepted pos=$position ad=$ad " +
                            "fmt=${snapshot.format} ${snapshot.bitrate}b ${snapshot.quality}"
                    )
                }
                if (!ad) queueGaplessNext(event.uri.ifBlank { state.trackUri })
            }

            is PlayerEvent.Seeked -> {
                log.i("player seeked ${event.positionMs}ms")
                scope.launch {
                    val snapshot = Snapshot.of(
                        command,
                        DebugSource.SEEK,
                        positionMs = event.positionMs,
                        previousPositionMs = event.positionMs,
                        speed = 1.0,
                        videoOn = canvasOn,
                    )
                    snapshot?.let { connect.report(it) }
                }
            }

            is PlayerEvent.Ended -> {
                log.i("player ended at $raw clamped=$position dur=$duration ad=$ad")
                scope.launch {
                    if (ad) {
                        val thresholdSnapshot = connect.playback?.let {
                            Snapshot.of(
                                it,
                                DebugSource.PLAYED_THRESHOLD_REACHED,
                                positionMs = AD_THRESHOLD_MS,
                                previousPositionMs = AD_THRESHOLD_MS,
                                speed = 1.0,
                                durationMs = AD_THRESHOLD_MS,
                                videoOn = canvasOn,
                            )
                        }
                        val accepted = thresholdSnapshot?.let { connect.report(it) }
                        log.i("report played_threshold_reached accepted=$accepted ad=true")
                    }
                    val finalized = connect.playback?.let {
                        Snapshot.of(
                            it,
                            DebugSource.TRACK_DATA_FINALIZED,
                            positionMs = position,
                            previousPositionMs = position,
                            speed = 1.0,
                            durationMs = duration,
                            videoOn = canvasOn,
                        )
                    }
                    if (finalized == null) {
                        log.w("report track_data_finalized: no snapshot")
                    } else {
                        val accepted = connect.report(finalized)
                        log.i("report track_data_finalized accepted=$accepted ad=$ad pos=$position")
                    }
                    val ended = event.uri.ifBlank {
                        command.currentTrack?.uri.orEmpty().ifBlank { player.state.value.trackUri }
                    }
                    if (gaplessEnabled() && player.currentUri != ended) {
                        log.i("player ended '$ended', the queued track takes over without a restart")
                        return@launch
                    }
                    advance(ended)
                }
            }

            is PlayerEvent.Progress -> Unit

            is PlayerEvent.Failed -> {
                log.w("player failed: ${event.message}")
                if (gaplessQueuedUri.isNotBlank()) {
                    log.w("gapless: the queued '${gaplessQueuedUri.takeLast(12)}' failed to take over, advancing the hard way")
                    gaplessQueuedUri = ""
                    player.clearQueuedNext()
                    scope.launch { advance(state.trackUri) }
                    return
                }
                if (videoActive()) {
                    fallbackFromVideo()
                    return
                }
                if (event.message.startsWith("license") || event.message.startsWith("licence")) {
                    scope.launch { blameTheStream(event.message) }
                    if (event.message.contains("429")) {
                        log.w("a 429 does not clear the block on screen, the last track keeps its metadata " +
                                "until the balance comes back")
                        return
                    }
                }
                flushHeld()
            }
        }
    }

    private suspend fun blameTheStream(message: String) {
        val fetched = connect.refreshCluster()
        val at = connect.cluster.value
        val active = at?.activeDeviceId.orEmpty()
        log.w("$message, active=${active.ifBlank { "?" }} ours=${active == deviceId} " +
                "playing=${at?.isPlaying} refreshed=$fetched " +
                "context='${at?.contextUri.orEmpty()}'"
        )
        when {
            active.isNotBlank() && active != deviceId ->
                log.w("the stream is held by another device, this account allows one at a time")
            active.isBlank() ->
                log.w("no active device in the cluster, so the 429 is not another device holding the stream")
            else ->
                log.w("this device holds the stream and was still refused, the 429 came from the edge")
        }
    }

    private fun threshold(playing: PlayerState) {
        if (playing.format <= 0) return
        if (playing.positionMs < THRESHOLD_MS) return
        if (playing.trackUri.isBlank() || thresholdTrack == playing.trackUri) return
        thresholdTrack = playing.trackUri
        log.i("threshold at ${playing.positionMs}ms '${playing.trackUri}'")
        scope.launch {
            val command = connect.playback ?: return@launch
            val snapshot = Snapshot.of(
                command,
                DebugSource.PLAYED_THRESHOLD_REACHED,
                positionMs = playing.positionMs,
                previousPositionMs = playing.positionMs,
                speed = 1.0,
                durationMs = playing.durationMs,
                format = playing.format.toString(),
                bitrate = playing.bitrate,
                quality = playing.quality,
            )
            val accepted = snapshot?.let { connect.report(it) }
            log.i("report played_threshold_reached accepted=$accepted pos=${playing.positionMs}")
        }
    }

    private fun chainFor(): List<String> {
        if (chainContext.isBlank()) return emptyList()
        val cluster = connect.cluster.value
        val playing = cluster?.contextUri.orEmpty()
        if (playing.isNotBlank() && playing != chainContext) {
            val single = playing.startsWith("spotify:track:") ||
                playing.startsWith("spotify:episode:")
            val barren = cluster == null || cluster.nextTracks.isEmpty()
            val holds = cluster?.track?.uri?.let { it in chainRows } == true
            if ((!single && !barren) || (single && !holds)) {
                log.w("chain '$chainContext' is not the playing context '$playing', following the server")
                return emptyList()
            }
            log.i("chain '$chainContext' kept, the server still names " +
                    "'${playing.takeLast(12)}' with ${cluster?.nextTracks?.size ?: 0} next row(s)")
        }
        return chainRows
    }

    private fun rowOf(rows: List<String>, uri: String): Int {
        if (uri.isBlank()) return -1
        if (resumeRow in rows.indices && rows[resumeRow] == uri) return resumeRow
        return rows.indexOfFirst { it == uri }
    }

    private suspend fun advance(ended: String) {
        if (advancing) {
            log.w("advance: already running for the track that just ended, not chaining a second time")
            return
        }
        advancing = true
        try {
            runAdvance(ended)
        } finally {
            advancing = false
        }
    }

    private suspend fun runAdvance(endedIn: String) {
        val ended = endedIn
        if (sleepEndOfTrackArmed) {
            sleepEndOfTrackArmed = false
            log.i("sleep timer: '${ended.takeLast(12)}' ended, stopping instead of advancing")
            player.pause()
            reportPaused()
            state = state.copy(sleepTimer = null)
            return
        }
        val rows = chainFor()
        val at = rowOf(rows, ended)
        if (at >= 0) resumeRow = at + 1
        val server = connect.cluster.value
        val serverQueued = server?.queued.orEmpty()
        val queued = queuedTail.firstOrNull()
        val next = when {
            connect.playback == null -> null
            queued != null || serverQueued.isNotEmpty() -> awaitServerNext(ended)
            else -> serverNext(ended)
        }
        val moved = next?.currentTrack
        val engine = player.state.value.trackUri
        val engineOnEnded = engine.isBlank() || engine == ended ||
                (ended.startsWith(AD_PREFIX) && engine.startsWith(AD_PREFIX))
        if (!engineOnEnded) {
            log.i("advance: the engine already moved to '${engine.takeLast(12)}', nothing to do")
            return
        }
        if (repeatTrackOn(server) && ended.isNotBlank() && !ended.startsWith(AD_PREFIX)) {
            log.i("advance -> repeating '${ended.takeLast(12)}' (repeat one)")
            playTrack(
                ended,
                chainContext.ifBlank { server?.contextUri.orEmpty() },
                if (at >= 0) at else -1,
            )
            return
        }
        val fromQueue = moved != null &&
                (moved.uri == queued?.uri || serverQueued.any { it.uri == moved.uri })
        if (next != null && moved != null) {
            log.i("advance -> '${moved.name}' from the server, the context stays put " +
                    "sm=${next.stateMachineId.take(10)} queued=$fromQueue"
            )
            queuedTail = queuedTail.filterNot { it.uri == moved.uri }
            startFromServer(next, moved)
            return
        }
        val enhancedContext = chainContext.ifBlank { server?.contextUri.orEmpty() }
        if (enhancedQueue(enhancedContext).isNotEmpty()) {
            val upcoming = enhancedUpcoming(enhancedContext, ended)
            val pick = upcoming.firstOrNull()?.uri
            if (!pick.isNullOrBlank() && pick != ended) {
                log.i("advance -> '${pick.takeLast(12)}' from the enhanced queue, " +
                        "${upcoming.size} upcoming"
                )
                playTrack(pick, enhancedContext, -1)
                return
            }
            log.i("advance: the enhanced queue ran out after '${ended.takeLast(12)}'")
        }
        if (queued != null && serverQueued.isNotEmpty() && serverQueued.none { it.uri == queued.uri }) {
            queuedTail = queuedTail.drop(1)
            log.w("advance: '${queued.uri.takeLast(12)}' is no longer queued on the server, skipping it")
        } else if (queued != null) {
            queuedTail = queuedTail.drop(1)
            publishQueue(server)
            log.i("advance -> the queued '${queued.title.ifBlank { queued.uri.takeLast(12) }}', " +
                    "the list resumes at row $resumeRow of ${rows.size}")
            playTrack(queued.uri, chainContext.ifBlank { server?.contextUri.orEmpty() }, -1)
            return
        }
        if (sessionQueueContext == chainContext.ifBlank { server?.contextUri.orEmpty() } && sessionQueue.isNotEmpty()) {
            if (sessionQueue.firstOrNull() == ended) sessionQueue = sessionQueue.drop(1)
            val pick = sessionQueue.firstOrNull()
            if (!pick.isNullOrBlank()) {
                sessionQueue = sessionQueue.drop(1)
                log.i("advance -> '${pick.takeLast(12)}' from the play order, ${sessionQueue.size} left")
                playTrack(pick, chainContext.ifBlank { server?.contextUri.orEmpty() }, -1)
                return
            }
        }
        if (shuffleOn(server) && !smartOn(server) && !ended.startsWith(AD_PREFIX)) {
            val pool = rows.ifEmpty {
                server?.window()?.filter { it.isVisible }?.map { it.uri }.orEmpty()
            }.filter { it.isNotBlank() && it != ended }
            if (pool.isNotEmpty()) {
                val pick = pool.random()
                log.i("advance -> '${pick.takeLast(12)}' shuffled out of ${pool.size} rows")
                playTrack(
                    pick,
                    chainContext.ifBlank { server?.contextUri.orEmpty() },
                    rows.indexOf(pick),
                )
                return
            }
        }
        if (resumeRow in rows.indices) {
            val row = rows[resumeRow]
            log.i("advance -> '$row' from the list that started it, row $resumeRow of ${rows.size} " +
                    "in '$chainContext'"
            )
            playTrack(row, chainContext, resumeRow)
            return
        }
        if (next != null && moved != null) {
            startFromServer(next, moved)
            return
        }
        val command = connect.playback
        if (command != null) {
            log.i("advance: no transition, state=${command.stateId.take(8)} " +
                    "idx=${command.stateIndex}/${command.states.size} adv=${command.currentState?.advanceIndex} " +
                    "track=${command.currentTrack?.uri?.takeLast(12)}"
            )
        }
        val cluster = connect.cluster.value ?: return
        if (cluster.window().none { it.uri == ended }) {
            log.w("advance: '$ended' is not in the cluster window, not guessing")
            return
        }
        val target = targetFrom(cluster, 0)
        if (target == null || target.track.uri == ended) {
            if (repeatContextOn(cluster) && !ended.startsWith(AD_PREFIX)) {
                val firstRow = rows.firstOrNull { it.isNotBlank() && it != ended }
                    ?: cluster.window().firstOrNull { it.isVisible && it.uri != ended }?.uri
                if (firstRow != null) {
                    log.i("advance -> wrapping to '${firstRow.takeLast(12)}' (repeat all)")
                    playTrack(
                        firstRow,
                        chainContext.ifBlank { cluster.contextUri },
                        rows.indexOf(firstRow),
                    )
                    return
                }
            }
            log.w("advance: end of the context at '${command?.currentTrack?.name ?: ended.takeLast(12)}'")
            autoplayFrom(ended)
            return
        }
        log.i("advance -> '${target.track.title}' from the cluster row")
        playTrack(target.track.uri, cluster.contextUri, target.index)
    }

    private suspend fun autoplayFrom(ended: String) {
        if (ended.isBlank() || ended.startsWith(AD_PREFIX)) return
        if (!autoplayEnabled()) {
            log.i("autoplay: off, staying quiet at the end of the context")
            return
        }
        val seed = ended.takeIf { it.startsWith("spotify:track:") }
            ?: chainRows.lastOrNull { it.startsWith("spotify:track:") }
            ?: return
        val station = runCatching { Krespot.artists.artistRadio(context, seed) }.getOrNull()
        if (station.isNullOrBlank()) {
            log.w("autoplay: no station for '${seed.takeLast(12)}', staying quiet")
            return
        }
        val rows = runCatching { Krespot.collections.collection(context, station) }.getOrNull()
            ?.tracks
            ?.map { it.uri }
            ?.filter { it.startsWith("spotify:track:") || it.startsWith("spotify:episode:") }
            .orEmpty()
        val start = rows.indexOfFirst { it != ended }
        val first = rows.getOrNull(start)
        if (first == null) {
            log.w("autoplay: the station '$station' has no playable rows, staying quiet")
            return
        }
        log.i("autoplay -> '${first.takeLast(12)}' from the station '$station' " +
                "seeded by '${seed.takeLast(12)}', ${rows.size} rows at row $start"
        )
        chainRows = rows
        chainContext = station
        resumeRow = start
        playTrack(first, station, start)
    }

    private fun autoplayEnabled(): Boolean =
        com.quackify.backend.core.store.AppPrefs.prefs(context, CLIENT_PREFS)
            .getBoolean(AUTOPLAY_ON_END_PREF, true)

    private fun serverNext(ended: String): PlaybackCommand? {
        val command = connect.playback ?: return null
        val same = command.currentTrack?.uri.orEmpty() == ended
        val following = if (same) command.following() else command
        val track = following?.currentTrack ?: return null
        return if (track.uri.isBlank() || track.uri == ended) null else following
    }

    private suspend fun awaitServerNext(ended: String): PlaybackCommand? {
        serverNext(ended)?.let { return it }
        val until = System.currentTimeMillis() + ADVANCE_WAIT_MS
        while (System.currentTimeMillis() < until) {
            delay(120L)
            val engine = player.state.value.trackUri
            if (engine.isNotBlank() && engine != ended) return null
            serverNext(ended)?.let { return it }
        }
        log.i("advance: the server stayed on '${ended.takeLast(12)}' for ${ADVANCE_WAIT_MS}ms")
        return null
    }

    private suspend fun startFromServer(next: PlaybackCommand, track: CommandTrack) {
        if (remoteTarget().isNotBlank()) {
            log.i("advance: another device owns the stream, not starting the engine here")
            return
        }
        val from = next.currentState?.initialPositionMs ?: 0L
        log.i("advance -> '${track.name}' sm=${next.stateMachineId.take(10)} " +
                "state=${next.stateId.take(8)} tracks=${next.tracks.size} from=${from}ms"
        )
        player.play(track, connect.spclientHost, from)
        val snapshot = Snapshot.of(next, DebugSource.BEFORE_TRACK_LOAD, 0L, 0L, speed = 0.0)
        if (snapshot == null) {
            log.w("advance: no snapshot for '${track.name}'")
            return
        }
        val accepted = connect.report(snapshot)
        log.i("advance before_track_load accepted=$accepted state=${snapshot.stateId.take(8)}")
        if (accepted) settleCluster(track.uri, -1)
    }

    private fun playingUri(cluster: ClusterState): String =
        playingUri(cluster, navigatorState())

    private fun targetFrom(cluster: ClusterState, offset: Int): QueueTarget? =
        targetFrom(cluster, offset, navigatorState())

    private fun navigatorState(): QueueNavigatorState = QueueNavigatorState(
        engineUri = player.state.value.trackUri,
        commandedUri = connect.playback?.currentTrack?.uri.orEmpty(),
    )

    private companion object {
        const val KEY_LOOKAHEAD = 6

    const val AD_THRESHOLD_MS = 1000L
        const val AD_PREFIX = "spotify:ad:"
        const val THRESHOLD_MS = 30_000L
        const val CONFIRM_MS = 2500L
        const val CLUSTER_TRIES = 3
        const val DEVICE_WATCH_MS = 4_000L
        const val DEVICE_POLL_MS = 15_000L
        const val SKIP_SETTLE_MS = 600L
        const val REMOTE_TICK_MS = 1_000L
        const val RESYNC_MS = 3_000L

        const val SKIP_TRIES = 5
        const val SKIP_RETRY_MS = 400L
        const val CLUSTER_RETRY_MS = 400L
        const val RESTART_MS = 3000L
        const val PENDING_MS = 10_000L
        const val ADVANCE_WAIT_MS = 900L
        const val LIVE_STATE_MS = 30_000L
        const val ENHANCEMENT_SMART = "RECOMMENDATION"
        const val MODE_SETTLE_MS = 1200L
        const val MODE_OVERRIDE_MS = 8000L
        const val REMOTE_INTENT_MS = 10_000L
        const val BLANK = "blank"
        const val AUTOPLAY_WINDOW_MS = 30_000L
        const val GONE_READS = 2
        const val TRANSFER_FRESH_MS = 120_000L
    }
}
