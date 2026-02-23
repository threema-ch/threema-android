package ch.threema.app.widget

import android.content.Context

class WidgetUpdater(
    private val appContext: Context,
) {
    fun updateWidgets() {
        WidgetUtil.updateWidgets(appContext)
    }
}
