package com.mwask.bat

import android.content.Context
import com.mwask.bat.backend.connect.dealer.DealerHost
import com.mwask.bat.backend.core.auth.EncoreAuth
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.net.ClientToken
import com.mwask.bat.backend.core.net.Net
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val log = Logs.of(Logs.Module.TRACKS)

/**
 *
 * The canvas for a track is resolved from the Pathfinder GraphQL api (the
 * same `canvas` persisted query the web player uses). The answer carries a
 * direct url plus a `fileId` for the encoded source video on the segments
 * cdn; real clients feed that id to the manifests/v9 api, which answers the
 * full rendition ladder (mp4, ts and webm variants from 180p up to 1080p)
 * together with signed init/segment url templates.
 *
 * The direct url points at a canvaz.scdn.co single file whose resolution
 * depends on what was uploaded for that canvas (720p or 1080p in practice),
 * so the ladder is preferred: we take the best `mp4` profile it offers at
 * or under 720p, which is where the web player's own canvas ABR settles —
 * the 1080p top rung decodes at ~9 Mbps into a card-sized view and
 * visibly stutters. The manifest is scoped per encoding, so its
 * init url derives numbered segment urls exactly like the legacy segmented
 * ones did:
 *
 *   .../profiles/5/inits/mp4?fauth=...   (init)
 *   .../profiles/5/0.mp4?fauth=...       (media segments)
 *   .../profiles/5/4.mp4?fauth=...
 *
 * When the manifest is unreachable we fall back to the direct file, and when
 * even that url is segmented (an older delivery format without a manifest
 * stride) the +1/+4 probe stepper still applies.
 *
 * We download the init plus the available segments and stitch them into one
 * playable file; a concatenated fMP4 is a valid progressive mp4. The CDN
 * fragments, however, can carry their master's media timeline — non-zero
 * starts, sub-second holes and multi-hour jumps between fragments have all
 * been seen — and a progressive player renders those literally: the canvas
 * stalls at every hole and freezes outright on the jumps. The stitch is
 * therefore normalized at the fragment level: every movie-fragment's tfdt
 * (baseMediaDecodeTime) is rewritten so the first fragment starts at 0 and
 * each later one starts exactly when the previous one's samples end. Sample
 * bytes, order and — critically — the trun composition offsets that carry
 * B-frame reordering are untouched, so the presentation order the decoder
 * reconstructs stays exactly the CDN's (a previous sample-level rewrite
 * rearranged PTS along the decode order and stuttered every B-framed
 * canvas, no matter the resolution).
 *
 * The segments also arrive fronted by their own sidx (segment index) boxes,
 * and those cannot stay in the stitch: media3's FragmentedMp4Extractor
 * emits a fresh seek map for every top-level sidx it parses, so a stitched
 * file's timeline duration flips between one segment's length and the
 * merged length while the loader walks it — and every flip that lands under
 * a renderer already reading past the phantom boundary forces a seek back
 * to the current position with the renderers torn down, i.e. a codec flush,
 * over and over. They are stripped below (see stripSegmentIndexes).
 */
object CanvasKit {

    private const val PATHFINDER = "https://api-partner.spotify.com/pathfinder/v2/query"
    private const val CANVAS_HASH = "575138ab27cd5c1b3e54da54d0a7cc8d85485402de26340c2145f0f6bb5e7a9f"
    private const val INIT_MARKER = "/inits/mp4"
    private const val MAX_SEGMENTS = 16
    private const val MAX_BYTES = 12 * 1024 * 1024
    private const val MAX_CACHED = 16

    /** Playback ceiling: the ladder tops out at 1080p and the resolution
     * picker in the player decides how high we climb. */
    private const val PLAYBACK_RESOLUTION_CAP = 1080

    /** Hard ceiling for degenerate ladders with no rung at or under the
     * playback cap. */
    private const val MAX_RESOLUTION = 1080

