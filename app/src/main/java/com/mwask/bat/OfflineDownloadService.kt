package com.mwask.bat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import com.mwask.bat.backend.core.offline.DownloadPhase
import com.mwask.bat.backend.core.offline.DownloadStatus
import com.mwask.bat.backend.player.OfflineAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the offline queue running while the app is in the background. The queue itself lives in
 * [OfflineAudio]; this only owns the foreground notification and the cancel action, so nothing
 * here can stall a download.
 */
class OfflineDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** True once the queue has actually reported a song, so an idle start can stop itself. */
    private var sawWork = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        goForeground(buildNotification(emptyList()))
        scope.launch {
            OfflineAudio.statuses.collect { statuses ->
                val working = statuses.entries
                    .filter { it.value.working }
                    .map { it.toPair() }
                if (working.isEmpty()) {
                    if (sawWork) stopSelf()
                    return@collect
                }
                sawWork = true
                notificationManager().notify(NOTIFICATION_ID, buildNotification(working))
            }
        }
        // Nothing was queued after all (the tap raced the queue): do not sit in the shade.
        scope.launch {
            delay(IDLE_START_GRACE_MS)
            if (!sawWork) stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL_ALL) OfflineAudio.cancelAll(this)
        goForeground(buildNotification(emptyList()))
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(working: List<Pair<String, DownloadStatus>>): Notification {
        val known = working.map { it.second }.filter { it.percent >= 0f }
        val percent = if (known.isEmpty()) {
            0
        } else {
            ((known.sumOf { it.percent.toDouble() } / known.size) * 100).toInt()
        }
        // The song being saved right now, so the lock screen says which one it is.
        val active = working.firstOrNull { it.second.phase == DownloadPhase.RUNNING }
            ?: working.firstOrNull()
        val title = active?.let { (uri, _) -> OfflineAudio.titleOf(uri) }
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.downloading)
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getService(
            this,
            0,
            Intent(this, OfflineDownloadService::class.java).setAction(CANCEL_ALL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(
                if (working.isEmpty()) {
                    getString(R.string.downloading)
                } else {
                    getString(R.string.download_queue, working.size)
                },
            )
            .setProgress(100, percent, known.isEmpty())
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            // Progress is not private: let it through on a locked screen.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(open)
            .addAction(0, getString(android.R.string.cancel), cancel)
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0)
            )
            .build()
    }

    private fun notificationManager() =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = notificationManager()
        // Android ignores most settings on an existing channel, so rebuild it once if an older
        // build created it without lock-screen visibility.
        manager.getNotificationChannel(CHANNEL_ID)?.let { existing ->
            if (existing.lockscreenVisibility != Notification.VISIBILITY_PUBLIC) {
                manager.deleteNotificationChannel(CHANNEL_ID)
            }
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.download_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "offline_downloads"
        const val NOTIFICATION_ID = 2
        const val CANCEL_ALL = "com.mwask.bat.offline.CANCEL_ALL"
        private const val IDLE_START_GRACE_MS = 15_000L

        /** Brings the foreground service up; called whenever a song is queued. */
        fun start(context: Context) {
            val intent = Intent(context, OfflineDownloadService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure {
                // Background start restrictions: the queue still runs while the app is alive.
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, OfflineDownloadService::class.java)) }
        }
    }
}
