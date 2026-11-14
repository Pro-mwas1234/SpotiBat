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

package com.quackify.backend.core.net

import android.content.Context
import com.quackify.backend.core.auth.EncoreAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import com.quackify.backend.core.log.Logs

private val log = Logs.of(Logs.Module.PATHFINDER)

object Pathfinder {

    private const val ENDPOINT = "https://api-partner.spotify.com/pathfinder/v1/query"
    private const val ENDPOINT_POST = "https://api-partner.spotify.com/pathfinder/v2/query"
    private const val HOME_OP = "home"
    private const val PREFS = "quackify_pathfinder"
    private const val KEY_OPS = "ops"
    private const val KEY_ATTEMPT = "last_attempt"
    private const val RETRY_AFTER_MS = 30 * 60 * 1000L
    private val FALLBACK_CODES = setOf(404, 405, 501)

    private val SEEDS = mapOf(
        "searchTopResultsList" to
            "dd78eaff943eba629ed70ee25517b9cea0dcaa41193e2592ae2727660b21892c",
        "searchSuggestions" to
            "f244254b94c0e824d458ac216e0f8406a73f2f69a9ff52831a2f78fa774ff6ce",
        "searchTracks" to
            "b02683192a98dde7966b5e6655a79eeb62713eab703eda9902c932818dd52751",
        "searchArtists" to
            "7bf95d754fdbe32c8b161fbbe54d1ae50974900df4dce4c8f1afcbcad153224d",
        "searchAlbums" to
            "202cb3305e31e5a0767ba7925f28bd728cf8f8b0217e6da43909056071cd70e9",
        "searchPlaylists" to
            "d520014e748f9ea44f7707d8df1819867ac1205e8b7f3e28f22fe5fc858921b1",
        "searchUsers" to
            "8f358dd82e62f61dd4ceaa9f8cd0889e644c9b707f1b724fbfb356a757cb7e5a",
        "searchPodcasts" to
            "0195d9f61b43606d490bca64c3456e3593528cea6cc05c7e822c7c42beed0f4e",
        "searchFullEpisodes" to
            "397a4e93a21d140d088cfd26641133c5146a2f5f08e420654868153e720f5571",
        "searchGenres" to
            "9e1c0e056c46239dd1956ea915b988913c87c04ce3dadccdb537774490266f46",
        "recentSearches" to
            "ece96d7b82c820fb9938dd1df50dee3e53737d808c3883e3d01c467c423601b0",
        "homeSection" to
            "5fb7da7a03ee2776c0670856609cb85a189a3cecc36cc160b91cde79063b9cf1",
        "areEntitiesInLibrary" to
            "134337999233cc6fdd6b1e6dbf94841409f04a946c5c7b744b09ba0dfe5a85ed",
        "decorateContextTracks" to
            "383de00240775c39a6afe0b1055dc562b2a3930894201f9762f3fc32a74971c7",
        "isCurated" to
            "e4ed1f91a2cc5415befedb85acf8671dc1a4bf3ca1a5b945a6386101a22e28a6",
        "addToLibrary" to
            "896ebcb47815681340860d121cb5d494e157e2a78d3950385cd54e0393c67148",
        "removeFromLibrary" to
            "896ebcb47815681340860d121cb5d494e157e2a78d3950385cd54e0393c67148",
        "pinLibraryItem" to
            "896ebcb47815681340860d121cb5d494e157e2a78d3950385cd54e0393c67148",
        "unpinLibraryItem" to
            "896ebcb47815681340860d121cb5d494e157e2a78d3950385cd54e0393c67148",
        "libraryV3" to
            "390c78e5b951029bad359785e69b07b536a509c581cbcd0aded5e5067f187455",
        "fetchLibraryTracks" to
            "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240",
        "fetchPlaylist" to
            "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf",
        "playlistPermissions" to
            "e43d1d35f231cf289c23c9d9c489f4a4f502e4eda09839c530608f107b6556b8",
        "browseAll" to
            "dbd8b55e09a58afc52eab438bc228ba28fd72ac2f2148c6c26354980e4579001",
        "browsePage" to
            "f5c4e6d668f5716464a231c1cc8b22c1cbf6ad68b09929fd7de813a30581298b",
        "getAlbum" to
            "0b6ce3511e1b02e4a40fa95440f992acbe85080eb308af9194322d708fc1914f",
        "queryAlbumTracks" to
            "0b6ce3511e1b02e4a40fa95440f992acbe85080eb308af9194322d708fc1914f",
        "queryAlbumMerch" to
            "3ef44ed6f17be67299538fe77faffab4075aeaf9e1085f10fc835592266711b5",
        "queryTrackCreditsGroupedModal" to
            "f135fb9be58a72d041ab5d214d817021a272405d883860468e2627afb01a3ca9",
        "queryArtistOverview" to
            "9f8134ef565e78621f1e1793555bd6633c5ac144ae0f89604ed3ae3f80b3c8e6",
        "queryArtistDiscographyOverview" to
            "5e07d323febb57b4a56a42abbf781490e58764aa45feb6e3dc0591564fc56599",
        "queryArtistDiscographyAll" to
            "5e07d323febb57b4a56a42abbf781490e58764aa45feb6e3dc0591564fc56599",
        "queryArtistDiscographyAlbums" to
            "5e07d323febb57b4a56a42abbf781490e58764aa45feb6e3dc0591564fc56599",
        "queryArtistDiscographySingles" to
            "5e07d323febb57b4a56a42abbf781490e58764aa45feb6e3dc0591564fc56599",
        "queryArtistDiscographyCompilations" to
            "5e07d323febb57b4a56a42abbf781490e58764aa45feb6e3dc0591564fc56599",
        "queryArtistDiscoveredOn" to
            "71c2392e4cecf6b48b9ad1311ae08838cbdabcfd189c6bf0c66c2430b8dcfdb1",
        "queryArtistFeaturing" to
            "20842d6d9d2d28ef945984b68cb927bb33edd00eab84a8da1667def21f1f2c54",
        "queryArtistAppearsOn" to
            "9a4bb7a20d6720fe52d7b47bc001cfa91940ddf5e7113761460b4a288d18a4c1",
        "queryArtistPlaylists" to
            "54f7e5a5a2af05b7dc98526df376a46c6b15c05440c8dfdc8f6cecb1a807eca7",
        "queryArtistRelated" to
            "3d031d6cb22a2aa7c8d203d49b49df731f58b1e2799cc38d9876d58771aa66f3",
        "editablePlaylists" to
            "d5c4b8096437dcc2ac9528c91dfcd299e35b747cda2f8f75d28f41f49c5092ba",
        "addToPlaylist" to
            "b907ad6b088d5bf7680fa81198996d58398c2d55dfcdf59fcc959e0be98a418a",
        "moveItemsInPlaylist" to
            "b907ad6b088d5bf7680fa81198996d58398c2d55dfcdf59fcc959e0be98a418a",
        "accountAttributes" to
            "be4ce359daee01463ddb8e77536220e108e3d28105e12199f244d7a5954e9d14",
        "changeAttributes" to
            "c51f514c143b5a4ff830bb2e85c3bef7441814ae4e3392a671f476c903c487ac",
        "profileAttributes" to
            "08ffb4730af3746e04a8301396f20875dbbce10c75243803091a9274eacc8ac0",
        "userTopContent" to
            "61b7353d3625780022dbc63d459eb64cd609e7e5e2a820f9160d65030efe2e15",
        "queryShowMetadataV2" to
            "b475447846f37cc426add800b995a7859c169c53c72b62de6338c0c40a5dacea",
        "queryPodcastEpisodes" to
            "3539d746cf882f3909660de40b4ef472b3f5893a0761d617c282541de5d412c5",
        "getEpisodeOrChapter" to
            "5f77db47e5a2ce6680330bb61317aa09e17ddd1d189513cb218e7cd3e43288be",
        "internalLinkRecommenderShow" to
            "6c369ff272a666b31fef1629c169925a1bd80f372195396c82304142cacd89e8",
        "internalLinkRecommenderEpisode" to
            "122f5c777aae5c0918baec11cd646b7034b8f213f260097b4d229ad947ec7f93",
        "getCommentsForEntity" to
            "5075cb710c32d369eb5b67f6abdfd11a48d58214795064181a7da830b5fc84b6",
        "queryWhatsNewFeed" to
            "d889c8c936ab192af8ced595427f5ba2acdf63478fdc0a181c8d477f8322630e",
        "whatsNewFeedNewItems" to
            "d889c8c936ab192af8ced595427f5ba2acdf63478fdc0a181c8d477f8322630e",
        "SetItemsStateInWhatsNewFeed" to
            "d889c8c936ab192af8ced595427f5ba2acdf63478fdc0a181c8d477f8322630e",
        "smartShuffle" to
            "3384085be84fbf2f855b024f99bc06cded1c0fd71af3a8fb8abb84e9656faba2",
        "getTrack" to
            "e7b1bf4e95cb3d733d48b442f93006e9712fd504e8b88d7a82e6e3bac9306a6b",
    )

