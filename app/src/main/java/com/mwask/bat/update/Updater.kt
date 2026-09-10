package com.mwask.bat.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal in-app updater: checks GitHub for a newer release, downloads the
 * release APK and hands it to the system installer (user confirms install).
 *
 * Compares versionCode, so the release workflow only needs to keep bumping
 * it. Falls back to comparing the "vX.Y.Z" tag when versionCode is absent.
 */
object Updater {
    private const val TAG = "Spl-Updater"
    private const val RELEASES_URL =
        "https://api.github.com/repos/Pro-mwas1234/SpotiBat/releases/latest"

    data class CheckResult(
        val updateAvailable: Boolean,
        val latestTag: String?,
        val apkUrl: String?,
        val apkSizeBytes: Long?,
    )

    /** Fetch the latest release and decide whether it is newer than [currentVersionCode]. */
    suspend fun checkForUpdate(currentVersionCode: Long): CheckResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = open(RELEASES_URL)
                val code = conn.responseCode
                if (code != 200) {
                    Log.w(TAG, "checkForUpdate: HTTP $code")
                    return@runCatching CheckResult(false, null, null, null)
                }
                val body = conn.inputStream.use { it.readBytes().decodeToString() }
                conn.disconnect()

                val release = JSONObject(body)
                if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) {
                    return@runCatching CheckResult(false, null, null, null)
                }
                val tag = release.optString("tag_name").takeIf { it.isNotBlank() }
                val assets = release.optJSONArray("assets")
                var apkUrl: String? = null
                var apkSize: Long? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        val name = a.optString("name")
                        if (name.equals("SpotiBat-latest.apk", ignoreCase = true) ||
                            (apkUrl == null && name.endsWith(".apk", ignoreCase = true))
                        ) {
                            apkUrl = a.optString("browser_download_url")
                            apkSize = a.optLong("size")
                            if (name.equals("SpotiBat-latest.apk", ignoreCase = true)) break
                        }
                    }
                }
                val remoteCode = parseVersionCode(tag)
                val newer = (remoteCode != null && remoteCode > currentVersionCode) ||
                    // No versionCode in tag (e.g. non vX.Y.Z tag): assume an
                    // APK asset means there is something to offer; install
                    // will no-op if the package is not actually newer.
                    (remoteCode == null && apkUrl != null)
                CheckResult(newer, tag, apkUrl.takeIf { newer }, apkSize)
            }.getOrElse {
                Log.e(TAG, "checkForUpdate failed: ${it.message}", it)
                CheckResult(false, null, null, null)
            }
        }

    /** Download the APK to app-internal storage and return a shareable content Uri. */
    suspend fun downloadApk(context: Context, url: String): Uri? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, "updates").apply { mkdirs() }
                val dest = File(dir, "spotibat-update.apk")
                val tmp = File(dir, "spotibat-update.apk.part")

                val conn = open(url)
                if (conn.responseCode !in 200..299) {
                    Log.w(TAG, "downloadApk: HTTP ${conn.responseCode}")
                    return@runCatching null
                }
                conn.inputStream.use { input ->
                    FileOutputStream(tmp).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val r = input.read(buf)
                            if (r < 0) break
                            out.write(buf, 0, r)
                            total += r
                        }
                        Log.d(TAG, "downloadApk: $total bytes")
                    }
                }
                conn.disconnect()

                if (tmp.length() == 0L) {
                    tmp.delete()
                    return@runCatching null
                }
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) {
                    Log.w(TAG, "downloadApk: rename failed")
                    tmp.delete()
                    return@runCatching null
                }
                FileProvider.getUriForFile(
                    context,
                    context.packageName + ".updateprovider",
                    dest
                )
            }.getOrElse {
                Log.e(TAG, "downloadApk failed: ${it.message}", it)
                null
            }
        }

    /** Hand the APK to the system installer. */
    fun install(context: Context, apkUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun parseVersionCode(tag: String?): Long? {
        // Expect vX.Y.Z or X.Y.Z — treat Y*100 + Z as a sortable code so a
        // bump in the minor or patch version reads as an update.
        val m = Regex("""^v?(\d+)\.(\d+)(?:\.(\d+))?$""").find(tag?.trim() ?: "") ?: return null
        val (major, minor, patch) = m.destructured
        val p = if (patch.isBlank()) 0L else patch.toLong()
        return major.toLong() * 100_000 + minor.toLong() * 1_000 + p
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "SpotiBat-Updater")
        }
}
