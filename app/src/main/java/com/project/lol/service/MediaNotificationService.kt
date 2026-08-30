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
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.webkit.WebView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.project.lol.R
import android.bluetooth.BluetoothDevice
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min
import androidx.core.graphics.scale
import java.util.concurrent.Executors

class MediaNotificationService : Service() {

    companion object {
        private const val TAG = "MediaNotifService"
        private const val CHANNEL_ID = "spotilol_media_playback"
        private const val NOTIFICATION_ID = 1
        private val mainHandler = Handler(Looper.getMainLooper())

        const val ACTION_PLAY_PAUSE = "com.project.lol.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.project.lol.ACTION_NEXT"
        const val ACTION_PREV = "com.project.lol.ACTION_PREV"
        const val ACTION_SHUFFLE = "com.project.lol.ACTION_SHUFFLE"
        private const val ACTION_FAVORITE = "com.project.lol.ACTION_FAVORITE"

        private const val CUSTOM_ACTION_TOGGLE_FAV = "toggle_fav"
        private const val CUSTOM_ACTION_TOGGLE_SHUFFLE = "toggle_shuffle"

        private const val PLAYBACK_ACTIONS: Long =
            PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SEEK_TO

        private const val NOTIF_COLOR = 0xFFE0E0E0.toInt()


        @Volatile
        private var instanceRef: WeakReference<MediaNotificationService>? = null
        @Volatile
        private var webViewRef: WeakReference<WebView>? = null
        private var isAndAutoEnabled = true

        var instance: MediaNotificationService?
            get() = instanceRef?.get()
            private set(value) {
                instanceRef = value?.let { WeakReference(it) }
            }

        var webView: WebView?
            get() = webViewRef?.get()
            set(value) {
                webViewRef = value?.let { WeakReference(it) }
            }
    }

    private val coverExecutor = Executors.newSingleThreadExecutor()

