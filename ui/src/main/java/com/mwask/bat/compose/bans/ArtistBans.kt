package com.mwask.bat.compose.bans

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mwask.bat.backend.api.ArtistBanStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ArtistBans {
    var uris by mutableStateOf<Set<String>>(emptySet())
        private set
    private var loaded = false
    private var lastRefreshAt = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        scope.launch {
            var ok = ArtistBanStore.forceRefresh(context)
            var attempt = 0
            while (!ok && attempt < 3) {
                attempt++
                delay(1500L * attempt)
                ok = ArtistBanStore.forceRefresh(context)
            }
            if (ok) uris = ArtistBanStore.snapshot()
        }
    }

    fun refresh(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRefreshAt < REFRESH_THROTTLE_MS) return
        lastRefreshAt = now
        scope.launch {
            if (ArtistBanStore.forceRefresh(context)) uris = ArtistBanStore.snapshot()
        }
    }

    fun setBanned(artistUri: String, banned: Boolean) {
        uris = if (banned) uris + artistUri else uris - artistUri
    }

    fun isBanned(artistUri: String): Boolean =
        artistUri.startsWith("spotify:artist:") && artistUri in uris

    private const val REFRESH_THROTTLE_MS = 30_000L
}
