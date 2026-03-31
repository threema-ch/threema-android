package ch.threema.app.services.notification

import android.graphics.Bitmap
import ch.threema.app.messagereceiver.MessageReceiver

data class ConversationNotificationGroup(
    @JvmField
    val uid: String,
    @JvmField
    var name: String,
    @JvmField
    var shortName: String?,
    @JvmField
    val messageReceiver: MessageReceiver<*>,
    private val onFetchAvatar: () -> Bitmap?,
) {
    @JvmField
    var lastNotificationDate: Long = 0L

    @JvmField
    val conversations: MutableList<ConversationNotification> = mutableListOf()

    @JvmField
    val notificationId: Int = messageReceiver.uniqueId

    fun loadAvatar(): Bitmap? = onFetchAvatar()
}
