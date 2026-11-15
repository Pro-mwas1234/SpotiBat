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
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import com.quackify.app.MainActivity
import com.quackify.app.PlaybackHost
import com.quackify.app.PlaybackService
import com.quackify.app.R
import com.quackify.compose.components.resolveRemoteImageUrl

class SquareArtWidget : AppWidgetProvider() {

    companion object {

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
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, SquareArtWidget::class.java))
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
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_square_art)

            val bridge = PlaybackHost.bridge
            val live = bridge?.let { WidgetSnapshot.from(it.state, it.progress) }?.takeIf { it.hasTrack }
            val snapshot = WidgetSync.resolve(live, context)

            val density = context.resources.displayMetrics.density

            if (snapshot.hasTrack) {
                val artUrl = resolveRemoteImageUrl(snapshot.coverUrl)
                val art = artUrl?.let { url -> runCatching { WidgetArt.cached(url) }.getOrNull() }
                if (art != null && !art.isRecycled) {
                    try {
                        val side = Math.min(art.width, art.height)
                        val square = if (art.width != art.height) {
                            val xOffset = (art.width - side) / 2
                            val yOffset = (art.height - side) / 2
                            Bitmap.createBitmap(art, xOffset, yOffset, side, side)
                        } else art
                        val maxDimension = 360
                        val scale = Math.min(maxDimension.toFloat() / square.width, maxDimension.toFloat() / square.height)
                        val targetW = Math.max(1, (square.width * (if (scale < 1.0f) scale else 1.0f)).toInt())
                        val targetH = Math.max(1, (square.height * (if (scale < 1.0f) scale else 1.0f)).toInt())
                        val scaled = Bitmap.createScaledBitmap(square, targetW, targetH, true)
                        val rounded = roundCorners(scaled, 28f, density)
                        remoteViews.setImageViewBitmap(R.id.img_cover, rounded)
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
                remoteViews.setImageViewResource(R.id.img_cover, R.drawable.logo_duck)
            }

            // Click opens the player in app
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                putExtra(PlaybackService.EXTRA_OPEN_PLAYER, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(context, 201, mainIntent, flags)
            remoteViews.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            remoteViews.setOnClickPendingIntent(R.id.img_cover, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
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
