/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.app.processors.incomingcspmessage.fs

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.groupcontrol.handleIncomingGroupSyncRequest
import ch.threema.app.processors.incomingcspmessage.groupcontrol.runCommonGroupReceiveSteps
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.domain.models.BasicContact
import ch.threema.domain.models.Contact
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.fs.ForwardSecurityDecryptionResult
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataReject
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.protobuf.Common
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("IncomingForwardSecurityRejectTask")

class IncomingForwardSecurityRejectTask(
    private val sender: Contact,
    private val data: ForwardSecurityDataReject,
    private val serviceManager: ServiceManager,
) : IncomingForwardSecurityEnvelopeTask {
    private val forwardSecurityMessageProcessor by lazy { serviceManager.forwardSecurityMessageProcessor }
    private val messageService by lazy { serviceManager.messageService }
    private val voipStateService by lazy { serviceManager.voipStateService }
    private val userService by lazy { serviceManager.userService }
    private val contactService by lazy { serviceManager.contactService }
    private val contactStore by lazy { serviceManager.contactStore }
    private val contactModelRepository by lazy { serviceManager.modelRepositories.contacts }
    private val groupService by lazy { serviceManager.groupService }
    private val databaseService by lazy { serviceManager.databaseServiceNew }
    private val notificationService by lazy { serviceManager.notificationService }

    override suspend fun run(handle: ActiveTaskCodec): ForwardSecurityDecryptionResult {
        logger.info("Received a reject message for message {}", data.rejectedApiMessageId)
        // TODO(ANDR-2519): Remove when md allows fs
        // Note that in this case we should not send a terminate if we do not support fs. Sending a
        // terminate could trigger the sender to respond with a terminate again.
        if (!forwardSecurityMessageProcessor.canForwardSecurityMessageBeProcessed(
                sender = sender,
                sessionId = data.sessionId,
                sendTerminate = false,
                handle = handle,
            )
        ) {
            return ForwardSecurityDecryptionResult.NONE
        }

        forwardSecurityMessageProcessor.processReject(sender, data, handle)

        val groupIdentityProto = data.groupIdentity
        if (groupIdentityProto == null || groupIdentityProto.isEmpty()) {
            handleContactMessageReject()
        } else {
            handleGroupMessageReject(groupIdentityProto.convert(), handle)
        }

        return ForwardSecurityDecryptionResult.NONE
    }

    private fun handleContactMessageReject() {
        when (val messageModel = getContactMessageModel()) {
            is MessageModel -> {
                handleReject(messageModel)
            }

            null -> {
                voipStateService.handlePotentialCallMessageReject(data.rejectedApiMessageId)
            }

            else -> {
                logger.warn(
                    "Invalid message model found for rejected message id {}",
                    data.rejectedApiMessageId,
                )
            }
        }
    }

    private suspend fun handleGroupMessageReject(
        groupIdentity: GroupIdentity,
        handle: ActiveTaskCodec,
    ) {
        // 1. Run the common group receive steps and abort if the group is not found
        val group = runCommonGroupReceiveSteps(
            groupIdentity,
            sender.identity,
            handle,
            serviceManager,
        )

        // TODO(ANDR-3565): Fix protocol step numbers
        // 2. Lookup the group and 3. abort if not defined
        if (group == null) {
            logger.warn("Received reject of a group message of unknown group")
            return
        }

        // 4. Look up the message for the message id
        val messageModel = getGroupMessageModel(group)

        // 5. If the message is not defined
        if (messageModel == null) {
            // 5.1 If the user is the creator of the group, assume that a group sync request has
            // been received
            if (groupIdentity.creatorIdentity == userService.identity) {
                val senderContact = getBasicContact(sender.identity)
                if (senderContact == null) {
                    logger.error("Could not get cached sender contact")
                    return
                }
                handleIncomingGroupSyncRequest(group, senderContact, handle, serviceManager)
            }
            // 5.2 Abort these steps
            return
        }

        // 6. If the user is not the sender of the message, abort these steps
        if (!messageModel.isOutbox) {
            logger.warn("Received reject of a message where the user is not the sender")
            return
        }

        // 7. If the reject logic of the message type allows re-send, mark the message with
        // 're-send requested' and add the sender to the list of receivers requesting a re-send
        handleReject(messageModel, group)
    }

    private fun getContactMessageModel(): AbstractMessageModel? =
        databaseService.messageModelFactory.getByApiMessageIdAndIdentityAndIsOutbox(
            data.rejectedApiMessageId,
            sender.identity,
            true,
        )

    private fun getGroupMessageModel(group: GroupModel): GroupMessageModel? {
        val messageReceiver = groupService.createReceiver(group) ?: return null
        val messageModel = messageService.getMessageModelByApiMessageIdAndReceiver(
            data.rejectedApiMessageId.toString(),
            messageReceiver,
        )
        return when (messageModel) {
            is GroupMessageModel -> {
                messageModel
            }

            null -> {
                logger.info("Rejected message model is not found. Handling reject as incoming group sync request.")
                null
            }

            else -> {
                logger.warn("Rejected message model is not a group message model even though the group identity is set")
                null
            }
        }
    }

    private fun handleReject(messageModel: MessageModel) {
        val type = messageModel.type ?: return
        when (type) {
            MessageType.TEXT,
            MessageType.LOCATION,
            MessageType.FILE,
            MessageType.BALLOT,
            -> {
                // Mark the message with 're-send requested'. Note that we use the fs key mismatch
                // state to represent the 're-send requested'-mark.
                messageService.updateOutgoingMessageState(
                    messageModel,
                    MessageState.FS_KEY_MISMATCH,
                    Date(),
                )

                // Show a notification that a reject was received (if the contact is known)
                contactService.getByIdentity(messageModel.identity)
                    ?.let { contactService.createReceiver(it) }?.let { receiver ->
                        notificationService.showForwardSecurityMessageRejectedNotification(receiver)
                    }
            }

            MessageType.IMAGE,
            MessageType.VIDEO,
            MessageType.VOICEMESSAGE,
            MessageType.CONTACT,
            -> logger.warn("Received a reject for a deprecated message")

            MessageType.STATUS,
            MessageType.VOIP_STATUS,
            MessageType.DATE_SEPARATOR,
            MessageType.GROUP_CALL_STATUS,
            MessageType.FORWARD_SECURITY_STATUS,
            MessageType.GROUP_STATUS,
            -> logger.warn("Received a reject for a status message")
        }
    }

    private fun handleReject(messageModel: GroupMessageModel, group: GroupModel) {
        val type = messageModel.type ?: return
        when (type) {
            MessageType.TEXT,
            MessageType.LOCATION,
            MessageType.FILE,
            MessageType.BALLOT,
            -> {
                // Mark the message with 're-send requested'. Note that we use the fs key mismatch
                // state to represent the 're-send requested'-mark.
                messageService.updateOutgoingMessageState(
                    messageModel,
                    MessageState.FS_KEY_MISMATCH,
                    Date(),
                )

                // Add the sender to the list of recipients requesting a re-send
                databaseService.rejectedGroupMessageFactory.insertMessageReject(
                    MessageId.fromString(messageModel.apiMessageId),
                    sender.identity,
                    group.getDatabaseId(),
                )

                // Show a notification that a reject was received
                groupService.createReceiver(group)?.let {
                    notificationService.showForwardSecurityMessageRejectedNotification(it)
                }
            }

            MessageType.IMAGE,
            MessageType.VIDEO,
            MessageType.VOICEMESSAGE,
            MessageType.CONTACT,
            -> logger.warn("Received a reject for a deprecated message")

            MessageType.STATUS,
            MessageType.VOIP_STATUS,
            MessageType.DATE_SEPARATOR,
            MessageType.GROUP_CALL_STATUS,
            MessageType.FORWARD_SECURITY_STATUS,
            MessageType.GROUP_STATUS,
            -> logger.warn("Received a reject for a status message")
        }
    }

    private fun Common.GroupIdentity.isEmpty() = groupId == 0L && creatorIdentity == ""

    private fun Common.GroupIdentity.convert() = GroupIdentity(creatorIdentity, groupId)

    private fun getBasicContact(identity: String): BasicContact? =
        contactStore.getCachedContact(identity)
            ?: contactModelRepository.getByIdentity(identity)?.data?.value?.toBasicContact()
}
