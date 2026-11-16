package com.mwask.bat

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import com.mwask.bat.compose.screens.AppUpdate
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateInstaller {
    private const val ACTION_INSTALL_STATUS = "com.mwask.bat.INSTALL_STATUS"

    fun flavorOf(packageName: String): String = when {
        packageName.startsWith("com.mwask.bat") -> "standard"
        packageName.startsWith("com.spotify.music") -> "spotify"
        packageName.startsWith("com.apple.android.music") -> "apple"
        else -> "standard"
    }

    fun download(
        context: Context,
        update: AppUpdate,
        onProgress: (doneBytes: Long, totalBytes: Long, speedBps: Long) -> Unit,
    ): File? = runCatching {
        val conn = URL(update.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "SpotiBat-UpdateChecker")
        try {
            if (conn.responseCode !in 200..299) return null
            val total = conn.contentLengthLong.let { if (it > 0) it else -1L }
            val target = File(context.cacheDir, "update-${update.version}.apk")
            var done = 0L
            var lastTick = System.currentTimeMillis()
            var lastBytes = 0L
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val now = System.currentTimeMillis()
                        if (now - lastTick >= 250) {
                            onProgress(done, total, (done - lastBytes) * 1000 / (now - lastTick))
                            lastTick = now
                            lastBytes = done
                        }
                    }
                    onProgress(done, if (total > 0) total else done, 0)
                }
            }
            if (done <= 0) return null
            target
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    fun install(context: Context, file: File) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context, intent: Intent) {
                try {
                    context.unregisterReceiver(this)
                } catch (_: IllegalArgumentException) {
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, IntentFilter(ACTION_INSTALL_STATUS), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(ACTION_INSTALL_STATUS))
        }
        val statusIntent = Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName)
        val flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val sender = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags)
        try {
            session.openWrite("spotibat_update", 0, file.length()).use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            session.commit(sender.intentSender)
        } catch (t: Throwable) {
            runCatching { session.abandon() }
            runCatching { context.unregisterReceiver(receiver) }
            throw t
        }
    }

    fun cleanup(context: Context) {
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("update-") && it.name.endsWith(".apk") }
            ?.forEach { it.delete() }
    }
}
