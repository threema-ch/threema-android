package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.app.tasks.runCommonReactionMessageReceiveEmojiSequenceConversion
import ch.threema.app.tasks.runCommonReactionMessageReceiveSteps
import ch.threema.domain.protocol.csp.messages.GroupReactionMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D.OutgoingMessage
import ch.threema.storage.models.AbstractMessageModel

internal class ReflectedOutgoingGroupReactionTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingGroupMessageTask<GroupReactionMessage>(
    outgoingMessage = outgoingMessage,
    message = GroupReactionMessage.fromReflected(outgoingMessage),
    type = Common.CspE2eMessageType.GROUP_REACTION,
    serviceManager = serviceManager,
) {
    private val messageService = serviceManager.messageService
    private val userService = serviceManager.userService

    override fun processOutgoingMessage() {
        check(outgoingMessage.conversation.hasGroup()) {
            "The message does not have a group identity set"
        }

        val targetMessage: AbstractMessageModel = runCommonReactionMessageReceiveSteps(
            reactionMessage = message,
            receiver = messageReceiver,
            messageService = messageService,
        ) ?: return

        val emojiSequence: String = runCommonReactionMessageReceiveEmojiSequenceConversion(
            emojiSequenceBytes = message.data.emojiSequenceBytes,
        ) ?: return

        messageService.saveEmojiReactionMessage(
            /* targetMessage = */
            targetMessage,
            /* senderIdentity = */
            userService.identity!!,
            /* actionCase = */
            message.data.actionCase,
            /* emojiSequence = */
            emojiSequence,
        )
    }
}
