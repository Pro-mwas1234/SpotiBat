package com.quackify.backend.api

import android.content.Context
import android.os.SystemClock

object ArtistBanStore {
    private var uris: Set<String> = emptySet()
    private var fetchedAt = 0L
    private val TTL_MS = 5 * 60 * 1000L

    suspend fun banned(context: Context, artistUri: String): Boolean {
        if (!artistUri.startsWith("spotify:artist:")) return false
        warm(context)
        return artistUri in uris
    }

    suspend fun warm(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - fetchedAt <= TTL_MS) return
        val fresh = runCatching { Collection2.items(context, Collection2.ARTIST_BAN).toSet() }.getOrNull()
        if (fresh != null) {
            uris = fresh
            fetchedAt = now
        }
    }

    fun bannedCached(artistUri: String): Boolean =
        artistUri.startsWith("spotify:artist:") && artistUri in uris

    fun snapshot(): Set<String> = uris

    fun invalidate() {
        uris = emptySet()
        fetchedAt = 0L
    }
}
