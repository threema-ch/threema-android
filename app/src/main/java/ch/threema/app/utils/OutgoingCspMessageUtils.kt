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
import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.protocol.runIdentityBlockedSteps
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.ContactService
import ch.threema.app.services.GroupService
import ch.threema.app.services.PreferenceService
import ch.threema.app.services.UserService
import ch.threema.base.crypto.Nonce
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceScope
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.Utils
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.BasicContact
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.fs.ForwardSecurityEncryptionResult
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.DeleteProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.SetProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityMode
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.stores.IdentityStoreInterface
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.PassiveTaskCodec
import ch.threema.domain.taskmanager.awaitOutgoingMessageAck
import ch.threema.domain.taskmanager.awaitReflectAck
import ch.threema.domain.taskmanager.getEncryptedOutgoingMessageEnvelope
import ch.threema.domain.taskmanager.getEncryptedOutgoingMessageUpdateSentEnvelope
import ch.threema.domain.taskmanager.toCspMessage
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.GroupModel
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("OutgoingCspMessageUtils")

/**
 * Map each identity to a cached contact. If the contact is known, it is converted to a cached
 * contact. Otherwise, the contact is loaded from the contact store's cache or fetched from the
 * server.
 */
@WorkerThread
fun Iterable<String>.toBasicContacts(
    contactModelRepository: ContactModelRepository,
    contactStore: ContactStore,
    apiConnector: APIConnector,
) = map { it.toBasicContact(contactModelRepository, contactStore, apiConnector) }

/**
 * Map the identity to a cached contact. If the contact is known, it is converted to a cached
 * contact. Otherwise, the contact is loaded from the contact store's cache or fetched from the
 * server.
 */
@WorkerThread
fun String.toBasicContact(
    contactModelRepository: ContactModelRepository,
    contactStore: ContactStore,
    apiConnector: APIConnector,
) = contactModelRepository.getByIdentity(this)?.data?.value?.toBasicContact()
    ?: contactStore.getCachedContact(this)
    ?: fetchContactModel(apiConnector)

/**
 * Fetch a contact model. Note that this may throw an exception when no connection is available or
 * the identity is invalid.
 */
@WorkerThread
fun String.fetchContactModel(apiConnector: APIConnector): BasicContact =
    apiConnector.fetchIdentity(this)
        .let {
            BasicContact(
                it.identity,
                it.publicKey,
                it.featureMask.toULong(),
                when (it.state) {
                    IdentityState.ACTIVE.value -> IdentityState.ACTIVE
                    IdentityState.INACTIVE.value -> IdentityState.INACTIVE
                    else -> IdentityState.INVALID
                },
                when (it.type) {
                    0 -> IdentityType.NORMAL
                    1 -> IdentityType.WORK
                    else -> IdentityType.NORMAL /* Out of range! */
                },
            )
        }

/**
 * Remove the group creator if no messages should be sent to it according to
 * [GroupUtil.sendMessageToCreator].
 */
fun Collection<ContactModel>.removeCreatorIfRequired(group: GroupModel): Collection<ContactModel> =
    filterIf(!GroupUtil.sendMessageToCreator(group)) { it.identity != group.creatorIdentity }

/**
 * Remove the group creator if no messages should be sent to it according to
 * [GroupUtil.sendMessageToCreator].
 */
fun Collection<ch.threema.data.models.ContactModel>.removeGroupCreatorIfRequired(group: GroupModel): Collection<ch.threema.data.models.ContactModel> =
    filterIf(!GroupUtil.sendMessageToCreator(group)) { it.identity != group.creatorIdentity }

/**
 * If the [condition] is fulfilled, the [predicate] is used to filter the collection.
 */
fun <T> Collection<T>.filterIf(condition: Boolean, predicate: (T) -> Boolean): Collection<T> =
    if (condition) {
        this.filter(predicate)
    } else {
        this
    }

/**
 * Used to create messages that are sent with [ActiveTaskCodec.runBundledMessagesSendSteps].
 */
sealed interface OutgoingCspMessageCreator {

    /**
     * The message id that is used to create the message.
     */
    val messageId: MessageId

