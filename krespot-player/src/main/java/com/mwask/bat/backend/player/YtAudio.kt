package com.mwask.bat.backend.player

import android.content.Context
import com.mwask.bat.backend.core.log.Logs
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeSearchExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException

private val log = Logs.of(Logs.Module.PLAYER)

/** A downloadable audio stream found on YouTube, with no DRM. */
data class YtStream(
    val url: String,
    val mime: String,
    val durationSeconds: Long,
    val title: String,
    val uploader: String,
)

/**
 * Finds a song's audio on YouTube via NewPipeExtractor (the same library SpotiBat depends on)
 * instead of Spotify's encrypted CDN. The result is a plain, directly playable file.
 */
object YtAudio {

    @Volatile
    private var initialized = false

    private val initLock = Any()

    private fun init() {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            NewPipe.init(object : Downloader() {
                private val client = OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()

                @Throws(IOException::class)
                override fun execute(request: NpRequest): NpResponse {
                    val bodyBytes = request.dataToSend()
                    val body = bodyBytes?.toRequestBody("application/json".toMediaTypeOrNull())
                    val builder = Request.Builder()
                        .method(request.httpMethod(), body)
                        .url(request.url())
                    request.headers().forEach { (name, values) ->
                        values.forEach { builder.addHeader(name, it) }
                    }
                    val response = client.newCall(builder.build()).execute()
                    val responseText = response.body?.string() ?: ""
                    return NpResponse(
                        response.code,
                        response.message,
                        response.headers.toMultimap(),
                        responseText,
                        response.request.url.toString(),
                    )
                }
            })
            initialized = true
            log.i("yt audio resolver ready")
        }
    }

    /**
     * Searches YouTube Music for [title]/[artist] and returns the best matching audio stream.
     * [expectedDurationMs] is used to pick the right video among same-named uploads.
     */
    fun resolve(title: String, artist: String, expectedDurationMs: Long): YtStream? {
        init()
        val service = NewPipe.getService(0) as YoutubeService
        val query = if (artist.isNotBlank()) "$title $artist" else title
        return try {
            val sqh = service.getSearchQHFactory().fromQuery(query)
            val extractor = YoutubeSearchExtractor(service, sqh)
            extractor.fetchPage()
            val page = extractor.initialPage
            val items = page.items
                .filterIsInstance<StreamInfoItem>()
            if (items.isEmpty()) {
                log.w("yt: no items for '$query'")
                return null
            }
            val targetSec = (expectedDurationMs / 1000L).coerceAtLeast(1L)
            val chosen = items
                .sortedBy { kotlin.math.abs(it.duration - targetSec) }
                .firstOrNull()
            if (chosen == null) return null
            log.i("yt: picked '${chosen.name}' by ${chosen.uploaderName} ${chosen.duration}s (target ${targetSec}s)")

            val info = StreamInfo.getInfo(service, chosen.url)
            val audio = info.audioStreams
                .filter { it.url?.isNotBlank() == true }
                .maxByOrNull { it.averageBitrate }
            if (audio == null) {
                log.w("yt: no audio stream for '${chosen.name}'")
                return null
            }
            YtStream(
                url = audio.url!!,
                mime = audio.itagItem?.mediaFormat?.mimeType ?: "audio/webm",
                durationSeconds = info.duration,
                title = info.name ?: title,
                uploader = info.uploaderName ?: artist,
            )
        } catch (t: Throwable) {
            log.w("yt: resolve failed: ${t.javaClass.simpleName} ${t.message}")
            null
        }
    }
}