    /** A canvas to download: its init url plus the segment stride when known. */
    private class Target(val initUrl: String, val stride: Int?)

    /** Resolve the best available canvas url for a track, null when the track has none. */
    suspend fun canvasUrl(
        context: Context,
        trackUri: String,
        preferredHeight: Int = PLAYBACK_RESOLUTION_CAP,
    ): String? =
        resolve(context, trackUri, preferredHeight)?.initUrl

    /**
     * Ask the canvas query, then upgrade to the manifest ladder: the direct
     * canvaz file varies in quality, the ladder offers a capped pick at the
     * preferred height.
     */
    private suspend fun resolve(
        context: Context,
        trackUri: String,
        preferredHeight: Int,
    ): Target? {
        val id = trackUri.substringAfterLast(":")
        if (!trackUri.startsWith("spotify:track:") || id.isBlank()) return null
        val token = EncoreAuth.token(context)
        if (token.isBlank()) return null
        val headers = mutableMapOf(
            "Authorization" to "Bearer $token",
            "Accept" to "application/json",
            "app-platform" to "WebPlayer",
            "spotify-app-version" to "9.1.92.3058",
            "Origin" to "https://open.spotify.com",
            "Referer" to "https://open.spotify.com/",
            "User-Agent" to Net.DESKTOP_UA,
        )
        ClientToken.get(context).takeIf { it.isNotBlank() }?.let { headers["client-token"] = it }
        val body = JSONObject()
            .put("operationName", "canvas")
            .put("variables", JSONObject().put("trackUri", trackUri))
            .put(
                "extensions",
                JSONObject().put(
                    "persistedQuery",
                    JSONObject().put("version", 1).put("sha256Hash", CANVAS_HASH),
                ),
            )
            .toString()
        var response = Net.postJson(PATHFINDER, body, headers, "canvas") ?: return null
        if (response.code == 403) {
            log.w("canvas $id 403, retrying with a fresh client-token")
            ClientToken.get(context, force = true).takeIf { it.isNotBlank() }?.let {
                headers["client-token"] = it
            }
            response = Net.postJson(PATHFINDER, body, headers, "canvas") ?: return null
        }
        if (!response.ok) {
            log.i("canvas $id none (${response.code})")
            return null
        }
        val canvas = response.json()
            ?.optJSONObject("data")
            ?.optJSONObject("trackUnion")
            ?.optJSONObject("canvas")
            ?: return null
        val url = canvas.optString("url")
        if (!url.startsWith("http")) return null
        log.i("canvas $id -> ${url.substringBefore('?')}")
        val fileId = canvas.optString("fileId")
        if (fileId.isNotBlank()) {
            manifestTarget(context, fileId, headers, preferredHeight)?.let { return it }
        }
        return Target(url, null)
    }