    /**
     * The created at date of the message.
     */
    val createdAt: Date

    /**
     * Create a generic message that can be used to be reflected.
     */
    fun createGenericMessage(myIdentity: String): AbstractMessage

    /**
     * Create an abstract message containing all the message type specific information. Any
     * implementation of this must set the following fields:
     *
     * - [AbstractMessage.messageId]
     * - [AbstractMessage.date]
     *
     * In case of a group message, the following fields must be set additionally:
     *
     * - [AbstractGroupMessage.apiGroupId]
     * - [AbstractGroupMessage.groupCreator]
     *
     *  Note that each call of this method must return a new instance of the message.
     */
    fun createAbstractMessage(fromIdentity: String, toIdentity: String): AbstractMessage
}

/**
 * Used to create messages that are sent with [ActiveTaskCodec.runBundledMessagesSendSteps].
 */
class OutgoingCspContactMessageCreator(
    /**
     * The message id that will be applied to the given message.
     */
    override val messageId: MessageId,

    /**
     * The date that will be used as created-at date for the message.
     */
    override val createdAt: Date,

    /**
     * The identity of the recipient contact.
     */
    private val identity: String,

    /**
     * This should create the contact message with all message type specific attributes set. Note
     * that the following fields should not be set, as they will get overridden by these utils:
     *
     * - [AbstractMessage.toIdentity]
     * - [AbstractMessage.fromIdentity]
     * - [AbstractMessage.messageId]
     * - [AbstractMessage.date]
     */
    private val createContactMessage: () -> AbstractMessage,
) : OutgoingCspMessageCreator {

    override fun createGenericMessage(myIdentity: String) =
    // We need to set the 'toIdentity' here because the conversation is determined based on this
        // message when reflecting it.
        createAbstractMessage(myIdentity, identity)

    override fun createAbstractMessage(fromIdentity: String, toIdentity: String): AbstractMessage {
        return createContactMessage().also {
            // Check that this message creator is only used for contact messages
            if (it is AbstractGroupMessage) {
                throw IllegalArgumentException(
                    "The contact message creator cannot be used for group messages"
                )
            }

            it.messageId = messageId
            it.date = createdAt
            it.fromIdentity = fromIdentity
            it.toIdentity = toIdentity
        }
    }

}

/**
 * Used to create message that are sent with [ActiveTaskCodec.runBundledMessagesSendSteps].
 */
class OutgoingCspGroupMessageCreator(
    /**
     * The message id that will be set for the outgoing messages.
     */
    override val messageId: MessageId,

    /**
     * The date that will be used as created-at date for the outgoing messages.
     */
    override val createdAt: Date,

    /**
     * The group id of the group that will be set for the outgoing messages.
     */
    private val groupId: GroupId,

    /**
     * The group creator identity that will be set for the outgoing messages.
     */
    private val groupCreator: String,

    /**
     * This should create the contact message with all message type specific attributes set. Note
     * that the following fields should not be set, as they will get overridden by these utils:
     *
     * - [AbstractMessage.toIdentity]
     * - [AbstractMessage.fromIdentity]
     * - [AbstractMessage.messageId]
     * - [AbstractMessage.date]
     * - [AbstractGroupMessage.apiGroupId]
     * - [AbstractGroupMessage.groupCreator]
     *
     * Each call of this function must return a new instance of the message.
     */
    private val createGroupMessage: () -> AbstractGroupMessage,
) : OutgoingCspMessageCreator {

    constructor(
        messageId: MessageId,
        createdAt: Date,
        group: GroupModel,
        createAbstractGroupMessage: () -> AbstractGroupMessage,
    ) : this(
        messageId,
        createdAt,
        group.apiGroupId,
        group.creatorIdentity,
        createAbstractGroupMessage,
    )

    override fun createGenericMessage(myIdentity: String): AbstractMessage =
    // We do not need to set a 'toIdentity' in case of a generic message as this message will
    // never be sent. In case of a group message it is sufficient if it contains the correct
        // group identity.
        createAbstractMessage(myIdentity, "")

    override fun createAbstractMessage(
        fromIdentity: String,
        toIdentity: String,
    ): AbstractGroupMessage {
        return createGroupMessage().also {
            it.messageId = messageId
            it.date = createdAt
            it.fromIdentity = fromIdentity
            it.toIdentity = toIdentity
            it.apiGroupId = groupId
            it.groupCreator = groupCreator
        }
    }
}

