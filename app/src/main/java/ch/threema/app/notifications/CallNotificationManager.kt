package ch.threema.app.notifications

import android.app.PendingIntent
import ch.threema.data.models.ContactModelData

interface CallNotificationManager {
    fun showIncomingCallNotification(
        contactModelData: ContactModelData,
        acceptIntent: PendingIntent,
        inCallPendingIntent: PendingIntent,
        rejectIntent: PendingIntent,
    ): IncomingCallNotificationResult

    sealed interface IncomingCallNotificationResult {
        object Success : IncomingCallNotificationResult
        object IncomingCallNotificationsDisabled : IncomingCallNotificationResult
    }
}
