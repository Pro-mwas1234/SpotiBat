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

package com.mwask.bat.backend.connect.command

import org.json.JSONObject

object ContentType {
    const val TRACK = "TRACK"
    const val EPISODE = "EPISODE"
    const val AD = "AD"
    const val CHAPTER = "CHAPTER"
}

private const val AD_PREFIX = "spotify:ad:"

private const val REPLACE_STATE = "replace_state"

private const val TRANSFER = "transfer"

private const val DIRECT_FORMAT = "file_urls_external"

data class AudioFile(
    val fileId: String,
    val format: Int,
    val bitrate: Int,
    val quality: String,
    val gainDb: Double,
)

data class DirectFile(
    val url: String,
    val bitrate: Int,
    val impressions: List<String>,
)

data class CommandTrack(
    val uri: String,
    val name: String,
    val artists: String,
    val album: String,
    val albumUri: String,
    val durationMs: Long,
    val imageUrl: String,
    val explicit: Boolean,
    val contentType: String,
    val files: List<AudioFile>,
    val dualFiles: List<AudioFile>,
    val directFiles: List<DirectFile>,
    val contextUri: String = "",
) {
    val isAd: Boolean get() = contentType == ContentType.AD || uri.startsWith(AD_PREFIX)

    val isDirect: Boolean get() = files.isEmpty() && directFiles.isNotEmpty()

    fun best(): AudioFile? = files.maxByOrNull { it.bitrate }

    fun bestDirect(): DirectFile? = directFiles.maxByOrNull { it.bitrate }

    val reportFormat: String
        get() = if (isDirect || files.isEmpty()) DIRECT_FORMAT else best()?.format?.toString().orEmpty()

    val reportBitrate: Int
        get() = if (isDirect || files.isEmpty()) bestDirect()?.bitrate ?: 160000 else best()?.bitrate ?: 128000

    val reportQuality: String
        get() = if (isDirect || files.isEmpty()) "HIGH" else best()?.quality.orEmpty()
}

data class CommandState(
    val stateId: String,
    val trackIndex: Int,
    val trackUid: String,
    val initialPositionMs: Long,
    val disallowSeeking: Boolean,
    val advanceIndex: Int?,
) {
    val isFiller: Boolean get() = stateId.startsWith("filler_")
}

data class PlaybackCommand(
    val stateMachineId: String,
    val stateIndex: Int,
    val paused: Boolean,
    val contextDescription: String,
    val playbackSessionId: String,
    val tracks: List<CommandTrack>,
    val states: List<CommandState>,
    val endpoint: String = "",
    val type: String = "",
    val seekToMs: Long = -1L,
) {
    val startAtMs: Long get() = if (seekToMs >= 0L) seekToMs else (currentState?.initialPositionMs ?: 0L)

    val isControl: Boolean get() = endpoint.isNotBlank() && endpoint != TRANSFER

    val isTransfer: Boolean get() = endpoint == TRANSFER

    val isReplaceState: Boolean get() = type == REPLACE_STATE || endpoint == TRANSFER
    val currentState: CommandState? get() = states.getOrNull(stateIndex)

    val currentTrack: CommandTrack? get() = currentState?.let { tracks.getOrNull(it.trackIndex) }

    val stateId: String get() = currentState?.stateId.orEmpty()

    fun following(): PlaybackCommand? {
        val next = currentState?.advanceIndex ?: return null
        if (next !in states.indices) return null
        if (next == stateIndex) return null
        return copy(stateIndex = next)
    }

    val isAd: Boolean get() = currentTrack?.isAd == true

    val disallowSeeking: Boolean get() = currentState?.disallowSeeking ?: isAd
}

private fun JSONObject.text(name: String): String =
    if (isNull(name)) "" else optString(name, "")

private fun JSONObject.number(name: String): Double =
    if (isNull(name)) 0.0 else optDouble(name, 0.0)