/**
 * This handle can be used to keep track of the sending process of a message.
 */
class OutgoingCspMessageHandle(
    /**
     * The receivers of the message.
     */
    val receivers: Set<BasicContact>,
    /**
     * The message creator that creates the message that will be sent.
     */
    val messageCreator: OutgoingCspMessageCreator,
    /**
     * This callback is run as soon as the sent at timestamp is determined.
     */
    val markAsSent: (sentAt: ULong) -> Unit = { },
    /**
     * This callback is run as soon as the forward security modes are known.
     */
    val addForwardSecurityStateInfo: (stateMap: Map<String, ForwardSecurityMode>) -> Unit = { },
) {
    constructor(
        receiver: BasicContact,
        messageCreator: OutgoingCspMessageCreator,
        markAsSent: (sentAt: ULong) -> Unit = { },
        addForwardSecurityStateInfo: (stateMap: Map<String, ForwardSecurityMode>) -> Unit = { },
    ) : this(setOf(receiver), messageCreator, markAsSent, addForwardSecurityStateInfo)
}

private fun OutgoingCspMessageHandle.toOutgoingCspMessageSender(
    services: OutgoingCspMessageServices,
): OutgoingCspMessageSender {
    val myIdentity = services.identityStore.identity
    val genericMessage = messageCreator.createGenericMessage(myIdentity)
    val filteredReceivers = receivers
        .filter { it.identity != myIdentity }
        .filter { it.identityState != IdentityState.INVALID }
        .filterBlockedRecipients(
            genericMessage,
            services.contactModelRepository,
            services.contactStore,
            services.groupService,
            services.blockedIdentitiesService,
            services.preferenceService,
        )
        .toSet()

    return OutgoingCspMessageSender(
        filteredReceivers,
        messageCreator,
        genericMessage,
        markAsSent,
        addForwardSecurityStateInfo,
        services.multiDeviceManager,
        services.forwardSecurityMessageProcessor,
        services.identityStore,
        services.contactStore,
        services.nonceFactory,
    )
}

