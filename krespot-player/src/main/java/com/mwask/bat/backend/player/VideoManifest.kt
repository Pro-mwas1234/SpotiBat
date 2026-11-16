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
import com.mwask.bat.backend.core.log.Logs
import com.mwask.bat.backend.core.net.Net
import org.json.JSONArray
import org.json.JSONObject

private val log = Logs.of(Logs.Module.SOURCE)

private const val METADATA_PATH = "metadata/4/track/%s?market=from_token"

private const val MANIFEST_PATH = "manifests/v9/json/sources/%s/options/supports_drm"

private const val DEFAULT_SEGMENT_SECONDS = 4

private const val MEDIA_TYPE_VIDEO = "VIDEO"

data class VideoProfile(
    val id: Int,
    val mime: String,
    val codecs: String,
    val bitrate: Int,
    val width: Int,
    val height: Int,
    val fileType: String,
    val keyId: String,
) {
    val isAudio: Boolean get() = mime.startsWith("audio/")

    val isVideo: Boolean get() = mime.startsWith("video/")
}

data class VideoEncryption(
    val keySystem: String,
    val scheme: String,
    val licenseEndpoint: String,
    val pssh: String,
    val keyId: String,
)

data class VideoManifest(
    val sourceGid: String,
    val encodingId: String,
    val segmentSeconds: Int,
    val durationMs: Long,
    val baseUrls: List<String>,
    val initTemplate: String,
    val segmentTemplate: String,
    val audioProfile: VideoProfile?,
    val videoProfiles: List<VideoProfile>,
    val encryption: VideoEncryption?,
) {
    val segmentCount: Int
        get() {
            val seconds = durationMs / 1000L
            return ((seconds + segmentSeconds - 1) / segmentSeconds).toInt().coerceAtLeast(1)
        }

    fun segmentUrl(baseUrl: String, profile: VideoProfile, segment: Int): String {
        val timestamp = segment.toLong() * segmentSeconds
        return baseUrl + segmentTemplate
            .replace("{{profile_id}}", profile.id.toString())
            .replace("{{file_type}}", profile.fileType)
            .replace("{{segment_timestamp}}", timestamp.toString())
    }

    fun initUrl(baseUrl: String, profile: VideoProfile): String = baseUrl + initTemplate
        .replace("{{profile_id}}", profile.id.toString())
        .replace("{{file_type}}", profile.fileType)

    fun bestVideoProfile(preferredHeight: Int = Int.MAX_VALUE): VideoProfile? = videoProfiles
        .filter { it.height <= preferredHeight }
        .maxByOrNull { it.height }
        ?: videoProfiles.maxByOrNull { it.height }
}

data class VideoSource(
    val trackUri: String,
    val sourceGid: String,
    val manifest: VideoManifest,
)

object VideoManifests {