    /**
     * manifests/v9 knows every rendition of a canvas source. Pick the best
     * mp4 profile at or under the preferred height and rebuild its init url
     * from the signed template. The template tokens are scoped to the whole
     * encoding, so segment urls follow from the init url by name like the
     * legacy segmented ones.
     */
    private suspend fun manifestTarget(
        context: Context,
        fileId: String,
        headers: Map<String, String>,
        preferredHeight: Int,
    ): Target? {
        val spclient = DealerHost.resolve(context)?.second ?: return null
        val response = Net.get(
            "https://$spclient/manifests/v9/json/sources/$fileId/options/supports_drm",
            headers,
            "canvas/manifest",
        )
        if (response == null || !response.ok) {
            log.w("canvas $fileId manifest miss (${response?.code ?: "network"})")
            return null
        }
        val root = response.json() ?: return null
        val initTemplate = root.optString("initialization_template")
        val baseUrl = root.optJSONArray("base_urls")?.optString(0).orEmpty()
        if (!initTemplate.startsWith("v1/") || baseUrl.isBlank()) return null
        val encodingId = initTemplate.substringAfter("/encodings/", "").substringBefore("/")
        val contents = root.optJSONArray("contents") ?: return null
        val want = preferredHeight.coerceIn(1, PLAYBACK_RESOLUTION_CAP)
        var best: JSONObject? = null
        var fallback: JSONObject? = null
        var stride = 0
        for (i in 0 until contents.length()) {
            val content = contents.optJSONObject(i) ?: continue
            if (content.optString("encoding_id") != encodingId) continue
            if ((content.optJSONArray("encryption_infos")?.length() ?: 0) > 0) continue
            stride = content.optInt("segment_length", 0)
            val profiles = content.optJSONArray("profiles") ?: continue
            for (j in 0 until profiles.length()) {
                val profile = profiles.optJSONObject(j) ?: continue
                if (profile.optString("file_type") != "mp4") continue
                val resolution = profile.optInt("video_resolution", 0)
                if (resolution <= 0) continue
                if (resolution <= want) {
                    if (best == null ||
                        resolution > best.optInt("video_resolution")
                    ) {
                        best = profile
                    }
                } else if (resolution <= MAX_RESOLUTION &&
                    (fallback == null ||
                        resolution < fallback.optInt("video_resolution"))
                ) {
                    fallback = profile
                }
            }
        }
        val chosen = best ?: fallback ?: return null
        val initUrl = baseUrl.trimEnd('/') + "/" + initTemplate.trimStart('/')
            .replace("{{profile_id}}", chosen.optString("id"))
            .replace("{{file_type}}", "mp4")
        log.i(
            "canvas $fileId manifest ${chosen.optInt("video_resolution")}p " +
                "profile ${chosen.optString("id")} stride $stride",
        )
        return Target(initUrl, stride.takeIf { it > 0 })
    }

    /** Download and stitch the canvas of a track, returning a playable local file path. */
    suspend fun download(
        context: Context,
        trackUri: String,
        preferredHeight: Int,
    ): String? = withContext(Dispatchers.IO) {
        val id = trackUri.substringAfterLast(":")
        val dir = File(context.cacheDir, "canvas")
        val cache = File(dir, "v8-$id-$preferredHeight.mp4")
        if (cache.isFile && cache.length() > 0L) return@withContext cache.absolutePath
        dir.listFiles()?.filter { it.isFile && (it.name.startsWith("v6-") || it.name.startsWith("v7-")) }?.forEach {
            if (it.delete()) log.i("canvas cache dropped ${it.name}")
        }
        dir.listFiles()?.filter { it.isFile && it.name.startsWith("v8-$id-") && it.name != cache.name }?.forEach {
            if (it.delete()) log.i("canvas cache dropped ${it.name}")
        }

        val target = resolve(context, trackUri, preferredHeight) ?: return@withContext null
        val initUrl = target.initUrl
        val headers = mapOf(
            "User-Agent" to Net.DESKTOP_UA,
            "Referer" to "https://open.spotify.com/",
        )

        val init = Net.blob(initUrl, headers, "canvas/init")?.takeIf { it.ok }?.bytes ?: run {
            log.w("canvas $id init failed")
            return@withContext null
        }

        val parts = ArrayList<ByteArray>(MAX_SEGMENTS + 1)
        parts += init
        var total = init.size

        val segmented = initUrl.substringBefore('?').endsWith(INIT_MARKER)
        if (segmented) {
            val base = initUrl.substringBefore('?').removeSuffix(INIT_MARKER)
            val query = initUrl.substringAfter('?', "")
            if (target.stride != null) {
                var at = 0
                while (parts.size - 1 < MAX_SEGMENTS && total < MAX_BYTES) {
                    val segment = "$base/$at.mp4" + if (query.isBlank()) "" else "?$query"
                    val blob = Net.blob(segment, headers, "canvas/seg") ?: break
                    if (!blob.ok || blob.size <= 0) break
                    parts += blob.bytes
                    total += blob.size
                    at += target.stride
                }
            } else {
            var index = 0
            var step = 0 // 0 = numbering still unknown, probe both strides
            while (parts.size - 1 < MAX_SEGMENTS && total < MAX_BYTES) {
                val candidates = when (step) {
                    1 -> listOf(index + 1)
                    4 -> listOf(index + 4)
                    else -> listOf(index + 1, index + 4)
                }
                var advanced = -1
                for (candidate in candidates) {
                    val segment = "$base/$candidate.mp4" + if (query.isBlank()) "" else "?$query"
                    val blob = Net.blob(segment, headers, "canvas/seg") ?: continue
                    if (blob.ok && blob.size > 0) {
                        parts += blob.bytes
                        total += blob.size
                        advanced = candidate
                        break
                    }
                }
                if (advanced < 0) break
                step = advanced - index
                index = advanced
                }
            }
            if (parts.size < 2) {
                log.w("canvas $id has no media segments, skipping")
                return@withContext null
            }
        }

        runCatching {
            dir.mkdirs()
            var stripped = 0
            var size = 0
            val cleaned = ArrayList<ByteArray>(parts.size)
            for (part in parts) {
                val pair = stripSegmentIndexes(part)
                stripped += pair.second
                cleaned += pair.first
                size += pair.first.size
            }
            val stitched = ByteArray(size)
            var at = 0
            for (part in cleaned) {
                part.copyInto(stitched, at)
                at += part.size
            }
            val rebased = rebaseFragmentTimelines(stitched)
            cache.outputStream().use { it.write(stitched) }
            trim(dir)
            val note = when {
                rebased > 0 -> ", $rebased fragment(s) rebased"
                rebased == 0 -> "" // plain single file, nothing to rebase
                else -> ", raw timeline kept" // box layout not recognized
            } + if (stripped > 0) ", $stripped sidx stripped" else ""
            log.i("canvas $id stitched ${parts.size} part(s) ${size}B -> ${cache.name}$note")
            cache.absolutePath
        }.onFailure {
            log.w("canvas $id write failed: ${it.message}")
        }.getOrNull()
    }

