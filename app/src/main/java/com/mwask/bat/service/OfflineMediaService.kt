package com.mwask.bat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.mwask.bat.R
import com.mwask.bat.offline.OfflinePlayback
import com.mwask.bat.ui.OfflineActivity
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that renders [OfflinePlayback.state] as the media
 * notification + media session, and forwards remote user intents (notification
 * buttons, headset/media buttons) back into [OfflinePlayback.submit] as
 * commands. It holds no playback state of its own: the screen owns the player
 * engine and publishes state; this service only reflects it.
 */
class OfflineMediaService : Service() {

    companion object {
        private const val TAG = "OfflineMediaSvc"
        private const val CHANNEL_ID = "spotiBat_offline_playback"
        private const val NOTIFICATION_ID = 2

        /** Extra on the content intent: open the full-screen player, not just the app. */
        const val EXTRA_OPEN_PLAYER = "open_player"

        const val ACTION_PLAY_PAUSE = "com.mwask.bat.offline.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.mwask.bat.offline.ACTION_NEXT"
        const val ACTION_PREV = "com.mwask.bat.offline.ACTION_PREV"
        const val ACTION_STOP = "com.mwask.bat.offline.ACTION_STOP"

        private val PLAYBACK_ACTIONS: Long =
            PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO

        private const val NOTIF_COLOR = 0xFF1DB954.toInt()

        var instance: OfflineMediaService? = null
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var mediaSession: MediaSessionCompat

    /** Snapshot actually rendered into the notification/session, to dedupe work. */
    private var lastRendered: OfflinePlayback.Snapshot? = null
    private var renderedCoverKey: String? = null
    private var coverBitmap: Bitmap? = null

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PLAY_PAUSE -> OfflinePlayback.submit(OfflinePlayback.Command.PlayPause)
                ACTION_NEXT -> OfflinePlayback.submit(OfflinePlayback.Command.Next)
                ACTION_PREV -> OfflinePlayback.submit(OfflinePlayback.Command.Prev)
                ACTION_STOP -> OfflinePlayback.submit(OfflinePlayback.Command.Stop)
            }
        }
    }

    private val audioBecomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                val prefs = getSharedPreferences("spotiBat_prefs", MODE_PRIVATE)
                if (prefs.getBoolean("BtAutoPause", false)) {
                    OfflinePlayback.submit(OfflinePlayback.Command.PlayPause)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            createNotificationChannel()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to create notification channel", e)
        }
        try {
            setupMediaSession()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to setup media session", e)
        }
        try {
            registerReceivers()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to register receivers", e)
        }
        // Single source of truth: render whatever the screen publishes.
        serviceScope.launch {
            OfflinePlayback.state.collect { snapshot ->
                if (snapshot != lastRendered) {
                    renderState(snapshot)
                    lastRendered = snapshot
                }
            }
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotificationSafe(), getStartForegroundServiceType())
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "Failed to start foreground", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun getStartForegroundServiceType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            OfflinePlayback.submit(OfflinePlayback.Command.Stop)
            stopPlaybackService()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotificationSafe(), getStartForegroundServiceType())
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "Failed to re-assert foreground", e)
        }
        try {
            MediaButtonReceiver.handleIntent(mediaSession, intent)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to handle media button intent", e)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        try { unregisterReceiver(actionReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(audioBecomingNoisyReceiver) } catch (_: Exception) {}
        if (::mediaSession.isInitialized) {
            try { mediaSession.release() } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    /** Push the shared snapshot into the media session and notification. */
    private fun renderState(s: OfflinePlayback.Snapshot) {
        // Cover art: decode off the main thread, keyed by path so it reloads
        // only when the track's artwork actually changes.
        if (s.coverPath != renderedCoverKey) {
            renderedCoverKey = s.coverPath
            coverBitmap = null
            if (!s.coverPath.isNullOrBlank()) {
                loadCoverArt(File(s.coverPath))
            }
        }
        updateMetadata(s)
        updatePlaybackState(s)
        showNotification()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Offline Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "SpotiBat offline playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "SpotiBatOfflineSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (lastRendered?.playing == false) OfflinePlayback.submit(OfflinePlayback.Command.PlayPause)
                }

                override fun onPause() {
                    if (lastRendered?.playing == true) OfflinePlayback.submit(OfflinePlayback.Command.PlayPause)
                }

                override fun onSkipToNext() {
                    OfflinePlayback.submit(OfflinePlayback.Command.Next)
                }

                override fun onSkipToPrevious() {
                    OfflinePlayback.submit(OfflinePlayback.Command.Prev)
                }

                override fun onStop() {
                    OfflinePlayback.submit(OfflinePlayback.Command.Stop)
                }

                override fun onSeekTo(pos: Long) {
                    OfflinePlayback.submit(OfflinePlayback.Command.Seek(pos))
                }
            })
            isActive = true
        }
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREV)
            addAction(ACTION_STOP)
            addAction(Intent.ACTION_MEDIA_BUTTON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(actionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(actionReceiver, filter)
        }

        val noisyFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(audioBecomingNoisyReceiver, noisyFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(audioBecomingNoisyReceiver, noisyFilter)
        }
    }

    fun stopPlaybackService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        if (::mediaSession.isInitialized) {
            try { mediaSession.isActive = false } catch (_: Exception) {}
        }
        stopSelf()
    }

    private fun updatePlaybackState(s: OfflinePlayback.Snapshot) {
        val state = PlaybackStateCompat.Builder()
            .setActions(PLAYBACK_ACTIONS)
            .setState(
                if (s.playing) PlaybackStateCompat.STATE_PLAYING
                else PlaybackStateCompat.STATE_PAUSED,
                s.positionMs, if (s.playing) 1f else 0f
            )
            .build()
        if (::mediaSession.isInitialized) {
            try { mediaSession.setPlaybackState(state) } catch (_: Exception) {}
        }
    }

    private fun updateMetadata(s: OfflinePlayback.Snapshot) {
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, s.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, s.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, s.album.ifBlank { "SpotiBat" })
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, s.durationMs)
        coverBitmap?.let { bmp ->
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bmp)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bmp)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bmp)
        }
        if (::mediaSession.isInitialized) {
            try { mediaSession.setMetadata(builder.build()) } catch (_: Exception) {}
        }
    }

    private fun loadCoverArt(file: File) {
        Thread {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / 512)
                }
                val raw = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@Thread
                val target = 512
                val scale = min(target.toFloat() / raw.width, target.toFloat() / raw.height)
                val w = (raw.width * scale).toInt()
                val h = (raw.height * scale).toInt()
                val scaled = Bitmap.createScaledBitmap(raw, w, h, true)
                if (scaled != raw) raw.recycle()
                coverBitmap = scaled
                lastRendered?.let { updateMetadata(it) }
                showNotification()
            } catch (_: Exception) {}
        }.start()
    }

    private fun showNotification() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) {}
    }

    private fun buildNotificationSafe(): Notification {
        return try {
            buildNotification()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to build notification", e)
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SpotiBat")
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .build()
        }
    }

    private fun buildNotification(): Notification {
        val s = lastRendered ?: OfflinePlayback.Snapshot()

        // Tapping the notification opens the app straight into the
        // full-screen player, like Samsung Music / Spotify do.
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, OfflineActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ).putExtra(EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevAction = NotificationCompat.Action.Builder(
            R.drawable.ic_skip_prev, "Previous", getActionPendingIntent(ACTION_PREV)
        ).build()

        val playPauseAction = NotificationCompat.Action.Builder(
            if (s.playing) R.drawable.ic_pause else R.drawable.ic_play,
            if (s.playing) "Pause" else "Play",
            getActionPendingIntent(ACTION_PLAY_PAUSE)
        ).build()

        val nextAction = NotificationCompat.Action.Builder(
            R.drawable.ic_skip_next, "Next", getActionPendingIntent(ACTION_NEXT)
        ).build()

        val style = MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
            .setShowCancelButton(true)
            .setCancelButtonIntent(getActionPendingIntent(ACTION_STOP))
        if (::mediaSession.isInitialized) {
            style.setMediaSession(mediaSession.sessionToken)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(s.title.ifEmpty { "SpotiBat" })
            .setContentText(s.artist)
            .setSubText("Offline Mode")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(NOTIF_COLOR)
            .setStyle(style)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)

        coverBitmap?.let { builder.setLargeIcon(it) }

        return builder.build()
    }

    private fun getActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(action).setPackage(packageName)
        return PendingIntent.getBroadcast(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
