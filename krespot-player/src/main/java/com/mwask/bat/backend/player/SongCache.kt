/*
 *  _   __                          _
 * | | / /                         | |
 * | |/ / _ __ ___  ___ _ __   ___ | |_
 * |    \| '__/ _ \ / __| '_ \ / _ \| __|
 * |\  \ | |  __/ \__ \ |_) | (_) | |_
 * \_| \/_|  \___||___/ .__/ \___/ \__|
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

package com.mwask.bat.backend.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.mwask.bat.backend.core.log.Logs
import java.io.File

private val log = Logs.of(Logs.Module.PLAYER)

/**
 * Song caching for offline playback, on top of Media3's SimpleCache.
 *
 * Every played or preloaded track flows through a CacheDataSource: online
 * the bytes seed the disk cache while ExoPlayer streams them (no extra
 * download pass), offline the same bytes are served back for the tracks
 * the cache still holds. Keys are track URIs, so a re-resolved CDN URL
 * still hits the copy already on disk.
 *
 * The cache keeps at most [TRACK_LIMIT] tracks: the byte budget makes the
 * LRU evictor drop the least recently used one when a fourth track starts
 * filling, and stale entries are removed explicitly once a track leaves
 * the playback window, so the limit holds even for large files.
 */
internal object SongCache {

    /** How many tracks the offline cache may hold. */
    const val TRACK_LIMIT = 3

    // Three lossless-or-high tracks (~96 MB each in the worst case) stay
    // well inside a cacheDir budget while leaving room for the eviction
    // window; typical OGG/MP4 plays land around 5-15 MB per track.
    private const val MAX_BYTES = 96L * 1024L * 1024L * TRACK_LIMIT

    private const val DIR = "song_cache"

    @Volatile
    private var cache: SimpleCache? = null

    private val tracked = LinkedHashSet<String>()

    @Synchronized
    fun get(context: Context): SimpleCache = cache ?: SimpleCache(
        File(context.cacheDir, DIR),
        LeastRecentlyUsedCacheEvictor(MAX_BYTES),
        StandaloneDatabaseProvider(context),
    ).also { cache = it }

    /**
     * A cache-aware datasource factory: online reads pass through the disk
     * cache on their way to the network, offline reads are served from it.
     */
    fun factory(context: Context): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(get(context))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * Records that a track was played or preloaded, so it counts against
     * the [TRACK_LIMIT] and older entries beyond the limit are dropped.
     */
    @Synchronized
    fun touch(trackUri: String, context: Context) {
        if (!trackUri.startsWith("spotify:track:")) return
        val instance = get(context)
        if (tracked.remove(trackUri)) tracked.add(trackUri)
        else {
            tracked.add(trackUri)
            while (tracked.size > TRACK_LIMIT) {
                val oldest = tracked.firstOrNull() ?: break
                tracked.remove(oldest)
                removeResource(instance, oldest)
                log.i("song cache evicted '$oldest' at the ${TRACK_LIMIT}-track limit")
            }
        }
    }

    /** Whether the cache already holds bytes for the track. */
    @Synchronized
    fun has(trackUri: String): Boolean =
        trackUri.startsWith("spotify:track:") && cache?.keys?.any { it.startsWith(trackUri) } == true

    @Synchronized
    fun clear() {
        tracked.clear()
        cache?.let { instance ->
            instance.keys.toList().forEach { removeResource(instance, it) }
        }
    }

    private fun removeResource(instance: SimpleCache, key: String) {
        runCatching { instance.removeResource(key) }
            .onFailure { log.w("song cache could not drop '$key': ${it.message}") }
    }
}