    /** tfhd flags whose extra fields must be skipped while walking the box. */
    private const val TFHD_BASE_DATA_OFFSET = 0x000001
    private const val TFHD_SAMPLE_DESC_INDEX = 0x000002
    private const val TFHD_DEFAULT_DURATION = 0x000008

    /** trun flags marking which per-sample fields every entry carries. */
    private const val TRUN_DATA_OFFSET = 0x000001
    private const val TRUN_FIRST_SAMPLE_FLAGS = 0x000004
    private const val TRUN_SAMPLE_DURATION = 0x000100
    private const val TRUN_SAMPLE_SIZE = 0x000200
    private const val TRUN_SAMPLE_FLAGS = 0x000400
    private const val TRUN_SAMPLE_CTS = 0x000800

    /** One parsed ISO-BMFF box: start offset, total size, header size, fourcc. */
    private class Box(val start: Int, val size: Int, val header: Int, val type: String)

    /** One parsed trun: sample count and, when present, the summed
     *  per-sample durations (negative when the box relies on defaults). */
    private class Trun(val count: Int, val durations: Long)

    private fun u32(data: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0..3) v = (v shl 8) or (data[off + i].toLong() and 0xFF)
        return v
    }

    private fun u64(data: ByteArray, off: Int): Long =
        (u32(data, off) shl 32) or u32(data, off + 4)

    /** Boxes of a container spanning [from, until); throws when malformed. */
    private fun boxes(data: ByteArray, from: Int, until: Int): ArrayList<Box> {
        val out = ArrayList<Box>()
        var off = from
        while (off + 8 <= until) {
            val rawSize = u32(data, off)
            var size: Int
            var header = 8
            when {
                rawSize == 1L -> {
                    if (off + 16 > until) throw IllegalStateException("truncated largesize")
                    val large = u64(data, off + 8)
                    if (large > until - off) throw IllegalStateException("largesize past container")
                    size = large.toInt()
                    header = 16
                }
                rawSize == 0L -> size = until - off
                else -> {
                    if (rawSize > Int.MAX_VALUE) throw IllegalStateException("oversized box")
                    size = rawSize.toInt()
                }
            }
            if (size < header || off + size > until) {
                throw IllegalStateException("box past its container")
            }
            out.add(Box(off, size, header, String(data, off + 4, 4, Charsets.US_ASCII)))
            off += size
        }
        return out
    }

    private fun writeUint(data: ByteArray, offset: Int, length: Int, value: Long) {
        for (i in 0 until length) {
            data[offset + i] = (value ushr ((length - 1 - i) * 8)).toByte()
        }
    }

    /** Copy [part] minus its top-level sidx boxes, returning the bytes and
     *  how many were dropped; anything unparsable comes back untouched.
     *
     *  Why: the CDN fronts every media segment with its own sidx, and a naive
     *  stitch therefore carries several of them. FragmentedMp4Extractor
     *  (media3 1.11.1) emits a fresh SeekMap for each top-level sidx it
     *  parses, so the timeline's duration flips from one segment's length to
     *  the merged length as the loader walks the file (in-app logs show
     *  player.duration alternating 4000/8000 ms on a 2-segment canvas). Every
     *  flip routes through MediaPeriodQueue.updateQueuedPeriods'
     *  duration-change branch, and because the video renderer reads ahead
     *  past the phantom first-segment boundary, the reading period counts as
     *  beyond the new duration: the queue reports the reading period altered,
     *  ExoPlayer seeks back to the current position with the renderers forced
     *  off — a MediaCodec flush — and the re-created repeat period re-parses
     *  the file, flipping the duration again. The cycle spins at ~300 ms and
     *  burns most frames (render 109 / dropped 262 in one capture).
     *
     *  Stripped, the extractor emits exactly one Unseekable seek map whose
     *  duration comes from the moov track headers — which the v6 rewrite keeps
     *  true to the stitch — so the timeline settles once and looping advances
     *  without a single extra flush. */
    private fun stripSegmentIndexes(part: ByteArray): Pair<ByteArray, Int> {
        return try {
            var dropped = 0
            var covered = 0
            val out = ByteArrayOutputStream(part.size)
            for (box in boxes(part, 0, part.size)) {
                covered += box.size
                if (box.type == "sidx") {
                    dropped++
                } else {
                    out.write(part, box.start, box.size)
                }
            }
            if (dropped == 0 || covered != part.size) part to 0
            else out.toByteArray() to dropped
        } catch (e: Exception) {
            part to 0 // not worth losing the canvas over a malformed index
        }
    }

    /**
     * Rewrite every movie fragment's tfdt (baseMediaDecodeTime) onto a clean
     * 0-based, gap-free timeline: the first fragment of a track starts at 0
     * and every later one starts exactly when the previous fragment's samples
     * end, accumulated from the trun/tfhd/trex sample durations. Only those
     * bytes change — sample order, sizes and the trun composition offsets
     * (B-frame ctts) are untouched, so the decoder still reconstructs the
     * CDN's presentation order. Non-zero starts, sub-second holes between
     * segments and huge master-timeline jumps all disappear, unlike the old
     * sample-level rewrite, which reordered PTS along the decode order and
     * destroyed the pacing of every B-framed canvas.
     *
     * v6: the moov that rides along from the CDN init still describes the
     * MASTER's timeline — mvhd/tkhd/mdhd durations of zero or master-scale
     * garbage — while the samples now span 0..total. Players that trust the
     * header (ExoPlayer builds its playback timeline, and therefore its
     * repeat/loop behaviour, from it) end the period at the wrong spot:
     * REPEAT_MODE_ONE then hammers the renderer with internal seeks, every
     * reset flushes the codec, and the canvas plays as a ~3 fps slideshow
     * (decoder logs: FLUSHING/FLUSHED/STARTED cycling every ~330 ms, 70% of
     * frames skipped as decode-only). The true stitched duration, already
     * accumulated per track while rebasing, is therefore also written back
     * into those three header fields so the container finally describes the
     * media it actually carries.
     *
     * Returns the number of fragments rebased, or -1 when the box layout is
     * not what we expect — the caller then keeps the raw stitch, which still
     * plays correctly whenever the CDN timeline happens to be sound.
     */
    private fun rebaseFragmentTimelines(data: ByteArray): Int {
        return try {
            val trex = HashMap<Int, Long>()
            val nextBase = HashMap<Int, Long>()
            val timings = ArrayList<TrakTiming>()
            var rebased = 0
            var mvhdTimescale = 0L
            var mvhdDurationOffset = -1
            var mvhdDurationLength = 0
            for (box in boxes(data, 0, data.size)) {
                if (box.type == "moov") {
                    for (child in boxes(data, box.start + box.header, box.start + box.size)) {
                        when (child.type) {
                            "mvhd" -> {
                                if (child.size < 16) throw IllegalStateException("short mvhd")
                                when (val version = data[child.start + 8].toInt() and 0xFF) {
                                    0 -> {
                                        if (child.size < 32) throw IllegalStateException("short mvhd v0")
                                        mvhdTimescale = u32(data, child.start + 20)
                                        mvhdDurationOffset = child.start + 24
                                        mvhdDurationLength = 4
                                    }
                                    1 -> {
                                        if (child.size < 44) throw IllegalStateException("short mvhd v1")
                                        mvhdTimescale = u32(data, child.start + 28)
                                        mvhdDurationOffset = child.start + 32
                                        mvhdDurationLength = 8
                                    }
                                    else -> throw IllegalStateException("mvhd version $version")
                                }
                            }
                            "trak" -> trakTiming(data, child)?.let { timings.add(it) }
                            "mvex" -> {
                                for (trexBox in boxes(data, child.start + child.header, child.start + child.size)) {
                                    if (trexBox.type == "trex" && trexBox.size >= 24) {
                                        trex[u32(data, trexBox.start + 12).toInt()] =
                                            u32(data, trexBox.start + 20)
                                    }
                                }
                            }
                        }
                    }
                } else if (box.type == "moof") {
                    for (traf in boxes(data, box.start + box.header, box.start + box.size)) {
                        if (traf.type == "traf") rebased += rebaseTraf(data, traf, trex, nextBase)
                    }
                }
            }
            if (rebased > 0) {
                rewriteHeaderDurations(
                    data, timings, nextBase,
                    mvhdTimescale, mvhdDurationOffset, mvhdDurationLength,
                )
            }
            rebased
        } catch (e: Exception) {
            log.w("canvas fragment rebase skipped: ${e.message}")
            -1
        }
    }

    /** One parsed trak header: where its tkhd/mdhd duration fields live, the
     * track's own mdhd timescale, and the track id the tfdt rebase keys on. */
    private class TrakTiming(
        val trackId: Int,
        val trackTimescale: Long,
        val mdhdDurationOffset: Int,
        val mdhdDurationLength: Int,
        val tkhdDurationOffset: Int,
        val tkhdDurationLength: Int,
    )

    /** Locate a trak's duration fields (tkhd + mdhd) and its track id; null
     *  when the layout is too unusual to edit safely. */
    private fun trakTiming(data: ByteArray, trak: Box): TrakTiming? {
        var trackId = -1
        var tkhdDurationOffset = -1
        var tkhdDurationLength = 0
        var trackTimescale = 0L
        var mdhdDurationOffset = -1
        var mdhdDurationLength = 0
        for (child in boxes(data, trak.start + trak.header, trak.start + trak.size)) {
            when (child.type) {
                "tkhd" -> {
                    if (child.size < 16) throw IllegalStateException("short tkhd")
                    when (val version = data[child.start + 8].toInt() and 0xFF) {
                        0 -> {
                            if (child.size < 32) throw IllegalStateException("short tkhd v0")
                            trackId = u32(data, child.start + 20).toInt()
                            tkhdDurationOffset = child.start + 28
                            tkhdDurationLength = 4
                        }
                        1 -> {
                            if (child.size < 44) throw IllegalStateException("short tkhd v1")
                            trackId = u32(data, child.start + 28).toInt()
                            tkhdDurationOffset = child.start + 36
                            tkhdDurationLength = 8
                        }
                        else -> throw IllegalStateException("tkhd version $version")
                    }
                }
                "mdia" -> {
                    for (mdia in boxes(data, child.start + child.header, child.start + child.size)) {
                        if (mdia.type != "mdhd") continue
                        if (mdia.size < 16) throw IllegalStateException("short mdhd")
                        when (val version = data[mdia.start + 8].toInt() and 0xFF) {
                            0 -> {
                                if (mdia.size < 28) throw IllegalStateException("short mdhd v0")
                                trackTimescale = u32(data, mdia.start + 20)
                                mdhdDurationOffset = mdia.start + 24
                                mdhdDurationLength = 4
                            }
                            1 -> {
                                if (mdia.size < 40) throw IllegalStateException("short mdhd v1")
                                trackTimescale = u32(data, mdia.start + 28)
                                mdhdDurationOffset = mdia.start + 32
                                mdhdDurationLength = 8
                            }
                            else -> throw IllegalStateException("mdhd version $version")
                        }
                    }
                }
            }
        }
        if (trackId < 0 || mdhdDurationOffset < 0 || trackTimescale <= 0) return null
        return TrakTiming(
            trackId, trackTimescale,
            mdhdDurationOffset, mdhdDurationLength,
            tkhdDurationOffset, tkhdDurationLength,
        )
    }

    /** Write the true per-track stitched duration into mdhd (track timescale)
     *  and tkhd/mvhd (movie timescale), using the totals the tfdt rebase
     *  accumulated. tkhd/mvhd are skipped when their fields were not found. */
    private fun rewriteHeaderDurations(
        data: ByteArray,
        timings: List<TrakTiming>,
        nextBase: Map<Int, Long>,
        mvhdTimescale: Long,
        mvhdDurationOffset: Int,
        mvhdDurationLength: Int,
    ) {
        for (timing in timings) {
            val totalTicks = nextBase[timing.trackId] ?: continue
            if (totalTicks <= 0) continue
            writeUint(data, timing.mdhdDurationOffset, timing.mdhdDurationLength, totalTicks)
            if (timing.tkhdDurationOffset >= 0 && mvhdTimescale > 0) {
                writeUint(
                    data, timing.tkhdDurationOffset, timing.tkhdDurationLength,
                    totalTicks * mvhdTimescale / timing.trackTimescale,
                )
            }
        }
        if (mvhdDurationOffset >= 0 && mvhdTimescale > 0 && timings.isNotEmpty()) {
            val first = timings.first()
            val totalTicks = nextBase[first.trackId] ?: return
            if (totalTicks > 0) {
                writeUint(
                    data, mvhdDurationOffset, mvhdDurationLength,
                    totalTicks * mvhdTimescale / first.trackTimescale,
                )
            }
        }
    }

    /** Rewrite one traf's tfdt onto the accumulated timeline; throws when
     *  the fragment's structure or durations cannot be resolved. */
    private fun rebaseTraf(
        data: ByteArray,
        traf: Box,
        trex: Map<Int, Long>,
        nextBase: HashMap<Int, Long>,
    ): Int {
        var trackId = -1
        var tfhdDefaultDuration = 0L
        var tfdtOffset = -1
        var tfdtLength = 0
        val truns = ArrayList<Trun>()
        for (child in boxes(data, traf.start + traf.header, traf.start + traf.size)) {
            when (child.type) {
                "tfhd" -> {
                    if (child.size < 16) throw IllegalStateException("short tfhd")
                    val flags = (u32(data, child.start + 8) and 0xFFFFFF).toInt()
                    trackId = u32(data, child.start + 12).toInt()
                    var cursor = child.start + 16
                    if (flags and TFHD_BASE_DATA_OFFSET != 0) cursor += 8
                    if (flags and TFHD_SAMPLE_DESC_INDEX != 0) cursor += 4
                    if (flags and TFHD_DEFAULT_DURATION != 0) {
                        if (cursor + 4 > child.start + child.size) {
                            throw IllegalStateException("tfhd default duration past box")
                        }
                        tfhdDefaultDuration = u32(data, cursor)
                    }
                }
                "tfdt" -> {
                    if (child.size < 12) throw IllegalStateException("short tfdt")
                    when (val version = data[child.start + 8].toInt() and 0xFF) {
                        0 -> {
                            if (child.size < 16) throw IllegalStateException("short tfdt v0")
                            tfdtOffset = child.start + 12
                            tfdtLength = 4
                        }
                        1 -> {
                            if (child.size < 20) throw IllegalStateException("short tfdt v1")
                            tfdtOffset = child.start + 12
                            tfdtLength = 8
                        }
                        else -> throw IllegalStateException("tfdt version $version")
                    }
                }
                "trun" -> {
                    if (child.size < 16) throw IllegalStateException("short trun")
                    val flags = (u32(data, child.start + 8) and 0xFFFFFF).toInt()
                    val count = u32(data, child.start + 12).toInt()
                    var cursor = child.start + 16
                    if (flags and TRUN_DATA_OFFSET != 0) cursor += 4
                    if (flags and TRUN_FIRST_SAMPLE_FLAGS != 0) cursor += 4
                    var stride = 0
                    for (marker in intArrayOf(
                        TRUN_SAMPLE_DURATION, TRUN_SAMPLE_SIZE,
                        TRUN_SAMPLE_FLAGS, TRUN_SAMPLE_CTS,
                    )) {
                        if (flags and marker != 0) stride += 4
                    }
                    if (flags and TRUN_SAMPLE_DURATION != 0) {
                        var total = 0L
                        repeat(count) {
                            if (cursor + 4 > child.start + child.size) {
                                throw IllegalStateException("trun samples past box")
                            }
                            total += u32(data, cursor)
                            cursor += stride
                        }
                        truns.add(Trun(count, total))
                    } else {
                        truns.add(Trun(count, -1L))
                    }
                }
            }
        }
        if (trackId < 0 || tfdtOffset < 0) {
            throw IllegalStateException("traf without tfhd/tfdt")
        }
        var fragmentDuration = 0L
        for (trun in truns) {
            if (trun.durations >= 0) {
                fragmentDuration += trun.durations
            } else {
                val default = if (tfhdDefaultDuration > 0) tfhdDefaultDuration
                    else trex[trackId] ?: 0L
                if (default <= 0) throw IllegalStateException("no sample durations to accumulate")
                fragmentDuration += default * trun.count
            }
        }
        if (fragmentDuration <= 0) throw IllegalStateException("zero fragment duration")
        val base = nextBase[trackId] ?: 0L
        if (tfdtLength == 4 && base > 0xFFFFFFFFL) {
            throw IllegalStateException("rebased tfdt overflows v0 field")
        }
        writeUint(data, tfdtOffset, tfdtLength, base)
        nextBase[trackId] = base + fragmentDuration
        return 1
    }

    /** Keep the canvas cache small: drop the oldest files beyond [MAX_CACHED]. */
    private fun trim(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= MAX_CACHED) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_CACHED)
            .forEach { file ->
                if (file.delete()) log.i("canvas cache dropped ${file.name}")
            }
    }
}
