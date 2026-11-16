package com.mwask.bat

import android.content.Context
import com.mwask.bat.compose.screens.AppUpdate
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    private const val RELEASES_API = "https://api.github.com/repos/Pro-mwas1234/SpotiBat/releases/latest"
    // Releases use 2-5 numeric segments (v2.0.0, v2.0.0.1 ...), oldest first.
    private val VERSION = Regex("""v?(\d+)\.(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:\.(\d+))?""")

    fun check(context: Context): AppUpdate? {
        val current = currentVersion(context) ?: return null
        val release = latestRelease(UpdateInstaller.flavorOf(context.packageName)) ?: return null
        val mine = parse(current) ?: return null
        val theirs = parse(release.tag) ?: return null
        if (compare(theirs, mine) <= 0) return null
        return AppUpdate(version = release.tag, url = release.url, notes = release.notes)
    }

    fun currentVersion(context: Context): String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun parse(text: String): List<Int>? {
        val match = VERSION.find(text.trim()) ?: return null
        return match.groupValues.drop(1).filter { it.isNotEmpty() }.map { it.toInt() }
    }

    private fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            if (a[i] != b[i]) return a[i] - b[i]
        }
        return a.size - b.size
    }

    private class Release(val tag: String, val url: String, val notes: String)

    private fun latestRelease(flavor: String): Release? = runCatching {
        val conn = URL(RELEASES_API).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "SpotiBat-UpdateChecker")
        try {
            if (conn.responseCode !in 200..299) return@runCatching null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").orEmpty()
            if (tag.isBlank()) return@runCatching null
            Release(
                tag = tag,
                url = pickApk(json, flavor) ?: json.optString("html_url").orEmpty(),
                notes = json.optString("body").orEmpty(),
            )
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun pickApk(json: JSONObject, flavor: String): String? {
        val assets = json.optJSONArray("assets") ?: return null
        var fallback: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name").orEmpty()
            val download = asset.optString("browser_download_url").orEmpty()
            if (download.isBlank() || !name.endsWith(".apk", true)) continue
            if (name.contains(flavor, true)) return download
            if (fallback == null) fallback = download
        }
        return fallback
    }
}
