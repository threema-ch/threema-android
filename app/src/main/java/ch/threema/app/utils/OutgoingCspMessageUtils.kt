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

package ch.threema.app.utils

import androidx.annotation.WorkerThread
import ch.threema.app.services.ContactService
import ch.threema.app.services.GroupService
import ch.threema.app.services.IdListService
import ch.threema.app.tasks.TaskCreator
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.Utils
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.ThreemaFeature
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.fs.ForwardSecurityEncryptionResult
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityEnvelopeMessage
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityMode
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.stores.IdentityStoreInterface
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.toCspMessage
import ch.threema.domain.taskmanager.waitForServerAck
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.GroupModel
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("OutgoingCspMessageUtils")

/**
 * Get all contact models of the given identities. If there are unknown contact models, they are
 * fetched from the server. Note that this may throw an exception when no connection is available or
 * there are invalid identities.
 */
fun Collection<String>.toContactModels(contactService: ContactService, apiConnector: APIConnector) =
    map { contactService.getByIdentity(it) ?: it.fetchContactModel(apiConnector) }

/**
 * Fetch a contact model. Note that this may throw an exception when no connection is available or
 * the identity is invalid.
 */
@WorkerThread
fun String.fetchContactModel(apiConnector: APIConnector): ContactModel =
    apiConnector.fetchIdentity(this)
        .let {
            ContactModel(it.identity, it.publicKey)
                .setFeatureMask(it.featureMask)
                .setIdentityType(when (it.type) {
                    0 -> IdentityType.NORMAL
                    1 -> IdentityType.WORK
                    else -> IdentityType.NORMAL /* Out of range! */
                })
                .setState(
                    when (it.state) {
                        IdentityState.ACTIVE -> ContactModel.State.ACTIVE
                        IdentityState.INACTIVE -> ContactModel.State.INACTIVE
                        else -> ContactModel.State.INVALID
                    }
                )
        }

/**
 * Only known contact models are returned. Hidden contacts (acquaintance level 'group') are also
 * included. Note that no cached contacts (added with [ContactStore.addCachedContact]) are returned.
 */
fun Collection<String>.toKnownContactModels(contactService: ContactService): Collection<ContactModel> =
    mapNotNull { contactService.getByIdentity(it) }

/**
 * All contacts that are not invalid are included. Only invalid contacts are discarded.
 */
fun Collection<ContactModel>.filterValid() = filter { it.state != ContactModel.State.INVALID }

/**
 * Only include non-blocked contacts. Note that only explicitly blocked identities are excluded.
 */
fun Collection<ContactModel>.filterNotBlocked(blackListService: IdListService) =
    filterNotBlockedIf(true, blackListService)

/**
 * Only include non-blocked contacts if [applyFilter] is true. Note that only explicitly blocked
 * identities are excluded.
 */
fun Collection<ContactModel>.filterNotBlockedIf(
    applyFilter: Boolean,
    blackListService: IdListService,
) = filterIf(applyFilter) { !blackListService.has(it.identity) }

/**
 * Filter the broadcast identity if no messages should be sent to it according to
 * [GroupUtil.sendMessageToCreator].
 */
fun Collection<ContactModel>.filterBroadcastIdentity(group: GroupModel): Collection<ContactModel> =
    filterIf(!GroupUtil.sendMessageToCreator(group)) { it.identity != group.creatorIdentity }

fun <T> Collection<T>.filterIf(condition: Boolean, predicate: (T) -> Boolean): Collection<T> =
    if (condition) {
        this.filter(predicate)
    } else {
        this
    }

data class OutgoingMessageResult(
    /**
     * The recipient that received the message.
     */
    val recipient: ContactModel,

    /**
     * The forward security mode that has been used to encapsulate the message.
     */
    val forwardSecurityMode: ForwardSecurityMode,

    /**
     * The timestamp when the message has been sent.
     */
    val sentTimestamp: ULong,
)

