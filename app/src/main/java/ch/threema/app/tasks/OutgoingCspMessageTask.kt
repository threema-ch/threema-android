/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.tasks

import ch.threema.app.listeners.MessageListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.utils.OutgoingCspContactMessageCreator
import ch.threema.app.utils.OutgoingCspGroupMessageCreator
import ch.threema.app.utils.filterBroadcastIdentity
import ch.threema.app.utils.filterValid
import ch.threema.app.utils.sendContactMessage
import ch.threema.app.utils.sendGroupMessage
import ch.threema.app.utils.toKnownContactModels
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.fs.BadDHStateException
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityMode
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.catchExceptNetworkException
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.GroupModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageState
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("OutgoingCspMessageTask")

sealed class OutgoingCspMessageTask(serviceManager: ServiceManager) :
    ActiveTask<Unit>, PersistableTask {
    private val myIdentity by lazy { serviceManager.userService.identity }
    private val contactService by lazy { serviceManager.contactService }
    private val groupService by lazy { serviceManager.groupService }
    private val contactStore by lazy { serviceManager.contactStore }
    private val identityStore by lazy { serviceManager.identityStore }
    private val nonceFactory by lazy { serviceManager.nonceFactory }
    private val forwardSecurityMessageProcessor by lazy { serviceManager.forwardSecurityMessageProcessor }
    private val messageService by lazy { serviceManager.messageService }
    private val databaseService by lazy { serviceManager.databaseServiceNew }
    private val rejectedGroupMessageFactory by lazy { databaseService.rejectedGroupMessageFactory }

    // It is important that the task creator is loaded lazily, as the task archiver may instantiate
    // this class before the connection is initialized (which is used for the task creator).
    private val taskCreator by lazy { serviceManager.taskCreator }
    private val blackListService by lazy { serviceManager.blackListService }

    /**
     * Encapsulate and send the given message. Note that the message must be ready to be sent. This
     * includes having the correct flags, from and to identities as well as a message id. If there
     * is a message model to this message, it should also be passed to this method, so that it gets
     * updated.
     *
     * Note that there must exists a contact model of the receiver. Otherwise, the message cannot be
     * sent. In this case the message model state is set to [MessageState.SENDFAILED] (if provided)
     * and an [IllegalStateException] is thrown.
     */
    suspend fun sendContactMessage(
        message: AbstractMessage,
        messageModel: MessageModel?,
        handle: ActiveTaskCodec,
    ) {
        val recipient = contactService.getByIdentity(message.toIdentity)
        if (recipient == null) {
            logger.error(
                "Could not send message to {} as the contact model is null",
                message.toIdentity
            )
            messageModel?.let {
                messageService.updateMessageState(
                    it,
                    MessageState.SENDFAILED,
                    Date()
                )
            }
            throw IllegalStateException("Could not send message as the receiver model is unknown")
        }

        message.date = messageModel?.createdAt ?: message.date

        val sentMessageContainer = suspend {
            handle.sendContactMessage(
                OutgoingCspContactMessageCreator(message.messageId) { message },
                recipient,
                forwardSecurityMessageProcessor,
                identityStore,
                contactStore,
                nonceFactory,
                blackListService,
                taskCreator
            )
        }.catchExceptNetworkException { e: BadDHStateException ->
            if (messageModel != null) {
                messageService.updateMessageState(messageModel, MessageState.SENDFAILED, Date())
            }
            throw e
        }

        if (messageModel != null) {
            if (sentMessageContainer != null) {
                // Update the message state for the outgoing message
                val sentDate = Date(sentMessageContainer.sentTimestamp.toLong())
                messageService.updateMessageState(messageModel, MessageState.SENT, sentDate)

                // Set forward security mode
                messageModel.forwardSecurityMode = sentMessageContainer.forwardSecurityMode
            } else {
                // In this case the message was not sent because the recipient is blocked. This
                // should never happen as sending a message that corresponds to a message model
                // should be prevented by the UI. If the user sends a message and then blocks the
                // recipient, the message may end in this state if the message could not be sent in
                // the meanwhile.
                messageService.updateMessageState(messageModel, MessageState.SENDFAILED, Date())
            }

            // Save the updated message model
            messageService.save(messageModel)
        }
    }

    /**
     * Send the group message to the recipients following the _Common Send Steps_. The message is
     * only sent to those recipients that are still part of the group.
     *
     * Note that the message is only sent to valid members and if the member is not blocked (except
     * the message should be exempted from blocking). If there is no contact model for a member, the
     * message will not be sent to this member.
     */
    suspend fun sendGroupMessage(
        group: GroupModel,
        recipients: Collection<String>,
        messageModel: GroupMessageModel?,
        messageId: MessageId,
        createAbstractMessage: () -> AbstractGroupMessage,
        handle: ActiveTaskCodec,
    ) {
        if (!groupService.isGroupMember(group)) {
            logger.warn("The user is no member of the group and the message is therefore not sent")
            return
        }

        // Get the known contacts
        val finalRecipients = recipients
            .toKnownContactModels(contactService)
            .filterValid()
            .filterBroadcastIdentity(group)
            .toSet()

        // Create and send the messages for all recipients
        val sentMessageContainers = suspend {
            handle.sendGroupMessage(
                OutgoingCspGroupMessageCreator(messageId, group) { createAbstractMessage() },
                finalRecipients,
                group,
                forwardSecurityMessageProcessor,
                identityStore,
                contactStore,
                nonceFactory,
                groupService,
                blackListService,
                taskCreator
            )
        }.catchExceptNetworkException { e: BadDHStateException ->
            if (messageModel != null) {
                messageService.updateMessageState(messageModel, MessageState.SENDFAILED, Date())
            }
            throw e
        }

        if (sentMessageContainers == null) {
            // If the message has not been sent (sentMessageContainers is null), then update the
            // message state (if available) and return. Note that this only happens, if the user is
            // not a member of the group.
            if (messageModel != null) {
                // Note that this should be prevented by the UI, but may happen with some bad timing
                messageModel.state = MessageState.SENDFAILED
                messageService.save(messageModel)
            }

            return
        }

        sentMessageContainers.map { it.recipient.identity }.forEach {
            rejectedGroupMessageFactory.removeMessageReject(messageId, it, group)
        }

        // Update the message state as all messages have been sent now
        if (messageModel != null) {
            // If there is no recipient (notes group), then we set the message state directly to
            // read, otherwise sent. If there are (still) some rejected identities, we set the state
            // to fs key mismatch, so that the message can be sent again to those. Note that we use
            // the fs key mismatch state to represent the 're-send requested'-mark.
            val state = when {
                recipients.isEmpty() -> MessageState.READ

                rejectedGroupMessageFactory.getMessageRejects(messageId, group)
                    .isNotEmpty() -> MessageState.FS_KEY_MISMATCH

                else -> MessageState.SENT
            }
            messageModel.state = state

            // Update sent timestamp
            val sentDate = if (sentMessageContainers.isNotEmpty()) {
                // The message has been sent to at least someone
                Date(sentMessageContainers.first().sentTimestamp.toLong())
            } else {
                // The message wasn't sent to anybody. This can happen in notes groups.
                // TODO(ANDR-2705): When reflecting a message in a notes group, we should still be
                // able to get the timestamp (when it has been reflected)
                Date()
            }
            messageModel.postedAt = sentDate
            messageModel.modifiedAt = sentDate

            // Update forward security mode
            val forwardSecurityMode =
                when (sentMessageContainers.count { it.forwardSecurityMode != ForwardSecurityMode.NONE }) {
                    0 -> ForwardSecurityMode.NONE
                    sentMessageContainers.size -> ForwardSecurityMode.ALL
                    else -> ForwardSecurityMode.PARTIAL
                }
            if (messageModel.forwardSecurityMode == null) {
                // If the forward security mode is null, it is the first time we send this message.
                // Therefore we can set the mode directly to the current mode.
                messageModel.forwardSecurityMode = forwardSecurityMode
            } else {
                // If the previous forward security mode is already set, this means this has been a
                // resend of the message that only reached a subset of the group members. Therefore
                // we follow a best effort downgrade procedure:
                if (forwardSecurityMode == ForwardSecurityMode.PARTIAL || forwardSecurityMode == ForwardSecurityMode.NONE) {
                    // If there is a re-sent message without forward security, we set the mode to
                    // partial, as some may have received the message with forward security in an
                    // earlier attempt.
                    messageModel.forwardSecurityMode = ForwardSecurityMode.PARTIAL
                }
            }

            messageService.save(messageModel)

            // Trigger listener TODO(ANDR-2705): Check updated version of MessageService#updateMessageState
            ListenerManager.messageListeners.handle { listener: MessageListener ->
                listener.onModified(listOf(messageModel))
            }
        }

        groupService.setIsArchived(group, false)
    }
}
