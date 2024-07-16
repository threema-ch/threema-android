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

package ch.threema.app.processors

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.calls.IncomingCallAnswerTask
import ch.threema.app.processors.calls.IncomingCallHangupTask
import ch.threema.app.processors.calls.IncomingCallIceCandidateTask
import ch.threema.app.processors.calls.IncomingCallOfferTask
import ch.threema.app.processors.calls.IncomingCallRingingTask
import ch.threema.app.processors.contactcontrol.IncomingContactRequestProfilePictureTask
import ch.threema.app.processors.contactcontrol.IncomingDeleteProfilePictureTask
import ch.threema.app.processors.contactcontrol.IncomingSetProfilePictureTask
import ch.threema.app.processors.conversation.IncomingBallotVoteTask
import ch.threema.app.processors.conversation.IncomingContactConversationMessageTask
import ch.threema.app.processors.conversation.IncomingContactDeleteMessageTask
import ch.threema.app.processors.conversation.IncomingContactEditMessageTask
import ch.threema.app.processors.conversation.IncomingGroupConversationMessageTask
import ch.threema.app.processors.conversation.IncomingGroupDeleteMessageTask
import ch.threema.app.processors.conversation.IncomingGroupEditMessageTask
import ch.threema.app.processors.fs.IncomingEmptyTask
import ch.threema.app.processors.groupcontrol.IncomingGroupCallControlTask
import ch.threema.app.processors.groupcontrol.IncomingGroupDeleteProfilePictureTask
import ch.threema.app.processors.groupcontrol.IncomingGroupJoinRequestTask
import ch.threema.app.processors.groupcontrol.IncomingGroupJoinResponseMessage
import ch.threema.app.processors.groupcontrol.IncomingGroupLeaveTask
import ch.threema.app.processors.groupcontrol.IncomingGroupNameTask
import ch.threema.app.processors.groupcontrol.IncomingGroupSetProfilePictureTask
import ch.threema.app.processors.groupcontrol.IncomingGroupSetupTask
import ch.threema.app.processors.groupcontrol.IncomingGroupSyncRequestTask
import ch.threema.app.processors.push.IncomingWebSessionResumeMessageTask
import ch.threema.app.processors.statusupdates.IncomingDeliveryReceiptTask
import ch.threema.app.processors.statusupdates.IncomingGroupDeliveryReceiptTask
import ch.threema.app.processors.statusupdates.IncomingTypingIndicatorTask
import ch.threema.app.services.ContactService
import ch.threema.app.services.IdListService
import ch.threema.app.services.MessageService
import ch.threema.app.services.PreferenceService
import ch.threema.app.tasks.OutgoingContactDeliveryReceiptMessageTask
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.Utils
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.api.APIConnector.HttpConnectionException
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.coders.MessageBox
import ch.threema.domain.protocol.csp.coders.MessageCoder
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.protocol.csp.fs.PeerRatchetIdentifier
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.ContactRequestProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.DeleteMessage
import ch.threema.domain.protocol.csp.messages.DeleteProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage
import ch.threema.domain.protocol.csp.messages.EditMessage
import ch.threema.domain.protocol.csp.messages.EmptyMessage
import ch.threema.domain.protocol.csp.messages.GroupDeleteMessage
import ch.threema.domain.protocol.csp.messages.GroupDeleteProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.GroupDeliveryReceiptMessage
import ch.threema.domain.protocol.csp.messages.GroupEditMessage
import ch.threema.domain.protocol.csp.messages.GroupLeaveMessage
import ch.threema.domain.protocol.csp.messages.GroupNameMessage
import ch.threema.domain.protocol.csp.messages.GroupSetProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.protocol.csp.messages.GroupSyncRequestMessage
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException
import ch.threema.domain.protocol.csp.messages.SetProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.TypingIndicatorMessage
import ch.threema.domain.protocol.csp.messages.WebSessionResumeMessage
import ch.threema.domain.protocol.csp.messages.ballot.BallotVoteInterface
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityEnvelopeMessage
import ch.threema.domain.protocol.csp.messages.group.GroupJoinRequestMessage
import ch.threema.domain.protocol.csp.messages.group.GroupJoinResponseMessage
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallControlMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipICECandidatesMessage
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.stores.IdentityStoreInterface
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.IncomingMessageProcessor
import ch.threema.domain.taskmanager.NetworkException
import ch.threema.domain.taskmanager.ProtocolException
import ch.threema.domain.taskmanager.catchAllExceptNetworkException
import ch.threema.domain.taskmanager.catchExceptNetworkException
import ch.threema.storage.models.ServerMessageModel
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("IncomingMessageProcessorImpl")