private fun parseFile(node: JSONObject): AudioFile = AudioFile(
    fileId = node.text("file_id"),
    format = node.text("format").toIntOrNull() ?: 0,
    bitrate = node.optInt("bitrate", 0),
    quality = node.text("audio_quality"),
    gainDb = node.number("gain_db"),
)

private fun parseFiles(node: JSONObject?, name: String): List<AudioFile> {
    val array = node?.optJSONArray(name) ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let { parseFile(it) }
    }
}

private fun parseDirect(node: JSONObject?, name: String): List<DirectFile> {
    val array = node?.optJSONArray(name) ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val impressions = item.optJSONArray("impression_urls")?.let { list ->
            (0 until list.length()).mapNotNull { list.optString(it).ifBlank { null } }
        }.orEmpty()
        DirectFile(
            url = item.text("file_url"),
            bitrate = item.optInt("bitrate", 0),
            impressions = impressions,
        )
    }
}

private fun parseTrack(node: JSONObject): CommandTrack {
    val metadata = node.optJSONObject("metadata") ?: JSONObject()
    val authors = metadata.optJSONArray("authors")?.let { array ->
        (0 until array.length()).mapNotNull { array.optJSONObject(it)?.text("name") }
    }.orEmpty()
    val image = metadata.optJSONArray("images")?.optJSONObject(0)?.text("url").orEmpty()
    val manifest = node.optJSONObject("manifest")
    return CommandTrack(
        uri = metadata.text("uri"),
        name = metadata.text("name"),
        artists = authors.joinToString(", "),
        album = metadata.text("group_name"),
        albumUri = metadata.text("group_uri"),
        durationMs = metadata.optLong("duration", 0L),
        imageUrl = image,
        explicit = metadata.optBoolean("is_explicit", false),
        contentType = node.text("content_type"),
        files = parseFiles(manifest, "file_ids_mp4"),
        dualFiles = parseFiles(manifest, "file_ids_mp4_dual"),
        directFiles = parseDirect(manifest, "file_urls_mp3"),
        contextUri = metadata.text("context_uri"),
    )
}

private fun parseState(node: JSONObject): CommandState = CommandState(
    stateId = node.text("state_id"),
    trackIndex = node.optInt("track", 0),
    trackUid = node.text("track_uid"),
    initialPositionMs = if (node.isNull("initial_playback_position")) 0L else node.optLong("initial_playback_position", 0L),
    disallowSeeking = node.optBoolean("disallow_seeking", false),
    advanceIndex = node.optJSONObject("transitions")
        ?.optJSONObject("advance")
        ?.takeIf { !it.isNull("state_index") }
        ?.optInt("state_index"),
)

object CommandParser {

    fun parse(payload: String): PlaybackCommand? {
        val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        val command = root.optJSONObject("command")
        val endpoint = (command ?: root).text("endpoint")
        val machine = root.optJSONObject("state_machine")
        val tracks = machine?.optJSONArray("tracks")?.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::parseTrack) }
        }.orEmpty()
        val states = machine?.optJSONArray("states")?.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::parseState) }
        }.orEmpty()
        if (machine != null && (tracks.isEmpty() || states.isEmpty())) return null
        if (machine == null && endpoint.isBlank()) return null
        val reference = root.optJSONObject("updated_state_ref") ?: root.optJSONObject("state_ref")
        val attributes = machine?.optJSONObject("attributes")
        val contextMetadata = root.optJSONObject("context_metadata")
        return PlaybackCommand(
            stateMachineId = machine?.text("state_machine_id").orEmpty(),
            stateIndex = reference?.optInt("state_index", 0) ?: 0,
            paused = reference?.optBoolean("paused", false) ?: (endpoint == "pause"),
            contextDescription = contextMetadata?.text("context_description").orEmpty(),
            playbackSessionId = attributes?.text("playback_session_id").orEmpty(),
            tracks = tracks,
            states = states,
            endpoint = endpoint,
            type = root.text("type"),
            seekToMs = if (root.isNull("seek_to")) -1L else root.optLong("seek_to", -1L),
        )
    }
}
