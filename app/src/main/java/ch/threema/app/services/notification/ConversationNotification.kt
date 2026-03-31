package ch.threema.app.services.notification

import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.core.app.Person
import ch.threema.app.emojis.EmojiMarkupUtil
import ch.threema.app.services.MessageService.MessageString
import ch.threema.app.services.notification.NotificationService.FetchCacheUri
import ch.threema.storage.models.MessageType
import java.time.Instant

class ConversationNotification(
    messageString: MessageString,
    val createdAt: Instant?,
    val id: Int,
    val uid: String?,
    val group: ConversationNotificationGroup,
    private val fetchThumbnailUri: FetchCacheUri?,
    val thumbnailMimeType: String?,
    senderPerson: Person?,
    val messageType: MessageType?,
    val isMessageEdited: Boolean,
    val isMessageDeleted: Boolean,
) {
    val message: CharSequence =
        messageString.message
            ?.let(EmojiMarkupUtil.getInstance()::addTextSpans)
            ?: ""

    val rawMessage: CharSequence = messageString.rawMessage
        ?: ""

    val senderPerson: Person? =
        senderPerson
            ?.takeIf { message.isNotBlank() }

    private var thumbnailUri: Uri? = null

    @WorkerThread
    fun getOrCreateThumbnail(): Uri? {
        if (thumbnailUri == null) {
            thumbnailUri = fetchThumbnailUri?.fetch()
        }
        return thumbnailUri
    }
}