class IncomingMessageProcessorImpl(
    private val messageService: MessageService,
    private val nonceFactory: NonceFactory,
    private val forwardSecurityMessageProcessor: ForwardSecurityMessageProcessor,
    private val contactService: ContactService,
    private val contactStore: ContactStore,
    private val identityStore: IdentityStoreInterface,
    private val blackListService: IdListService,
    private val preferenceService: PreferenceService,
    private val serviceManager: ServiceManager,
) : IncomingMessageProcessor {
    private val incomingForwardSecurityProcessor = IncomingForwardSecurityProcessor(serviceManager)

    override suspend fun processIncomingMessage(messageBox: MessageBox, handle: ActiveTaskCodec) {
        suspend {
            processMessage(messageBox, handle)
        }.catchAllExceptNetworkException { e ->
            val messageId = messageBox.messageId
            val fromIdentity = messageBox.fromIdentity

            logger.error("Processing message {} from {} failed", messageId, fromIdentity, e)

            // If we catch a network related exception, we throw a protocol exception to trigger a
            // reconnect by the task manager.
            if (e is HttpConnectionException || e is APIConnector.NetworkException) {
                val errorMsg = "Could not process message $messageId from $fromIdentity"
                logger.error(errorMsg, e)
                throw ProtocolException(e.message ?: errorMsg)
            }

            // For every other exception, we acknowledge the failed message. Due to forward
            // security, it would not make sense to process a message later on. Note that when a
            // NetworkException is thrown, we do not need to acknowledge the message as the task
            // manager will be stopped before the next message can be processed. On the next
            // reconnect, we will receive the non-acked messages.
            // Note that we do not protect messages that could not be decoded against replay
            acknowledgeMessage(messageBox, false, null, handle)
        }
    }

    override fun processIncomingServerAlert(alertData: CspMessage.ServerAlertData) {
        val msg = ServerMessageModel(alertData.message, ServerMessageModel.TYPE_ALERT)
        messageService.saveIncomingServerMessage(msg)
    }

    override fun processIncomingServerError(errorData: CspMessage.ServerErrorData) {
        val errorMessage = errorData.message
        if (errorMessage.contains("Another connection")) {
            // See `MonitoringLayer#handleCloseError(CspContainer)` for more info
            logger.info("Do not display `Another connection` close-error")
        } else {
            val msg = ServerMessageModel(errorMessage, ServerMessageModel.TYPE_ERROR)
            messageService.saveIncomingServerMessage(msg)
        }
    }

    private suspend fun processMessage(messageBox: MessageBox, handle: ActiveTaskCodec) {
        logger.info(
            "Incoming message from {} with ID {}",
            messageBox.fromIdentity,
            messageBox.messageId
        )

        val (message, peerRatchetIdentifier) = suspend {
            decryptMessage(messageBox, handle)
        }.catchExceptNetworkException { e: DiscardMessageException ->
            logger.warn("Discard message {}", messageBox.messageId)
            // If the message could be decrypted, then check if it should be protected against
            // replay. Otherwise we do not protect it against replay.
            val protectAgainstReplay = e.discardedMessage?.protectAgainstReplay() ?: false
            acknowledgeMessage(messageBox, protectAgainstReplay, e.peerRatchetIdentifier, handle)
            return
        }

        if (message == null) {
            // Note that if the message is null, it is an fs control message or an invalid message
            // that does not need any further processing. Therefore we just acknowledge the message.
            // Note that we need to protect fs control message against replay.
            acknowledgeMessage(messageBox, true, peerRatchetIdentifier, handle)
            return
        }

        suspend {
            processMessage(message, handle)
        }.catchExceptNetworkException { _: DiscardMessageException ->
            logger.warn("Discard message {}", messageBox.messageId)
            acknowledgeMessage(
                messageBox,
                message.protectAgainstReplay(),
                peerRatchetIdentifier,
                handle
            )
            return
        }

        // Acknowledge the message
        acknowledgeMessage(messageBox, message.protectAgainstReplay(), peerRatchetIdentifier, handle)

        // If the message type requires automatic delivery receipts and the message does not contain
        // the no delivery receipt flag, send a delivery receipt
        if (message.sendAutomaticDeliveryReceipt()
            && !message.hasFlags(ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS)
        ) {
            OutgoingContactDeliveryReceiptMessageTask(
                ProtocolDefines.DELIVERYRECEIPT_MSGRECEIVED,
                arrayOf(message.messageId),
                Date().time,
                message.fromIdentity,
                serviceManager
            ).invoke(handle)
            logger.info(
                "Sent delivery receipt (delivered) message for message ID {} from {}",
                message.messageId, message.fromIdentity
            )
        }
    }

    private suspend fun decryptMessage(
        messageBox: MessageBox,
        handle: ActiveTaskCodec,
    ): Pair<AbstractMessage?, PeerRatchetIdentifier?> {
        // If the nonce has already been used, acknowledge and discard the message
        if (nonceFactory.exists(messageBox.nonce)) {
            logger.warn(
                "Skipped processing message {} as its nonce has already been used",
                messageBox.messageId
            )
            throw DiscardMessageException()
        }

        // First, we need to ensure we have the public key of the sender
        contactService.fetchAndCacheContact(messageBox.fromIdentity)

        // Try to decode the message. At this point we have the public key of the sender either
        // stored or cached in the contact store.
        val messageCoder = MessageCoder(this.contactStore, this.identityStore)
        val encapsulatedMessage = try {
            messageCoder.decode(messageBox)
        } catch (e: BadMessageException) {
            logger.warn("Could not decode message: {}", e.message)
            throw DiscardMessageException()
        }

        logger.info(
            "Incoming message {} from {} to {} (type {})",
            messageBox.messageId,
            messageBox.fromIdentity,
            messageBox.toIdentity,
            Utils.byteToHex(encapsulatedMessage.type.toByte(), true, true)
        )

        // Decapsulate fs message if it is an fs envelope message
        val (message, peerRatchetIdentifier) = decapsulateMessage(encapsulatedMessage, handle)

        // In case there is no decapsulated message, it was an fs control message or an invalid
        // message that does not need any further processing
        if (message == null) {
            return null to peerRatchetIdentifier
        }

        logger.info(
            "Processing decrypted message {} from {} to {} (type {})",
            message.messageId,
            message.fromIdentity,
            message.toIdentity,
            Utils.byteToHex(message.type.toByte(), true, true)
        )

        if (isBlocked(message.fromIdentity) && !message.exemptFromBlocking()) {
            logger.info(
                "Message {} from {} will be discarded: Contact is implicitly or explicitly blocked.",
                message.messageId,
                message.fromIdentity
            )
            throw DiscardMessageException(message, peerRatchetIdentifier)
        }

        return Pair(message, peerRatchetIdentifier)
    }

    private suspend fun decapsulateMessage(
        encapsulated: AbstractMessage,
        handle: ActiveTaskCodec,
    ): Pair<AbstractMessage?, PeerRatchetIdentifier?> {
        // If the message is not a fs encapsulated message, warn the user depending on the fs
        // session and return the already decapsulated message.
        if (encapsulated !is ForwardSecurityEnvelopeMessage) {
            forwardSecurityMessageProcessor.warnIfMessageWithoutForwardSecurityReceived(
                encapsulated,
                handle
            )
            return Pair(encapsulated, null)
        }

        val contact = contactStore.getContactForIdentityIncludingCache(encapsulated.fromIdentity)
            ?: throw MissingPublicKeyException("Missing public key for ID ${encapsulated.fromIdentity}")

        val fsDecryptionResult = incomingForwardSecurityProcessor
            .processEnvelopeMessage(contact, encapsulated, handle)

        return Pair(fsDecryptionResult.message, fsDecryptionResult.peerRatchetIdentifier)
    }

    private suspend fun processMessage(message: AbstractMessage, handle: ActiveTaskCodec) {
        if (message.fromIdentity == ProtocolDefines.SPECIAL_CONTACT_PUSH) {
            when (message) {
                is WebSessionResumeMessage -> IncomingWebSessionResumeMessageTask(
                    message,
                    serviceManager,
                )

                else -> throw DiscardMessageException(message)
            }.run(handle)

            return
        }

        // Create implicit direct contact depending on the message type and if not already exists
        if (message.createImplicitlyDirectContact() && contactService.getByIdentity(message.fromIdentity) == null) {
            contactService.createContactByIdentity(message.fromIdentity, true)
        }

        // Update the nickname and set contact as active (if contact exists)
        contactService.updatePublicNickName(message)
        contactService.setActive(message.fromIdentity)

        // Determine the message type and get its corresponding receive steps. Note that the order
        // of checking the types is important. For instance, an abstract group message must first be
        // checked for a group control message before processing it as a group conversation message.
        val subtask = when (message) {
            // Check if message is a status update
            is TypingIndicatorMessage -> IncomingTypingIndicatorTask(message, serviceManager)
            is DeliveryReceiptMessage -> IncomingDeliveryReceiptTask(message, serviceManager)
            is GroupDeliveryReceiptMessage -> IncomingGroupDeliveryReceiptTask(
                message,
                serviceManager
            )

            // Check if message is a group control message
            is GroupSetupMessage -> IncomingGroupSetupTask(message, serviceManager)
            is GroupNameMessage -> IncomingGroupNameTask(message, serviceManager)
            is GroupSetProfilePictureMessage -> IncomingGroupSetProfilePictureTask(
                message,
                serviceManager
            )
            is GroupDeleteProfilePictureMessage -> IncomingGroupDeleteProfilePictureTask(
                message,
                serviceManager
            )
            is GroupLeaveMessage -> IncomingGroupLeaveTask(message, serviceManager)
            is GroupSyncRequestMessage -> IncomingGroupSyncRequestTask(message, serviceManager)
            is GroupCallControlMessage -> IncomingGroupCallControlTask(message, serviceManager)

            // Check if message is a contact control message
            is SetProfilePictureMessage -> IncomingSetProfilePictureTask(message, serviceManager)
            is DeleteProfilePictureMessage -> IncomingDeleteProfilePictureTask(
                message,
                serviceManager
            )
            is ContactRequestProfilePictureMessage -> IncomingContactRequestProfilePictureTask(
                message,
                serviceManager
            )

            // Check if message is a ballot or group join message
            is BallotVoteInterface -> IncomingBallotVoteTask(message, serviceManager)
            is GroupJoinRequestMessage -> IncomingGroupJoinRequestTask(message, serviceManager)
            is GroupJoinResponseMessage -> IncomingGroupJoinResponseMessage(message, serviceManager)

            // Check if message is a call message
            is VoipCallOfferMessage -> IncomingCallOfferTask(message, serviceManager)
            is VoipCallAnswerMessage -> IncomingCallAnswerTask(message, serviceManager)
            is VoipICECandidatesMessage -> IncomingCallIceCandidateTask(message, serviceManager)
            is VoipCallRingingMessage -> IncomingCallRingingTask(message, serviceManager)
            is VoipCallHangupMessage -> IncomingCallHangupTask(message, serviceManager)

            // Check if message is an edit message
            is EditMessage -> IncomingContactEditMessageTask(message, serviceManager)
            is GroupEditMessage -> IncomingGroupEditMessageTask(message, serviceManager)

            // Check if message is a delete message
            is DeleteMessage -> IncomingContactDeleteMessageTask(message, serviceManager)
            is GroupDeleteMessage -> IncomingGroupDeleteMessageTask(message, serviceManager)

            // If it is a group message, process it as a group conversation message
            is AbstractGroupMessage -> IncomingGroupConversationMessageTask(message, serviceManager)

            // Process the empty message in its corresponding task
            is EmptyMessage -> IncomingEmptyTask(message, serviceManager)

            // Otherwise it must be a contact conversation message
            else -> IncomingContactConversationMessageTask(message, serviceManager)
        }

        val result = try {
            subtask.run(handle)
        } catch (e: Exception) {
            when (e) {
                // If a network exception is thrown, we cancel processing the message to start over
                // when the connection has been restarted.
                is NetworkException -> throw e
                // Any other exception should never be thrown. If there is one, we discard the
                // message. Note that we also acknowledge discarded messages towards the server.
                else -> {
                    logger.error("Error while processing incoming message", e)
                    throw DiscardMessageException(message)
                }
            }
        }

        if (result == ReceiveStepsResult.DISCARD) {
            throw DiscardMessageException(message)
        }
    }

    private suspend fun acknowledgeMessage(
        messageBox: MessageBox,
        protectAgainstReplay: Boolean,
        peerRatchetIdentifier: PeerRatchetIdentifier?,
        handle: ActiveTaskCodec,
    ) {
        // If the no-server-ack message flag is not set, send a message-ack to the server
        if ((messageBox.flags and ProtocolDefines.MESSAGE_FLAG_NO_SERVER_ACK) == 0) {
            sendAck(messageBox.messageId, messageBox.fromIdentity, handle)
        }

        // If the message should be protected against replay, store the nonce
        if (protectAgainstReplay) {
            nonceFactory.store(messageBox.nonce)
        }

        // If there is a peer ratchet identifier known, then turn the peer ratchet
        peerRatchetIdentifier?.let {
            forwardSecurityMessageProcessor.commitPeerRatchet(it, handle)
        }
    }

    private suspend fun sendAck(messageId: MessageId, identity: String, handle: ActiveTaskCodec) {
        logger.debug("Sending ack for message ID {} from {}", messageId, identity)

        val data = identity.encodeToByteArray() + messageId.messageId

        handle.write(CspMessage(ProtocolDefines.PLTYPE_INCOMING_MESSAGE_ACK.toUByte(), data))
    }

    private fun isBlocked(identity: String): Boolean =
        blackListService.has(identity) || contactService.getByIdentity(identity) == null && preferenceService.isBlockUnknown

    private class DiscardMessageException(
        val discardedMessage: AbstractMessage?,
        val peerRatchetIdentifier: PeerRatchetIdentifier?,
    ) : Exception() {
        constructor() : this(null, null)

        constructor(discardedMessage: AbstractMessage?) : this(discardedMessage, null)

    }
}
