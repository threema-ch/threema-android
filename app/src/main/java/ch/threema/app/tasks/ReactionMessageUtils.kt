package ch.threema.app.tasks

import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.MessageService
import ch.threema.app.utils.MessageUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupReactionMessage
import ch.threema.domain.protocol.csp.messages.ReactionMessage
import ch.threema.storage.models.AbstractMessageModel
import com.google.protobuf.ByteString
import kotlin.text.isNotEmpty

private val logger = getThreemaLogger("ReactionMessageUtils")

private const val MAX_EMOJI_SEQUENCE_BYTE_SIZE = 64

fun runCommonReactionMessageReceiveSteps(
    reactionMessage: ReactionMessage,
    receiver: MessageReceiver<*>,
    messageService: MessageService,
): AbstractMessageModel? = runCommonReactionMessageReceiveSteps(
    emojiSequenceBytes = reactionMessage.data.emojiSequenceBytes,
    messageId = reactionMessage.data.messageId,
    receiver = receiver,
    messageService = messageService,
)

fun runCommonReactionMessageReceiveSteps(
    reactionMessage: GroupReactionMessage,
    receiver: MessageReceiver<*>,
    messageService: MessageService,
): AbstractMessageModel? = runCommonReactionMessageReceiveSteps(
    emojiSequenceBytes = reactionMessage.data.emojiSequenceBytes,
    messageId = reactionMessage.data.messageId,
    receiver = receiver,
    messageService = messageService,
)

private fun runCommonReactionMessageReceiveSteps(
    emojiSequenceBytes: ByteString?,
    messageId: Long,
    receiver: MessageReceiver<*>,
    messageService: MessageService,
): AbstractMessageModel? {
    val apiMessageId = MessageId(messageId).toString()
    val message = messageService.getMessageModelByApiMessageIdAndReceiver(apiMessageId, receiver)

    if (message == null) {
        logger.warn("Incoming Reaction Message: No message found for id: $apiMessageId")
        return null
    }

    if (!MessageUtil.canEmojiReact(message)) {
        logger.warn(
            "Incoming Reaction Message: Message of type {} cannot be reacted to",
            message.type,
        )
        return null
    }

    if (!isValidReactionSequence(emojiSequenceBytes)) {
        logger.warn("Incoming Reaction Message: Invalid emoji sequence")
        return null
    }

    return message
}

/**
 * Returns the emoji byte string as a UTF-8 string or null if the string is empty or null.
 */
fun runCommonReactionMessageReceiveEmojiSequenceConversion(
    emojiSequenceBytes: ByteString?,
): String? = emojiSequenceBytes
    ?.toStringUtf8()
    ?.takeIf(String::isNotEmpty)
    ?: run {
        logger.warn("Incoming Reaction Message: Emoji sequence is empty or null.")
        null
    }

private fun isValidReactionSequence(reactionSequence: ByteString?): Boolean =
    reactionSequence != null &&
        !reactionSequence.isEmpty &&
        reactionSequence.size() <= MAX_EMOJI_SEQUENCE_BYTE_SIZE