private class OutgoingCspMessageSender(
    receivers: Set<BasicContact>,
    val messageCreator: OutgoingCspMessageCreator,
    /**
     * A generic message that can be used to check message type properties.
     */
    val genericMessage: AbstractMessage,
    private val markAsSent: (sentAt: ULong) -> Unit,
    private val addForwardSecurityStateInfo: (stateMap: Map<String, ForwardSecurityMode>) -> Unit,
    multiDeviceManager: MultiDeviceManager,
    private val forwardSecurityMessageProcessor: ForwardSecurityMessageProcessor,
    private val identityStore: IdentityStoreInterface,
    private val contactStore: ContactStore,
    private val nonceFactory: NonceFactory,
) {
    /**
     * The multi device properties. Note that they are not null if and only if multi device is
     * active. Therefore, this can be used to check whether multi device is enabled or not.
     */
    private val multiDeviceProperties = if (multiDeviceManager.isMultiDeviceActive) {
        multiDeviceManager.propertiesProvider.get()
    } else {
        null
    }

    /**
     * The csp nonce that is used for the message. Note that any additional forward security control
     * message uses a different nonce.
     */
    private val receiverPairs: List<Pair<BasicContact, Nonce>> by lazy {
        receivers.map { it to nonceFactory.next(NonceScope.CSP) }
    }

    /**
     * The receivers of the message.
     */
    val receivers: List<BasicContact> by lazy {
        receiverPairs.map { it.first }
    }

    /**
     * Contains the recipient identities and message ids where the csp message ack is still pending.
     */
    private val pendingCspMessageAcks by lazy { mutableListOf<Pair<String, MessageId>>() }

    /**
     * Contains the reflect id and d2d nonce where the reflect ack is still pending.
     */
    private var pendingReflectAck: Pair<UInt, Nonce>? = null

    /**
     * Stores the forward security encryption results that contain the updated fs session states
     * that must be commited after the csp message has been acknowledged by the server.
     */
    private val forwardSecurityResults by lazy { mutableListOf<ForwardSecurityEncryptionResult>() }

    /**
     * The forward security modes of the message. This should reflect the state of the main message
     * (of the same type as [genericMessage]) and is independent of any other messages sent with it,
     * e.g., a forward security control message.
     */
    private val fsModeMap by lazy { mutableMapOf<String, ForwardSecurityMode>() }

    /**
     * Reflect the message. Note that the message will only be reflected if multi device is enabled.
     * It is safe to call this method even if multi device is deactivated.
     */
    suspend fun reflectMessage(handle: ActiveTaskCodec) {
        if (multiDeviceProperties != null && genericMessage.reflectOutgoing()) {
            genericMessage.logMessage("Reflecting")
            val cspNonces = receiverPairs.map { it.second }.toList()
            val encryptedEnvelopeResult = getEncryptedOutgoingMessageEnvelope(
                genericMessage,
                cspNonces,
                multiDeviceProperties.mediatorDeviceId,
                multiDeviceProperties.keys,
            )

            if (encryptedEnvelopeResult != null) {
                val reflectId: UInt = handle.reflect(encryptedEnvelopeResult)
                pendingReflectAck = reflectId to encryptedEnvelopeResult.nonce
            } else {
                logger.error("Cannot reflect message")
            }
        }
    }

    /**
     * Await every reflection ack in [pendingReflectAck]. If no message has been reflected this
     * immediately returns. Note that this message may return without suspending even if a message
     * has been reflected before. This method clears the [pendingReflectAck]s.
     *
     * @return the reflected at timestamp or null if there is nothing to await
     */
    suspend fun awaitReflectAck(handle: PassiveTaskCodec): ULong? {
        val (reflectId, d2dNonce) = pendingReflectAck ?: return null
        val reflectedAt = handle.awaitReflectAck(reflectId)
        if (genericMessage.protectAgainstReplay()) {
            nonceFactory.store(NonceScope.D2D, d2dNonce)
        }
        pendingReflectAck = null
        genericMessage.logMessage("Received reflect ack for")
        return reflectedAt
    }

    /**
     * Send the message. This will encapsulate the message in a forward security envelope if it is
     * not already a forward security envelope message.
     */
    suspend fun sendMessage(handle: ActiveTaskCodec) {
        genericMessage.logMessage("Sending")
        receiverPairs.mapNotNull { (receiver, nonce) ->
            // Encapsulate the messages with the given nonce. Note that this may lead to two
            // messages in total if an fs init or empty message needs to be sent too.
            if (genericMessage.type != ProtocolDefines.MSGTYPE_FS_ENVELOPE) {
                val result = forwardSecurityMessageProcessor.runFsEncapsulationSteps(
                    receiver,
                    messageCreator.createAbstractMessage(
                        identityStore.identity,
                        receiver.identity
                    ),
                    nonce,
                    nonceFactory,
                    handle
                )
                receiver.identity to result
            } else {
                logger.error("Sending an already encapsulated message with the bundled messages send steps is currently not possible.")
                null
            }
        }.forEach { (receiverIdentity, fsEncryptionResult) ->
            // Update the fs state map and store the result for commiting the fs session later on
            fsModeMap[receiverIdentity] = fsEncryptionResult.forwardSecurityMode
            forwardSecurityResults.add(fsEncryptionResult)

            for ((message, nonce) in fsEncryptionResult.outgoingMessages) {
                // If a server ack is required, store the message id to await it later on
                if (!message.hasFlags(ProtocolDefines.MESSAGE_FLAG_NO_SERVER_ACK)) {
                    pendingCspMessageAcks.add(receiverIdentity to message.messageId)
                }

                // Store the nonce if the message should be protected against replay
                if (message.protectAgainstReplay()) {
                    nonceFactory.store(NonceScope.CSP, nonce)
                }

                // Send the message
                handle.write(
                    message.toCspMessage(
                        identityStore,
                        contactStore,
                        nonce
                    )
                )
                message.logMessage("Sent")
            }
        }
    }

    /**
     * Await server ack. If there is no message without the flag
     * [ProtocolDefines.MESSAGE_FLAG_NO_SERVER_ACK]
     */
    suspend fun awaitServerAck(
        handle: PassiveTaskCodec,
    ) {
        pendingCspMessageAcks.forEach { (receiverIdentity, messageId) ->
            handle.awaitOutgoingMessageAck(messageId, receiverIdentity)
        }
        pendingCspMessageAcks.clear()
    }

    /**
     * Commit the forward security session states. Every state that has changed in [sendMessage]
     * will be committed by running this method. Note that this should happen after the server ack
     * has been received. The [forwardSecurityResults] will be cleared afterwards.
     */
    fun commitFsSession() {
        forwardSecurityResults.forEach {
            forwardSecurityMessageProcessor.commitSessionState(it)
        }
        forwardSecurityResults.clear()
    }

    /**
     * Reflect a message update sent if MD is active and the message type requires this. This will
     * add the reflect ids to [pendingReflectAck].
     */
    suspend fun reflectMessageUpdateSent(handle: ActiveTaskCodec) {
        if (multiDeviceProperties != null && genericMessage.reflectSentUpdate()) {
            genericMessage.logMessage("Reflecting outgoing message sent update for")
            val encryptedEnvelopeResult = getEncryptedOutgoingMessageUpdateSentEnvelope(
                genericMessage,
                multiDeviceProperties.mediatorDeviceId,
                multiDeviceProperties.keys
            )
            val reflectId: UInt = handle.reflect(encryptedEnvelopeResult)
            pendingReflectAck = reflectId to encryptedEnvelopeResult.nonce
        }
    }

    fun storeForwardSecurityStateInfo() {
        addForwardSecurityStateInfo(fsModeMap)
    }

    fun storeSentAt(sentAt: ULong) {
        markAsSent(sentAt)
    }
}

