/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.project.lol.yt

import android.net.ConnectivityManager
import android.net.Uri
import android.util.Log
import com.project.lol.innertube.NewPipeExtractor
import com.project.lol.innertube.YouTube
import com.project.lol.innertube.models.YouTubeClient
import com.project.lol.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.project.lol.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.project.lol.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.project.lol.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.project.lol.innertube.models.YouTubeClient.Companion.IOS
import com.project.lol.innertube.models.YouTubeClient.Companion.IPADOS
import com.project.lol.innertube.models.YouTubeClient.Companion.MOBILE
import com.project.lol.innertube.models.YouTubeClient.Companion.TVHTML5
import com.project.lol.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.project.lol.innertube.models.YouTubeClient.Companion.WEB
import com.project.lol.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.project.lol.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.project.lol.innertube.models.response.PlayerResponse
import com.project.lol.yt.AudioQuality
import com.project.lol.yt.cipher.CipherDeobfuscator
import com.project.lol.yt.potoken.PoTokenGenerator
import com.project.lol.yt.potoken.PoTokenResult
import com.project.lol.yt.sabr.EjsNTransformSolver
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"
    /** Max seconds to wait for signature-timestamp resolution before giving up. */
    private const val SIG_FUTURE_TIMEOUT_SEC = 10L
    /** Max seconds to wait for PoToken generation before giving up. */
    private const val POT_FUTURE_TIMEOUT_SEC = 14L

    private val httpClient = OkHttpClient.Builder()
        .proxy(YouTube.proxy)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,  // Try embedded player first for age-restricted content
        TVHTML5,
        ANDROID_VR_1_43_32,
        ANDROID_VR_1_61_48,
        ANDROID_CREATOR,
        IPADOS,
        ANDROID_VR_NO_AUTH,
        MOBILE,
        IOS,
        WEB,
        WEB_CREATOR
    )
    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
    )
    /**
     * Custom player response intended to use for playback.
     * Metadata like audioConfig and videoDetails are from [MAIN_CLIENT].
     * Format & stream can be from [MAIN_CLIENT] or [STREAM_FALLBACK_CLIENTS].
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        skipValidation: Boolean = false,
    ): Result<PlaybackData> = runCatching {
        Log.d(TAG, "=== PLAYER RESPONSE FOR PLAYBACK ===")
        Log.d(TAG, "videoId: $videoId")
        Log.d(TAG, "playlistId: $playlistId")
        Log.d(TAG, "audioQuality: $audioQuality")

        // Check if this is an uploaded/privately owned track
        val isUploadedTrack = playlistId == "MLPT" || playlistId?.contains("MLPT") == true
        Log.d(TAG, "Content type detection (preliminary):")
        Log.d(TAG, "  isUploadedTrack (from playlistId): $isUploadedTrack")

        val isLoggedIn = YouTube.cookie != null
        Log.d(TAG, "Authentication status: ${if (isLoggedIn) "LOGGED_IN" else "ANONYMOUS"}")

        // Run signature-timestamp and PoToken generation in parallel — they are
        // independent and each takes 1-3s, so overlapping them nearly halves the
        // cold-start latency. Both are blocking calls, so we use Java futures on
        // the IO executor rather than coroutine async (runCatching is non-suspend).
        val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData
        val mainClientNeedsPoToken = MAIN_CLIENT.useWebPoTokens

        val sigFuture = java.util.concurrent.CompletableFuture.supplyAsync {
            getSignatureTimestampOrNull(videoId)
        }
        val potFuture: java.util.concurrent.CompletableFuture<PoTokenResult?>? =
            if (mainClientNeedsPoToken && sessionId != null) {
                java.util.concurrent.CompletableFuture.supplyAsync {
                    Log.d(TAG, "Generating PoToken for WEB_REMIX with sessionId")
                    try {
                        poTokenGenerator.getWebClientPoToken(videoId, sessionId).also {
                            if (it != null) Log.d(TAG, "PoToken generated successfully")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "PoToken generation failed: ${e.message}", e)
                        null
                    }
                }
            } else null

        // Both signature-timestamp and PoToken generation can hang indefinitely when
        // the app is in the background (WebView JS may never call back, or NewPipe
        // extraction may stall). Use bounded waits so the resolution pipeline can
        // still fall through to clients that don't require these tokens.
        val signatureTimestamp = try {
            sigFuture.get(SIG_FUTURE_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "Signature timestamp timed out or failed: ${e.message}")
            SignatureTimestampResult(null, isAgeRestricted = false)
        }
        Log.d(TAG, "Signature timestamp: ${signatureTimestamp.timestamp}")
        var poToken: PoTokenResult? = try {
            potFuture?.get(POT_FUTURE_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "PoToken timed out or failed: ${e.message}")
            null
        }

        // If MAIN_CLIENT needs a PoToken but we couldn't get one (WebView missing, JS
        // blocked, network hostile), WEB_REMIX will return streams that 403 on play.
        // Skip it and go straight to the fallback chain.
        val skipMainClient = mainClientNeedsPoToken && poToken == null
        if (skipMainClient) {
            Log.w(TAG, "PoToken unavailable — skipping MAIN_CLIENT and using fallback chain directly")
        }

        // Try WEB_REMIX with signature timestamp and poToken (same as before)
        Log.d(TAG, "Attempting to get player response using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        var mainPlayerResponse = YouTube.player(videoId, playlistId, MAIN_CLIENT, signatureTimestamp.timestamp, poToken?.playerRequestPoToken).getOrThrow()

        // Debug uploaded track response
        if (isUploadedTrack || playlistId?.contains("MLPT") == true) {
            println("[PLAYBACK_DEBUG] Main player response status: ${mainPlayerResponse.playabilityStatus.status}")
            println("[PLAYBACK_DEBUG] Playability reason: ${mainPlayerResponse.playabilityStatus.reason}")
            println("[PLAYBACK_DEBUG] Video details: title=${mainPlayerResponse.videoDetails?.title}, videoId=${mainPlayerResponse.videoDetails?.videoId}")
            println("[PLAYBACK_DEBUG] Streaming data null? ${mainPlayerResponse.streamingData == null}")
            println("[PLAYBACK_DEBUG] Adaptive formats count: ${mainPlayerResponse.streamingData?.adaptiveFormats?.size ?: 0}")
        }

        var usedAgeRestrictedClient: YouTubeClient? = null
        val wasOriginallyAgeRestricted: Boolean

        // Check if WEB_REMIX response indicates age-restricted
        val mainStatus = mainPlayerResponse.playabilityStatus.status
        val isAgeRestrictedFromResponse = mainStatus in listOf("AGE_CHECK_REQUIRED", "AGE_VERIFICATION_REQUIRED", "LOGIN_REQUIRED", "CONTENT_CHECK_REQUIRED")
        wasOriginallyAgeRestricted = isAgeRestrictedFromResponse

        if (isAgeRestrictedFromResponse && isLoggedIn) {
            // Age-restricted: use WEB_CREATOR directly (no NewPipe needed from here)
            Log.d(TAG, "Age-restricted detected, using WEB_CREATOR")
            Log.i(TAG, "Age-restricted: using WEB_CREATOR for videoId=$videoId")
            val creatorResponse = YouTube.player(videoId, playlistId, WEB_CREATOR, null, null).getOrNull()
            if (creatorResponse?.playabilityStatus?.status == "OK") {
                Log.d(TAG, "WEB_CREATOR works for age-restricted content")
                mainPlayerResponse = creatorResponse
                usedAgeRestrictedClient = WEB_CREATOR
            }
        }

        // If we still don't have a valid response, throw

        val audioConfig = mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = mainPlayerResponse.videoDetails
        val playbackTracking = mainPlayerResponse.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        val retryMainPlayerResponse: PlayerResponse? = if (usedAgeRestrictedClient != null) mainPlayerResponse else null

        // Check current status
        val currentStatus = mainPlayerResponse.playabilityStatus.status
        val isAgeRestricted = currentStatus in listOf("AGE_CHECK_REQUIRED", "AGE_VERIFICATION_REQUIRED", "LOGIN_REQUIRED", "CONTENT_CHECK_REQUIRED")

        if (isAgeRestricted) {
            Log.d(TAG, "Content is still age-restricted (status: $currentStatus), will try fallback clients")
            Log.i(TAG, "Age-restricted content detected: videoId=$videoId, status=$currentStatus")
        }

        // Check if this is a privately owned track (uploaded song)
        val isPrivateTrack = mainPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

        // For private tracks: use TVHTML5 (index 1) with PoToken + n-transform
        // For age-restricted: skip main client, start with fallbacks
        // For normal content: standard order
        val startIndex = when {
            isPrivateTrack -> 1  // TVHTML5
            isAgeRestricted -> 0
            skipMainClient -> 0  // MAIN_CLIENT streams unplayable without PoToken
            else -> -1
        }

        for (clientIndex in (startIndex until STREAM_FALLBACK_CLIENTS.size)) {
            // reset for each client
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            // decide which client to use for streams and load its player response
            val client: YouTubeClient
            if (clientIndex == -1) {
                // try with streams from main client first (use retry response if available)
                client = MAIN_CLIENT
                streamPlayerResponse = retryMainPlayerResponse ?: mainPlayerResponse
                Log.d(TAG, "Trying stream from MAIN_CLIENT: ${client.clientName}")
            } else {
                // after main client use fallback clients
                client = STREAM_FALLBACK_CLIENTS[clientIndex]
                Log.d(TAG, "Trying fallback client ${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}: ${client.clientName}")

                if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) {
                    // skip client if it requires login but user is not logged in
                    Log.d(TAG, "Skipping client ${client.clientName} - requires login but user is not logged in")
                    continue
                }

                Log.d(TAG, "Fetching player response for fallback client: ${client.clientName}")
                // Only pass poToken for clients that support it
                val clientPoToken = if (client.useWebPoTokens) poToken?.playerRequestPoToken else null
                // Skip signature timestamp for age-restricted (faster), use it for normal content
                val clientSigTimestamp = if (wasOriginallyAgeRestricted) null else signatureTimestamp.timestamp
                streamPlayerResponse =
                    YouTube.player(videoId, playlistId, client, clientSigTimestamp, clientPoToken).getOrNull()
            }

            // YouTube content substitution guard: a client with a mismatched
            // session can return a playable response for a DIFFERENT video (the
            // classic "plays the wrong song" bug). Never accept streams whose
            // videoId doesn't match what we asked for.
            val returnedVideoId = streamPlayerResponse?.videoDetails?.videoId
            if (returnedVideoId != null && returnedVideoId != videoId) {
                Log.w(TAG, 
                    "Client ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName} " +
                        "returned WRONG video: $returnedVideoId != $videoId — skipping",
                )
                continue
            }

            // process current client response
            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                Log.d(TAG, "Player response status OK for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")

                // Skip NewPipe for age-restricted content (NewPipe doesn't use our auth)
                val responseToUse = if (wasOriginallyAgeRestricted) {
                    Log.d(TAG, "Skipping NewPipe for age-restricted content")
                    streamPlayerResponse
                } else {
                    // Try to get streams using newPipePlayer method
                    val newPipeResponse = YouTube.newPipePlayer(videoId, streamPlayerResponse)
                    newPipeResponse ?: streamPlayerResponse
                }

                format =
                    findFormat(
                        responseToUse,
                        audioQuality,
                        connectivityManager,
                    )

                if (format == null) {
                    Log.d(TAG, "No suitable format found for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    continue
                }

                Log.d(TAG, "Format found: ${format.mimeType}, bitrate: ${format.bitrate}")

                streamUrl = findUrlOrNull(format, videoId, responseToUse, skipNewPipe = wasOriginallyAgeRestricted)
                if (streamUrl == null) {
                    Log.d(TAG, "Stream URL not found for format")
                    continue
                }

                // Apply n-transform for throttle parameter handling
                val currentClient = if (clientIndex == -1) {
                    usedAgeRestrictedClient ?: MAIN_CLIENT
                } else {
                    STREAM_FALLBACK_CLIENTS[clientIndex]
                }

                // Check if this is a privately owned track
                val isPrivatelyOwnedTrack = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"
                val musicVideoType = streamPlayerResponse.videoDetails?.musicVideoType

                Log.d(TAG, "=== N-TRANSFORM DECISION ===")
                Log.d(TAG, "Content type analysis:")
                Log.d(TAG, "  musicVideoType: $musicVideoType")
                Log.d(TAG, "  isPrivatelyOwnedTrack: $isPrivatelyOwnedTrack")
                Log.d(TAG, "  isUploadedTrack (from playlistId): $isUploadedTrack")
                Log.d(TAG, "  wasOriginallyAgeRestricted: $wasOriginallyAgeRestricted")
                Log.d(TAG, "Client analysis:")
                Log.d(TAG, "  currentClient: ${currentClient.clientName}")
                Log.d(TAG, "  useWebPoTokens: ${currentClient.useWebPoTokens}")

                // Apply n-transform and PoToken for web clients OR for private tracks (including TVHTML5)
                val needsNTransform = currentClient.useWebPoTokens ||
                    currentClient.clientName in listOf("WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5") ||
                    isPrivatelyOwnedTrack

                Log.d(TAG, "N-transform decision:")
                Log.d(TAG, "  needsNTransform: $needsNTransform")
                Log.d(TAG, "  Reason: useWebPoTokens=${currentClient.useWebPoTokens}, " +
                    "clientInList=${currentClient.clientName in listOf("WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5")}, " +
                    "isPrivatelyOwnedTrack=$isPrivatelyOwnedTrack")

                if (needsNTransform) {
                    try {
                        Log.d(TAG, "Applying n-transform to stream URL...")
                        Log.d(TAG, "  Original URL length: ${streamUrl.length}")
                        Log.d(TAG, "  Original URL preview: ${streamUrl.take(100)}...")

                        val originalUrl = streamUrl
                        // Use CipherDeobfuscator for n-transform (fixed implementation)
                        streamUrl = CipherDeobfuscator.transformNParamInUrl(streamUrl)

                        Log.d(TAG, "  Transformed URL length: ${streamUrl.length}")
                        Log.d(TAG, "  URL changed: ${originalUrl != streamUrl}")

                        // Append pot= parameter with streaming data poToken
                        val needsPoToken = (currentClient.useWebPoTokens || isPrivatelyOwnedTrack) && poToken?.streamingDataPoToken != null
                        Log.d(TAG, "PoToken decision:")
                        Log.d(TAG, "  needsPoToken: $needsPoToken")
                        Log.d(TAG, "  hasStreamingDataPoToken: ${poToken?.streamingDataPoToken != null}")

                        if (needsPoToken) {
                            Log.d(TAG, "Appending pot= parameter to stream URL")
                            val separator = if ("?" in streamUrl) "&" else "?"
                            streamUrl = "${streamUrl}${separator}pot=${Uri.encode(poToken.streamingDataPoToken)}"
                            Log.d(TAG, "  Final URL length (with pot): ${streamUrl.length}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "N-transform or pot append failed: ${e.message}", e)
                        Log.e(TAG, "Stack trace: ${e.stackTraceToString().take(500)}")
                        // Continue with original URL
                    }
                } else {
                    Log.d(TAG, "Skipping n-transform (not required for this client/content)")
                }

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    Log.d(TAG, "Stream expiration time not found")
                    continue
                }

                Log.d(TAG, "Stream expires in: $streamExpiresInSeconds seconds")

                // Check if this is a privately owned track (uploaded song)
                val isPrivatelyOwned = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                if (clientIndex == STREAM_FALLBACK_CLIENTS.size - 1 || isPrivatelyOwned) {
                    /** skip [validateStatus] for last client or private tracks */
                    if (isPrivatelyOwned) {
                        Log.d(TAG, "Skipping validation for privately owned track: ${currentClient.clientName}")
                        println("[PLAYBACK_DEBUG] Using stream without validation for PRIVATELY_OWNED_TRACK")
                    } else {
                        Log.d(TAG, "Using last fallback client without validation: ${STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    }
                    Log.i(TAG, "Playback: client=${currentClient.clientName}, videoId=$videoId, private=$isPrivatelyOwned")
                    break
                }

                if (skipValidation || validateStatus(streamUrl)) {
                    // working stream found
                    Log.d(TAG, "Stream validated successfully with client: ${currentClient.clientName}")
                    // Log for release builds
                    Log.i(TAG, "Playback: client=${currentClient.clientName}, videoId=$videoId")
                    break
                } else {
                    Log.d(TAG, "Stream validation failed for client: ${currentClient.clientName}")
                }
            } else {
                Log.d(TAG, "Player response status not OK: ${streamPlayerResponse?.playabilityStatus?.status}, reason: ${streamPlayerResponse?.playabilityStatus?.reason}")
            }
        }

        if (streamPlayerResponse == null) {
            Log.e(TAG, "Bad stream player response - all clients failed")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: All clients failed for uploaded track videoId=$videoId")
            }
            throw Exception("Bad stream player response")
        }

        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val errorReason = streamPlayerResponse.playabilityStatus.reason
            Log.e(TAG, "Playability status not OK: $errorReason")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: Playability not OK for uploaded track - status=${streamPlayerResponse.playabilityStatus.status}, reason=$errorReason")
            }
            throw Exception(errorReason)
        }

        if (streamExpiresInSeconds == null) {
            Log.e(TAG, "Missing stream expire time")
            throw Exception("Missing stream expire time")
        }

        if (format == null) {
            Log.e(TAG, "Could not find format")
            throw Exception("Could not find format")
        }

        if (streamUrl == null) {
            Log.e(TAG, "Could not find stream url")
            throw Exception("Could not find stream url")
        }

        Log.d(TAG, "Successfully obtained playback data with format: ${format.mimeType}, bitrate: ${format.bitrate}")
        if (isUploadedTrack) {
            println("[PLAYBACK_DEBUG] SUCCESS: Got playback data for uploaded track - format=${format.mimeType}, streamUrl=${streamUrl.take(100)}...")
        }
        PlaybackData(
            audioConfig,
            videoDetails,
            playbackTracking,
            format,
            streamUrl,
            streamExpiresInSeconds,
        )
    }.onFailure { e ->
        println("[PLAYBACK_DEBUG] EXCEPTION during playback for videoId=$videoId: ${e::class.simpleName}: ${e.message}")
        e.printStackTrace()
    }
    /**
     * Simple player response intended to use for metadata only.
     * Stream URLs of this response might not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        Log.d(TAG, "Fetching metadata-only player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        return YouTube.player(videoId, playlistId, client = WEB_REMIX) // ANDROID_VR does not work with history
            .onSuccess { Log.d(TAG, "Successfully fetched metadata") }
            .onFailure { Log.e(TAG, "Failed to fetch metadata", it) }
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): PlayerResponse.StreamingData.Format? {
        Log.d(TAG, "Finding format with audioQuality: $audioQuality, network metered: ${connectivityManager.isActiveNetworkMetered}")

        val format = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.isOriginal }
            ?.maxByOrNull {
                it.bitrate * when (audioQuality) {
                    AudioQuality.AUTO -> if (connectivityManager.isActiveNetworkMetered) -1 else 1
                    AudioQuality.HIGH -> 1
                    AudioQuality.LOW -> -1
                } + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0) // prefer opus stream
            }

        if (format != null) {
            Log.d(TAG, "Selected format: ${format.mimeType}, bitrate: ${format.bitrate}")
        } else {
            Log.d(TAG, "No suitable audio format found")
        }

        return format
    }
    /**
     * Checks if the stream url returns a successful status.
     *
     * Why the leniency: on slow mobile networks HEAD can time out or be rejected by edge
     * CDNs (405/403/410 on HEAD while GET works). If we treat those as "failed" we skip a
     * stream that actually plays. Rules here:
     *  - 2xx → valid
     *  - 405/403/410 → treat as valid (HEAD may be restricted; ExoPlayer will GET)
     *  - IOException (timeout/reset) → treat as valid; ExoPlayer has its own retry and
     *    killing the client here just cascades us down the fallback chain for no reason
     *  - other HTTP codes (4xx/5xx) → invalid
     */
    private fun validateStatus(url: String): Boolean {
        Log.d(TAG, "Validating stream URL status")
        try {
            val requestBuilder = okhttp3.Request.Builder()
                .head()
                .url(url)

            YouTube.cookie?.let { cookie ->
                requestBuilder.addHeader("Cookie", cookie)
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            response.close()
            val code = response.code
            val accepted = response.isSuccessful || code == 405 || code == 403 || code == 410
            Log.d(TAG, "Stream URL validation: code=$code accepted=$accepted")
            return accepted
        } catch (e: java.io.IOException) {
            // Network timeout / reset while HEAD-probing. The stream URL itself may still
            // be fine — let ExoPlayer attempt GET rather than burning a fallback client.
            Log.w(TAG, "Stream URL HEAD probe failed (IO); accepting optimistically", e)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Stream URL validation failed with exception", e)
            reportException(e)
        }
        return false
    }
    data class SignatureTimestampResult(
        val timestamp: Int?,
        val isAgeRestricted: Boolean
    )

    private fun getSignatureTimestampOrNull(videoId: String): SignatureTimestampResult {
        Log.d(TAG, "Getting signature timestamp for videoId: $videoId")
        val result = NewPipeExtractor.getSignatureTimestamp(videoId)
        return result.fold(
            onSuccess = { timestamp ->
                Log.d(TAG, "Signature timestamp obtained: $timestamp")
                SignatureTimestampResult(timestamp, isAgeRestricted = false)
            },
            onFailure = { error ->
                val isAgeRestricted = error.message?.contains("age-restricted", ignoreCase = true) == true ||
                    error.cause?.message?.contains("age-restricted", ignoreCase = true) == true
                if (isAgeRestricted) {
                    Log.d(TAG, "Age-restricted content detected from NewPipe")
                    Log.i(TAG, "Age-restricted detected early via NewPipe: videoId=$videoId")
                } else {
                    Log.e(TAG, "Failed to get signature timestamp", error)
                    reportException(error)
                }
                SignatureTimestampResult(null, isAgeRestricted)
            }
        )
    }

    private suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
        skipNewPipe: Boolean = false
    ): String? {
        Log.d(TAG, "Finding stream URL for format: ${format.mimeType}, videoId: $videoId, skipNewPipe: $skipNewPipe")

        // First check if format already has a URL
        if (!format.url.isNullOrEmpty()) {
            Log.d(TAG, "Using URL from format directly")
            return format.url
        }

        // Try custom cipher deobfuscation for signatureCipher formats
        val signatureCipher = format.signatureCipher ?: format.cipher
        if (!signatureCipher.isNullOrEmpty()) {
            Log.d(TAG, "Format has signatureCipher, using custom deobfuscation")
            val customDeobfuscatedUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
            if (customDeobfuscatedUrl != null) {
                Log.d(TAG, "Stream URL obtained via custom cipher deobfuscation")
                return customDeobfuscatedUrl
            }
            Log.d(TAG, "Custom cipher deobfuscation failed")
        }

        // Skip NewPipe for age-restricted content
        if (skipNewPipe) {
            Log.d(TAG, "Skipping NewPipe methods for age-restricted content")
            return null
        }

        // Try to get URL using NewPipeExtractor signature deobfuscation
        val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
        if (deobfuscatedUrl != null) {
            Log.d(TAG, "Stream URL obtained via NewPipe deobfuscation")
            return deobfuscatedUrl
        }

        // Fallback: try to get URL from StreamInfo
        Log.d(TAG, "Trying StreamInfo fallback for URL")
        val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
        if (streamUrls.isNotEmpty()) {
            val streamUrl = streamUrls.find { it.first == format.itag }?.second
            if (streamUrl != null) {
                Log.d(TAG, "Stream URL obtained from StreamInfo")
                return streamUrl
            }

            // If exact itag not found, try to find any audio stream
            val audioStream = streamUrls.find { urlPair ->
                playerResponse.streamingData?.adaptiveFormats?.any {
                    it.itag == urlPair.first && it.isAudio
                } == true
            }?.second

            if (audioStream != null) {
                Log.d(TAG, "Audio stream URL obtained from StreamInfo (different itag)")
                return audioStream
            }
        }

        Log.e(TAG, "Failed to get stream URL")
        return null
    }

    fun forceRefreshForVideo(videoId: String) {
        Log.d(TAG, "Force refreshing for videoId: $videoId")
    }
}
