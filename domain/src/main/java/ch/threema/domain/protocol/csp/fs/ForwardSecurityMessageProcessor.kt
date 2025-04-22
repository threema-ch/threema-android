/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.domain.protocol.csp.fs

import ch.threema.base.ThreemaException
import ch.threema.base.crypto.Nonce
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceScope
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.fs.DHSession
import ch.threema.domain.fs.DHSession.RejectMessageError
import ch.threema.domain.fs.DHSessionId
import ch.threema.domain.fs.KDFRatchet.RatchetRotationException
import ch.threema.domain.models.BasicContact
import ch.threema.domain.models.Contact
import ch.threema.domain.protocol.ThreemaFeature
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.coders.MessageCoder
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.EmptyMessage
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityData
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataAccept
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataInit
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataMessage
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataReject
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataTerminate
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityEnvelopeMessage
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityMode
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.stores.DHSessionStoreException
import ch.threema.domain.stores.DHSessionStoreInterface
import ch.threema.domain.stores.IdentityStoreInterface
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.awaitOutgoingMessageAck
import ch.threema.domain.taskmanager.toCspMessage
import ch.threema.protobuf.Common.GroupIdentity
import ch.threema.protobuf.csp.e2e.fs.Encapsulated.DHType
import ch.threema.protobuf.csp.e2e.fs.Reject
import ch.threema.protobuf.csp.e2e.fs.Terminate
import ch.threema.protobuf.csp.e2e.fs.Terminate.Cause
import com.neilalexander.jnacl.NaCl
import java.io.ByteArrayOutputStream
import java.util.Date
import kotlinx.coroutines.runBlocking

private val logger = LoggingUtil.getThreemaLogger("ForwardSecurityMessageProcessor")

