package com.mwask.bat.backend.player

import android.content.Context
import android.util.Base64
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.offline.DownloadPhase
import com.mwask.bat.backend.core.offline.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val log = Logs.of(Logs.Module.PLAYER)

private const val PREFS = "spotibat_offline"
private const val KEY_TRACKS = "tracks"
private const val KEY_LICENCE = "licence_"

/**
 * Spotify hands out audio in short lived, ranged CDN urls, so the downloader asks for fixed
 * sized byte ranges instead of one long stream: a socket that dies mid-song only costs the
 * range that was in flight, not the whole download. This is the same chunking SpotiBat uses.
 */
private const val CHUNK_BYTES = 8L * 1024 * 1024
private const val CHUNK_ATTEMPTS = 4

/** YouTube's media servers refuse requests from a bare client, so send a normal browser UA. */
private const val MEDIA_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/131.0.0.0 Safari/537.36"
private const val READ_BUFFER_BYTES = 64 * 1024

/** Whole-track retries, used when resolving a stream url fails outright. */
private const val TRACK_ATTEMPTS = 3
private const val TRACK_RETRY_BASE_MS = 2_000L

/**
 * Songs pulled at the same time. One at a time left a 40 track playlist waiting on 40
 * YouTube resolves back to back; two keep the link busy without hammering it.
 */
private const val PARALLEL_DOWNLOADS = 2

/** Pause before retrying a dropped range, so a flaky network is not hit in a tight loop. */
private const val CHUNK_BACKOFF_MS = 400L
private const val CHUNK_BACKOFF_MAX_MS = 3_000L

/** The stream url resolve is bounded; longer than this and the song is stuck, not slow. */
private const val RESOLVE_TIMEOUT_MS = 45_000L

private const val CONNECT_TIMEOUT_S = 15L
private const val READ_TIMEOUT_S = 30L
private const val MAX_IDLE_CONNECTIONS = 12
private const val CONNECTION_KEEP_ALIVE_MINUTES = 5L

private const val PROGRESS_MIN_INTERVAL_MS = 150L

/** A song the user asked to keep on the device. */
data class OfflineTrack(
    val uri: String,
    val title: String,
    val artists: String,
    val album: String,
    val imageUrl: String,
    val durationMs: Long,
    val format: Int,
    val bitrate: Int,
    val quality: String,
    val bytes: Long,
    val savedAt: Long,
    val offlineReady: Boolean,
    val encrypted: Boolean = false,
)

/** What the queue needs to know before the audio exists. */
data class OfflineRequest(
    val uri: String,
    val spclient: String,
    val title: String,
    val artists: String,
    val album: String,
    val imageUrl: String,
    val durationMs: Long,
)

/**
 * Offline storage in the shape SpotiBat uses: the downloader owns its queue, pulls the audio
 * in ranged chunks over its own HTTP client, and keeps the finished bytes as a plain file on
 * disk. Nothing about the transfer depends on media3's [androidx.media3.exoplayer.offline.DownloadManager],
 * so a slow or flaky link retries per chunk instead of failing the song a few percent in.
 *
 * The file is still CENC encrypted, exactly as it comes off the CDN, so playback reads the local
 * file and asks for a Widevine licence for its pssh; Spotify refuses to persist licences, but the
 * unlock is a single small request, and none of the audio has to come down again.
 */
object OfflineAudio {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _library = MutableStateFlow<List<OfflineTrack>>(emptyList())
    val library: StateFlow<List<OfflineTrack>> = _library

    /** Ids of the downloads that finished. */
    private val _completed = MutableStateFlow<Set<String>>(emptySet())
    val completedDownloadIds: StateFlow<Set<String>> = _completed

    /** uri -> 0f..1f, what the notification averages. */
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progressFlow: StateFlow<Map<String, Float>> = _progress

    /** uri -> phase and percent, what the track sheet and the downloads list draw. */
    private val _statuses = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, DownloadStatus>> = _statuses

