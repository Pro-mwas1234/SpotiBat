package com.mwask.bat

import android.net.Uri

data class SpotLink(
    val kind: String,
    val uri: String,
    val id: String = "",
    val query: String = "",
)

object SpotLinks {
    private val WEB_HOSTS = setOf(
        "open.spotify.com",
        "play.spotify.com",
        "spotify.com",
        "www.spotify.com",
    )
    private val ENTITY_KINDS = setOf("track", "album", "artist", "playlist", "episode", "show", "station")
    private val ID = Regex("^[0-9A-Za-z]{16,30}$")

    fun parse(data: Uri?): SpotLink? {
        data ?: return null
        return when (data.scheme?.lowercase()) {
            "spotify" -> fromSpotifyUri(data)
            "http", "https" -> fromWeb(data)
            else -> null
        }
    }

    private fun fromSpotifyUri(data: Uri): SpotLink? {
        val parts = data.toString().split(":").filter { it.isNotBlank() }
        if (parts.isEmpty() || !parts[0].equals("spotify", true)) return null
        if (parts.size < 3) return null
        val kind = parts[1].lowercase()
        if (kind == "search") {
            val query = parts.drop(2).joinToString(" ").trim()
            if (query.isEmpty()) return null
            return SpotLink("search", "spotify:search:${Uri.encode(query)}", query = query)
        }
        if (kind == "user") {
            if (parts.size >= 5 && parts[3].equals("playlist", true)) {
                val id = parts[4]
                if (!ID.matches(id)) return null
                return SpotLink("playlist", "spotify:user:${parts[2]}:playlist:$id", id)
            }
            return null
        }
        if (kind !in ENTITY_KINDS) return null
        val id = parts[2]
        if (!ID.matches(id)) return null
        return SpotLink(kind, "spotify:$kind:$id", id)
    }

    private fun fromWeb(data: Uri): SpotLink? {
        if (data.host?.lowercase() !in WEB_HOSTS) return null
        val segments = data.pathSegments
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("intl-", true) }
            .filterNot { it.equals("embed", true) }
        if (segments.isEmpty()) return null
        val kind = segments[0].lowercase()
        if (kind == "search") {
            val query = data.getQueryParameter("q")
                ?: segments.drop(1).joinToString(" ").trim()
            if (query.isEmpty()) return null
            return SpotLink("search", "spotify:search:${Uri.encode(query)}", query = query)
        }
        if (kind == "user") {
            if (segments.size >= 4 && segments[2].equals("playlist", true)) {
                val id = segments[3]
                if (!ID.matches(id)) return null
                return SpotLink("playlist", "spotify:user:${segments[1]}:playlist:$id", id)
            }
            return null
        }
        if (kind !in ENTITY_KINDS) return null
        val id = segments.getOrNull(1) ?: return null
        if (!ID.matches(id)) return null
        return SpotLink(kind, "spotify:$kind:$id", id)
    }
}
