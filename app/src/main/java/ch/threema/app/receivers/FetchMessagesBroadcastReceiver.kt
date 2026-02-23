package ch.threema.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ch.threema.app.services.PollingHelper
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("FetchMessagesBroadcastReceiver")

class FetchMessagesBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        logger.info("FetchMessagesBroadcastReceiver: onReceive")
        PollingHelper(context, "retryFromAlarmManager").poll(true)
    }
}