/**
 * This class is internally used to bundle different information used for sending the messages out.
 */
private data class MessageSendContainer(
    val recipient: ContactModel,
    val forwardSecurityEncryptionResult: ForwardSecurityEncryptionResult?,
    val nonces: List<ByteArray>,
    val outgoingMessages: List<AbstractMessage>,
    val forwardSecurityMode: ForwardSecurityMode,
) {
    companion object {
        internal fun create(
            recipient: ContactModel,
            innerMessage: AbstractMessage,
            forwardSecurityMessageProcessor: ForwardSecurityMessageProcessor,
            nonceFactory: NonceFactory,
            handle: ActiveTaskCodec,
        ): MessageSendContainer {
            //TODO(ANDR-2519): Remove when md allows fs
            val senderCanForwardSecurity = forwardSecurityMessageProcessor.isForwardSecurityEnabled()
            val recipientCanForwardSecurity =
                ThreemaFeature.canForwardSecurity(recipient.featureMask)
            val innerMessageEncapsulated = innerMessage is ForwardSecurityEnvelopeMessage

            // Create forward security encryption result
            val encryptionResult =
                if (senderCanForwardSecurity && recipientCanForwardSecurity && !innerMessageEncapsulated) {
                    forwardSecurityMessageProcessor.makeMessage(recipient, innerMessage, handle)
                } else {
                    null
                }

            // Get the message from the result. If there is no result, then forward security is not
            // supported (or the provided message is already encapsulated) and we add the list of
            // the message without encapsulating it (again)
            val outgoingMessages = encryptionResult?.outgoingMessages ?: listOf(innerMessage)

            // Get the forward security mode from the encryption result if available, otherwise take
            // the forward security mode of the inner message.
            val forwardSecurityMode =
                encryptionResult?.forwardSecurityMode ?: innerMessage.forwardSecurityMode

            // Create a nonce for every outgoing message. Note that the nonce will be saved when the
            // message is encoded (depending on the message type)
            val nonces = outgoingMessages.map { nonceFactory.next(false) }

            return MessageSendContainer(
                recipient,
                encryptionResult,
                nonces,
                outgoingMessages,
                forwardSecurityMode,
            )
        }
    }
}

/**
 * Used to create messages that are sent with [ActiveTaskCodec.sendMessageToReceivers].
 */
sealed interface OutgoingCspMessageCreator {
    /**
     * Create an abstract message containing all the message type specific information. The
     * following fields must not be set, as they will be set by the send utils or the message
     * creator:
     *
     * - [AbstractMessage.toIdentity]
     * - [AbstractMessage.fromIdentity]
     * - [AbstractMessage.date]
     * - [AbstractMessage.messageId]
     * - [AbstractGroupMessage.apiGroupId]
     * - [AbstractGroupMessage.groupCreator]
     *
     *  Note that each call of this method must return a new instance of the message.
     */
    fun createAbstractMessage(): AbstractMessage
}

/**
 * Used to create messages that are sent with [ActiveTaskCodec.sendContactMessage].
 */
class OutgoingCspContactMessageCreator(
    private val messageId: MessageId,
    private val createContactMessage: () -> AbstractMessage,
) : OutgoingCspMessageCreator {
    override fun createAbstractMessage(): AbstractMessage {
        return createContactMessage().also {
            // Check that this message creator is only used for contact messages
            if (it is AbstractGroupMessage) {
                throw IllegalArgumentException(
                    "The contact message creator cannot be used for group messages"
                )
            }

            it.messageId = messageId
        }
    }
}

/**
 * Used to create message that are sent with [ActiveTaskCodec.sendGroupMessage].
 */