class ForwardSecurityMessageProcessor(
    private val dhSessionStoreInterface: DHSessionStoreInterface,
    private val contactStore: ContactStore,
    private val identityStoreInterface: IdentityStoreInterface,
    private val nonceFactory: NonceFactory,
    private val statusListener: ForwardSecurityStatusListener,
) {
    init {
        dhSessionStoreInterface.setDHSessionStoreErrorHandler { peerIdentity, sessionId, handle ->
            // Try to send a terminate to the peer contact
            val contact = contactStore.getContactForIdentity(peerIdentity)
            if (contact != null) {
                runBlocking {
                    sendTerminateAndDeleteSession(contact, sessionId, Cause.RESET, handle)
                }
            } else {
                logger.error("Cannot send terminate to unknown contact where DH session is invalid")
            }
            // Show a status message to the user
            if (contact != null) {
                statusListener.postIllegalSessionState(sessionId, contact)
            }
        }
    }

    private var isFsEnabled = true

    /**
     * Set whether forward security is enabled.
     *
     * If disabled, [runFsEncapsulationSteps] does not encapsulate the message and received fs
     * messages will be answered with a [Terminate].
     *
     * TODO(ANDR-2519): Remove when md allows fs
     */
    fun setForwardSecurityEnabled(fsEnabled: Boolean) {
        this.isFsEnabled = fsEnabled
    }

    fun isForwardSecurityEnabled() = isFsEnabled

    /**
     * Check whether a forward security message from the given sender can be processed. If forward
     * security is disabled because of md, this method sends a terminate to the contact and returns
     * false.
     *
     * @param sender        the sender of the received message
     * @param sessionId     the session id of the received message
     * @param sendTerminate true if a terminate should be sent
     * @param handle        the task handle to allow sending a terminate
     * @return true if forward security is enabled, false otherwise
     */
    suspend fun canForwardSecurityMessageBeProcessed(
        sender: Contact,
        sessionId: DHSessionId,
        sendTerminate: Boolean,
        handle: ActiveTaskCodec,
    ): Boolean {
        if (!isFsEnabled) {
            if (sendTerminate) {
                sendTerminateAndDeleteSession(sender, sessionId, Cause.DISABLED_BY_LOCAL, handle)
            }
            return false
        }

        return true
    }

    /**
     * Run the forward security encapsulation steps for the given recipient and inner message.
     *
     * Note that this result must be used to commit the forward security session after all messages
     * of this result have been acknowledged by the server. To commit the forward security session
     * use [commitSessionState].
     *
     * @param recipient    the recipient of the [innerMessage]
     * @param innerMessage the message that will be encapsulated if the session allows it
     * @param nonce        the nonce that will be used for the [innerMessage]. Note that the nonce
     *                     is only appended to the corresponding [innerMessage] in the result.
     * @param nonceFactory the nonce factory is only used to pre generate a nonce for every outgoing
     *                     message
     * @param handle       the task codec that is only used to communicate an illegal state of a
     *                     session to the chat partner
     *
     * @return a [ForwardSecurityEncryptionResult] with the outgoing messages and their nonce
     */
    fun runFsEncapsulationSteps(
        recipient: BasicContact,
        innerMessage: AbstractMessage,
        nonce: Nonce,
        nonceFactory: NonceFactory,
        handle: ActiveTaskCodec,
    ): ForwardSecurityEncryptionResult {
        // TODO(ANDR-2519): Remove when md allows fs
        val senderCanForwardSecurity = isForwardSecurityEnabled()
        val recipientCanForwardSecurity =
            ThreemaFeature.canForwardSecurity(recipient.featureMask.toLong())
        val innerMessageEncapsulated = innerMessage is ForwardSecurityEnvelopeMessage

        // Create forward security encryption result
        val (outgoingMessages, session) =
            if (senderCanForwardSecurity && recipientCanForwardSecurity && !innerMessageEncapsulated) {
                makeMessage(recipient, innerMessage, handle)
            } else {
                listOf(innerMessage) to null
            }

        // Get the forward security mode from the encryption result if available, otherwise take
        // the forward security mode of the inner message.
        val forwardSecurityMode = outgoingMessages.last().forwardSecurityMode

        // Create a nonce for every outgoing message. Note that the nonce will be saved when the
        // message is encoded (depending on the message type)
        val nonces = outgoingMessages.dropLast(1).map { nonceFactory.next(NonceScope.CSP) }

        return ForwardSecurityEncryptionResult(
            outgoingMessages zip (nonces + nonce),
            session,
            forwardSecurityMode,
        )
    }

    /**
     * Encapsulate the message for sending it with forward security. This method returns a list of
     * messages to be sent in the same order and an updated dh session. The list of messages may
     * contain an init or an empty forward security message. The given inner message is always part
     * of the returned list - either encapsulated or in its original form if it cannot be
     * encapsulated in the existing session.
     *
     * @param contact      the recipient identity
     * @param innerMessage the inner message that may get encapsulated
     * @param handle       the task codec that is only used to communicate an illegal state of a
     *                     session to the chat partner
     * @return the encapsulated messages and an updated dh session
     *
     * @throws IllegalStateException if [isFsEnabled] is false
     */
    @Throws(ThreemaException::class)
    private fun makeMessage(
        contact: Contact,
        innerMessage: AbstractMessage,
        handle: ActiveTaskCodec,
    ): Pair<List<AbstractMessage>, DHSession> {
        // TODO(ANDR-2519): Remove when md allows fs
        if (!isFsEnabled) {
            throw IllegalStateException("Sending messages with fs is not supported locally")
        }

        var initMessage: ForwardSecurityEnvelopeMessage? = null

        // Check if we already have a session with this contact
        var session = dhSessionStoreInterface.getBestDHSession(
            identityStoreInterface.identity,
            contact.identity,
            handle,
        )
        var isExistingSession = true
        if (session == null) {
            // Establish a new DH session
            session = DHSession(contact, identityStoreInterface)
            // Set last outgoing message timestamp to now, as we will just send a message (init) in
            // this session.
            session.lastOutgoingMessageTimestamp = Date().time
            // Do not yet save the session. In case the send task fails and is restarted, the init
            // would not be sent if the session would already exist. Therefore, a new session should
            // be created again.
            logger.debug(
                "Starting new DH session ID {} with {}",
                session.id,
                contact.identity,
            )
            statusListener.newSessionInitiated(session, contact)
            isExistingSession = false

            // Create init message
            val init = ForwardSecurityDataInit(
                session.id,
                DHSession.SUPPORTED_VERSION_RANGE,
                session.myEphemeralPublicKey,
            )
            initMessage = ForwardSecurityEnvelopeMessage(init, true)
            initMessage.toIdentity = contact.identity

            // Check that the message type is supported in the new session
            val requiredVersion = innerMessage.minimumRequiredForwardSecurityVersion
            if (requiredVersion == null || requiredVersion.number > DHSession.SUPPORTED_VERSION_MIN.number) {
                logger.info(
                    "As the session has just been created (with min version {}), we cannot send the message {} " +
                        "with forward security (required version {})",
                    session.outgoingAppliedVersion,
                    innerMessage.messageId,
                    requiredVersion,
                )

                // If the session has been newly created, add the inner message un-encapsulated
                return listOfNotNull(initMessage, innerMessage) to session
            }
        }

        // Warn if we're trying to send something in an illegal state
        if (session.state == DHSession.State.R20) {
            logger.error("Encapsulating a message in R20 state is illegal")
        }

        // Check that the message type is supported in the current session
        val appliedVersion = session.outgoingAppliedVersion
        val requiredVersion = innerMessage.minimumRequiredForwardSecurityVersion
        return if (requiredVersion == null || requiredVersion.number > appliedVersion.number) {
            logger.info(
                "The session's outgoing applied version ({}) is too low to send the message {} (that requires version {}) with forward security",
                session.outgoingAppliedVersion,
                innerMessage.messageId,
                requiredVersion,
            )
            val dayInMs = 1000L * 60 * 60 * 24
            val now = Date().time
            val lastOutgoingMessageTimestamp = session.lastOutgoingMessageTimestamp
            var emptyMessage: ForwardSecurityEnvelopeMessage? = null
            if (now - lastOutgoingMessageTimestamp >= dayInMs) {
                logger.info(
                    "Empty message to enforce fs session freshness required (last outgoing message {})",
                    lastOutgoingMessageTimestamp,
                )
                val innerEmptyMessage = EmptyMessage()
                innerEmptyMessage.toIdentity = contact.identity
                emptyMessage = encapsulateMessage(session, innerEmptyMessage, isExistingSession)
                // Update the session, but do not yet persist this change. It must only be persisted
                // after the outgoing messages have been acknowledged by the server.
                session.lastOutgoingMessageTimestamp = now
            }
            listOfNotNull(emptyMessage, innerMessage) to session
        } else {
            // Update the session, but do not yet persist this change. It must only be persisted
            // after the outgoing messages have been acknowledged by the server.
            session.lastOutgoingMessageTimestamp = Date().time
            val encapsulatedMessage = encapsulateMessage(session, innerMessage, isExistingSession)
            listOfNotNull(initMessage, encapsulatedMessage) to session
        }
    }

    /**
     * Commit the session state after the encrypted forward security message has been successfully
     * sent with the result obtained from {@link #makeMessage(Contact, AbstractMessage, ActiveTaskCodec)}.
     *
     * @param result the encryption result that was generated when encrypting the messages
     */
    fun commitSessionState(result: ForwardSecurityEncryptionResult) {
        val updatedSessionState = result.updatedSessionState ?: return

        try {
            dhSessionStoreInterface.storeDHSession(updatedSessionState)
        } catch (e: DHSessionStoreException) {
            logger.error("Could not store updated session state", e)
        }
    }

    /**
     * Refresh the session with the given contact. Note that this method must only be called if fs
     * is enabled.
     *
     * @param contact the contact whose session should be refreshed
     */
    @Throws(
        DHSessionStoreException::class,
        ForwardSecurityData.InvalidEphemeralPublicKeyException::class,
    )
    suspend fun runFsRefreshSteps(contact: Contact, handle: ActiveTaskCodec) {
        val session = dhSessionStoreInterface.getBestDHSession(
            identityStoreInterface.identity,
            contact.identity,
            handle,
        )
        if (session == null) {
            createAndSendNewSession(contact, handle)
        } else {
            // In case of an existing session, we create an encapsulated empty message and send it
            createAndSendEmptyMessage(session, contact, handle)

            // We update the session's timestamp and persist it. Note that at this point the server
            // ack must have been received.
            session.lastOutgoingMessageTimestamp = Date().time
            dhSessionStoreInterface.storeDHSession(session)
        }
    }

    fun warnIfMessageWithoutForwardSecurityReceived(
        message: AbstractMessage,
        handle: ActiveTaskCodec,
    ) {
        val contact = contactStore.getContactForIdentity(message.fromIdentity) ?: return
        val bestSession = try {
            dhSessionStoreInterface.getBestDHSession(
                identityStoreInterface.identity,
                message.fromIdentity,
                handle,
            )
        } catch (e: DHSessionStoreException) {
            logger.error("Could not get best session", e)
            return
        }

        if (bestSession != null) {
            val minimumVersion = message.minimumRequiredForwardSecurityVersion
            if (minimumVersion != null &&
                minimumVersion.number <= bestSession.minimumIncomingAppliedVersion.number
            ) {
                // TODO(ANDR-2452): Remove this feature mask update when enough clients have updated
                // Check whether this contact still supports forward security when receiving a
                // message without forward security.
                if (statusListener.hasForwardSecuritySupport(contact)) {
                    statusListener.updateFeatureMask(contact)
                }

                // Warn only if the contact still has forward security support, otherwise a status
                // message that the contact has downgraded is shown to the user
                if (statusListener.hasForwardSecuritySupport(contact)) {
                    statusListener.messageWithoutFSReceived(contact, bestSession, message)
                }
            }
        }
    }

    /**
     * Turn and commit the peer ratchet. Call this method after an incoming message has been
     * processed completely.
     *
     * @param peerRatchetIdentifier the information needed to identify the corresponding ratchet
     */
    @Throws(DHSessionStoreException::class)
    fun commitPeerRatchet(peerRatchetIdentifier: PeerRatchetIdentifier, handle: ActiveTaskCodec) {
        val sessionId = peerRatchetIdentifier.sessionId
        val peerIdentity = peerRatchetIdentifier.peerIdentity
        val dhType = peerRatchetIdentifier.dhType

        val session = dhSessionStoreInterface.getDHSession(
            identityStoreInterface.identity,
            peerIdentity,
            sessionId,
            handle,
        )
        if (session == null) {
            logger.warn(
                "Could not find session {}. Ratchet of type {} can not be turned for the last received message from {}",
                sessionId,
                dhType,
                peerIdentity,
            )
            return
        }

        val ratchet = when (dhType) {
            DHType.TWODH -> session.peerRatchet2DH
            DHType.FOURDH -> session.peerRatchet4DH
            else -> null
        }
        if (ratchet == null) {
            logger.warn(
                "Ratchet of type {} is null in session {} with contact {}",
                dhType,
                sessionId,
                peerIdentity,
            )
            return
        }

        ratchet.turn()
        dhSessionStoreInterface.storeDHSession(session)
    }

    @Throws(ThreemaException::class, BadMessageException::class)
    suspend fun processInit(
        contact: Contact,
        init: ForwardSecurityDataInit,
        handle: ActiveTaskCodec,
    ) {
        // Is there already a session with this ID?
        if (dhSessionStoreInterface.getDHSession(
                identityStoreInterface.identity,
                contact.identity,
                init.sessionId,
                handle,
            ) != null
        ) {
            // Silently discard init message for existing session
            logger.warn("Received init message for existing session")
            return
        }

        // The initiator will only send an Init if it does not have an existing session. This means
        // that any 4DH sessions that we have stored for this contact are obsolete and should be deleted.
        // We will keep 2DH sessions (which will have been initiated by us), as otherwise messages may
        // be lost during Init race conditions.
        val existingSessionPreempted: Boolean = dhSessionStoreInterface.deleteAllSessionsExcept(
            identityStoreInterface.identity,
            contact.identity,
            init.sessionId,
            true,
        ) > 0

        // TODO(ANDR-2452): Remove this check when enough clients have updated
        if (!statusListener.hasForwardSecuritySupport(contact)) {
            statusListener.updateFeatureMask(contact)
        }

        if (statusListener.hasForwardSecuritySupport(contact)) {
            // Only create a new session from the init if the contact supports forward security
            val session = DHSession(
                init.sessionId,
                init.versionRange,
                init.ephemeralPublicKey,
                contact,
                identityStoreInterface,
            )

            // Save the current timestamp to the session as we will send an accept in this session
            session.lastOutgoingMessageTimestamp = Date().time
            logger.debug(
                "Responding to new DH session ID {} request from {}",
                session.id,
                contact.identity,
            )

            // Send an accept
            val accept = ForwardSecurityDataAccept(
                init.sessionId,
                DHSession.SUPPORTED_VERSION_RANGE,
                session.myEphemeralPublicKey,
            )

            // Send the accept to the contact. Note that if the accept has been sent, but the server
            // ack got lost or the app is killed at this moment, the session won't be persisted.
            // Therefore another key will be generated the next time the init is processed. The
            // contact will discard the second accept and therefore stick to the old keys. The next
            // message exchange will result in a session reset due to failing decryption (different
            // keys).
            sendControlMessageToContact(contact, accept, handle)

            // Store the session
            dhSessionStoreInterface.storeDHSession(session)

            // Create status message of new session
            statusListener.responderSessionEstablished(session, contact, existingSessionPreempted)
        } else {
            // We may still have a FS session to report that was terminated
            if (existingSessionPreempted) {
                statusListener.sessionTerminated(null, contact, false, false)
            }

            // If the contact does not have the feature mask set correctly, we assume that the
            // `Init` is stale, then silently terminate this session.
            sendTerminateAndDeleteSession(contact, init.sessionId, Cause.DISABLED_BY_REMOTE, handle)

            // The feature mask update subroutine should have already detected the downgrade and
            // removed any existing FS sessions. But we'll do it here again anyways for good
            // measures and because the remote may be dishonest about its feature capabilities.
            clearAndTerminateAllSessions(contact, Cause.DISABLED_BY_REMOTE, handle)
        }
    }

    @Throws(ThreemaException::class, BadMessageException::class)
    suspend fun processAccept(
        contact: Contact,
        accept: ForwardSecurityDataAccept,
        handle: ActiveTaskCodec,
    ) {
        val session = dhSessionStoreInterface.getDHSession(
            identityStoreInterface.identity,
            contact.identity,
            accept.sessionId,
            handle,
        )
        if (session == null) {
            // Session not found, probably lost local data or old accept
            logger.warn(
                "No DH session found for accepted session ID {} from {}",
                accept.sessionId,
                contact.identity,
            )

            // Send "terminate" message for this session ID
            sendTerminateAndDeleteSession(contact, accept.sessionId, Cause.UNKNOWN_SESSION, handle)
            statusListener.sessionNotFound(accept.sessionId, contact)
            return
        }

        session.processAccept(
            accept.versionRange,
            accept.ephemeralPublicKey,
            contact,
            identityStoreInterface,
        )
        dhSessionStoreInterface.storeDHSession(session)
        logger.info(
            "Established 4DH session {} with {}",
            session,
            contact.identity,
        )
        statusListener.initiatorSessionEstablished(session, contact)
    }

    @Throws(DHSessionStoreException::class)
    fun processReject(
        contact: Contact,
        reject: ForwardSecurityDataReject,
        handle: ActiveTaskCodec,
    ) {
        logger.warn(
            "Received reject for DH session ID {} from {}, cause: {}",
            reject.sessionId,
            contact.identity,
            reject.cause,
        )
        val session = dhSessionStoreInterface.getDHSession(
            identityStoreInterface.identity,
            contact.identity,
            reject.sessionId,
            handle,
        )
        if (session != null) {
            // Discard session
            dhSessionStoreInterface.deleteDHSession(
                identityStoreInterface.identity,
                contact.identity,
                reject.sessionId,
            )
        } else {
            // Session not found, probably lost local data or old reject
            logger.info(
                "No DH session found for rejected session ID {} from {}",
                reject.sessionId,
                contact.identity,
            )
        }

        // Refresh feature mask now, in case contact downgraded to a build without PFS
        statusListener.updateFeatureMask(contact)

        statusListener.rejectReceived(
            reject,
            contact,
            session,
            statusListener.hasForwardSecuritySupport(contact),
        )
    }

    @Throws(DHSessionStoreException::class)
    fun processTerminate(contact: Contact, message: ForwardSecurityDataTerminate) {
        logger.debug(
            "Terminating DH session ID {} with {}, cause: {}",
            message.sessionId,
            contact.identity,
            message.cause,
        )
        val sessionDeleted = dhSessionStoreInterface.deleteDHSession(
            identityStoreInterface.identity,
            contact.identity,
            message.sessionId,
        )

        // Refresh feature mask now, in case contact downgraded to a build without PFS
        statusListener.updateFeatureMask(contact)

        statusListener.sessionTerminated(
            message.sessionId,
            contact,
            !sessionDeleted,
            statusListener.hasForwardSecuritySupport(contact),
        )
    }

    @Throws(ThreemaException::class, BadMessageException::class)
    suspend fun processMessage(
        contact: Contact,
        envelopeMessage: ForwardSecurityEnvelopeMessage,
        handle: ActiveTaskCodec,
    ): ForwardSecurityDecryptionResult {
        val message = envelopeMessage.data as ForwardSecurityDataMessage

        val session = dhSessionStoreInterface.getDHSession(
            identityStoreInterface.identity,
            contact.identity,
            message.sessionId,
            handle,
        )
        if (session == null) {
            // Session not found, probably lost local data or old message
            logger.warn(
                "No DH session found for message {} in session ID {} from {}",
                envelopeMessage.messageId,
                message.sessionId,
                contact.identity,
            )
            sendReject(
                contact,
                message.sessionId,
                envelopeMessage,
                Reject.Cause.UNKNOWN_SESSION,
                handle,
            )
            statusListener.sessionForMessageNotFound(
                message.sessionId,
                envelopeMessage.messageId,
                contact,
            )
            return ForwardSecurityDecryptionResult.NONE
        }

        // Validate offered and applied version
        val processedVersions = try {
            session.processIncomingMessageVersion(message)
        } catch (e: RejectMessageError) {
            // Message rejected by session validator, `Reject` and terminate the session
            logger.warn(
                "Rejecting message in session {} with {}, cause: {}",
                session,
                contact.identity,
                e.message,
            )
            sendReject(contact, session.id, envelopeMessage, Reject.Cause.STATE_MISMATCH, handle)
            dhSessionStoreInterface.deleteDHSession(
                identityStoreInterface.identity,
                contact.identity,
                session.id,
            )
            // TODO(SE-354): Should we supply an error cause for the UI here? Otherwise this looks as if the remote willingly terminated.
            statusListener.sessionTerminated(message.sessionId, contact, false, true)
            return ForwardSecurityDecryptionResult.NONE
        }

        // Obtain appropriate ratchet and turn to match the message's counter value
        val (ratchet, mode) = when (message.type) {
            DHType.TWODH -> session.peerRatchet2DH to ForwardSecurityMode.TWODH

            DHType.FOURDH -> session.peerRatchet4DH to ForwardSecurityMode.FOURDH

            else -> null to ForwardSecurityMode.NONE
        }

        if (ratchet == null) {
            // This can happen if the Accept message from our peer has been lost. In that case
            // they will think they are in 4DH mode, but we are still in 2DH. `Reject` and
            // terminate the session.
            logger.warn(
                "Rejecting message in session {} with {}, cause: DH type mismatch (mode={})",
                session,
                contact.identity,
                mode,
            )
            sendReject(
                contact,
                message.sessionId,
                envelopeMessage,
                Reject.Cause.STATE_MISMATCH,
                handle,
            )
            dhSessionStoreInterface.deleteDHSession(
                identityStoreInterface.identity,
                contact.identity,
                session.id,
            )
            // TODO(SE-354): Should we supply an error cause for the UI here? Otherwise this looks as if the remote willingly terminated.
            statusListener.sessionTerminated(message.sessionId, contact, false, true)
            return ForwardSecurityDecryptionResult.NONE
        }

        // We should already be at the correct ratchet count since we increment it after
        // processing a message. If we have missed any messages, we will need to increment further.
        try {
            val numTurns = ratchet.turnUntil(message.counter)
            if (numTurns > 0) {
                statusListener.messagesSkipped(message.sessionId, contact, numTurns)
            }
        } catch (e: RatchetRotationException) {
            statusListener.messageOutOfOrder(message.sessionId, contact, envelopeMessage.messageId)
            throw BadMessageException("Out of order FS message, cannot decrypt")
        }

        // A new key is used for each message, so the nonce can be zero
        val nonce = ByteArray(NaCl.NONCEBYTES)
        val plaintext =
            NaCl.symmetricDecryptData(message.message, ratchet.currentEncryptionKey, nonce)
        if (plaintext == null) {
            logger.warn(
                "Rejecting message in session {} with {}, cause: Message decryption failed (message-id={})",
                session,
                contact.identity,
                envelopeMessage.messageId,
            )
            sendReject(
                contact,
                message.sessionId,
                envelopeMessage,
                Reject.Cause.STATE_MISMATCH,
                handle,
            )
            dhSessionStoreInterface.deleteDHSession(
                identityStoreInterface.identity,
                contact.identity,
                session.id,
            )
            // TODO(SE-354): Should we supply an error cause for the UI here? Otherwise this looks as if the remote willingly terminated.
            statusListener.sessionTerminated(message.sessionId, contact, false, true)
            return ForwardSecurityDecryptionResult.NONE
        }

        logger.debug(
            "Decapsulated message from {} (message-id={}, mode={}, session={}, offered-version={}, applied-version={})",
            contact.identity,
            envelopeMessage.messageId,
            mode,
            session,
            processedVersions.offeredVersion,
            processedVersions.appliedVersion,
        )

        // Commit the updated version
        val updatedVersionsSnapshot = session.commitVersions(processedVersions)
        if (updatedVersionsSnapshot != null) {
            statusListener.versionsUpdated(session, updatedVersionsSnapshot, contact)
        }

        if (mode == ForwardSecurityMode.FOURDH) {
            // If this was a 4DH message, then we should erase the 2DH peer ratchet, as we shall not
            // receive (or send) any further 2DH messages in this session. Note that this is also
            // necessary to determine the correct session state.
            if (session.peerRatchet2DH != null) {
                session.discardPeerRatchet2DH()
            }

            // If this message was sent in what we also consider to be the "best" session (lowest ID),
            // then we can delete any other sessions.
            val bestSession = dhSessionStoreInterface.getBestDHSession(
                identityStoreInterface.identity,
                contact.identity,
                handle,
            )
            if (bestSession != null && bestSession.id == session.id) {
                dhSessionStoreInterface.deleteAllSessionsExcept(
                    identityStoreInterface.identity,
                    contact.identity,
                    session.id,
                    false,
                )
            }

            // If this was the first 4DH message in this session, inform the user (only required in
            // version 1.0)
            if (ratchet.counter == 2L) {
                statusListener.first4DhMessageReceived(session, contact)
            }

            // If the commonly supported (local) version is different, then we should send back an
            // empty message
            if (updatedVersionsSnapshot != null &&
                updatedVersionsSnapshot.before.local.number < updatedVersionsSnapshot.after.local.number
            ) {
                createAndSendEmptyMessage(session, contact, handle)
            }
        }

        // Save session, as ratchets and negotiated version may have changed. Note that the peer
        // ratchet is not yet turned at this point. This is required for being able to reprocess the
        // last message when processing it is aborted.
        dhSessionStoreInterface.storeDHSession(session)

        // Collect the information needed to identify the used ratchet
        val ratchetIdentifier = PeerRatchetIdentifier(session.id, contact.identity, message.type)

        // Decode inner message
        val innerMsg = try {
            MessageCoder(contactStore, identityStoreInterface)
                .decodeEncapsulated(plaintext, envelopeMessage, processedVersions.appliedVersion)
                .also { it.forwardSecurityMode = mode }
        } catch (e: BadMessageException) {
            logger.warn("Inner message is invalid", e)
            null
        }

        // Pass the inner message and the ratchet information to the message processor
        return ForwardSecurityDecryptionResult(innerMsg, ratchetIdentifier)
    }

    /**
     * Create a new session and send the corresponding init directly to the contact. Note that this
     * method only creates a new session if there exists no session with this contact.
     */
    private suspend fun createAndSendNewSession(contact: Contact, handle: ActiveTaskCodec) {
        val existingSession = dhSessionStoreInterface.getBestDHSession(
            identityStoreInterface.identity,
            contact.identity,
            handle,
        )
        if (existingSession != null) {
            logger.warn("No session is created as there is already an existing session")
            return
        }

        // When there is no existing session, we create a new session
        val session = DHSession(contact, identityStoreInterface)
        // Set last outgoing message timestamp
        session.lastOutgoingMessageTimestamp = Date().time
        // Do not yet save the session. In case the send task fails and is restarted, the init
        // would not be sent if the session would already exist. Therefore, a new session should
        // be created again.
        logger.debug("Starting new DH session ID {} with {}", session.id, contact.identity)
        statusListener.newSessionInitiated(session, contact)

        // Create and send init message
        val init = ForwardSecurityDataInit(
            session.id,
            DHSession.SUPPORTED_VERSION_RANGE,
            session.myEphemeralPublicKey,
        )
        val message = ForwardSecurityEnvelopeMessage(init, true)
        message.toIdentity = contact.identity

        // Send and await server ack
        sendMessageToContact(message, handle)

        // As soon as the server ack has been received, we store the session locally.
        dhSessionStoreInterface.storeDHSession(session)
    }

    private suspend fun createAndSendEmptyMessage(
        session: DHSession,
        contact: Contact,
        handle: ActiveTaskCodec,
    ) {
        val emptyMessage = EmptyMessage()
        emptyMessage.toIdentity = contact.identity
        val fsMessage = encapsulateMessage(session, emptyMessage, true)
        logger.info(
            "Sending empty message {} to refresh session version to {}",
            emptyMessage.messageId,
            emptyMessage.toIdentity,
        )
        sendMessageToContact(fsMessage, handle)
    }

    @Throws(ThreemaException::class)
    private fun encapsulateMessage(
        session: DHSession,
        message: AbstractMessage,
        persistSession: Boolean,
    ): ForwardSecurityEnvelopeMessage {
        // Obtain encryption key from ratchet
        var ratchet = session.myRatchet4DH
        var dhType: DHType = DHType.FOURDH
        if (ratchet == null) {
            // 2DH mode
            ratchet = session.myRatchet2DH
            dhType = DHType.TWODH
            if (ratchet == null) {
                throw BadDHStateException(
                    "No DH mode negotiated in session ${session.id} with ${session.peerIdentity}",
                )
            }
        }
        val currentKey = ratchet.currentEncryptionKey
        val counter = ratchet.counter
        ratchet.turn()

        // Save session, as ratchet has turned. Note that this should only be done for existing
        // sessions to prevent reusing the same key and nonce with a potentially different
        // plaintext. For new sessions, we do not save the session, as in case we are re-encrypting,
        // we would create a new session anyways.
        if (persistSession) {
            dhSessionStoreInterface.storeDHSession(session)
        }

        // Symmetrically encrypt message (type byte + body)
        val bos = ByteArrayOutputStream()
        bos.write(message.type)
        message.body?.let { bos.write(it) } ?: throw ThreemaException("Message body is null")
        val plaintext = bos.toByteArray()
        // A new key is used for each message, so the nonce can be zero
        val nonce = ByteArray(NaCl.NONCEBYTES)
        val ciphertext = NaCl.symmetricEncryptData(plaintext, currentKey, nonce)

        val groupIdentity = when (message) {
            is AbstractGroupMessage ->
                GroupIdentity.newBuilder()
                    .setCreatorIdentity(message.groupCreator)
                    .setGroupId(message.apiGroupId.toLong())
                    .build()

            else -> null
        }
        val dataMessage = ForwardSecurityDataMessage(
            session.id,
            (dhType),
            counter,
            session.outgoingOfferedVersion.number,
            session.outgoingAppliedVersion.number,
            groupIdentity,
            ciphertext,
        )
        val mode: ForwardSecurityMode = getForwardSecurityMode(dataMessage.type)
        return ForwardSecurityEnvelopeMessage(dataMessage, message, mode)
    }

    @Throws(BadDHStateException::class)
    private fun getForwardSecurityMode(dhType: DHType): ForwardSecurityMode {
        return when (dhType) {
            DHType.TWODH -> ForwardSecurityMode.TWODH
            DHType.FOURDH -> ForwardSecurityMode.FOURDH
            else -> {
                logger.error("Invalid forward security mode")
                throw BadDHStateException(
                    String.format(
                        "Invalid forward security type %d",
                        dhType.number,
                    ),
                )
            }
        }
    }

    private suspend fun sendReject(
        contact: Contact,
        sessionId: DHSessionId,
        rejectedMessage: ForwardSecurityEnvelopeMessage,
        cause: Reject.Cause,
        handle: ActiveTaskCodec,
    ) {
        val groupIdentity = when (val data = rejectedMessage.data) {
            is ForwardSecurityDataMessage -> data.groupIdentity
            else -> null
        }

        val reject =
            ForwardSecurityDataReject(sessionId, rejectedMessage.messageId, groupIdentity, cause)
        sendControlMessageToContact(contact, reject, handle)
    }

    /**
     * The options when terminating a session.
     */
    private enum class TerminateOptions {
        /**
         * Remove the session after sending the terminate message. Note that in this case no new
         * session is created - independent of the cause.
         */
        REMOVE,

        /**
         * Remove the session after sending the terminate message and additionally initiate a new
         * session with the contact, if the cause is one of the following:
         * - [Cause.UNKNOWN_SESSION]
         * - [Cause.RESET]
         */
        RENEW,
    }

    private suspend fun sendTerminateAndDeleteSession(
        contact: Contact,
        sessionId: DHSessionId,
        cause: Cause,
        handle: ActiveTaskCodec,
        options: TerminateOptions = TerminateOptions.RENEW,
    ) {
        val terminate = ForwardSecurityDataTerminate(sessionId, cause)
        sendControlMessageToContact(contact, terminate, handle)

        // Try to delete the dh session
        try {
            dhSessionStoreInterface.deleteDHSession(
                identityStoreInterface.identity,
                contact.identity,
                sessionId,
            )
        } catch (e: DHSessionStoreException) {
            logger.error("Unable to delete DH session", e)
        }

        if (options == TerminateOptions.RENEW &&
            cause == Cause.UNKNOWN_SESSION || cause == Cause.RESET
        ) {
            createAndSendNewSession(contact, handle)
        }
    }

    private suspend fun sendControlMessageToContact(
        contact: Contact,
        data: ForwardSecurityData,
        handle: ActiveTaskCodec,
    ) {
        val message = ForwardSecurityEnvelopeMessage(data, true)
        message.toIdentity = contact.identity
        logger.info(
            "Sending fs control message {} to contact {}",
            message.messageId,
            contact.identity,
        )

        sendMessageToContact(message, handle)
    }

    /**
     * Send the [message] to the contact that is specified as 'toIdentity'. Stores the nonce
     * depending on the message type and awaits the server ack if expected to receive one.
     */
    private suspend fun sendMessageToContact(message: AbstractMessage, handle: ActiveTaskCodec) {
        val nonce = nonceFactory.next(NonceScope.CSP)
        handle.write(message.toCspMessage(identityStoreInterface, contactStore, nonce))
        if (message.protectAgainstReplay()) {
            nonceFactory.store(NonceScope.CSP, nonce)
        }
        if (!message.hasFlags(ProtocolDefines.MESSAGE_FLAG_NO_SERVER_ACK)) {
            handle.awaitOutgoingMessageAck(message.messageId, message.toIdentity)
        }
    }

    /**
     * Clear all sessions with the peer contact and send a terminate message for each of those. Note
     * that depending on the cause, a new session may be initiated afterwards.
     *
     * @param contact the peer contact
     */
    suspend fun clearAndTerminateAllSessions(
        contact: Contact,
        cause: Cause,
        handle: ActiveTaskCodec,
    ) {
        try {
            val myIdentity = identityStoreInterface.identity
            val peerIdentity = contact.identity
            val sessions =
                dhSessionStoreInterface.getAllDHSessions(myIdentity, peerIdentity, handle)

            // Terminate and remove all sessions without renewing them except the last
            sessions.dropLast(1).forEach {
                sendTerminateAndDeleteSession(
                    contact,
                    it.id,
                    cause,
                    handle,
                    TerminateOptions.REMOVE,
                )
            }

            // Terminate and renew the last remaining session
            sessions.lastOrNull()?.let {
                sendTerminateAndDeleteSession(
                    contact,
                    it.id,
                    cause,
                    handle,
                    TerminateOptions.RENEW,
                )
            }

            if (sessions.isNotEmpty()) {
                statusListener.allSessionsTerminated(contact, cause)
            }
        } catch (e: DHSessionStoreException) {
            logger.error("Could not delete DH sessions", e)
        }
    }

    /**
     * Terminate all invalid sessions. If an invalid session has been removed and there is no
     * session left afterwards, a new session will be initiated.
     */
    @Throws(DHSessionStoreException::class)
    suspend fun terminateAllInvalidSessions(contact: Contact, handle: ActiveTaskCodec) {
        val invalidSessions = dhSessionStoreInterface.getAllDHSessions(
            identityStoreInterface.identity,
            contact.identity,
            handle,
        ).mapNotNull {
            val state = try {
                it.state
            } catch (e: DHSession.IllegalDHSessionStateException) {
                return@mapNotNull it
            }

            if (state == DHSession.State.RL44 && it.current4DHVersions == null) {
                it
            } else {
                null
            }
        }

        invalidSessions.forEach {
            sendTerminateAndDeleteSession(contact, it.id, Cause.RESET, handle)
        }
    }
}

