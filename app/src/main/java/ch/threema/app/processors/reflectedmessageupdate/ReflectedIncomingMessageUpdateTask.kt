package ch.threema.app.processors.reflectedmessageupdate

import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.common.GroupIdentity
import ch.threema.protobuf.d2d.ConversationId
import ch.threema.protobuf.d2d.IncomingMessageUpdate
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.group.GroupMessageModel
import java.util.Date

private val logger = getThreemaLogger("ReflectedIncomingMessageUpdateTask")

class ReflectedIncomingMessageUpdateTask(
    private val incomingMessageUpdate: IncomingMessageUpdate,
    serviceManager: ServiceManager,
) {
    private val messageService by lazy { serviceManager.messageService }
    private val contactService by lazy { serviceManager.contactService }
    private val groupService by lazy { serviceManager.groupService }
    private val notificationService by lazy { serviceManager.notificationService }

    fun run() {
        logger.info("Processing reflected incoming message update")

        incomingMessageUpdate.updatesList.forEach { update ->
            when (update.updateCase) {
                IncomingMessageUpdate.Update.UpdateCase.READ -> applyReadUpdate(update)
                else -> logger.error("Received an unknown incoming message update '${update.updateCase}'")
            }
        }
    }

    private fun applyReadUpdate(update: IncomingMessageUpdate.Update) {
        val conversationId: ConversationId = update.conversation
        val messageId = MessageId(update.messageId)
        val readAt = update.read.at
        when (conversationId.idCase) {
            ConversationId.IdCase.CONTACT -> applyContactMessageReadUpdate(messageId, conversationId.contact, readAt)

            ConversationId.IdCase.GROUP -> applyGroupMessageReadUpdate(messageId, conversationId.group, readAt)

            ConversationId.IdCase.DISTRIBUTION_LIST -> throw IllegalStateException(
                "Received incoming message update for a distribution list",
            )

            ConversationId.IdCase.ID_NOT_SET -> logger.warn("Received incoming message update where id is not set")

            null -> logger.warn("Received incoming message update where id is null")
        }
    }

    private fun applyContactMessageReadUpdate(
        messageId: MessageId,
        senderIdentity: IdentityString,
        readAt: Long,
    ) {
        val abstractMessageModel = messageService.getContactMessageModel(messageId, senderIdentity)
        if (abstractMessageModel == null) {
            logger.warn("Message model for message {} of {} not found", messageId, senderIdentity)
            return
        }

        markMessageModelAsRead(abstractMessageModel, readAt)
    }

    private fun applyGroupMessageReadUpdate(
        messageId: MessageId,
        groupIdentity: GroupIdentity,
        readAt: Long,
    ) {
        val abstractMessageModel = messageService.getGroupMessageModel(
            messageId,
            groupIdentity.creatorIdentity,
            GroupId(groupIdentity.groupId),
        )

        if (abstractMessageModel == null) {
            logger.warn("Group message model for message {} not found", messageId)
            return
        }

        markMessageModelAsRead(abstractMessageModel, readAt)
    }

    private fun markMessageModelAsRead(abstractMessageModel: AbstractMessageModel, readAt: Long) {
        abstractMessageModel.isRead = true
        Date(readAt).let { readAtDate ->
            abstractMessageModel.readAt = readAtDate
            abstractMessageModel.modifiedAt = readAtDate
        }
        messageService.save(abstractMessageModel)
        ListenerManager.messageListeners.handle { l -> l.onModified(listOf(abstractMessageModel)) }
        cancelNotification(abstractMessageModel)
    }

    private fun cancelNotification(abstractMessageModel: AbstractMessageModel) {
        val receiver: MessageReceiver<out AbstractMessageModel>? = when (abstractMessageModel) {
            is MessageModel -> contactService.createReceiver(abstractMessageModel.identity!!)
            is GroupMessageModel -> groupService.getById(abstractMessageModel.groupId)
                ?.let { groupModel ->
                    groupService.createReceiver(groupModel)
                }

            else -> null
        }
        if (receiver == null) {
            logger.error(
                "Failed to determine message receiver for message with id {}",
                abstractMessageModel.apiMessageId,
            )
            return
        }
        notificationService.cancel(receiver)
    }
}