    private lateinit var mediaSession: MediaSessionCompat
    private var isPlaying = false
    private var isShuffle = false
    private var isSmartShuffle = false
    private var isShuffleAvailable = true
    private var isFavorite = false
    private var coverBitmap: Bitmap? = null
    private var currentTitle = ""
    private var currentArtist = ""
    private var currentPosition: Long = 0L
    private var currentDuration: Long = 0L
    private var lastCoverUrl = ""
    private var wakeLock: PowerManager.WakeLock? = null

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PLAY_PAUSE -> {
                    webView?.evaluateJavascript("actPlayPause(${!isPlaying})", null)
                }
                ACTION_NEXT -> webView?.evaluateJavascript("actSkipForward()", null)
                ACTION_PREV -> webView?.evaluateJavascript("actSkipBack()", null)
                ACTION_SHUFFLE -> webView?.evaluateJavascript("actToggleShuffle()", null)
                ACTION_FAVORITE -> webView?.evaluateJavascript("actAddToFav()", null)
            }
        }
    }

    private val audioBecomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                val prefs = getSharedPreferences("spotilol_prefs", MODE_PRIVATE)
                if (prefs.getBoolean("BtAutoPause", false)) pausePlayback()
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val prefs = getSharedPreferences("spotilol_prefs", MODE_PRIVATE)
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (prefs.getBoolean("BtAutoPause", false)) pausePlayback()
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (prefs.getBoolean("BtAutoResume", false)) resumePlayback()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instanceRef = WeakReference(this); instance = this

        try {
            createNotificationChannel()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to create notification channel", e)
        }

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotificationSafe(), getStartForegroundServiceType())
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "Failed to start foreground", e)
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
            registerDisconnectReceivers()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to register disconnect receivers", e)
        }
        isAndAutoEnabled = getSharedPreferences("spotilol_prefs", MODE_PRIVATE)
            .getBoolean("AndAuto", true)
    }

    private fun getStartForegroundServiceType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotificationSafe(), getStartForegroundServiceType())
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "Failed to re-assert foreground", e)
        }
        try {
            if (::mediaSession.isInitialized) {
                MediaButtonReceiver.handleIntent(mediaSession, intent)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to handle media button intent", e)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            coverBitmap = null
            lastCoverUrl = ""
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        coverExecutor.shutdown()
        instanceRef = null
        try { unregisterReceiver(actionReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(bluetoothReceiver) } catch (_: Exception) {}
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
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Spotilol media playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "SpotilolSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    webView?.evaluateJavascript("actPlayPause(true)", null)
                }

                override fun onPause() {
                    webView?.evaluateJavascript("actPlayPause(false)", null)
                }

                override fun onSkipToNext() {
                    webView?.evaluateJavascript("actSkipForward()", null)
                }

                override fun onSkipToPrevious() {
                    webView?.evaluateJavascript("actSkipBack()", null)
                }

                override fun onStop() {
                    webView?.evaluateJavascript("actPlayPause(false)", null)
                }

                override fun onSeekTo(pos: Long) {
                    webView?.evaluateJavascript("actSeek($pos)", null)
                }

                override fun onCustomAction(action: String?, extras: Bundle?) {
                    when (action) {
                        CUSTOM_ACTION_TOGGLE_FAV -> webView?.evaluateJavascript("actAddToFav()", null)
                        CUSTOM_ACTION_TOGGLE_SHUFFLE -> webView?.evaluateJavascript("actToggleShuffle()", null)
                    }
                }
            })
            isActive = true
        }
    }

    private fun registerReceivers() {
        val customFilter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREV)
            addAction(ACTION_SHUFFLE)
            addAction(ACTION_FAVORITE)
        }
        ContextCompat.registerReceiver(this, actionReceiver, customFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val mediaButtonFilter = IntentFilter(Intent.ACTION_MEDIA_BUTTON)
        ContextCompat.registerReceiver(this, actionReceiver, mediaButtonFilter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun registerDisconnectReceivers() {
        val noisyFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        ContextCompat.registerReceiver(this, audioBecomingNoisyReceiver, noisyFilter, ContextCompat.RECEIVER_EXPORTED)

        val btFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        }
        ContextCompat.registerReceiver(this, bluetoothReceiver, btFilter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun pausePlayback() {
        isPlaying = false
        updatePlaybackState()
        showNotification()
        if (::mediaSession.isInitialized) {
            try {
                mediaSession.controller.transportControls.pause()
            } catch (_: Exception) {}
        }
        webView?.evaluateJavascript("actPlayPause(false)", null)
    }

    private fun resumePlayback() {
        isPlaying = true
        updatePlaybackState()
        showNotification()
        if (::mediaSession.isInitialized) {
            try {
                mediaSession.controller.transportControls.play()
            } catch (_: Exception) {}
        }
        webView?.evaluateJavascript("actPlayPause(true)", null)
    }

    fun updateFromMediaStatus(json: String) {
        try {
            val obj = org.json.JSONObject(json)
            val andAuto = getSharedPreferences("spotilol_prefs", MODE_PRIVATE)
                .getBoolean("AndAuto", true)

            if (andAuto) {
                currentTitle = obj.optString("track", "")
                currentArtist = obj.optString("artist", "")
                val coverUrl = obj.optString("cover", "")

                if (coverUrl.isNotEmpty() && coverUrl != "null" && coverUrl != lastCoverUrl) {
                    lastCoverUrl = coverUrl
                    loadCoverArt(coverUrl)
                } else if (coverUrl.isEmpty() || coverUrl == "null") {
                    lastCoverUrl = ""
                    coverBitmap = null
                }
            } else {
                if (currentTitle.isNotEmpty() || currentArtist.isNotEmpty() || coverBitmap != null) {
                    currentTitle = ""
                    currentArtist = ""
                    lastCoverUrl = ""
                    coverBitmap = null
                }
            }

            isPlaying = obj.optBoolean("playing", false)
            isFavorite = obj.optBoolean("fav", false)
            val shuffleVal = obj.optString("shuffle", "off")
            isShuffle = shuffleVal == "shuffle" || shuffleVal == "smart"
            isSmartShuffle = shuffleVal == "smart"
            isShuffleAvailable = shuffleVal != "disabled"
            currentDuration = obj.optLong("duration", 0L)
            currentPosition = obj.optLong("position", 0L)

            if (isPlaying) acquireWakeLock() else releaseWakeLock()

            mainHandler.post {
                updatePlaybackState()
                updateMetadata()
                showNotification()
            }
        } catch (_: Exception) {}
    }

    fun updatePlaybackPosition(position: Long) {
        currentPosition = position
        updatePlaybackState()
    }

    private fun updatePlaybackState() {
        val favIcon = if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite
        val shuffleIcon = when {
            isSmartShuffle -> R.drawable.ic_shuffle_smart_active
            isShuffle -> R.drawable.ic_shuffle_active
            else -> R.drawable.ic_shuffle
        }
        val state = PlaybackStateCompat.Builder()
            .setActions(PLAYBACK_ACTIONS)
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING
                else PlaybackStateCompat.STATE_PAUSED,
                currentPosition, if (isPlaying) 1f else 0f
            )
            .addCustomAction(
                CUSTOM_ACTION_TOGGLE_FAV,
                if (isFavorite) "Unlike" else "Like",
                favIcon
            )
            .addCustomAction(
                CUSTOM_ACTION_TOGGLE_SHUFFLE,
                when {
                    isSmartShuffle -> "Disable smart shuffle"
                    isShuffle -> "Disable shuffle"
                    else -> "Enable shuffle"
                },
                shuffleIcon
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
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Spotilol")
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

    private fun loadCoverArt(url: String) {
        // FIX: was a raw Thread per cover - album-flipping machine-gunned the
        // scheduler. Single-thread executor serializes fetches (covers arrive in
        // order, no stale-bitmap race between back-to-back track changes) and
        // keeps exactly one worker alive for the service's lifetime.
        coverExecutor.execute {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.connect()
                val stream = conn.inputStream
                val raw = BitmapFactory.decodeStream(stream)
                stream.close()
                if (raw != null) {
                    val powerSave = getSharedPreferences("spotilol_prefs", MODE_PRIVATE).getBoolean("PowerSave", false)
                    val target = if (powerSave) 192 else 512
                    val scale = min(target.toFloat() / raw.width, target.toFloat() / raw.height)
                    val w = (raw.width * scale).toInt()
                    val h = (raw.height * scale).toInt()
                    val scaled = raw.scale(w, h)
                    if (scaled != raw) raw.recycle()
                    coverBitmap = scaled
                    mainHandler.post {
                        updateMetadata()
                        showNotification()
                    }
                }
            } catch (_: Exception) {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }
    }

    private fun showNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
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

    private fun buildMediaStyle(): MediaStyle {
        val style = MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
            .setShowCancelButton(true)
            .setCancelButtonIntent(getActionPendingIntent(ACTION_PLAY_PAUSE))
        if (::mediaSession.isInitialized) {
            style.setMediaSession(mediaSession.sessionToken)
        }
        return style
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
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

        val shuffleAction = NotificationCompat.Action.Builder(
            when {
                isSmartShuffle -> R.drawable.ic_shuffle_smart_active
                isShuffle -> R.drawable.ic_shuffle_active
                else -> R.drawable.ic_shuffle
            },
            when {
                isSmartShuffle -> "Disable smart shuffle"
                isShuffle -> "Disable shuffle"
                else -> "Enable shuffle"
            },
            getActionPendingIntent(ACTION_SHUFFLE)
        ).build()

        val favAction = NotificationCompat.Action.Builder(
            if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite,
            if (isFavorite) "Unlike" else "Like",
            getActionPendingIntent(ACTION_FAVORITE)
        ).build()

        val actions = mutableListOf<NotificationCompat.Action>()
        actions.add(prevAction)
        actions.add(playPauseAction)
        actions.add(nextAction)
        if (isShuffleAvailable) actions.add(shuffleAction)
        actions.add(favAction)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle.ifEmpty { "Spotilol" })
            .setContentText(currentArtist)
            .setSubText("Spotilol")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(NOTIF_COLOR)
            .setStyle(buildMediaStyle())
        actions.forEach { builder.addAction(it) }

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

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "spotilol:media_playback"
            ).apply { acquire(60 * 60 * 1000L) }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
            wakeLock = null
        }
    }
}