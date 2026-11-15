package com.quackify.app.widget

import android.content.Context
import android.os.SystemClock
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import com.quackify.app.PlaybackHost
import com.quackify.backend.core.log.Logs
import com.quackify.compose.widget.WidgetOptionsStore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object WidgetSync {

    private const val ACTIVE_INTERVAL_MS = 400L
    private const val IDLE_INTERVAL_MS = 3000L
    private const val ERROR_BACKOFF_MS = 1000L
    private const val OPTIMISTIC_TTL_MS = 4000L

    private val log = Logs.of(Logs.Module.HOST)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private class Override(val snapshot: WidgetSnapshot, val at: Long)

    @Volatile
    private var loop: Job? = null

    @Volatile
    private var optimistic: Override? = null

    @Synchronized
    fun expectOptimistic(expected: WidgetSnapshot) {
        optimistic = Override(expected, SystemClock.elapsedRealtime())
    }

    @Synchronized
    fun resolve(live: WidgetSnapshot?, context: Context): WidgetSnapshot {
        reconcile(live)
        return optimistic?.snapshot ?: live ?: WidgetSnapshotStore.load(context)
    }

    @Synchronized
    private fun overrideActive(): Boolean = optimistic != null

    private fun reconcile(live: WidgetSnapshot?) {
        val current = optimistic ?: return
        if (SystemClock.elapsedRealtime() - current.at >= OPTIMISTIC_TTL_MS) {
            optimistic = null
            return
        }
        if (live != null && live.trackUri.isNotBlank() && current.snapshot.trackUri.isNotBlank() &&
            live.trackUri != current.snapshot.trackUri
        ) {
            optimistic = null
            return
        }
        if (live != null && live.isPlaying == current.snapshot.isPlaying &&
            live.isLiked == current.snapshot.isLiked
        ) {
            optimistic = null
        }
    }

    fun start(context: Context) {
        synchronized(this) {
            if (loop?.isActive == true) return
            val app = context.applicationContext
            loop = scope.launch {
                var lastKey: String? = null
                while (isActive) {
                    try {
                        val bridge = PlaybackHost.bridge
                        val live = bridge
                            ?.let { WidgetSnapshot.from(it.state, it.progress) }
                            ?.takeIf { it.hasTrack }
                        val snapshot = resolve(live, app)
                        val options = WidgetOptionsStore.load(app)
                        val key = "${snapshot.fingerprint()}|$options"
                        if (key != lastKey) {
                            lastKey = key
                            if (live != null && !overrideActive()) {
                                WidgetSnapshotStore.save(app, snapshot)
                            }
                            snapshot.coverUrl.takeIf { it.isNotBlank() }?.let { url ->
                                WidgetArt.prefetch(app, url) { pushNow(app) }
                            }
                            refresh(app)
                        }
                        delay(if (bridge == null) IDLE_INTERVAL_MS else ACTIVE_INTERVAL_MS)
                    } catch (canceled: CancellationException) {
                        throw canceled
                    } catch (t: Throwable) {
                        log.w("widget sync tick failed ${t.javaClass.simpleName}: ${t.message}", t)
                        lastKey = null
                        delay(ERROR_BACKOFF_MS)
                    }
                }
            }
        }
    }

    fun refreshSoon(context: Context) {
        val app = context.applicationContext
        scope.launch {
            refresh(app)
            delay(300L)
            refresh(app)
            delay(700L)
            refresh(app)
            delay(1500L)
            refresh(app)
        }
    }

    private fun pushNow(context: Context) {
        scope.launch { refresh(context) }
    }

    suspend fun refresh(context: Context) {
        updateAllSafely(QuackifyWidget4x1(), context)
        updateAllSafely(QuackifyWidget3x2(), context)
        updateAllSafely(QuackifyWidget3x3(), context)
        try {
            HomeWidget.updateAllWidgets(context)
        } catch (t: Throwable) {
            log.w("HomeWidget update failed: ${t.message}", t)
        }
        try {
            MusicCardWidget.updateAllWidgets(context)
        } catch (t: Throwable) {
            log.w("MusicCardWidget update failed: ${t.message}", t)
        }
        try {
            SquareArtWidget.updateAllWidgets(context)
        } catch (t: Throwable) {
            log.w("SquareArtWidget update failed: ${t.message}", t)
        }
    }

    private suspend fun updateAllSafely(widget: GlanceAppWidget, context: Context) {
        try {
            widget.updateAll(context)
        } catch (canceled: CancellationException) {
            throw canceled
        } catch (t: Throwable) {
            log.w("widget update failed ${t.javaClass.simpleName}: ${t.message}", t)
        }
    }
}
