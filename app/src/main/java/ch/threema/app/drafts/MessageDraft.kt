package ch.threema.app.drafts

import ch.threema.domain.models.MessageId

/**
 * Represents the contents of a message that the user has started to type but hasn't sent yet.
 */
data class MessageDraft(
    val text: String,
    val quotedMessageId: MessageId?,
)
