package ch.threema.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import ch.threema.app.R
import ch.threema.app.receivers.WidgetProvider

object WidgetUtil {
    @JvmStatic
    fun updateWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java))
        appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.widget_list)
    }
}