/**
 * Contains the information about the session and ratchet that was used to decrypt a message.
 */
class PeerRatchetIdentifier(
    /**
     * The session id of the dh session.
     */
    val sessionId: DHSessionId,
    /**
     * The peer identity of the session.
     */
    val peerIdentity: String,
    /**
     * The dh type of the received message.
     */
    val dhType: DHType,
)

/**
 * When decrypting a message, we get this decryption result. It contains the unencrypted message as
 * well as information about the forward security session. This information is used to finalize the
 * peer ratchet state after the message has been completely processed.
 */
class ForwardSecurityDecryptionResult(
    /**
     * The unencrypted message. Null if it is a forward security control message or an invalid
     * message that could not be decoded. Note that in the case of an invalid message, the
     * [PeerRatchetIdentifier] is not null as the message has been decrypted and the
     * ratchet has been turned.
     */
    val message: AbstractMessage?,
    /**
     * The information to identify the ratchet that was used to decrypt the message.
     */
    val peerRatchetIdentifier: PeerRatchetIdentifier?,
) {
    companion object {
        @JvmStatic
        val NONE = ForwardSecurityDecryptionResult(null, null)
    }
}

/**
 * This is the result we get when encrypting a message with forward security. It contains a list
 * of messages and nonces that should be sent out in the same order. After a server acknowledge has
 * been received, this result should be used to commit the session. For more details see
 * [ForwardSecurityMessageProcessor.runFsEncapsulationSteps] and
 * [ForwardSecurityMessageProcessor.commitSessionState].
 */
class ForwardSecurityEncryptionResult(
    /**
     * This contains the outgoing messages and the nonces that will be used. These messages must be
     * sent in the same order as in this list.
     */
    val outgoingMessages: List<Pair<AbstractMessage, Nonce>>,
    /**
     * This is the updated session state of the session in which the message(s) should be sent. This
     * state must be committed once all the messages have been successfully acknowledged by the
     * server.
     */
    internal val updatedSessionState: DHSession?,
    /**
     * The forward security mode of the aimed message.
     */
    val forwardSecurityMode: ForwardSecurityMode,
)

class UnknownMessageTypeException(msg: String) : ThreemaException(msg)

class BadDHStateException(msg: String) : ThreemaException(msg)
