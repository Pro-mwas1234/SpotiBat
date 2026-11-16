package com.mwask.bat.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.mwask.bat.PlaybackHost

class WidgetPlaybackAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val action = parameters[ACTION_KEY] ?: return
        val app = context.applicationContext
        val bridge = PlaybackHost.ensure(app)
        val live = WidgetSnapshot.from(bridge.state, bridge.progress).takeIf { it.hasTrack }
        val current = WidgetSync.resolve(live, app)
        when (action) {
            ACTION_TOGGLE -> {
                WidgetSync.expectOptimistic(current.copy(isPlaying = !current.isPlaying))
                bridge.listener.onPlayPause()
            }
            ACTION_NEXT -> bridge.listener.onSkipNext()
            ACTION_PREVIOUS -> bridge.listener.onSkipPrevious()
            ACTION_LIKE -> bridge.listener.onLikeToggle()
        }
        WidgetSync.start(app)
        WidgetSync.refreshSoon(app)
    }

    companion object {
        val ACTION_KEY = ActionParameters.Key<String>("spotibat.widget.action")
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_NEXT = "next"
        const val ACTION_PREVIOUS = "previous"
        const val ACTION_LIKE = "like"
    }
}
