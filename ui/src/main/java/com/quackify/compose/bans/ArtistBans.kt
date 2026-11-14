package com.quackify.compose.bans

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.quackify.backend.api.ArtistBanStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object ArtistBans {
    var uris by mutableStateOf<Set<String>>(emptySet())
        private set
    private var loaded = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        scope.launch {
            ArtistBanStore.warm(context)
            uris = ArtistBanStore.snapshot()
        }
    }

    fun setBanned(artistUri: String, banned: Boolean) {
        uris = if (banned) uris + artistUri else uris - artistUri
    }

    fun isBanned(artistUri: String): Boolean =
        artistUri.startsWith("spotify:artist:") && artistUri in uris
}
