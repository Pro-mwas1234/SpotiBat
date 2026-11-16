package com.mwask.bat

import android.content.Context
import com.mwask.bat.backend.core.store.AppPrefs
import org.json.JSONObject
import java.util.Calendar

data class PlayStatsSnapshot(
    val totalMs: Long = 0L,
    val loginStreak: Int = 0,
    val listenStreak: Int = 0,
    val topArtistName: String = "",
    val topArtistPlays: Int = 0,
    val topPlaylistName: String = "",
    val topPlaylistPlays: Int = 0,
)

object PlayStats {

    private const val PREFS = "spotibat.client"
    private const val KEY = "playStats"

    fun snapshot(context: Context): PlayStatsSnapshot {
        return snapshotOf(read(context))
    }

    fun recordLogin(context: Context): PlayStatsSnapshot {
        val root = read(context)
        val today = dayKey()
        if (root.optString("lastLoginDay") != today) {
            root.put("loginStreak", nextStreak(root.optInt("loginStreak"), root.optString("lastLoginDay")))
            root.put("lastLoginDay", today)
            write(context, root)
        }
        return snapshotOf(root)
    }

    fun recordListen(context: Context) {
        val root = read(context)
        val today = dayKey()
        if (root.optString("lastListenDay") != today) {
            root.put("listenStreak", nextStreak(root.optInt("listenStreak"), root.optString("lastListenDay")))
            root.put("lastListenDay", today)
            write(context, root)
        }
    }

    fun addPlayedMs(context: Context, ms: Long) {
        if (ms <= 0L) return
        val root = read(context)
        root.put("totalMs", root.optLong("totalMs") + ms)
        write(context, root)
    }

    fun tallyArtist(context: Context, uri: String, name: String) {
        if (!uri.startsWith("spotify:artist:") || name.isBlank()) return
        val root = read(context)
        bump(root, "artists", uri, name)
        write(context, root)
    }

    fun tallyPlaylist(context: Context, uri: String, name: String) {
        if (!uri.startsWith("spotify:playlist:") || name.isBlank()) return
        val root = read(context)
        bump(root, "playlists", uri, name)
        write(context, root)
    }

    private fun bump(root: JSONObject, section: String, uri: String, name: String) {
        val nodes = root.optJSONObject(section) ?: JSONObject().also { root.put(section, it) }
        val node = nodes.optJSONObject(uri) ?: JSONObject().also { nodes.put(uri, it) }
        node.put("name", name)
        node.put("plays", node.optInt("plays") + 1)
    }

    private fun snapshotOf(root: JSONObject): PlayStatsSnapshot {
        fun top(section: String): Pair<String, Int> {
            val nodes = root.optJSONObject(section) ?: return "" to 0
            var bestName = ""
            var bestPlays = 0
            val keys = nodes.keys()
            while (keys.hasNext()) {
                val node = nodes.optJSONObject(keys.next()) ?: continue
                val plays = node.optInt("plays")
                if (plays > bestPlays) {
                    bestPlays = plays
                    bestName = node.optString("name")
                }
            }
            return bestName to bestPlays
        }
        val topArtist = top("artists")
        val topPlaylist = top("playlists")
        return PlayStatsSnapshot(
            totalMs = root.optLong("totalMs"),
            loginStreak = root.optInt("loginStreak"),
            listenStreak = root.optInt("listenStreak"),
            topArtistName = topArtist.first,
            topArtistPlays = topArtist.second,
            topPlaylistName = topPlaylist.first,
            topPlaylistPlays = topPlaylist.second,
        )
    }

    private fun nextStreak(current: Int, lastDay: String): Int =
        if (lastDay == dayKey(1)) current + 1 else 1

    private fun dayKey(offsetDays: Int = 0): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -offsetDays)
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun read(context: Context): JSONObject {
        val raw = prefs(context).getString(KEY, null) ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
    }

    private fun write(context: Context, root: JSONObject) {
        prefs(context).edit().putString(KEY, root.toString()).apply()
    }

    private fun prefs(context: Context) = AppPrefs.prefs(context, PREFS)
}
