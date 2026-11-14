package com.quackify.backend.api

import android.content.Context
import android.os.SystemClock

object ArtistBanStore {
    private var uris: Set<String> = emptySet()
    private var fetchedAt = 0L
    private val TTL_MS = 5 * 60 * 1000L

    suspend fun banned(context: Context, artistUri: String): Boolean {
        if (!artistUri.startsWith("spotify:artist:")) return false
        val now = SystemClock.elapsedRealtime()
        if (now - fetchedAt > TTL_MS) {
            val fresh = runCatching { Collection2.items(context, Collection2.ARTIST_BAN).toSet() }.getOrNull()
            if (fresh != null) {
                uris = fresh
                fetchedAt = now
            }
        }
        return artistUri in uris
    }

    fun invalidate() {
        uris = emptySet()
        fetchedAt = 0L
    }
}