    data class Op(
        val name: String,
        val hash: String,
        val variables: String,
        val capturedAt: Long,
        val hits: Int,
    )

    private val ops = LinkedHashMap<String, Op>()
    private var loaded = false

    var silentRefresh: (suspend (Context, String) -> Boolean)? = null

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val raw = prefs(context).getString(KEY_OPS, null) ?: return
        runCatching {
            val root = JSONObject(raw)
            root.keys().asSequence().forEach { name ->
                val o = root.optJSONObject(name) ?: return@forEach
                val hash = o.optString("hash")
                if (hash.isBlank()) return@forEach
                ops[name] = Op(
                    name = name,
                    hash = hash,
                    variables = o.optString("vars"),
                    capturedAt = o.optLong("at"),
                    hits = o.optInt("hits"),
                )
            }
            log.i("loaded ${ops.size} operations: ${ops.keys.joinToString(",")}")
        }.onFailure { log.w("load failed", it) }
    }

    @Synchronized
    fun record(context: Context, name: String, hash: String, variables: String) {
        if (name.isBlank() || hash.isBlank()) return
        load(context)
        val previous = ops[name]
        if (previous?.hash == hash) {
            ops[name] = previous.copy(hits = previous.hits + 1)
            return
        }
        ops[name] = Op(
            name = name,
            hash = hash,
            variables = variables,
            capturedAt = System.currentTimeMillis(),
            hits = 1,
        )
        log.i("captured $name hash=$hash vars=${variables.take(160)}")
        save(context)
    }

    @Synchronized
    fun hashOf(name: String): String? = ops[name]?.hash ?: SEEDS[name]

    @Synchronized
    fun hashOf(context: Context, name: String): String? {
        val captured = ops[name]
        val bundle = BundleHashes.hash(context, name)
        if (bundle != null &&
            (captured == null || BundleHashes.refreshedAt(context) > captured.capturedAt)
        ) {
            return bundle
        }
        return captured?.hash ?: SEEDS[name]
    }

    @Synchronized
    fun variablesOf(name: String): String = ops[name]?.variables.orEmpty()

    private val spSession: String = java.util.UUID.randomUUID().toString()

    fun sessionToken(): String = spSession

    @Synchronized
    fun count(): Int = ops.size

    @Synchronized
    fun shouldCapture(context: Context, tokenUsable: Boolean = true): Boolean {
        load(context)
        if (ops.isNotEmpty() && tokenUsable) return false
        if (ops.isNotEmpty()) return true
        val last = prefs(context).getLong(KEY_ATTEMPT, 0L)
        return System.currentTimeMillis() - last > RETRY_AFTER_MS
    }

    fun markAttempt(context: Context) {
        prefs(context).edit().putLong(KEY_ATTEMPT, System.currentTimeMillis()).apply()
    }

    private fun prefs(context: Context) = com.quackify.backend.core.store.AppPrefs.prefs(context, PREFS)

    private fun save(context: Context) {
        val root = JSONObject()
        ops.values.forEach { op ->
            root.put(
                op.name,
                JSONObject()
                    .put("hash", op.hash)
                    .put("vars", op.variables)
                    .put("at", op.capturedAt)
                    .put("hits", op.hits),
            )
        }
        prefs(context).edit()
            .putString(KEY_OPS, root.toString())
            .apply()
    }

    fun endpointUrl(operation: String, hash: String, variables: String): String {
        val ext = """{"persistedQuery":{"version":1,"sha256Hash":"$hash"}}"""
        return buildString {
            append(ENDPOINT)
            append("?operationName=").append(enc(operation))
            append("&variables=").append(enc(variables.ifBlank { "{}" }))
            append("&extensions=").append(enc(ext))
        }
    }

    private suspend fun headersFor(
        context: Context,
        token: String,
        operation: String = "",
    ): Map<String, String> = buildMap {
        put("Authorization", "Bearer $token")
        put("Accept", "application/json")
        if (operation != HOME_OP) {
            put("app-platform", "WebPlayer")
            put("spotify-app-version", "9.1.92.3058")
        }
        put("Origin", "https://open.spotify.com")
        put("Referer", "https://open.spotify.com/")
        put("User-Agent", Net.DESKTOP_UA)
        ClientToken.get(context).takeIf { it.isNotBlank() }?.let {
            put("client-token", it)
        }
    }

    private fun bodyOf(operation: String, hash: String, variables: String): String = JSONObject()
        .put("variables", JSONObject(variables.ifBlank { "{}" }))
        .put("operationName", operation)
        .put(
            "extensions",
            JSONObject().put(
                "persistedQuery",
                JSONObject().put("version", 1).put("sha256Hash", hash),
            ),
        )
        .toString()

    private suspend fun attempt(
        context: Context,
        token: String,
        operation: String,
        variables: String,
        hash: String,
        post: Boolean,
    ): Net.Response? {
        val tag = "pathfinder/$operation"
        val headers = headersFor(context, token, operation)
        val first = if (post) {
            Net.postJson(ENDPOINT_POST, bodyOf(operation, hash, variables), headers, tag)
        } else {
            Net.get(endpointUrl(operation, hash, variables), headers, tag)
        }
        if (first == null || (first.code != 401 && first.code != 403)) return first

        log.w("$operation http=${first.code}, suspecting the client-token — minting a new one")
        ClientToken.get(context, force = true)
        val retryHeaders = headersFor(context, token, operation)
        val second = if (retryHeaders["client-token"] == headers["client-token"]) {
            log.w("$operation: no fresh client-token available, not retrying")
            first
        } else {
            if (post) {
                Net.postJson(ENDPOINT_POST, bodyOf(operation, hash, variables), retryHeaders, tag)
            } else {
                Net.get(endpointUrl(operation, hash, variables), retryHeaders, tag)
            }
        }
        log.i("$operation retried with a fresh client-token http=${second?.code ?: -1}")
        if (second == null || (second.code != 401 && second.code != 403)) return second

        val refresh = silentRefresh ?: return second
        log.w("$operation still ${second.code} — the access token is dead, refreshing it silently")
        if (!refresh(context, operation)) return second
        val fresh = EncoreAuth.token(context)
        if (fresh.isBlank() || fresh == token) return second
        val freshHeaders = headersFor(context, fresh, operation)
        return if (post) {
            Net.postJson(ENDPOINT_POST, bodyOf(operation, hash, variables), freshHeaders, tag)
        } else {
            Net.get(endpointUrl(operation, hash, variables), freshHeaders, tag)
        }
    }

    private fun staleHash(response: Net.Response): Boolean {
        if (response.code != 400 && response.code != 200) return false
        val body = response.body
        return body.contains("PersistedQueryNotFound", ignoreCase = true) ||
            body.contains("PersistedQueryNotSupported", ignoreCase = true)
    }

    private suspend fun freshHash(context: Context, operation: String): String? {
        log.w("$operation was rejected as a stale persisted query, reading the public bundle")
        val table = BundleHashes.refresh(context, operation) ?: return null
        return table[operation]
    }

    suspend fun query(
        context: Context,
        token: String,
        operation: String,
        variables: String = "{}",
    ): JSONObject? {
        load(context)
        val hash = hashOf(context, operation)
        if (hash == null) {
            log.w("no captured hash for '$operation' — browse the player in the webview first")
            return null
        }
        var response = attempt(context, token, operation, variables, hash, post = false) ?: return null
        if (staleHash(response)) {
            val fresh = freshHash(context, operation)
            if (fresh != null) {
                response = attempt(context, token, operation, variables, fresh, post = false) ?: return null
            }
        }
        return finish(context, operation, response, token)
    }

    suspend fun queryPost(
        context: Context,
        token: String,
        operation: String,
        variables: String = "{}",
        fallbackToGet: Boolean = true,
    ): JSONObject? {
        load(context)
        val hash = hashOf(context, operation)
        if (hash == null) {
            log.w("no captured hash for '$operation'")
            return null
        }
        var response = attempt(context, token, operation, variables, hash, post = true) ?: return null
        if (staleHash(response)) {
            val fresh = freshHash(context, operation)
            if (fresh != null) {
                response = attempt(context, token, operation, variables, fresh, post = true) ?: return null
            }
        }
        if (response.ok || response.code == 401 || response.code == 403) {
            return finish(context, operation, response, token)
        }
        if (!fallbackToGet || response.code !in FALLBACK_CODES) {
            log.w("$operation post http=${response.code} ${response.body.take(200)}")
            return null
        }
        log.w("$operation post http=${response.code}, falling back to GET v1")
        return query(context, token, operation, variables)
    }

    private suspend fun finish(context: Context, operation: String, response: Net.Response, token: String): JSONObject? {
        if (response.code == 401 || response.code == 403) {
            log.w("$operation http=${response.code} even after a fresh client-token, " +
                    "so it is the access token — ${response.body.take(200)}"
            )
            val current = EncoreAuth.token(context)
            if (current.isNotBlank() && current != token) {
                log.w("$operation failed with a stale bearer and a newer one is stored, not clearing it")
                return null
            }
            EncoreAuth.invalidateToken(context)
            return null
        }
        if (!response.ok) {
            log.w("$operation http=${response.code} ${response.body.take(200)}")
            return null
        }
        return withContext(Dispatchers.Default) {
            val json = response.json()
            if (json == null) {
                log.w("$operation not-json ${response.body.take(200)}")
                return@withContext null
            }
            logShape(operation, json)
            json
        }
    }

    private fun logShape(operation: String, json: JSONObject) {
        val data = json.optJSONObject("data")
        val keys = data?.keys()?.asSequence()?.toList().orEmpty()
        val errors = json.optJSONArray("errors")?.length() ?: 0
        log.i("$operation ok data=${keys.joinToString(",")} errors=$errors")
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
