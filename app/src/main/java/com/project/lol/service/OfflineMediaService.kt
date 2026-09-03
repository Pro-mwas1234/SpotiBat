package com.project.lol.service

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
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.project.lol.R
import com.project.lol.ui.OfflineActivity
import java.io.File
import kotlin.math.min

class OfflineMediaService : Service() {

    companion object {
        private const val TAG = "OfflineMediaSvc"
        private const val CHANNEL_ID = "spotilol_offline_playback"
        private const val NOTIFICATION_ID = 2

        const val ACTION_PLAY_PAUSE = "com.project.lol.offline.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.project.lol.offline.ACTION_NEXT"
        const val ACTION_PREV = "com.project.lol.offline.ACTION_PREV"
        const val ACTION_STOP = "com.project.lol.offline.ACTION_STOP"

        private val PLAYBACK_ACTIONS: Long =
            PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO

        private const val NOTIF_COLOR = 0xFF1DB954.toInt()

        var controller: OfflineController? = null
        var instance: OfflineMediaService? = null
    }

    interface OfflineController {
        fun onPlayPause()
        fun onNext()
        fun onPrev()
        fun onStop()
        fun onSeekTo(position: Long)
    }

    private lateinit var mediaSession: MediaSessionCompat
    private var isPlaying = false
    private var coverBitmap: Bitmap? = null
    private var currentTitle = ""
    private var currentArtist = ""
    private var currentAlbum = "Spotilol"
    private var currentPosition: Long = 0L
    private var currentDuration: Long = 0L

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PLAY_PAUSE -> controller?.onPlayPause()
                ACTION_NEXT -> controller?.onNext()
                ACTION_PREV -> controller?.onPrev()
                ACTION_STOP -> controller?.onStop()
            }
        }
    }

    private val audioBecomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                val prefs = getSharedPreferences("spotilol_prefs", MODE_PRIVATE)
                if (prefs.getBoolean("BtAutoPause", false)) {
                    controller?.onPlayPause()
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
            stopPlaybackService()
            return START_NOT_STICKY
        }
        if (intent?.hasExtra("title") == true) {
            currentTitle = intent.getStringExtra("title") ?: ""
            currentArtist = intent.getStringExtra("artist") ?: ""
            currentAlbum = intent.getStringExtra("album")?.ifBlank { "Spotilol" } ?: "Spotilol"
            currentDuration = intent.getLongExtra("duration", 0L)
            isPlaying = intent.getBooleanExtra("playing", false)
            currentPosition = intent.getLongExtra("position", 0L)
            coverBitmap = null
            val coverPath = intent.getStringExtra("coverPath")
            if (!coverPath.isNullOrBlank()) {
                loadCoverArt(File(coverPath))
            }
            updateMetadata()
            updatePlaybackState()
            showNotification()
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
        try { unregisterReceiver(actionReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(audioBecomingNoisyReceiver) } catch (_: Exception) {}
        if (::mediaSession.isInitialized) {
            try { mediaSession.release() } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Offline Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Spotilol offline playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "SpotilolOfflineSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (!isPlaying) OfflineMediaService.controller?.onPlayPause()
                }

                override fun onPause() {
                    if (isPlaying) OfflineMediaService.controller?.onPlayPause()
                }

                override fun onSkipToNext() {
                    OfflineMediaService.controller?.onNext()
                }

                override fun onSkipToPrevious() {
                    OfflineMediaService.controller?.onPrev()
                }

                override fun onStop() {
                    OfflineMediaService.controller?.onStop()
                }

                override fun onSeekTo(pos: Long) {
                    OfflineMediaService.controller?.onSeekTo(pos)
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

    fun updateTrack(title: String, artist: String, album: String, coverFile: File?, duration: Long) {
        currentTitle = title
        currentArtist = artist
        currentAlbum = album.ifBlank { "Spotilol" }
        currentDuration = duration
        coverBitmap = null
        coverFile?.let { loadCoverArt(it) }
        updateMetadata()
        updatePlaybackState()
        showNotification()
    }

    fun updatePlaying(playing: Boolean, position: Long) {
        isPlaying = playing
        currentPosition = position
        updatePlaybackState()
        showNotification()
    }

    fun updatePosition(position: Long) {
        currentPosition = position
        updatePlaybackState()
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

    private fun updatePlaybackState() {
        val state = PlaybackStateCompat.Builder()
            .setActions(PLAYBACK_ACTIONS)
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING
                else PlaybackStateCompat.STATE_PAUSED,
                currentPosition, if (isPlaying) 1f else 0f
            )
            .build()
        if (::mediaSession.isInitialized) {
            try { mediaSession.setPlaybackState(state) } catch (_: Exception) {}
        }
    }

    private fun updateMetadata() {
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentAlbum)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentDuration)
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
                updateMetadata()
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
                .setContentTitle("Spotilol")
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .build()
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, OfflineActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevAction = NotificationCompat.Action.Builder(
            R.drawable.ic_skip_prev, "Previous", getActionPendingIntent(ACTION_PREV)
        ).build()

        val playPauseAction = NotificationCompat.Action.Builder(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
            if (isPlaying) "Pause" else "Play",
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
            .setContentTitle(currentTitle.ifEmpty { "Spotilol" })
            .setContentText(currentArtist)
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