class OutgoingCspGroupMessageCreator(
    private val messageId: MessageId,
    private val groupId: GroupId,
    private val groupCreator: String,
    private val createGroupMessage: () -> AbstractGroupMessage,
) : OutgoingCspMessageCreator {

    constructor(
        messageId: MessageId,
        group: GroupModel,
        createAbstractGroupMessage: () -> AbstractGroupMessage,
    ) : this(
        messageId,
        group.apiGroupId,
        group.creatorIdentity,
        createAbstractGroupMessage
    )

    /**
     * Create an abstract message containing all the message type specific information. The
     * following fields must not be set, as they will be set by the send utils:
     *
     * - [AbstractMessage.toIdentity]
     * - [AbstractMessage.fromIdentity]
     * - [AbstractMessage.date]
     * - [AbstractMessage.messageId]
     *
     *  Note that each call of this method must return a new instance of the message.
     */
    override fun createAbstractMessage(): AbstractGroupMessage {
        return createGroupMessage().also {
            it.messageId = messageId
            it.apiGroupId = groupId
            it.groupCreator = groupCreator
        }
    }
}

/**
 * Run the _Common Send Steps_ to send the provided message to the given recipient. Note that if the
 * recipient is blocked and [AbstractMessage.exemptFromBlocking] is not set, the message is not sent
 * and null is returned.
 *
 * @return the [OutgoingMessageResult] with the send information of the message or null if it was
 * not sent because the recipient is blocked
 */
suspend fun ActiveTaskCodec.sendContactMessage(
    messageCreator: OutgoingCspContactMessageCreator,
    recipient: ContactModel,
    forwardSecurityMessageProcessor: ForwardSecurityMessageProcessor,
    identityStore: IdentityStoreInterface,
    contactStore: ContactStore,
    nonceFactory: NonceFactory,
    blackListService: IdListService,
    taskCreator: TaskCreator,
): OutgoingMessageResult? = sendMessageToReceivers(
    messageCreator,
    setOf(recipient),
    forwardSecurityMessageProcessor,
    identityStore,
    contactStore,
    nonceFactory,
    blackListService,
    taskCreator
).firstOrNull()

/**
 * Run the _Common Send Steps_ and the _Common Group Send Steps_. If the user is not a member of the
 * given group, null is returned. The messages are created for every recipient of the group.
 * Depending on the message type, they are only sent to not-blocked contacts.
 *
 * Note that the messages are only sent to recipients that are part of the group.
 *
 * @return the [OutgoingMessageResult]s with the send information of the messages that were sent, or
 * null if the user is not a member of the group
 */
suspend fun ActiveTaskCodec.sendGroupMessage(
    messageCreator: OutgoingCspGroupMessageCreator,
    recipients: Set<ContactModel>,
    group: GroupModel,
    forwardSecurityMessageProcessor: ForwardSecurityMessageProcessor,
    identityStore: IdentityStoreInterface,
    contactStore: ContactStore,
    nonceFactory: NonceFactory,
    groupService: GroupService,
    blackListService: IdListService,
    taskCreator: TaskCreator,
): Set<OutgoingMessageResult>? {
    if (!groupService.isGroupMember(group)) {
        logger.warn("Tried to send a message in a group where the user is not a member anymore")
        return null
    }

    val groupMembers = groupService.getMembers(group).toSet()

    return sendMessageToReceivers(
        messageCreator,
        recipients.intersect(groupMembers),
        forwardSecurityMessageProcessor,
        identityStore,
        contactStore,
        nonceFactory,
        blackListService,
        taskCreator
    )
}

/**
 * Run the _Common Send Steps_ for the given recipients. Note that this method does not run the
 * _Common Group Send Steps_. Therefore, this method can also be used to send a group message to
 * members that are not part of the group anymore (e.g. [GroupLeaveMessage]s) or in a group where
 * the user is not a member (e.g. when receiving a [GroupSetupMessage] from a blocked identity).
 */
