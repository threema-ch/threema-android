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
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver.MessageReceiverType
import ch.threema.app.utils.OutgoingCspContactMessageCreator
import ch.threema.app.utils.OutgoingCspGroupMessageCreator
import ch.threema.app.utils.OutgoingCspMessageHandle
import ch.threema.app.utils.OutgoingCspMessageServices.Companion.getOutgoingCspMessageServices
import ch.threema.app.utils.removeGroupCreatorIfRequired
import ch.threema.app.utils.runBundledMessagesSendSteps
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.Utils
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.fs.BadDHStateException
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityMode
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.NetworkException
import ch.threema.domain.taskmanager.catchAllExceptNetworkException
import ch.threema.domain.taskmanager.catchExceptNetworkException
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.GroupModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageState
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("OutgoingCspMessageTask")

sealed class OutgoingCspMessageTask(private val serviceManager: ServiceManager) :
    ActiveTask<Unit>, PersistableTask {
    protected val userService by lazy { serviceManager.userService }
    private val myIdentity by lazy { userService.identity }
    protected val contactModelRepository by lazy { serviceManager.modelRepositories.contacts }
    private val groupModelRepository by lazy { serviceManager.modelRepositories.groups }
    protected val contactService by lazy { serviceManager.contactService }
    protected val groupService by lazy { serviceManager.groupService }
    protected val contactStore by lazy { serviceManager.contactStore }
    protected val identityStore by lazy { serviceManager.identityStore }
    protected val nonceFactory by lazy { serviceManager.nonceFactory }
    protected val forwardSecurityMessageProcessor by lazy { serviceManager.forwardSecurityMessageProcessor }
    protected val messageService by lazy { serviceManager.messageService }
    private val databaseService by lazy { serviceManager.databaseServiceNew }
    private val rejectedGroupMessageFactory by lazy { databaseService.rejectedGroupMessageFactory }

    // It is important that the task creator is loaded lazily, as the task archiver may instantiate
    // this class before the connection is initialized (which is used for the task creator).
    private val taskCreator by lazy { serviceManager.taskCreator }
    protected val preferenceService by lazy { serviceManager.preferenceService }

    final override suspend fun invoke(handle: ActiveTaskCodec) {
        suspend {
            runSendingSteps(handle)
        }.catchAllExceptNetworkException { exception ->
            onSendingStepsFailed(exception)
            throw exception
        }
    }

    /**
     * Run the steps that need to be performed to send the message(s). If this throws an exception
     * that is not an [NetworkException], [onSendingStepsFailed] is called.
     */
    abstract suspend fun runSendingSteps(handle: ActiveTaskCodec)

    /**
     * This method is called if the sending steps have thrown an exception.
     *
     * Note that this method won't be called if the thrown exception is a [NetworkException].
     *
     * Note that the task will be finished and [runSendingSteps] must not be executed again to
     * prevent infinite retries. The task will exit by throwing [e]. This exception will then be
     * handled by the task manager that may re-run this task.
     */
    open fun onSendingStepsFailed(e: Exception) {
        // Nothing to do here
    }

    /**
     * Encapsulate and send the given message. Note that the message must be ready to be sent. This
     * must include the message flags that should be used. Note that the following fields should not
     * be set as they will get overridden:
     *
     * - [AbstractMessage.toIdentity]
     * - [AbstractMessage.fromIdentity]
     * - [AbstractMessage.messageId]
     * - [AbstractMessage.date]
     *
     * If there is a message model to this message, it should also be passed to this method, so that
     * it gets updated.
     *
     * Note that there must exists a contact model of the receiver. Otherwise, the message cannot be
     * sent. In this case the message model state is set to [MessageState.SENDFAILED] (if provided)
     * and an [IllegalStateException] is thrown.
     */
    suspend fun sendContactMessage(
        message: AbstractMessage,
        messageModel: MessageModel?,
        toIdentity: String,
        messageId: MessageId,
        createdAt: Date,
        handle: ActiveTaskCodec,
    ) {
        val contactModelData = contactModelRepository.getByIdentity(toIdentity)?.data?.value
        if (contactModelData == null) {
            logger.error(
                "Could not send message to {} as the contact model data is null",
                toIdentity
            )
            messageModel?.let {
                messageService.updateOutgoingMessageState(
                    it,
                    MessageState.SENDFAILED,
                    Date()
                )
            }
            throw IllegalStateException("Could not send message as the receiver model is unknown")
        }

        val createMessage = OutgoingCspContactMessageCreator(
            messageId,
            createdAt,
            toIdentity,
        ) { message }

        val markAsSent = { sentAt: ULong ->
            if (messageModel != null) {
                // Update the message state for the outgoing message
                messageService.updateOutgoingMessageState(
                    messageModel,
                    MessageState.SENT,
                    Date(sentAt.toLong())
                )
            }
        }

        val updateFsState = { stateMap: Map<String, ForwardSecurityMode> ->
            if (messageModel != null) {
                val forwardSecurityMode = stateMap[toIdentity]
                if (forwardSecurityMode == null) {
                    logger.error("No forward security mode available")
                } else {
                    messageModel.forwardSecurityMode = forwardSecurityMode
                    messageService.save(messageModel)
                }
            }
        }

        suspend {
            handle.runBundledMessagesSendSteps(
                OutgoingCspMessageHandle(
                    contactModelData.toBasicContact(),
                    createMessage,
                    markAsSent,
                    updateFsState,
                ),
                serviceManager.getOutgoingCspMessageServices(),
            )
        }.catchExceptNetworkException { e: BadDHStateException ->
            if (messageModel != null) {
                messageService.updateOutgoingMessageState(
                    messageModel,
                    MessageState.SENDFAILED,
                    Date()
                )
            }
            throw e
        }
    }

    /**
     * Send the group message to the recipients following the _Common Send Steps_. The message is
     * only sent to those recipients that are still part of the group.
     *
     * Note that the message created by [createAbstractMessage] must be ready to be sent. This must
     * include the message flags that should be used. Note that the following fields should not be
     * set as they will get overridden:
     *
     * - [AbstractMessage.toIdentity]
     * - [AbstractMessage.fromIdentity]
     * - [AbstractMessage.messageId]
     * - [AbstractMessage.date]
     * - [AbstractGroupMessage.apiGroupId]
     * - [AbstractGroupMessage.groupCreator]
     *
     * Each invocation of [createAbstractMessage] must return a new instance of the message.
     *
     * Note that the message is only sent to valid members and if the member is not blocked (except
     * the message should be exempted from blocking). If there is no contact model for a member, the
     * message will not be sent to this member.
     */
    suspend fun sendGroupMessage(
        group: GroupModel,
        recipients: Collection<String>,
        messageModel: GroupMessageModel?,
        createdAt: Date,
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
            .mapNotNull { contactModelRepository.getByIdentity(it) }
            .removeGroupCreatorIfRequired(group)
            .mapNotNull { it.data.value }
            .map { it.toBasicContact() }
            .toSet()

        val messageCreator = OutgoingCspGroupMessageCreator(
            messageId,
            createdAt,
            group,
            createAbstractMessage,
        )

        val markAsSent = { sentAt: ULong ->
            if (messageModel != null) {
                // Update sent timestamp
                val sentDate = Date(sentAt.toLong())

                // Note that we set the postedAt directly because the new state could be
                // FS_KEY_MISMATCH and then MessageService#updateOutgoingMessageState wouldn't set
                // this timestamp.
                messageModel.postedAt = sentDate
                messageModel.modifiedAt = sentDate

                messageService.save(messageModel)
            }
        }

        val updateFsState = { fsStateMap: Map<String, ForwardSecurityMode> ->
            fsStateMap.keys.forEach {
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
                // Note that we set the state directly (without using MessageService#updateOutgoingMessageState)
                // because we need to modify the postedAt timestamp also when the state is FS_KEY_MISMATCH.
                messageModel.state = state

                // Update forward security mode
                val forwardSecurityMode =
                    when (fsStateMap.count { it.value != ForwardSecurityMode.NONE }) {
                        0 -> ForwardSecurityMode.NONE
                        fsStateMap.size -> ForwardSecurityMode.ALL
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

                // Trigger listener
                ListenerManager.messageListeners.handle { listener: MessageListener ->
                    listener.onModified(listOf(messageModel))
                }
            }
        }

        suspend {
            handle.runBundledMessagesSendSteps(
                OutgoingCspMessageHandle(
                    finalRecipients,
                    messageCreator,
                    markAsSent,
                    updateFsState,
                ),
                serviceManager.getOutgoingCspMessageServices(),
            )
        }.catchExceptNetworkException { e: BadDHStateException ->
            if (messageModel != null) {
                messageService.updateOutgoingMessageState(
                    messageModel,
                    MessageState.SENDFAILED,
                    Date()
                )
            }
            throw e
        }

        groupService.setIsArchived(group, false)
    }

    /**
     * Returns the message id of the message model.
     *
     * @throws IllegalArgumentException if the message id of the message model is null
     */
    protected fun ensureMessageId(messageModel: AbstractMessageModel): MessageId {
        messageModel.apiMessageId?.let {
            return MessageId(Utils.hexStringToByteArray(it))
        }

        throw IllegalArgumentException("Message id of message model is null")
    }

    /**
     * Get the message model with the given local database message model id.
     *
     * @throws IllegalArgumentException if receiver type is not [MessageReceiver.Type_CONTACT] or
     * [MessageReceiver.Type_GROUP]
     */
    protected fun getMessageModel(
        @MessageReceiverType receiverType: Int,
        messageModelId: Int,
    ): AbstractMessageModel? {
        return when (receiverType) {
            MessageReceiver.Type_CONTACT -> getContactMessageModel(messageModelId)
            MessageReceiver.Type_GROUP -> getGroupMessageModel(messageModelId)
            else -> throw IllegalArgumentException("Invalid receiver type: $receiverType")
        }
    }

    /**
     * Get the contact message model with the given local database message model id.
     */
    protected fun getContactMessageModel(messageModelId: Int): MessageModel? {
        val messageModel = messageService.getContactMessageModel(messageModelId)
        if (messageModel == null) {
            logger.warn("Could not find contact message model with id {}", messageModelId)
        }
        return messageModel
    }

    /**
     * Get the group message model with the given local database message model id.
     */
    protected fun getGroupMessageModel(messageModelId: Int): GroupMessageModel? {
        val messageModel = messageService.getGroupMessageModel(messageModelId)
        if (messageModel == null) {
            logger.warn("Could not find group message model with id {}", messageModelId)
        }
        return messageModel
    }

    /**
     * Set the message model state to [MessageState.SENDFAILED] and save the model to the database.
     */
    protected fun AbstractMessageModel.saveWithStateFailed() {
        logger.info("Setting message state of model with message id {} to failed", apiMessageId)
        state = MessageState.SENDFAILED
        messageService.save(this)
    }
}
