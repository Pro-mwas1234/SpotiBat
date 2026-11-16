package com.mwask.bat

import android.content.Context
import com.mwask.bat.backend.core.store.AppPrefs
import com.mwask.bat.backend.model.SearchHit
import com.mwask.bat.backend.model.SearchKind
import org.json.JSONArray
import org.json.JSONObject

object RecentSearches {

    private const val PREFS = "spotibat.client"
    private const val KEY = "recentSearches"
    private const val LIMIT = 20

    data class Entry(
        val kind: String,
        val uri: String,
        val title: String,
        val subtitle: String,
        val imageUrl: String,
        val colorHex: String,
    )

    fun all(context: Context): List<Entry> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = ArrayList<Entry>(array.length())
        for (i in 0 until array.length()) {
            val node = array.optJSONObject(i) ?: continue
            val entry = Entry(
                kind = node.optString("kind"),
                uri = node.optString("uri"),
                title = node.optString("title"),
                subtitle = node.optString("subtitle"),
                imageUrl = node.optString("image"),
                colorHex = node.optString("color"),
            )
            if (entry.title.isNotBlank()) out += entry
        }
        return out
    }

    fun record(context: Context, hit: SearchHit) {
        if (hit.uri.isBlank() || hit.title.isBlank()) return
        val next = ArrayList<Entry>(LIMIT)
        next += Entry(
            kind = hit.kind.name,
            uri = hit.uri,
            title = hit.title,
            subtitle = hit.subtitle,
            imageUrl = hit.imageUrl,
            colorHex = hit.colorHex,
        )
        all(context).filterNot { it.uri == hit.uri }.take(LIMIT - 1).forEach(next::add)
        write(context, next)
    }

    fun recordQuery(context: Context, query: String) {
        val title = query.trim()
        if (title.isEmpty()) return
        val next = ArrayList<Entry>(LIMIT)
        next += Entry(
            kind = SearchKind.QUERY.name,
            uri = "",
            title = title,
            subtitle = "",
            imageUrl = "",
            colorHex = "",
        )
        all(context).filterNot {
            it.uri.isBlank() && it.title.equals(title, ignoreCase = true)
        }.take(LIMIT - 1).forEach(next::add)
        write(context, next)
    }

    fun remove(context: Context, key: String) {
        if (key.isBlank()) return
        write(context, all(context).filterNot {
            it.uri == key || (it.uri.isBlank() && it.title.equals(key, ignoreCase = true))
        })
    }

    fun clear(context: Context) {
        write(context, emptyList())
    }

    private fun write(context: Context, entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("kind", entry.kind)
                    .put("uri", entry.uri)
                    .put("title", entry.title)
                    .put("subtitle", entry.subtitle)
                    .put("image", entry.imageUrl)
                    .put("color", entry.colorHex)
            )
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    private fun prefs(context: Context) = AppPrefs.prefs(context, PREFS)
}
