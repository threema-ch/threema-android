package ch.threema.app.receivers

import android.content.Context
import android.content.Intent
import ch.threema.app.managers.ListenerManager
import ch.threema.app.utils.IntentDataUtil

/**
 * Receive the message models that could not be sent when the notification has been explicitly
 * canceled.
 */
class CancelResendMessagesBroadcastReceiver : ActionBroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // It is sufficient to trigger the listener. If the home activity (that manages resending
        // the messages) is not available, this event can be dismissed anyway.
        IntentDataUtil.getAbstractMessageModels(intent, messageService).forEach { messageModel ->
            ListenerManager.messageListeners.handle {
                it.onResendDismissed(messageModel)
            }
        }
    }
}
