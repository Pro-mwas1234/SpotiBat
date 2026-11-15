package com.quackify.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.quackify.app.MainActivity
import com.quackify.app.PlaybackHost
import com.quackify.app.PlaybackService
import com.quackify.app.R
import com.quackify.compose.components.RepeatMode
import com.quackify.compose.components.ShuffleMode
import com.quackify.compose.components.resolveRemoteImageUrl

class HomeWidget : AppWidgetProvider() {

    companion object {

        private const val ACTION_PLAYPAUSE = "com.quackify.app.widget.WIDGET_PLAYPAUSE"
        private const val ACTION_PREV = "com.quackify.app.widget.WIDGET_PREV"
        private const val ACTION_NEXT = "com.quackify.app.widget.WIDGET_NEXT"
        private const val ACTION_REPEAT = "com.quackify.app.widget.REPEAT_ACTION"
        private const val ACTION_SHUFFLE = "com.quackify.app.widget.SHUFFLE_ACTION"
        private const val ACTION_FAV = "com.quackify.app.widget.ADDTOFAV_ACTION"

        private val COLOR_ACTIVE = Color.WHITE
        private val COLOR_INACTIVE = Color.parseColor("#808080")

        private val handler = Handler(Looper.getMainLooper())
        private var isUpdateRunning = false
        private var appContext: Context? = null

        private val updateRunnable = object : Runnable {
            override fun run() {
                val context = appContext
                val bridge = PlaybackHost.bridge
                if (context != null && bridge != null && bridge.state.isPlaying) {
                    updateAllWidgets(context)
                    handler.postDelayed(this, 2000L)
                } else {
                    isUpdateRunning = false
                }
            }
        }

        @JvmStatic
        fun updateAllWidgets(context: Context) {
            appContext = context.applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, HomeWidget::class.java))
            if (appWidgetIds.isEmpty()) return
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId)
            }

            val bridge = PlaybackHost.bridge
            if (bridge != null && bridge.state.isPlaying && !isUpdateRunning) {
                isUpdateRunning = true
                handler.post(updateRunnable)
            }
        }

        private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val rowSpan = if (minHeight > 0) ((minHeight + 30) / 70).coerceAtLeast(1) else 1
            val isExpanded = rowSpan >= 2
            val layoutId = if (isExpanded) R.layout.widget_expanded else R.layout.home_widget
            val remoteViews = RemoteViews(context.packageName, layoutId)

            val bridge = PlaybackHost.bridge
            val live = bridge?.let { WidgetSnapshot.from(it.state, it.progress) }?.takeIf { it.hasTrack }
            val snapshot = WidgetSync.resolve(live, context)
            val repeatMode = bridge?.state?.repeatMode ?: RepeatMode.OFF
            val shuffleMode = bridge?.state?.shuffleMode ?: ShuffleMode.OFF

            if (isExpanded) {
                remoteViews.setChronometer(R.id.txt_position_chrono, SystemClock.elapsedRealtime(), null, false)
                remoteViews.setViewVisibility(R.id.txt_position, View.VISIBLE)
                remoteViews.setViewVisibility(R.id.txt_position_chrono, View.GONE)
            }

            if (snapshot.hasTrack) {
                val title = snapshot.title
                val artist = snapshot.artist
                val isPlaying = snapshot.isPlaying
                val isLiked = snapshot.isLiked
                val duration = snapshot.durationMs
                val position = snapshot.positionMs

                val (repeatRes, repeatColor) = when (repeatMode) {
                    RepeatMode.REPEAT_ONE -> Pair(R.drawable.repone, COLOR_ACTIVE)
                    RepeatMode.REPEAT_ALL -> Pair(R.drawable.repon, COLOR_ACTIVE)
                    else -> Pair(R.drawable.repoff, COLOR_INACTIVE)
                }

                val (shuffleRes, shuffleColor) = when (shuffleMode) {
                    ShuffleMode.SMART_SHUFFLE -> Pair(R.drawable.smart_shuffle, COLOR_ACTIVE)
                    ShuffleMode.SHUFFLE -> Pair(R.drawable.shuffle, COLOR_ACTIVE)
                    else -> Pair(R.drawable.shuffle, COLOR_INACTIVE)
                }

                val favRes = if (isLiked) R.drawable.favon else R.drawable.favoff
                val favColor = if (isLiked) COLOR_ACTIVE else COLOR_INACTIVE

                if (isExpanded) {
                    remoteViews.setTextViewText(R.id.txt_title, title.ifBlank { context.getString(R.string.txt_notrack) })
                    remoteViews.setTextViewText(R.id.txt_artist, artist.ifBlank { context.getString(R.string.txt_noartist) })
                    remoteViews.setImageViewResource(R.id.btn_play, if (isPlaying) R.drawable.pause else R.drawable.play)

                    remoteViews.setImageViewResource(R.id.btn_repeat, repeatRes)
                    remoteViews.setInt(R.id.btn_repeat, "setColorFilter", repeatColor)

                    remoteViews.setImageViewResource(R.id.btn_shuffle, shuffleRes)
                    remoteViews.setInt(R.id.btn_shuffle, "setColorFilter", shuffleColor)

                    remoteViews.setImageViewResource(R.id.btn_fav, favRes)
                    remoteViews.setInt(R.id.btn_fav, "setColorFilter", favColor)

                    if (isPlaying) {
                        remoteViews.setViewVisibility(R.id.txt_position, View.GONE)
                        remoteViews.setViewVisibility(R.id.txt_position_chrono, View.VISIBLE)
                        remoteViews.setChronometer(
                            R.id.txt_position_chrono,
                            SystemClock.elapsedRealtime() - position,
                            null,
                            true,
                        )
                    } else {
                        remoteViews.setViewVisibility(R.id.txt_position, View.VISIBLE)
                        remoteViews.setViewVisibility(R.id.txt_position_chrono, View.GONE)
                        remoteViews.setChronometer(R.id.txt_position_chrono, SystemClock.elapsedRealtime(), null, false)
                        remoteViews.setTextViewText(R.id.txt_position, formatTime(position))
                    }

                    remoteViews.setTextViewText(R.id.txt_duration, formatTime(duration))
                    val progressInt = if (duration > 0) (position * 1000L / duration).toInt().coerceIn(0, 1000) else 0
                    remoteViews.setProgressBar(R.id.progress_bar, 1000, progressInt, false)
                } else {
                    val fullTitle = if (artist.isNotBlank() && title.isNotBlank()) {
                        "$artist - $title"
                    } else {
                        title.ifBlank { artist.ifBlank { context.getString(R.string.txt_notrack) } }
                    }
                    remoteViews.setTextViewText(R.id.txt_fulltitle, fullTitle)
                    remoteViews.setImageViewResource(R.id.btn_wplay, if (isPlaying) R.drawable.pause else R.drawable.play)

                    remoteViews.setImageViewResource(R.id.btn_wrepeat, repeatRes)
                    remoteViews.setInt(R.id.btn_wrepeat, "setColorFilter", repeatColor)

                    remoteViews.setImageViewResource(R.id.btn_wfav, favRes)
                    remoteViews.setInt(R.id.btn_wfav, "setColorFilter", favColor)
                }

                // Artwork with native rounded corners (expanded mode only; slim widget stays flush)
                val artUrl = resolveRemoteImageUrl(snapshot.coverUrl)
                val art = artUrl?.let { url -> runCatching { WidgetArt.cached(url) }.getOrNull() }
                if (art != null && !art.isRecycled) {
                    try {
                        val maxDimension = 256
                        val scale = Math.min(maxDimension.toFloat() / art.width, maxDimension.toFloat() / art.height)
                        val targetW = Math.max(1, (art.width * (if (scale < 1.0f) scale else 1.0f)).toInt())
                        val targetH = Math.max(1, (art.height * (if (scale < 1.0f) scale else 1.0f)).toInt())
                        val scaled = Bitmap.createScaledBitmap(art, targetW, targetH, true)
                        if (isExpanded) {
                            val density = context.resources.displayMetrics.density
                            val rounded = roundCorners(scaled, 12f, density)
                            remoteViews.setImageViewBitmap(R.id.img_cover, rounded)
                        } else {
                            val safeBitmap = if (scaled.config != Bitmap.Config.RGB_565) {
                                val rgbBitmap = scaled.copy(Bitmap.Config.RGB_565, false)
                                if (scaled != art && !scaled.isRecycled) scaled.recycle()
                                rgbBitmap ?: scaled
                            } else {
                                scaled
                            }
                            remoteViews.setImageViewBitmap(R.id.img_cover, safeBitmap)
                        }
                    } catch (e: Throwable) {
                        remoteViews.setImageViewResource(R.id.img_cover, R.drawable.logo_duck)
                    }
                } else {
                    remoteViews.setImageViewResource(R.id.img_cover, R.drawable.logo_duck)
                    if (artUrl != null) {
                        WidgetArt.prefetch(context.applicationContext, artUrl) {
                            updateAllWidgets(context)
                        }
                    }
                }
            } else {
                // Idle state
                if (isExpanded) {
                    remoteViews.setTextViewText(R.id.txt_title, context.getString(R.string.txt_notrack))
                    remoteViews.setTextViewText(R.id.txt_artist, context.getString(R.string.txt_noartist))
                    remoteViews.setImageViewResource(R.id.btn_play, R.drawable.play)
                    remoteViews.setImageViewResource(R.id.btn_repeat, R.drawable.repoff)
                    remoteViews.setInt(R.id.btn_repeat, "setColorFilter", COLOR_INACTIVE)
                    remoteViews.setImageViewResource(R.id.btn_shuffle, R.drawable.shuffle)
                    remoteViews.setInt(R.id.btn_shuffle, "setColorFilter", COLOR_INACTIVE)
                    remoteViews.setImageViewResource(R.id.btn_fav, R.drawable.favoff)
                    remoteViews.setInt(R.id.btn_fav, "setColorFilter", COLOR_INACTIVE)
                    remoteViews.setViewVisibility(R.id.txt_position, View.VISIBLE)
                    remoteViews.setViewVisibility(R.id.txt_position_chrono, View.GONE)
                    remoteViews.setChronometer(R.id.txt_position_chrono, SystemClock.elapsedRealtime(), null, false)
                    remoteViews.setTextViewText(R.id.txt_position, "0:00")
                    remoteViews.setTextViewText(R.id.txt_duration, "0:00")
                    remoteViews.setProgressBar(R.id.progress_bar, 1000, 0, false)
                } else {
                    remoteViews.setTextViewText(R.id.txt_fulltitle, context.getString(R.string.txt_notrack))
                    remoteViews.setImageViewResource(R.id.btn_wplay, R.drawable.play)
                    remoteViews.setImageViewResource(R.id.btn_wrepeat, R.drawable.repoff)
                    remoteViews.setInt(R.id.btn_wrepeat, "setColorFilter", COLOR_INACTIVE)
                    remoteViews.setImageViewResource(R.id.btn_wfav, R.drawable.favoff)
                    remoteViews.setInt(R.id.btn_wfav, "setColorFilter", COLOR_INACTIVE)
                }
                remoteViews.setImageViewResource(R.id.img_cover, R.drawable.logo_duck)
            }

            // Click Handlers
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                putExtra(PlaybackService.EXTRA_OPEN_PLAYER, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val mainPendingIntent = PendingIntent.getActivity(context, appWidgetId, mainIntent, getFlags())
            remoteViews.setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)

            if (isExpanded) {
                remoteViews.setOnClickPendingIntent(R.id.btn_play, createPendingIntent(context, ACTION_PLAYPAUSE))
                remoteViews.setOnClickPendingIntent(R.id.btn_prev, createPendingIntent(context, ACTION_PREV))
                remoteViews.setOnClickPendingIntent(R.id.btn_next, createPendingIntent(context, ACTION_NEXT))
                remoteViews.setOnClickPendingIntent(R.id.btn_repeat, createPendingIntent(context, ACTION_REPEAT))
                remoteViews.setOnClickPendingIntent(R.id.btn_shuffle, createPendingIntent(context, ACTION_SHUFFLE))
                remoteViews.setOnClickPendingIntent(R.id.btn_fav, createPendingIntent(context, ACTION_FAV))
            } else {
                remoteViews.setOnClickPendingIntent(R.id.btn_wplay, createPendingIntent(context, ACTION_PLAYPAUSE))
                remoteViews.setOnClickPendingIntent(R.id.btn_wprev, createPendingIntent(context, ACTION_PREV))
                remoteViews.setOnClickPendingIntent(R.id.btn_wnext, createPendingIntent(context, ACTION_NEXT))
                remoteViews.setOnClickPendingIntent(R.id.btn_wrepeat, createPendingIntent(context, ACTION_REPEAT))
                remoteViews.setOnClickPendingIntent(R.id.btn_wfav, createPendingIntent(context, ACTION_FAV))
            }

            try {
                appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            } catch (e: Throwable) {
                // Fallback in case bitmap allocation limits exceed RemoteViews threshold
                try {
                    val fallbackViews = RemoteViews(context.packageName, layoutId)
                    fallbackViews.setImageViewResource(R.id.img_cover, R.drawable.logo_duck)
                    if (isExpanded) {
                        fallbackViews.setTextViewText(R.id.txt_title, snapshot.title)
                        fallbackViews.setTextViewText(R.id.txt_artist, snapshot.artist)
                    } else {
                        val fullTitle = if (snapshot.artist.isNotBlank() && snapshot.title.isNotBlank()) {
                            "${snapshot.artist} - ${snapshot.title}"
                        } else {
                            snapshot.title
                        }
                        fallbackViews.setTextViewText(R.id.txt_fulltitle, fullTitle)
                    }
                    fallbackViews.setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)
                    appWidgetManager.updateAppWidget(appWidgetId, fallbackViews)
                } catch (ignored: Throwable) {}
            }
        }

        private fun createPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, HomeWidget::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(context, action.hashCode(), intent, getFlags())
        }

        private fun getFlags(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

        private fun formatTime(millis: Long): String {
            if (millis <= 0L) return "0:00"
            val totalSeconds = millis / 1000L
            val seconds = totalSeconds % 60L
            val minutes = totalSeconds / 60L
            return "%d:%02d".format(minutes, seconds)
        }

        private fun roundCorners(bitmap: Bitmap, cornerRadiusDp: Float, density: Float): Bitmap {
            val cornerRadiusPx = cornerRadiusDp * density
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            paint.shader = shader
            val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
            return output
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val app = context.applicationContext
        val bridge = PlaybackHost.ensure(app)

        when (action) {
            ACTION_PLAYPAUSE -> {
                val live = bridge.let { WidgetSnapshot.from(it.state, it.progress) }.takeIf { it.hasTrack }
                val current = WidgetSync.resolve(live, app)
                WidgetSync.expectOptimistic(current.copy(isPlaying = !current.isPlaying))
                bridge.listener.onPlayPause()
                updateAllWidgets(app)
            }
            ACTION_PREV -> {
                bridge.listener.onSkipPrevious()
                updateAllWidgets(app)
            }
            ACTION_NEXT -> {
                bridge.listener.onSkipNext()
                updateAllWidgets(app)
            }
            ACTION_REPEAT -> {
                bridge.listener.onRepeatToggle()
                updateAllWidgets(app)
            }
            ACTION_SHUFFLE -> {
                bridge.listener.onShuffleToggle()
                updateAllWidgets(app)
            }
            ACTION_FAV -> {
                bridge.listener.onLikeToggle()
                updateAllWidgets(app)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        updateWidget(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }
}
