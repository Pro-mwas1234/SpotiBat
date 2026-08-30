package com.project.lol.util

import android.content.Context

class UpdateChecker(private val context: Context) {

    companion object {
        private const val OWNER = "lyssadev"
        private const val REPO = "Spotilol"
        private const val PREFS_NAME = "spotilol_prefs"
        private const val KEY_LAST_CHECK = "LastUpdateCheck"
        private const val CHECK_INTERVAL_MS = 60 * 60 * 1000L
    }

    fun autoCheck(onUpdateAvailable: (String) -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0)
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return

        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        GitHubApi.fetchLatestRelease(OWNER, REPO) { release ->
            val latest = release?.tagName?.removePrefix("v") ?: return@fetchLatestRelease
            val current = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            }.getOrElse { "" }

            if (isNewer(latest, current)) {
                val url = release.htmlUrl.ifBlank {
                    "https://github.com/$OWNER/$REPO/releases/latest"
                }
                onUpdateAvailable(url)
            }
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".")
        val currentParts = current.split(".")
        val size = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until size) {
            val l = latestParts.getOrNull(i)?.toIntOrNull() ?: 0
            val c = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