suspend fun ActiveTaskCodec.sendMessageToReceivers(
    messageCreator: OutgoingCspMessageCreator,
    recipients: Set<ContactModel>,
    forwardSecurityMessageProcessor: ForwardSecurityMessageProcessor,
    identityStore: IdentityStoreInterface,
    contactStore: ContactStore,
    nonceFactory: NonceFactory,
    blackListService: IdListService,
    taskCreator: TaskCreator,
): Set<OutgoingMessageResult> {
    val myIdentity = identityStore.identity

    val recipientList = recipients.filter { it.identity != myIdentity }
    val messageList = recipientList.map { messageCreator.createAbstractMessage() }

    // Perform some sanity checks
    messageList.let {
        val expectedSize = it.size
        val messageSet = it.toSet()

        if (messageSet.size < expectedSize) {
            throw IllegalArgumentException("The message creator created at least two identical messages")
        }

        messageSet.map { message -> message.type }.toSet().size.let { numTypes ->
            if (numTypes > 1) {
                throw IllegalArgumentException("The message create created messages of $numTypes different types")
            }
        }
    }

    // Create list of recipients that are not blocked (or exempted from blocking) with the messages
    val recipientMessageList = recipientList.zip(messageList).filter { (recipient, message) ->
        //Filter blocked recipients except message type is exempted from blocking
        (message.exemptFromBlocking() || !blackListService.has(recipient.identity)).also {
            if (!it) {
                message.logMessage("Skipping message because recipient $recipient is blocked:")
            }
        }
    }

    // TODO(ANDR-2705): Reflect the message here

    val createdAt = Date()

    // Prepare messages to be sent
    recipientMessageList.forEach { (recipient, message) ->
        message.also {
            it.fromIdentity = myIdentity
            it.toIdentity = recipient.identity
            it.date = createdAt
        }
        message.logMessage("Preparing to send")
    }

    // Create the send containers of the remaining recipients and messages
    val sendContainers = recipientMessageList.map { (recipient, message) ->
        MessageSendContainer.create(
            recipient,
            message,
            forwardSecurityMessageProcessor,
            nonceFactory,
            this
        )
    }

    // Cache the public keys of the contacts (if not available)
    sendContainers.forEach { (recipient, _) ->
        if (contactStore.getContactForIdentityIncludingCache(recipient.identity) == null) {
            contactStore.addCachedContact(recipient)
        }
    }

    // Send messages
    sendContainers.forEach { messageContainer ->
        val outgoingMessages = messageContainer.outgoingMessages

        for ((message, nonce) in outgoingMessages.zip(messageContainer.nonces)) {
            write(message.toCspMessage(identityStore, contactStore, nonceFactory, nonce))
            message.logMessage("Sent")
        }
    }

    // Await server acknowledgments
    sendContainers.forEach { messageContainer ->
        for (message in messageContainer.outgoingMessages) {
            if (!message.hasFlags(ProtocolDefines.MESSAGE_FLAG_NO_SERVER_ACK)) {
                waitForServerAck(message.messageId, message.toIdentity)
                message.logMessage("Received server ack for")
            }
        }
    }

    // Schedule user profile distribution tasks
    sendContainers.forEach { messageContainer ->
        // Schedule a user profile distribution task if at least one of the sent messages allows it
        if (messageContainer.outgoingMessages.any { it.allowUserProfileDistribution() }) {
            taskCreator.scheduleProfilePictureExecution(messageContainer.recipient.identity)
        }
    }

    // Commit the session state of each recipient
    sendContainers.forEach { messageContainer ->
        messageContainer.forwardSecurityEncryptionResult?.let {
            forwardSecurityMessageProcessor.commitSessionState(it)
        }
    }

    // TODO(ANDR-2705): Reflect an outgoing message update

    // Set the timestamp of each message container
    val sentTimestamp = Date().time.toULong()
    return sendContainers.map {
        OutgoingMessageResult(
            it.recipient,
            it.forwardSecurityMode,
            sentTimestamp,
        )
    }.toSet()
}

private fun AbstractMessage.logMessage(logMessage: String) {
    logger.info(
        "{} message {} of type {} to {}",
        logMessage,
        messageId,
        Utils.byteToHex(type.toByte(), true, true),
        toIdentity
    )
}