data class OutgoingCspMessageServices(
    val forwardSecurityMessageProcessor: ForwardSecurityMessageProcessor,
    val identityStore: IdentityStoreInterface,
    val userService: UserService,
    val contactStore: ContactStore,
    val contactService: ContactService,
    val contactModelRepository: ContactModelRepository,
    val groupService: GroupService,
    val nonceFactory: NonceFactory,
    val blockedIdentitiesService: BlockedIdentitiesService,
    val preferenceService: PreferenceService,
    val multiDeviceManager: MultiDeviceManager,
) {
    companion object {
        fun ServiceManager.getOutgoingCspMessageServices() = OutgoingCspMessageServices(
            forwardSecurityMessageProcessor,
            identityStore,
            userService,
            contactStore,
            contactService,
            modelRepositories.contacts,
            groupService,
            nonceFactory,
            blockedIdentitiesService,
            preferenceService,
            multiDeviceManager,
        )
    }
}

suspend fun ActiveTaskCodec.runBundledMessagesSendSteps(
    outgoingCspMessageHandle: OutgoingCspMessageHandle,
    services: OutgoingCspMessageServices,
) = runBundledMessagesSendSteps(listOf(outgoingCspMessageHandle), services)

suspend fun ActiveTaskCodec.runBundledMessagesSendSteps(
    outgoingCspMessageHandles: List<OutgoingCspMessageHandle>,
    services: OutgoingCspMessageServices,
) {
    val outgoingCspMessageSenders = outgoingCspMessageHandles
        .map { it.toOutgoingCspMessageSender(services) }

    val profilePictureSenders = outgoingCspMessageSenders
        .map { messageSender ->
            messageSender.receivers
                .mapNotNull { receiver ->
                    runProfilePictureDistributionSteps(
                        messageSender.genericMessage,
                        receiver,
                        services,
                    )
                }
        }.flatten()

    val messageSenders = outgoingCspMessageSenders + profilePictureSenders

    val localSentAtTimestamp = System.currentTimeMillis().toULong()

    messageSenders
        .onEach {
            it.reflectMessage(this)
        }.onEach {
            it.awaitReflectAck(this)
        }.onEach {
            it.sendMessage(this)
        }.onEach {
            it.awaitServerAck(this)
            it.commitFsSession()
        }.onEach {
            it.reflectMessageUpdateSent(this)
        }.forEach {
            val sentAt = it.awaitReflectAck(this) ?: localSentAtTimestamp
            it.storeSentAt(sentAt)
            it.storeForwardSecurityStateInfo()
        }
}

