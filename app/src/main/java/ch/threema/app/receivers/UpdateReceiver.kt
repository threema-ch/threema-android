package ch.threema.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ch.threema.app.utils.DownloadUtil
import ch.threema.app.utils.PushUtil
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("UpdateReceiver")

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            logger.info("*** App was updated ***")

            DownloadUtil.deleteOldAPKs(context)

            // force token register
            PushUtil.clearPushTokenSentDate(context)
        }
    }
}
