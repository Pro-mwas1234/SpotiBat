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
 * Copyright (c) 2026 23fpsz (Quackify). All Rights Reserved.
 * Original repository: https://github.com/23fpsz/Quackify
 *
 * PROPRIETARY / SOURCE-AVAILABLE MODULE:
 * You may view and audit this source code for personal or educational inspection.
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.quackify.backend.connect

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import com.quackify.backend.connect.cluster.ClusterParser
import com.quackify.backend.connect.cluster.ClusterState
import com.quackify.backend.connect.cluster.ClusterTrack
import com.quackify.backend.connect.cluster.Device
import com.quackify.backend.connect.command.CommandClient
import com.quackify.backend.connect.command.CommandParser
import com.quackify.backend.connect.command.PlaybackCommand
import com.quackify.backend.connect.command.RemoteCommand
import com.quackify.backend.connect.command.RemoteCommandParser
import com.quackify.backend.connect.command.Registration
import com.quackify.backend.connect.command.TrackUid
import com.quackify.backend.connect.dealer.Dealer
import com.quackify.backend.connect.dealer.DealerFrame
import com.quackify.backend.connect.dealer.DealerHost
import com.quackify.backend.connect.dealer.DealerUri
import com.quackify.backend.connect.state.Melody
import com.quackify.backend.connect.state.Snapshot
import com.quackify.backend.connect.state.StateReporter
import com.quackify.backend.core.auth.EncoreAuth
import com.quackify.backend.core.auth.WebToken
import com.quackify.backend.core.log.Logs
import com.quackify.backend.core.webview.SilentTokenRefresh
import org.json.JSONArray
import org.json.JSONObject

private val log = Logs.of(Logs.Module.CONNECT)

private const val TRANSFER_TRIES = 6

private const val TRANSFER_RETRY_MS = 500L

/** Dealer channels a player command (or a transfer itself) can arrive on. */
private const val PLAYER_COMMAND_URI = "hm://connect-state/v1/player/command"

private const val CONNECT_TRANSFER_URI = "hm://connect-state/v1/connect/transfer"

/** How often the connect-state player member is re-published while polling. */
private const val MEMBER_PUT_MS = 60_000L

private const val TOKEN_RENEW_MS = 60_000L

/** Window sizes the native clients cap their published queue at. */
private const val PREV_TRACKS_MAX = 10

private const val NEXT_TRACKS_MAX = 80

class KrespotConnect(private val context: Context) {

    val deviceId: String = Device.id(context)

    private val dealer = Dealer()

    private val reporter = StateReporter(context, deviceId) { spclient }

    val melody = Melody(context, { spclient }, { _command.value?.playbackSessionId.orEmpty() })

    private val _command = MutableStateFlow<PlaybackCommand?>(null)
    val command: StateFlow<PlaybackCommand?> = _command