private fun Iterable<BasicContact>.filterBlockedRecipients(
    abstractMessage: AbstractMessage,
    contactModelRepository: ContactModelRepository,
    contactStore: ContactStore,
    groupService: GroupService,
    blockedIdentitiesService: BlockedIdentitiesService,
    preferenceService: PreferenceService,
) = filter {
    if (abstractMessage.exemptFromBlocking()) {
        return@filter true
    }

    val isBlocked = runIdentityBlockedSteps(
        it.identity,
        contactModelRepository,
        contactStore,
        groupService,
        blockedIdentitiesService,
        preferenceService,
    ).isBlocked()

    if (isBlocked) {
        abstractMessage.logMessage("Skipping recipient ${it.identity} for")
    }

    !isBlocked
}

/**
 * Run the profile picture distribution steps.
 *
 * TODO(ANDR-3243): Update the profile picture distribution steps.
 */
private fun runProfilePictureDistributionSteps(
    genericMessage: AbstractMessage,
    receiver: BasicContact,
    services: OutgoingCspMessageServices,
): OutgoingCspMessageSender? {
    val prefix = "Profile picture distribution"
    val receiverIdentity = receiver.identity

    if (!genericMessage.allowUserProfileDistribution()) {
        return null
    }

    if (ContactUtil.isEchoEchoOrGatewayContact(receiverIdentity)) {
        logger.info(
            "{}: Contact {} should not receive the profile picture",
            prefix,
            receiverIdentity
        )
        return null
    }

    val contactModel = services.contactModelRepository.getByIdentity(receiverIdentity) ?: run {
        logger.info("{}: Contact model for identity {} not found", prefix, receiverIdentity)
        return null
    }

    if (!services.contactService.isContactAllowedToReceiveProfilePicture(receiverIdentity)) {
        logger.info(
            "{}: Contact {} is not allowed to receive the profile picture",
            prefix,
            receiverIdentity,
        )
        return null
    }

    val data = services.userService.uploadUserProfilePictureOrGetPreviousUploadData()
    if (data.blobId == null) {
        logger.warn("{}: Blob ID is null; abort", prefix)
        return null
    }

    val contactModelData = contactModel.data.value ?: run {
        logger.info("{}: Contact {} has been deleted", prefix, receiverIdentity)
        return null
    }

    if (data.blobId.contentEquals(contactModelData.profilePictureBlobId)) {
        logger.debug(
            "{}: Contact {} already has the latest profile picture",
            prefix,
            receiverIdentity
        )
        return null
    }

    contactModel.setProfilePictureBlobId(data.blobId)

    val profilePictureMessageCreator =
        OutgoingCspContactMessageCreator(
            MessageId(),
            Date(),
            receiverIdentity,
        ) {
            if (data.blobId.contentEquals(ContactModel.NO_PROFILE_PICTURE_BLOB_ID)) {
                DeleteProfilePictureMessage()
            } else {
                SetProfilePictureMessage(
                    blobId = data.blobId,
                    size = data.size,
                    encryptionKey = data.encryptionKey,
                )
            }
        }

    return OutgoingCspMessageHandle(
        setOf(receiver),
        profilePictureMessageCreator,
    ).toOutgoingCspMessageSender(services)
}

private fun AbstractMessage.logMessage(logMessage: String) {
    if (toIdentity.isNotBlank()) {
        logger.info(
            "{} message {} of type {} to {}",
            logMessage,
            messageId,
            Utils.byteToHex(type.toByte(), true, true),
            toIdentity,
        )
    } else {
        logger.info(
            "{} message {} of type {}",
            logMessage,
            messageId,
            Utils.byteToHex(type.toByte(), true, true),
        )
    }
}