    private val pending = ConcurrentHashMap<String, OfflineRequest>()
    private val queued = ConcurrentHashMap.newKeySet<String>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val lastPublishedAt = ConcurrentHashMap<String, Long>()
    private val queue = Channel<String>(Channel.UNLIMITED)
    private val consumerLock = Any()

    @Volatile
    private var consumerStarted = false

    /** Downloads currently transferring; more than one when the queue runs in parallel. */
    private val activeUris = ConcurrentHashMap.newKeySet<String>()

    private val activeCalls = ConcurrentHashMap<String, okhttp3.Call>()

    /**
     * A dedicated client for pulling audio. media3's DefaultHttpDataSource gives up after an
     * 8s read stall and never retries a dropped socket, which is what killed downloads a few
     * percent in; OkHttp keeps the connection pool warm and retries a dead route itself.
     */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = 4 })
            .connectionPool(
                ConnectionPool(
                    MAX_IDLE_CONNECTIONS,
                    CONNECTION_KEEP_ALIVE_MINUTES,
                    TimeUnit.MINUTES,
                ),
            )
            .build()
    }

    /** Queues one song; the stream url is resolved later, when the downloader runs. */
    fun enqueue(context: Context, request: OfflineRequest): Boolean {
        val uri = request.uri
        if (uri.isBlank()) return false
        if (isDownloaded(uri)) return true
        if (uri in activeUris || uri in queued) return true
        cancelled.remove(uri)
        pending[uri] = request
        queued += uri
        queue.trySend(uri)
        // Show the queued phase right away; the downloader only reports back once it runs.
        setStatus(uri, DownloadStatus(DownloadPhase.QUEUED))
        saveTrack(
            context,
            OfflineTrack(
                uri = uri,
                title = request.title,
                artists = request.artists,
                album = request.album,
                imageUrl = request.imageUrl,
                durationMs = request.durationMs,
                format = 0,
                bitrate = 0,
                quality = "",
                bytes = 0L,
                savedAt = System.currentTimeMillis(),
                offlineReady = false,
            ),
        )
        ensureConsumer(context)
        log.i("offline queued '${request.title.ifBlank { uri }}'")
        return true
    }

    fun remove(context: Context, uri: String) {
        cancel(uri)
        val file = fileFor(context, uri)
        file.delete()
        partOf(file).delete()
        prefs(context).edit().remove(KEY_LICENCE + uri).apply()
        _completed.update { it - uri }
        _progress.update { it - uri }
        _statuses.update { it - uri }
        _library.update { list -> list.filterNot { it.uri == uri } }
        lastPublishedAt.remove(uri)
        persist(context)
    }

    /** Deletes every downloaded song and its file, and drops anything still in the queue. */
    fun removeAll(context: Context) {
        cancelAll(context)
        val dir = OfflineStorage.audioDir(context)
        dir.listFiles()?.forEach { entry ->
            if (entry.isFile) entry.delete()
        }
        _library.value = emptyList()
        _completed.value = emptySet()
        _progress.value = emptyMap()
        _statuses.value = emptyMap()
        val editor = prefs(context).edit()
        prefs(context).all.keys.filter { it.startsWith(KEY_LICENCE) }.forEach(editor::remove)
        editor.apply()
        persist(context)
        log.i("offline library cleared")
    }

    /** Drops everything still waiting and aborts the one in flight. */
    fun cancelAll(context: Context) {
        queued.forEach { cancel(it) }
        activeUris.toList().forEach { cancel(it) }
        activeCalls.values.forEach { it.cancel() }
        queued.clear()
        _statuses.update { map -> map.filterValues { it.phase == DownloadPhase.DONE } }
        _progress.update { emptyMap() }
    }

    fun isDownloaded(uri: String): Boolean {
        if (uri.isBlank()) return false
        if (uri in _completed.value) return true
        return _library.value.any { it.uri == uri && it.offlineReady }
    }

    /** The downloaded audio file, or null while it is still missing or half written. */
    fun localFile(context: Context, uri: String): File? {
        if (uri.isBlank()) return null
        val file = fileFor(context, uri)
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    fun licenceKeySet(context: Context, uri: String): ByteArray? {
        val stored = prefs(context).getString(KEY_LICENCE + uri, null) ?: return null
        return runCatching { Base64.decode(stored, Base64.DEFAULT) }.getOrNull()
    }

    fun progress(uri: String): Float = _statuses.value[uri]?.percent?.coerceIn(0f, 1f) ?: 0f

    fun status(uri: String): DownloadStatus? = _statuses.value[uri]

    fun track(uri: String): OfflineTrack? = _library.value.firstOrNull { it.uri == uri }

    /** The song's name while it is still downloading, for the foreground notification. */
    fun titleOf(uri: String): String? =
        pending[uri]?.title?.takeIf { it.isNotBlank() }
            ?: _library.value.firstOrNull { it.uri == uri }?.title?.takeIf { it.isNotBlank() }

    fun load(context: Context) {
        sweepLegacyCache(context)
        val saved = read(context)
        _library.value = saved
        val ready = saved.filter { it.offlineReady }
        _completed.value = ready.map { it.uri }.toSet()
        // Keep whatever is still moving: a reload must not erase a queued or running song.
        val working = _statuses.value.filterValues { it.working }
        _statuses.value = ready.associate { it.uri to DownloadStatus(DownloadPhase.DONE, 1f) } + working
    }

    private fun ensureConsumer(context: Context) {
        val app = context.applicationContext
        synchronized(consumerLock) {
            if (consumerStarted) return
            consumerStarted = true
            log.i("offline queue started, $PARALLEL_DOWNLOADS at a time")
            scope.launch {
                try {
                    coroutineScope {
                        repeat(PARALLEL_DOWNLOADS) { worker ->
                            launch { consume(app, worker) }
                        }
                    }
                } finally {
                    // The channel is never closed, so reaching here means the loop died; let the
                    // next enqueue start a fresh one instead of queueing into a dead consumer.
                    consumerStarted = false
                    log.w("offline queue stopped")
                }
            }
        }
    }

    /** One queue worker: takes the next song and pulls it before asking for another. */
    private suspend fun consume(context: Context, worker: Int) {
        for (uri in queue) {
            queued.remove(uri)
            val request = pending[uri]
            if (request == null) {
                // Nothing to fetch any more (removed while it waited): never leave the row
                // sitting on "Queued" for ever.
                log.w("offline '${uri.takeLast(12)}' dropped, no request left")
                _statuses.update { it - uri }
                continue
            }
            if (cancelled.remove(uri)) {
                pending.remove(uri)
                _statuses.update { it - uri }
                continue
            }
            activeUris += uri
            log.i("offline[$worker] picked '${request.title.ifBlank { uri.takeLast(12) }}'")
            try {
                downloadTrack(context, request)
            } catch (t: Throwable) {
                log.e("offline '${uri.takeLast(12)}' crashed: ${t.javaClass.simpleName}", t)
                fail(uri, t.message?.takeIf { it.isNotBlank() } ?: "download crashed")
            } finally {
                activeUris -= uri
                activeCalls.remove(uri)
            }
        }
    }

    private suspend fun downloadTrack(context: Context, request: OfflineRequest) {
        val uri = request.uri
        var lastError: String? = null
        // Re-resolving the same video yields a byte-identical stream, so a later attempt can
        // carry on from the `.part` file instead of pulling the first 90% again.
        var lastStreamKey: String? = null
        log.i("offline starting '${uri.takeLast(12)}'")
        for (attempt in 1..TRACK_ATTEMPTS) {
            if (cancelled.contains(uri)) return
            if (attempt > 1) {
                log.w("offline '${uri.takeLast(12)}' retry $attempt/$TRACK_ATTEMPTS: $lastError")
                setStatus(uri, DownloadStatus(DownloadPhase.QUEUED))
                delay(TRACK_RETRY_BASE_MS shl (attempt - 2))
            }
            setStatus(uri, DownloadStatus(DownloadPhase.RUNNING, 0f))
            val stream = YtAudio.resolve(
                request.title,
                request.artists,
                request.durationMs,
            )
            if (stream == null) {
                lastError = "couldn't find on YouTube"
                continue
            }
            log.i("yt: stream ${stream.uploader} ${stream.durationSeconds}s ${stream.mime}")
            val file = fileFor(context, uri)
            val streamKey = "${stream.mime}|${stream.durationSeconds}|${stream.title}|${stream.uploader}"
            val resume = lastStreamKey == streamKey
            lastStreamKey = streamKey
            when (val result = fetch(uri, stream.url, file, resume)) {
                is FetchResult.Saved -> {
                    complete(context, request, stream, result.bytes)
                    return
                }
                is FetchResult.Failed -> lastError = result.reason
                FetchResult.Cancelled -> return
            }
        }
        fail(uri, lastError ?: "download failed")
    }

    /**
     * Mints the stream url. Bounded, because a resolve that never returns would strand the song
     * on "Queued" with no way for the user to tell that anything went wrong.
     */


    private sealed interface FetchResult {
        data class Saved(val bytes: Long) : FetchResult
        data class Failed(val reason: String) : FetchResult
        object Cancelled : FetchResult
    }

    /**
     * Downloads [url] into [file] in [CHUNK_BYTES] ranges, retrying a range up to
     * [CHUNK_ATTEMPTS] times from where the previous attempt stopped. Bytes land in a `.part`
     * file first so an interrupted song never looks finished.
     */
    private suspend fun fetch(
        uri: String,
        url: String,
        file: File,
        resume: Boolean = false,
    ): FetchResult {
        val part = partOf(file)
        part.parentFile?.mkdirs()
        var total = -1L
        var position = if (resume && part.isFile) part.length() else 0L
        var lastError = "connection dropped"
        try {
            RandomAccessFile(part, "rw").use { out ->
                if (position <= 0L) {
                    out.setLength(0)
                    position = 0L
                } else {
                    log.i("offline resuming '${uri.takeLast(12)}' at ${position}B")
                }
                transfer@ while (true) {
                    if (cancelled.contains(uri)) return FetchResult.Cancelled
                    val end = if (total > 0) {
                        minOf(position + CHUNK_BYTES - 1, total - 1)
                    } else {
                        position + CHUNK_BYTES - 1
                    }
                    var attempt = 0
                    var fullBody = false
                    while (true) {
                        if (cancelled.contains(uri)) return FetchResult.Cancelled
                        attempt++
                        val call = client.newCall(
                            Request.Builder()
                                .url(url)
                                .header("Range", "bytes=$position-$end")
                                .header("User-Agent", MEDIA_USER_AGENT)
                                .build(),
                        )
                        activeCalls[uri] = call
                        try {
                            call.execute().use { response ->
                                val code = response.code
                                if (code == 416 && position > 0L) {
                                    // The range starts past the end: the part file is already whole.
                                    total = position
                                    break@transfer
                                }
                                if (code !in 200..299) {
                                    return FetchResult.Failed(httpReason(code))
                                }
                                if (total <= 0L) {
                                    total = response.header("Content-Range")
                                        ?.substringAfter('/')?.trim()?.toLongOrNull()
                                        ?: response.body?.contentLength()?.takeIf { it > 0L }
                                        ?: -1L
                                }
                                fullBody = code == 200
                                if (fullBody && position > 0L) {
                                    // The range was ignored, so start over instead of stacking bytes.
                                    out.setLength(0)
                                    position = 0L
                                }
                                val body = response.body
                                    ?: return FetchResult.Failed("empty stream")
                                val buffer = ByteArray(READ_BUFFER_BYTES)
                                body.byteStream().use { input ->
                                    while (true) {
                                        val read = input.read(buffer)
                                        if (read < 0) break
                                        if (cancelled.contains(uri)) return FetchResult.Cancelled
                                        out.seek(position)
                                        out.write(buffer, 0, read)
                                        position += read
                                        if (total > 0L) publishProgress(uri, position, total)
                                    }
                                }
                            }
                            break
                        } catch (e: Exception) {
                            if (cancelled.contains(uri)) return FetchResult.Cancelled
                            lastError = e.message?.takeIf { it.isNotBlank() } ?: "connection dropped"
                            log.w("offline chunk @$position/$total attempt $attempt: $lastError")
                            if (attempt >= CHUNK_ATTEMPTS) return FetchResult.Failed(lastError)
                            // Back off a little so a dead route is not retried in a tight loop.
                            delay(minOf(CHUNK_BACKOFF_MAX_MS, CHUNK_BACKOFF_MS * attempt))
                        } finally {
                            activeCalls.remove(uri)
                        }
                    }
                    if (fullBody) {
                        total = position
                        break@transfer
                    }
                    if (total > 0L && position >= total) break@transfer
                    if (total <= 0L) break@transfer
                }
            }
            if (position <= 0L) return FetchResult.Failed("nothing was downloaded")
            if (total > 0L && position < total) {
                return FetchResult.Failed("stopped at $position/$total bytes")
            }
            file.delete()
            if (!part.renameTo(file)) {
                part.copyTo(file, overwrite = true)
                part.delete()
            }
            return FetchResult.Saved(file.length())
        } catch (e: Exception) {
            if (cancelled.contains(uri)) return FetchResult.Cancelled
            log.e("offline fetch failed: ${e.message}", e)
            return FetchResult.Failed(e.message?.takeIf { it.isNotBlank() } ?: "download failed")
        }
    }

    private fun publishProgress(uri: String, position: Long, total: Long) {
        val now = System.currentTimeMillis()
        val last = lastPublishedAt[uri] ?: 0L
        if (position < total && now - last < PROGRESS_MIN_INTERVAL_MS) return
        lastPublishedAt[uri] = now
        setStatus(uri, DownloadStatus(DownloadPhase.RUNNING, (position.toFloat() / total).coerceIn(0f, 1f)))
    }

    private suspend fun complete(
        context: Context,
        request: OfflineRequest,
        stream: YtStream,
        bytes: Long,
    ) {
        val uri = request.uri
        val stored = _library.value.firstOrNull { it.uri == uri }
        val track = OfflineTrack(
            uri = uri,
            title = request.title.ifBlank { stream.title.ifBlank { stored?.title.orEmpty() } },
            artists = request.artists.ifBlank { stored?.artists.orEmpty() },
            album = request.album.ifBlank { stored?.album.orEmpty() },
            imageUrl = request.imageUrl.ifBlank { stored?.imageUrl.orEmpty() },
            durationMs = (stream.durationSeconds * 1000L).takeIf { it > 0L }
                ?: request.durationMs.takeIf { it > 0L } ?: stored?.durationMs ?: 0L,
            format = 0,
            bitrate = 0,
            quality = "yt",
            bytes = bytes,
            savedAt = System.currentTimeMillis(),
            offlineReady = true,
            encrypted = false,
        )
        _library.update { list -> list.filterNot { it.uri == uri } + track }
        persist(context)
        pending.remove(uri)
        lastPublishedAt.remove(uri)
        setStatus(uri, DownloadStatus(DownloadPhase.DONE, 1f))
        _completed.update { it + uri }
        prefs(context).edit().remove(KEY_LICENCE + uri).apply()
        log.i("offline saved '${uri.takeLast(12)}' ${bytes}B (YouTube, plain)")
    }

    private fun fail(uri: String, reason: String) {
        pending.remove(uri)
        lastPublishedAt.remove(uri)
        _completed.update { it - uri }
        _statuses.update { it + (uri to DownloadStatus(DownloadPhase.FAILED, -1f, reason)) }
        _progress.update { it - uri }
        log.w("offline '${uri.takeLast(12)}' failed: $reason")
    }

    private fun cancel(uri: String) {
        cancelled += uri
        queued.remove(uri)
        pending.remove(uri)
        activeCalls.remove(uri)?.cancel()
        activeUris -= uri
    }

    private fun setStatus(uri: String, status: DownloadStatus) {
        _statuses.update { it + (uri to status) }
        if (status.percent >= 0f) {
            _progress.update { it + (uri to status.percent) }
        } else {
            _progress.update { it - uri }
        }
    }

    private fun httpReason(code: Int): String = when (code) {
        401, 403 -> "link expired"
        404 -> "not on the server"
        in 500..599 -> "server error $code"
        else -> "http $code"
    }

    private fun fileFor(context: Context, uri: String): File {
        val name = uri.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(OfflineStorage.audioDir(context), "$name.audio")
    }

    private fun partOf(file: File): File = File(file.parentFile, "${file.name}.part")

    private fun saveTrack(context: Context, track: OfflineTrack) {
        _library.update { list -> list.filterNot { it.uri == track.uri } + track }
        persist(context)
    }

    private fun persist(context: Context) {
        val array = JSONArray()
        _library.value.forEach { track ->
            array.put(
                JSONObject()
                    .put("uri", track.uri)
                    .put("title", track.title)
                    .put("artists", track.artists)
                    .put("album", track.album)
                    .put("imageUrl", track.imageUrl)
                    .put("durationMs", track.durationMs)
                    .put("format", track.format)
                    .put("bitrate", track.bitrate)
                    .put("quality", track.quality)
                    .put("bytes", track.bytes)
                    .put("savedAt", track.savedAt)
                    .put("offlineReady", track.offlineReady)
                    .put("encrypted", track.encrypted),
            )
        }
        prefs(context).edit().putString(KEY_TRACKS, array.toString()).apply()
    }

    /**
     * Reads the saved index and trusts the disk over it: a song whose file is gone (or that
     * was left behind by the old media3 cache) is not playable offline any more.
     */
    private fun read(context: Context): List<OfflineTrack> {
        val app = context.applicationContext
        val raw = prefs(app).getString(KEY_TRACKS, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val found = ArrayList<OfflineTrack>(array.length())
        for (index in 0 until array.length()) {
            val node = array.optJSONObject(index) ?: continue
            val uri = node.optString("uri")
            if (uri.isBlank()) continue
            // No file means nothing was actually kept (a stale entry, or a file left behind by
            // the old media3 cache): drop the row instead of pretending it is downloadable.
            val file = fileFor(app, uri).takeIf { it.isFile && it.length() > 0L } ?: continue
            found += OfflineTrack(
                uri = uri,
                title = node.optString("title"),
                artists = node.optString("artists"),
                album = node.optString("album"),
                imageUrl = node.optString("imageUrl"),
                durationMs = node.optLong("durationMs"),
                format = node.optInt("format"),
                bitrate = node.optInt("bitrate"),
                quality = node.optString("quality"),
                bytes = file.length(),
                savedAt = node.optLong("savedAt"),
                offlineReady = true,
                encrypted = node.optBoolean("encrypted", false),
            )
        }
        return found.sortedByDescending { it.savedAt }
    }

    private fun sweepLegacyCache(context: Context) {
        val dir = OfflineStorage.audioDir(context)
        val children = dir.listFiles() ?: return
        children.forEach { entry ->
            if (entry.isDirectory) entry.deleteRecursively()
            else if (entry.name.endsWith(".exo") || entry.name.endsWith(".uid") || entry.name.endsWith(".part")) entry.delete()
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