    private val _remoteCommands = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 16)
    val remoteCommands: SharedFlow<RemoteCommand> = _remoteCommands

    private val _cluster = MutableStateFlow<ClusterState?>(null)
    val cluster: StateFlow<ClusterState?> = _cluster

    private val _pushes = MutableSharedFlow<PlaybackCommand>(extraBufferCapacity = 8)
    val pushes: SharedFlow<PlaybackCommand> = _pushes

    /** Fires when the server hands this device the stream (another client transferred here). */
    private val _transfers = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val transfers: SharedFlow<Unit> = _transfers

    private val _connectionId = MutableStateFlow("")
    val connectionId: StateFlow<String> = _connectionId

    private val _registered = MutableStateFlow(false)
    val registered: StateFlow<Boolean> = _registered

    @Volatile
    private var spclient: String = ""

    @Volatile
    private var connected: Boolean = false

    @Volatile
    var accepts: ((PlaybackCommand) -> Boolean)? = null

    /**
     * The three registration PUTs take a few round trips; running them here
     * keeps them off the frame collector so cluster pushes and commands keep
     * flowing while the handshake is still in flight.
     */
    private val registerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)


    val playback: PlaybackCommand? get() = _command.value

    val spclientHost: String get() = spclient

    fun frames(): Flow<DealerFrame> = flow {
        var attempt = 0
        while (true) {
            val host = DealerHost.resolve(context)
            if (host == null) {
                attempt++
                log.w("apresolve returned no dealer, retry $attempt")
                delay(backoff(attempt))
                continue
            }
            spclient = host.second
            val token = usableToken()
            log.i("device=$deviceId dealer=${host.first} token=${token.length}B")
            if (_cluster.value == null) {
                runCatching { seedCluster() }
                    .onFailure { log.w("cluster seed failed ${it.javaClass.simpleName}: ${it.message}") }
            }
            try {
                emitAll(dealer.open(host.first, token).onEach(::absorb))
                log.w("dealer stream ended")
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                val message = failure.message.orEmpty()
                log.w("dealer failed ${failure.javaClass.simpleName}: $message")
                if (message.contains("401") || message.contains("403")) {
                    val refreshed = SilentTokenRefresh.refresh(context, "dealer")
                    log.i("dealer auth failure, token refreshed=$refreshed")
                }
            }
            if (connected) {
                connected = false
                attempt = 0
            }
            attempt++
            delay(backoff(attempt))
        }
    }

    private fun backoff(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, 4)
        return (1000L shl shift).coerceAtMost(15_000L)
    }

    private suspend fun usableToken(): String {
        val left = EncoreAuth.tokenRemainingMs(context)
        if (left == -1L || left >= TOKEN_RENEW_MS) return EncoreAuth.token(context)
        log.i("access token has ${left / 1000}s left, minting a fresh one before dialling")
        return WebToken.mint(context) ?: EncoreAuth.token(context)
    }

    /** A transfer request can also ride in as the bare options body, with no endpoint field. */
    private fun isTransferOptions(payload: String): Boolean =
        runCatching { JSONObject(payload).has("transfer_options") }.getOrDefault(false)

    private suspend fun absorb(frame: DealerFrame) {
        when {
            frame.isConnection -> {
                connected = true
                _connectionId.value = frame.connectionId
                log.i("connection ${frame.connectionId.take(24)}… len=${frame.connectionId.length} " +
                        "decoded=${frame.decodedConnectionId.length} " +
                        "frameHeaders=[${frame.headers.keys.joinToString(",")}]"
                )
                registerScope.launch {
                    runCatching { register(frame.connectionId) }
                        .onFailure { log.w("register failed ${it.javaClass.simpleName}: ${it.message}") }
                }
            }

            frame.uri == DealerUri.TRACK_COMMAND -> frame.payloads.forEach { payload ->
                val parsed = withContext(Dispatchers.Default) { CommandParser.parse(payload) } ?: return@forEach
                if (accepts?.invoke(parsed) == false) {
                    log.w("command dropped: '${parsed.currentTrack?.name}' idx=${parsed.stateIndex} " +
                            "sm=${parsed.stateMachineId.take(10)} is not what this device is playing"
                    )
                    return@forEach
                }
                if (parsed.isControl) {
                    _pushes.emit(parsed)
                    log.i("control '${parsed.endpoint}' from the dealer")
                    return@forEach
                }
                if (parsed.isReplaceState) {
                    log.i("replace_state pushed, the bridge decides what to load")
                    transferLandedByPush()
                    _pushes.emit(parsed)
                    return@forEach
                }
                _command.value = parsed
                TrackUid.learn(parsed)
                reconcile(parsed)
                _pushes.emit(parsed)
                log.v("command '${parsed.contextDescription}' tracks=${parsed.tracks.size} " +
                        "state=${parsed.stateId.take(8)} track=${parsed.currentTrack?.name}" +
                        if (parsed.isAd) " AD break" else ""
                )
            }

            frame.uri == DealerUri.CLUSTER -> frame.payloads.forEach { payload ->
                val parsed = withContext(Dispatchers.Default) { ClusterParser.parse(payload, deviceId, _cluster.value) }
                if (parsed == null) {
                    log.w("cluster frame did not parse, ${payload.length}B " +
                            "head=${payload.take(140)}"
                    )
                    return@forEach
                }
                _cluster.value = parsed
                TrackUid.learn(parsed)
                if (parsed.isAd || parsed.adBreak != null) {
                    log.i("cluster ${parsed.updateReason} ad=${parsed.track.uri.take(24)} " +
                            "break=${parsed.adBreak?.positionInBreak}/${parsed.adBreak?.totalEstimate} " +
                            "seek=${!parsed.disallowSeeking}"
                    )
                }
            }

            frame.uri.startsWith(PLAYER_COMMAND_URI) ||
                    frame.uri.startsWith(CONNECT_TRANSFER_URI) ->
                frame.payloads.forEach { payload ->
                    val remote = withContext(Dispatchers.Default) { RemoteCommandParser.parse(payload) }
                    if (remote != null) {
                        log.i("remote command '${remote.endpoint}' from '${remote.sentByDeviceId.take(8)}' " +
                                (if (remote.isTransfer) "" else "value=${remote.value} seek=${remote.seekToMs} " +
                                "track=${remote.trackUri.takeLast(12)} shuffle=${remote.shuffle} " +
                                "repeat=${remote.repeatContext}/${remote.repeatTrack}")
                        )
                        if (remote.isTransfer) {
                            _transfers.tryEmit(Unit)
                        } else {
                            _remoteCommands.tryEmit(remote)
                        }
                        return@forEach
                    }
                    val parsed = withContext(Dispatchers.Default) { CommandParser.parse(payload) }
                    when {
                        parsed?.isTransfer == true || isTransferOptions(payload) -> {
                            log.i("the server moved the stream here, the bridge takes it")
                            _transfers.tryEmit(Unit)
                        }
                        parsed != null ->
                            log.i("connect-state command '${parsed.endpoint}' is not handled here yet")
                        else ->
                            log.v("connect-state frame ${payload.length}B did not parse")
                    }
                }

            else -> log.v("frame ${frame.uri.take(56)} ${frame.payloads.size}p")
        }
    }

    private suspend fun register(connectionId: String) {
        if (connectionId.isBlank() || spclient.isBlank()) return
        val host = spclient
        val started = SystemClock.elapsedRealtime()
        val player = Registration.player(context, host, deviceId, connectionId)
        val observer = Registration.observer(context, host, deviceId, connectionId)
        val member = Registration.connectState(context, host, deviceId, connectionId)
        val body = observer?.takeIf { it.ok }?.body.orEmpty()
            .ifBlank { member?.takeIf { it.ok }?.body.orEmpty() }
        if (body.isNotBlank()) {
            val parsed = withContext(Dispatchers.Default) { ClusterParser.parse(body, deviceId, _cluster.value) }
            if (parsed != null) {
                _cluster.value = parsed
                TrackUid.learn(parsed)
                log.i("cluster '${parsed.track.title}' idx=${parsed.contextIndex} " +
                        "next=${parsed.nextTracks.size} prev=${parsed.prevTracks.size} " +
                        "context=${parsed.contextUri.take(32)}"
                )
            }
        }
        _registered.value = (player?.ok == true) || (member?.ok == true)
        if (player?.ok == true) reporter.seed(Registration.initialSeq(player))
        memberPutAt = SystemClock.elapsedRealtime()
        log.i("registration observer=${observer?.code ?: "-"} player=${player?.code ?: "-"} " +
                "member=${member?.code ?: "-"} registered=${_registered.value} " +
                "in ${SystemClock.elapsedRealtime() - started}ms"
        )
    }

    @Volatile
    private var memberPutAt = 0L

    /**
     * Keeps the connect-state player member alive. The observer poll keeps
     * the dealer socket busy, but the member that makes this phone show up
     * in "Listening on" needs an occasional touch of its own; while this
     * device is the one playing, the state reports already speak for it.
     */
    private suspend fun refreshMember() {
        val connection = _connectionId.value
        if (spclient.isBlank() || connection.isBlank()) return
        if (_cluster.value?.activeDeviceId == deviceId) return
        val now = SystemClock.elapsedRealtime()
        if (now - memberPutAt < MEMBER_PUT_MS) return
        memberPutAt = now
        Registration.connectState(context, spclient, deviceId, connection)
    }

    /** Set by the bridge: true while the local engine holds the stream. */
    @Volatile
    var holdsStream: Boolean = false
        set(value) {
            if (value && !field) activeSince = System.currentTimeMillis()
            field = value
        }

    /** Wall clock of the current local takeover, published as started_playing_at. */
    @Volatile
    private var activeSince = 0L

    /**
     * Publishes this phone as the account's active player, librespot style:
     * a connect-state member PUT with `is_active: true` and the full
     * player_state nested in the device block. The server answer is the
     * cluster, so it doubles as a refresh. While this runs there is no need
     * for the hobs_ observer poll - publishing state is what keeps the
     * member alive and the position current for every other client.
     */
    suspend fun publishLocalState(
        track: ClusterTrack,
        contextUri: String,
        contextIndex: Int,
        positionMs: Long,
        playing: Boolean,
        durationMs: Long,
        reason: String = Registration.REASON_STATE_CHANGED,
        shuffle: Boolean = false,
        repeatContext: Boolean = false,
        repeatTrack: Boolean = false,
        enhancement: String = "",
        queueRevision: String = "",
        contextDescription: String = "",
        nextRows: List<ClusterTrack> = emptyList(),
    ): Boolean {
        val connection = _connectionId.value
        if (spclient.isBlank() || connection.isBlank()) {
            log.w("publish: the dealer is not connected yet")
            return false
        }
        if (track.uri.isBlank()) return false
        if (activeSince <= 0L) activeSince = System.currentTimeMillis()
        val state = withContext(Dispatchers.Default) {
            localPlayerState(
                track, contextUri, contextIndex, positionMs, playing, durationMs,
                shuffle, repeatContext, repeatTrack, enhancement, queueRevision,
                contextDescription, nextRows,
            )
        }
        val response = Registration.connectState(
            context = context,
            spclient = spclient,
            deviceId = deviceId,
            connectionId = connection,
            reason = reason,
            isActive = true,
            playerState = state,
            startedPlayingAt = activeSince,
        )
        val ok = response?.ok == true
        if (!ok) {
            log.w("publish: member ${response?.code ?: "no response"} " +
                    response?.body?.take(160).orEmpty()
            )
            return false
        }
        memberPutAt = SystemClock.elapsedRealtime()
        val parsed = withContext(Dispatchers.Default) {
            ClusterParser.parse(response.body, deviceId, _cluster.value)
        }
        if (parsed != null) {
            _cluster.value = parsed
            TrackUid.learn(parsed)
        }
        log.i("publish '${track.title.ifBlank { track.uri.takeLast(12) }}' " +
                "pos=${positionMs}ms playing=$playing idx=$contextIndex " +
                "ctx='${contextUri.takeLast(24)}' -> ${response?.code}"
        )
        return true
    }

    /** Drops this device out of the cluster, for shutdown. */
    suspend fun unregister() {
        if (spclient.isBlank()) return
        Registration.unregister(context, spclient, deviceId)
    }

    private fun localPlayerState(
        track: ClusterTrack,
        contextUri: String,
        contextIndex: Int,
        positionMs: Long,
        playing: Boolean,
        durationMs: Long,
        shuffle: Boolean,
        repeatContext: Boolean,
        repeatTrack: Boolean,
        enhancement: String,
        queueRevision: String,
        contextDescription: String,
        nextRows: List<ClusterTrack>,
    ): JSONObject {
        val now = System.currentTimeMillis()
        val window = _cluster.value
            ?.takeIf { it.contextUri == contextUri && contextUri.isNotBlank() }
            ?.window()
            .orEmpty()
        val anchor = window.indexOfFirst { it.uri == track.uri }
        val prev = if (anchor > 0) window.take(anchor).takeLast(PREV_TRACKS_MAX) else emptyList()
        val upcoming = LinkedHashMap<String, ClusterTrack>()
        nextRows.filter { it.uri.isNotBlank() && it.uri != track.uri }.forEach { upcoming[it.uri] = it }
        if (anchor >= 0) {
            window.drop(anchor + 1)
                .filter { it.isVisible && it.uri != track.uri }
                .forEach { upcoming.putIfAbsent(it.uri, it) }
        }
        val next = upcoming.values.take(NEXT_TRACKS_MAX)
        val metadata = runCatching { JSONObject(track.raw) }.getOrElse { JSONObject() }
        if (metadata.length() == 0 && track.title.isNotBlank()) {
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
        val current = JSONObject()
            .put("uri", track.uri)
            .put("uid", track.uid)
            .put("metadata", metadata)
            .put("provider", track.provider.ifBlank { "context" })
        val state = JSONObject()
            .put("session_id", "")
            .put("is_system_initiated", true)
            .put("playback_speed", if (playing) 1 else 0)
            .put("position_as_of_timestamp", positionMs)
            .put("timestamp", now)
            .put("duration", if (durationMs > 0L) durationMs else track.durationMs)
            .put("is_playing", true)
            .put("is_paused", !playing)
            .put("is_buffering", !playing)
            .put("context_uri", contextUri)
            .put("context_url", "context://$contextUri")
            .put("context_restrictions", JSONObject())
            .put("context_metadata", JSONObject().put("context_description", contextDescription))
            .put(
                "play_origin",
                JSONObject()
                    .put("feature_identifier", "playlist")
                    .put("referrer_identifier", "deeplink")
                    .put("device_identifier", deviceId),
            )
            .put("index", JSONObject().put("page", 0).put("track", contextIndex.coerceAtLeast(0)))
            .put("track", current)
            .put("prev_tracks", JSONArray().apply { prev.forEach { put(trackNode(it)) } })
            .put("next_tracks", JSONArray().apply { next.forEach { put(trackNode(it)) } })
            .put(
                "options",
                JSONObject()
                    .put("shuffling_context", shuffle)
                    .put("repeating_context", repeatContext)
                    .put("repeating_track", repeatTrack)
                    .put(
                        "modes",
                        JSONObject().put(
                            "context_enhancement",
                            enhancement.ifBlank { "NONE" },
                        ),
                    ),
            )
            .put("suppressions", JSONObject())
            .put("queue_revision", queueRevision)
        return state
    }

    private fun trackNode(track: ClusterTrack): JSONObject {
        val metadata = runCatching { JSONObject(track.raw) }.getOrElse { JSONObject() }
        return JSONObject()
            .put("uri", track.uri)
            .put("uid", track.uid)
            .put("metadata", metadata)
            .put("provider", track.provider.ifBlank { "context" })
    }

    /** Reads the cluster over plain HTTP and mirrors it, dealer or not. */
    private suspend fun seedCluster() {
        val host = spclient
        if (host.isBlank()) return
        val response = Registration.cluster(context, host)
        val body = response?.body.orEmpty()
        if (response?.ok != true || body.isBlank()) {
            log.w("cluster seed: ${response?.code ?: "no response"}")
            return
        }
        val parsed = withContext(Dispatchers.Default) { ClusterParser.parse(body, deviceId) }
        if (parsed == null) {
            log.i("cluster seed: no player_state in ${body.length}B, nothing to mirror")
            return
        }
        _cluster.value = parsed
        TrackUid.learn(parsed)
        log.i("cluster seed '${parsed.track.title}' active=${parsed.activeDeviceId.take(12)} " +
                "devices=${parsed.devices.size} playing=${parsed.isPlaying}"
        )
    }

    suspend fun refreshCluster(): Boolean {
        val connection = _connectionId.value
        if (spclient.isBlank()) {
            log.w("cluster refresh: no spclient host yet")
            return false
        }
        if (holdsStream) {
            return true
        }
        val response = if (connection.isBlank()) {
            runCatching { Registration.cluster(context, spclient) }.getOrNull()
        } else {
            Registration.observer(context, spclient, deviceId, connection)
        }
        val body = response?.body.orEmpty()
        if (response?.ok != true || body.isBlank()) {
            log.w("cluster refresh: ${if (connection.isBlank()) "get" else "observer"} " +
                    "${response?.code ?: "no response"}"
            )
            return false
        }
        val parsed = withContext(Dispatchers.Default) { ClusterParser.parse(body, deviceId) }
        if (parsed == null) {
            log.w("cluster refresh: ${body.length}B with no player_state")
            return false
        }
        _cluster.value = parsed
        TrackUid.learn(parsed)
        if (parsed.devices.isEmpty()) {
            log.w("cluster refresh: the reply named no devices, root keys " +
                    runCatching { JSONObject(body).keys().asSequence().joinToString(",") }
                        .getOrDefault("-")
            )
        }
        log.v("cluster refresh '${parsed.track.title}' ctx=${parsed.contextUri.take(32)} " +
                "idx=${parsed.contextIndex} next=${parsed.nextTracks.size} " +
                "prev=${parsed.prevTracks.size}"
        )
        refreshMember()
        return true
    }

    suspend fun play(
        contextUri: String,
        trackUri: String,
        trackUid: String,
        trackIndex: Int,
        feature: String = "playlist",
        targetDeviceId: String = "",
    ): Boolean {
        if (spclient.isBlank()) {
            log.w("play: dealer not connected yet")
            return false
        }
        if (trackUri.isBlank()) {
            log.w("play: empty track uri")
            return false
        }
        val response = CommandClient.play(
            context = context,
            spclient = spclient,
            deviceId = deviceId,
            contextUri = contextUri,
            trackUri = trackUri,
            trackUid = trackUid,
            trackIndex = trackIndex,
            feature = feature,
            targetDeviceId = targetDeviceId,
        )
        log.i("play '$trackUri' idx=$trackIndex uid=${trackUid.take(12)} -> ${response?.code ?: "no response"} " +
                response?.body?.take(400).orEmpty()
        )
        return response?.ok == true
    }

    suspend fun addToQueue(trackUri: String): Boolean {
        if (spclient.isBlank() || trackUri.isBlank()) {
            log.w("queue: nothing to add, spclient='$spclient' uri='$trackUri'")
            return false
        }
        val response = CommandClient.addToQueue(context, spclient, deviceId, trackUri)
        return response?.ok == true
    }

    suspend fun setQueue(tracks: List<ClusterTrack>): Boolean {
        if (spclient.isBlank()) {
            log.w("queue: dealer not connected yet")
            return false
        }
        val revision = _cluster.value?.queueRevision.orEmpty()
        val response = CommandClient.setQueue(context, spclient, deviceId, tracks, revision)
        return response?.ok == true
    }

    suspend fun transferTo(targetDeviceId: String, restorePaused: String = CommandClient.RESTORE): Boolean {
        if (spclient.isBlank()) {
            log.w("transfer: dealer not connected yet")
            return false
        }
        if (targetDeviceId == deviceId) {
            val active = _cluster.value?.activeDevice
            if (active != null && active.id == deviceId) {
                log.i("transfer: this device is already the active one, nothing to do")
                return false
            }
            log.i("transfer: taking the stream back from another device")
        }
        val response = CommandClient.transfer(context, spclient, deviceId, targetDeviceId, restorePaused)
        val ok = response?.ok == true
        if (ok) {
            log.i("transfer accepted, watching for it to land")
            settleTransfer(targetDeviceId)
        } else {
            log.w("transfer to " + targetDeviceId.take(8) + " answered " + (response?.code ?: "nothing"))
        }
        return ok
    }

    @Volatile
    private var transferTarget: String = ""

    private fun transferLandedByPush() {
        if (transferTarget.isBlank()) return
        log.i("transfer landed by the pushed state, the active device is " + transferTarget.take(8))
        transferTarget = ""
    }

    private suspend fun settleTransfer(targetDeviceId: String) {
        transferTarget = targetDeviceId
        repeat(TRANSFER_TRIES) { attempt ->
            if (transferTarget.isBlank()) return
            refreshCluster()
            val at = _cluster.value ?: return
            val nowActive = at.activeDeviceId.ifBlank { at.activeDevice?.id.orEmpty() }
            if (nowActive == targetDeviceId) {
                transferTarget = ""
                log.i("transfer landed on attempt " + (attempt + 1) + ", the active device is now " + targetDeviceId.take(8))
                return
            }
            delay(TRANSFER_RETRY_MS)
        }
        if (transferTarget.isBlank()) return
        transferTarget = ""
        log.w("transfer never landed, the active device is still " +
                (_cluster.value?.activeDeviceId?.take(8) ?: "unknown")
        )
    }

    suspend fun setPausedOn(targetDeviceId: String, playing: Boolean): Boolean {
        if (spclient.isBlank()) {
            log.w("pause: the dealer is not connected yet")
            return false
        }
        val target = targetDeviceId.ifBlank { deviceId }
        val response = CommandClient.setPaused(context, spclient, deviceId, target, paused = playing)
        if (response?.ok == true) return true
        if (response?.code == 403) {
            val body = response.body.orEmpty()
            if (body.contains("already_paused")) {
                log.i("pause: '${target.take(8)}' is already paused, nothing to send")
                return true
            }
            if (body.contains("already_playing")) {
                log.i("resume: '${target.take(8)}' is already playing, nothing to send")
                return true
            }
        }
        return false
    }

    suspend fun seekOn(targetDeviceId: String, positionMs: Long): Boolean {
        if (spclient.isBlank()) {
            log.w("seek: the dealer is not connected yet")
            return false
        }
        val target = targetDeviceId.ifBlank { deviceId }
        val response = CommandClient.seekTo(context, spclient, deviceId, target, positionMs)
        return response?.ok == true
    }

    suspend fun skipOn(targetDeviceId: String, next: Boolean): Boolean {
        if (spclient.isBlank()) {
            log.w("skip: the dealer is not connected yet")
            return false
        }
        val target = targetDeviceId.ifBlank { deviceId }
        val response = CommandClient.skip(context, spclient, deviceId, target, next)
        return response?.ok == true
    }

    /** Turns context shuffle on/off on the target device (blank = this one). */
    suspend fun setShuffleOn(targetDeviceId: String, value: Boolean, smart: Boolean = false): Boolean {
        if (spclient.isBlank()) {
            log.w("shuffle: the dealer is not connected yet")
            return false
        }
        val target = targetDeviceId.ifBlank { deviceId }
        val response = CommandClient.setOptions(
            context, spclient, deviceId, target,
            shuffle = value,
            enhancement = if (smart) CommandClient.ENHANCEMENT_SMART else CommandClient.ENHANCEMENT_NONE,
        )
        val ok = response?.ok == true
        if (ok && smart) {
            val cluster = _cluster.value
            val contextUri = cluster?.contextUri.orEmpty()
            if (contextUri.isNotBlank()) {
                CommandClient.updateContext(context, spclient, deviceId, target, contextUri, "enhance")
            }
        }
        return ok
    }

    /** Drops the enhance lens from the active context (plain update_context). */
    suspend fun clearContextLens(targetDeviceId: String, contextUri: String): Boolean {
        if (spclient.isBlank()) {
            log.w("context lens: the dealer is not connected yet")
            return false
        }
        val target = targetDeviceId.ifBlank { deviceId }
        val response = CommandClient.updateContext(context, spclient, deviceId, target, contextUri, null)
        return response?.ok == true
    }

    /** Sets the repeat options on the target device (blank = this one). */    suspend fun setRepeatOn(
        targetDeviceId: String,
        repeatContext: Boolean,
        repeatTrack: Boolean,
    ): Boolean {
        if (spclient.isBlank()) {
            log.w("repeat: the dealer is not connected yet")
            return false
        }
        val target = targetDeviceId.ifBlank { deviceId }
        val response = CommandClient.setOptions(
            context, spclient, deviceId, target,
            repeatContext = repeatContext,
            repeatTrack = repeatTrack,
        )
        return response?.ok == true
    }

    suspend fun setVolumeOn(targetDeviceId: String, level: Int): Boolean {
        if (spclient.isBlank()) return false
        val response = CommandClient.volume(context, spclient, deviceId, targetDeviceId, level)
        return response?.ok == true
    }

    suspend fun report(snapshot: Snapshot): Boolean {
        val next = reporter.report(snapshot) ?: return false
        _command.value = next
        reconcile(next)
        return true
    }

    fun reset() {
        _command.value = null
        _cluster.value = null
        _registered.value = false
        _connectionId.value = ""
        TrackUid.forget()
        reporter.reset()
        spclient = ""
        connected = false
        holdsStream = false
        activeSince = 0L
        Device.forget(context)
        log.i("reset, command, cluster and registration dropped")
    }

    fun learn(command: PlaybackCommand) {
        _command.value = command
        reconcile(command)
        log.i("the engine follows a pushed state, sm=${command.stateMachineId.take(10)} " +
                "idx=${command.stateIndex} paused=${command.paused}"
        )
    }

    private fun reconcile(command: PlaybackCommand) {
        val cluster = _cluster.value ?: return
        val uri = command.currentTrack?.uri.orEmpty()
        if (uri.isBlank() || uri == cluster.track.uri) return
        val moved = cluster.movedTo(uri) ?: return
        _cluster.value = moved
        log.i("cluster follows to '${moved.track.title}' idx=${moved.contextIndex} " +
                "prev=${moved.prevTracks.size} next=${moved.nextTracks.size}"
        )
    }
}
