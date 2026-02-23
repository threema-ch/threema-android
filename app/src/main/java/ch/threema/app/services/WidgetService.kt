package ch.threema.app.services

import android.content.Intent
import android.widget.RemoteViewsService
import ch.threema.app.adapters.WidgetViewsFactory

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory =
        WidgetViewsFactory(applicationContext)
}