    /** Resolved sources of recent tracks, so the switch needs no round trips. */
    private val resolvedCache = object : LinkedHashMap<String, VideoSource>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VideoSource>): Boolean = size > 3
    }

    /**
     * The audio media manifest already names the music video entity of the
     * track: media[uri].video_version_uri. No GraphQL round trip needed.
     */
    suspend fun videoVersionUri(context: Context, spclient: String, trackUri: String): String? {
        if (!trackUri.startsWith("spotify:track:")) return null
        val host = spclientHost(spclient)
        val url = "https://$host/track-playback/v1/media/$trackUri?manifestFileFormat=file_ids_mp4&manifest"
        val response = Net.get(url, PlayerHeaders.of(context), tag = "media")
        val json = response?.takeIf { it.ok }?.json()
        if (json == null) {
            log.w("video version: media failed ${response?.code ?: "no response"}")
            return null
        }
        val entry = json.optJSONObject("media")?.optJSONObject(trackUri)
        val uri = entry?.text("video_version_uri").orEmpty()
        if (!uri.startsWith("spotify:track:")) {
            log.i("video version: '$trackUri' has no video_version_uri")
            return null
        }
        log.i("video version '$trackUri' -> '$uri'")
        return uri
    }

    suspend fun resolve(context: Context, spclient: String, trackUri: String): VideoSource? {
        if (trackUri.startsWith("spotify:episode:")) return resolveEpisode(context, spclient, trackUri)
        if (!trackUri.startsWith("spotify:track:")) return null
        synchronized(resolvedCache) { resolvedCache[trackUri] }?.let { cached ->
            log.i("video: '${trackUri.takeLast(12)}' served from the prefetch cache")
            return cached
        }
        val gid = trackUri.removePrefix("spotify:track:")
        val hex = runCatching { gidBase62ToHex(gid) }.getOrNull()
        if (hex.isNullOrBlank()) {
            log.w("video: '$trackUri' is not a base62 id")
            return null
        }
        val metadataUrl = "https://${spclientHost(spclient)}/" + METADATA_PATH.format(hex)
        val headers = PlayerHeaders.of(context) + ("Accept" to "application/json")
        val meta = Net.get(metadataUrl, headers, tag = "video-meta")
        val metaJson = meta?.takeIf { it.ok }?.json()
        if (metaJson == null) {
            log.w("video: metadata failed ${meta?.code ?: "no response"}")
            return null
        }
        if (metaJson.text("media_type") != MEDIA_TYPE_VIDEO) {
            log.i("video: '${gid}' is media_type=${metaJson.text("media_type")}, not a video entity")
            return null
        }
        val original = metaJson.optJSONArray("original_video")?.optJSONObject(0)
            ?.text("gid").orEmpty()
        if (original.isBlank()) {
            log.w("video: '${gid}' has no original_video gid")
            return null
        }
        val manifest = manifest(context, spclient, original) ?: return null
        log.i("video: '${gid}' -> source $original ${manifest.videoProfiles.size} video profiles " +
            "${manifest.durationMs}ms")
        val source = VideoSource(trackUri, original, manifest)
        synchronized(resolvedCache) { resolvedCache[trackUri] = source }
        return source
    }

    private val episodeVideoIds = object : LinkedHashMap<String, String?>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>): Boolean = size > 16
    }

    fun knownEpisodeVideoId(episodeUri: String): String? = synchronized(episodeVideoIds) {
        episodeVideoIds[episodeUri]
    }

    fun episodeVideoKnown(episodeUri: String): Boolean = synchronized(episodeVideoIds) {
        episodeVideoIds.containsKey(episodeUri)
    }

    suspend fun episodeVideoId(context: Context, spclient: String, episodeUri: String): String? {
        synchronized(episodeVideoIds) {
            if (episodeVideoIds.containsKey(episodeUri)) return episodeVideoIds[episodeUri]
        }
        val host = spclientHost(spclient)
        val url = "https://$host/track-playback/v1/media/$episodeUri?manifestFileFormat=file_ids_mp4&manifest"
        val response = Net.get(url, PlayerHeaders.of(context), tag = "media")
        val json = response?.takeIf { it.ok }?.json()
        if (json == null) {
            log.w("episode video: media failed ${response?.code ?: "no response"}")
            return null
        }
        val manifest = json.optJSONObject("media")?.optJSONObject(episodeUri)
            ?.optJSONObject("item")
        val videoId = manifest?.text("video_id")?.takeIf { it.isNotBlank() }
            ?: manifest?.optJSONObject("manifest")?.optJSONArray("manifest_ids_video")
                ?.optJSONObject(0)?.text("file_id").orEmpty()
        synchronized(episodeVideoIds) { episodeVideoIds[episodeUri] = videoId.takeIf { it.isNotBlank() } }
        if (videoId.isBlank()) log.i("episode video: '$episodeUri' has no video file")
        return videoId.takeIf { it.isNotBlank() }
    }

    private suspend fun resolveEpisode(context: Context, spclient: String, episodeUri: String): VideoSource? {
        synchronized(resolvedCache) { resolvedCache[episodeUri] }?.let { cached ->
            log.i("video: '${episodeUri.takeLast(12)}' served from the prefetch cache")
            return cached
        }
        val videoId = episodeVideoId(context, spclient, episodeUri) ?: return null
        val manifest = manifest(context, spclient, videoId) ?: return null
        log.i("video: episode '${episodeUri.takeLast(12)}' -> source $videoId " +
                "${manifest.videoProfiles.size} video profiles ${manifest.durationMs}ms")
        val source = VideoSource(episodeUri, videoId, manifest)
        synchronized(resolvedCache) { resolvedCache[episodeUri] = source }
        return source
    }

    suspend fun manifest(context: Context, spclient: String, sourceGid: String): VideoManifest? {
        val host = spclientHost(spclient)
        val url = "https://$host/" + MANIFEST_PATH.format(sourceGid)
        val response = Net.get(url, PlayerHeaders.of(context), tag = "video-manifest")
        val json = response?.takeIf { it.ok }?.json()
        if (json == null) {
            log.w("video manifest failed ${response?.code ?: "no response"} ${response?.body?.take(160).orEmpty()}")
            return null
        }
        val content = json.optJSONArray("contents")?.optJSONObject(0) ?: return null
        val baseUrls = json.optJSONArray("base_urls")?.toStringList().orEmpty()
        if (baseUrls.isEmpty()) {
            log.w("video manifest has no base_urls")
            return null
        }
        val initTemplate = json.text("initialization_template")
        val segmentTemplate = json.text("segment_template")
        if (initTemplate.isBlank() || segmentTemplate.isBlank()) {
            log.w("video manifest has no segment templates")
            return null
        }
        val profiles = content.optJSONArray("profiles").orEmpty().mapNotNull { node ->
            val mime = node.text("mime_type")
            val codecs = when {
                node.text("video_codec").isNotBlank() -> node.text("video_codec")
                node.text("audio_codec").isNotBlank() -> node.text("audio_codec")
                else -> return@mapNotNull null
            }
            VideoProfile(
                id = node.optInt("id", -1),
                mime = mime,
                codecs = codecs,
                bitrate = node.optInt("max_bitrate", node.optInt("video_bitrate", node.optInt("audio_bitrate", 0))),
                width = node.optInt("video_width", 0),
                height = node.optInt("video_height", 0),
                fileType = node.text("file_type").ifBlank { "mp4" },
                keyId = node.text("key_id"),
            )
        }.filter { it.id >= 0 }
        val audioProfile = profiles.firstOrNull { it.isAudio }
        val videoProfiles = profiles.filter { it.isVideo }.sortedByDescending { it.height }
        if (audioProfile == null || videoProfiles.isEmpty()) {
            log.w("video manifest profiles audio=${audioProfile != null} video=${videoProfiles.size}")
            return null
        }
        val encryption = content.optJSONArray("encryption_infos").orEmpty()
            .firstOrNull { it.text("key_system") == "widevine" }
            ?.let { node ->
                VideoEncryption(
                    keySystem = node.text("key_system"),
                    scheme = node.text("encryption_scheme"),
                    licenseEndpoint = node.text("license_server_endpoint"),
                    pssh = node.text("encryption_data"),
                    keyId = node.text("key_id"),
                )
            }
        val durationMs = content.optLong("end_time_millis", 0L) - content.optLong("start_time_millis", 0L)
        if (durationMs <= 0L) {
            log.w("video manifest has no duration")
            return null
        }
        return VideoManifest(
            sourceGid = sourceGid,
            encodingId = content.text("encoding_id"),
            segmentSeconds = content.optInt("segment_length", DEFAULT_SEGMENT_SECONDS),
            durationMs = durationMs,
            baseUrls = baseUrls,
            initTemplate = initTemplate,
            segmentTemplate = segmentTemplate,
            audioProfile = audioProfile,
            videoProfiles = videoProfiles,
            encryption = encryption,
        )
    }

    fun licenseUrl(spclient: String, endpoint: String): String =
        "https://${spclientHost(spclient)}${endpoint.ifBlank { "/widevine-license/v1/video/license" }}"

    private fun spclientHost(spclient: String): String = spclient.ifBlank { "guc3-spclient.spotify.com" }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }

    private fun JSONArray.orEmpty(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    private val BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    fun gidBase62ToHex(id: String): String? {
        if (id.length != 22) return null
        var value = java.math.BigInteger.ZERO
        val base = java.math.BigInteger.valueOf(62L)
        for (ch in id) {
            val index = BASE62.indexOf(ch)
            if (index < 0) return null
            value = value.multiply(base).add(java.math.BigInteger.valueOf(index.toLong()))
        }
        val hex = value.toString(16).padStart(32, '0')
        return hex
    }
}
