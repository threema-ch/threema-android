package ch.threema.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.getSystemService
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("PowerSaveModeReceiver")

class PowerSaveModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
            val powerManager = context.applicationContext.getSystemService<PowerManager>()
                ?: return
            if (powerManager.isPowerSaveMode) {
                logger.info("Power Save Mode enabled")
            } else {
                logger.info("Power Save Mode disabled")
            }
        }
    }
}
