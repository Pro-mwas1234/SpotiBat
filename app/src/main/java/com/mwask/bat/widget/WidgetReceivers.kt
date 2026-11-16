package com.mwask.bat.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

abstract class SpotiBatWidgetReceiver(private val widget: GlanceAppWidget) : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget get() = widget

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetSync.start(context)
    }
}

class SpotiBatWidget4x1Receiver : SpotiBatWidgetReceiver(SpotiBatWidget4x1())

class SpotiBatWidget3x2Receiver : SpotiBatWidgetReceiver(SpotiBatWidget3x2())

class SpotiBatWidget3x3Receiver : SpotiBatWidgetReceiver(SpotiBatWidget3x3())
