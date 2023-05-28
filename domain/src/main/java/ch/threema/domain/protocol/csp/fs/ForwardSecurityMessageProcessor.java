/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2023 Threema GmbH
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

package ch.threema.domain.protocol.csp.fs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.neilalexander.jnacl.NaCl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ch.threema.base.ThreemaException;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.fs.KDFRatchet;
import ch.threema.domain.models.Contact;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.coders.MessageCoder;
import ch.threema.domain.protocol.csp.connection.MessageQueue;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityData;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataAccept;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataInit;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataMessage;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataReject;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataTerminate;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityEnvelopeMessage;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityMode;
import ch.threema.domain.stores.ContactStore;
import ch.threema.domain.stores.DHSessionStoreException;
import ch.threema.domain.stores.DHSessionStoreInterface;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.protobuf.csp.e2e.fs.ForwardSecurityEnvelope;

public class ForwardSecurityMessageProcessor {

	private static final Logger logger = LoggerFactory.getLogger(ForwardSecurityMessageProcessor.class);

	private final @NonNull DHSessionStoreInterface dhSessionStoreInterface;
	private final @NonNull ContactStore contactStore;
	private final @NonNull IdentityStoreInterface identityStoreInterface;
	private final @NonNull MessageQueue messageQueue;
	private final @NonNull ForwardSecurityFailureListener failureListener;

	private final List<ForwardSecurityStatusListener> statusListeners;

	public ForwardSecurityMessageProcessor(@NonNull DHSessionStoreInterface dhSessionStoreInterface,
	                                       @NonNull ContactStore contactStore,
	                                       @NonNull IdentityStoreInterface identityStoreInterface,
	                                       @NonNull MessageQueue messageQueue,
	                                       @NonNull ForwardSecurityFailureListener failureListener) {
		this.dhSessionStoreInterface = dhSessionStoreInterface;
		this.contactStore = contactStore;
		this.identityStoreInterface = identityStoreInterface;
		this.messageQueue = messageQueue;
		this.failureListener = failureListener;
		this.statusListeners = new ArrayList<>();
	}

	/**
	 * Process a forward security envelope message by attempting to decapsulate/decrypt it.
	 *
	 * @param sender Sender contact
	 * @param envelopeMessage The envelope with the encapsulated message
	 * @return Decapsulated message, if any, or null
	 * @throws ThreemaException
	 * @throws BadMessageException
	 */
	public synchronized AbstractMessage processEnvelopeMessage(Contact sender,
	                                              ForwardSecurityEnvelopeMessage envelopeMessage) throws ThreemaException, BadMessageException {
		ForwardSecurityData data = envelopeMessage.getData();

		if (data instanceof ForwardSecurityDataInit) {
			processInit(sender, (ForwardSecurityDataInit) data);
		} else if (data instanceof ForwardSecurityDataAccept) {
			processAccept(sender, (ForwardSecurityDataAccept) data);
		} else if (data instanceof ForwardSecurityDataReject) {
			processReject(sender, (ForwardSecurityDataReject) data);
		} else if (data instanceof ForwardSecurityDataTerminate) {
			processTerminate(sender, (ForwardSecurityDataTerminate) data);
		} else if (data instanceof ForwardSecurityDataMessage) {
			return processMessage(sender, envelopeMessage);
		} else {
			throw new UnknownMessageTypeException("Unsupported message type");
		}

		return null;
	}

	public synchronized ForwardSecurityEnvelopeMessage makeMessage(Contact contact, AbstractMessage innerMessage) throws ThreemaException {
		// Check if we already have a session with this contact
		DHSession session = dhSessionStoreInterface.getBestDHSession(identityStoreInterface.getIdentity(), contact.getIdentity());
		if (session == null) {
			// Establish a new DH session
			session = new DHSession(contact, identityStoreInterface);
			dhSessionStoreInterface.storeDHSession(session);
			logger.debug("Starting new DH session ID {} with {}", session.getId(), contact.getIdentity());
			for (ForwardSecurityStatusListener listener : statusListeners) {
				listener.newSessionInitiated(session, contact);
			}

			// Send init message
			ForwardSecurityDataInit init = new ForwardSecurityDataInit(session.getId(), session.getMyEphemeralPublicKey());
			sendMessageToContact(contact, init);
		}

		// Obtain encryption key from ratchet
		KDFRatchet ratchet = session.getMyRatchet4DH();
		ForwardSecurityEnvelope.Message.DHType dhType = ForwardSecurityEnvelope.Message.DHType.FOURDH;
		ForwardSecurityMode forwardSecurityMode = ForwardSecurityMode.FOURDH;
		if (ratchet == null) {
			// 2DH mode
			ratchet = session.getMyRatchet2DH();
			dhType = ForwardSecurityEnvelope.Message.DHType.TWODH;
			forwardSecurityMode = ForwardSecurityMode.TWODH;
			if (ratchet == null) {
				throw new BadDHStateException("No DH mode negotiated");
			}
		}

		byte[] currentKey = ratchet.getCurrentEncryptionKey();
		long counter = ratchet.getCounter();
		ratchet.turn();

		// Save session, as ratchet has turned
		dhSessionStoreInterface.storeDHSession(session);

		// Symmetrically encrypt message (type byte + body)
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bos.write(innerMessage.getType());
		try {
			bos.write(innerMessage.getBody());
		} catch (IOException e) {
			// Should never happen
			throw new RuntimeException(e);
		}
		byte[] plaintext = bos.toByteArray();
		// A new key is used for each message, so the nonce can be zero
		byte[] nonce = new byte[NaCl.NONCEBYTES];
		byte[] ciphertext = NaCl.symmetricEncryptData(plaintext, currentKey, nonce);

		ForwardSecurityDataMessage dataMessage = new ForwardSecurityDataMessage(session.getId(), dhType, counter, ciphertext);
		ForwardSecurityEnvelopeMessage envelope = new ForwardSecurityEnvelopeMessage(dataMessage);

		// Copy attributes from inner message
		envelope.setFromIdentity(innerMessage.getFromIdentity());
		envelope.setToIdentity(innerMessage.getToIdentity());
		envelope.setMessageId(innerMessage.getMessageId());
		envelope.setDate(innerMessage.getDate());
		int flags = innerMessage.getMessageFlags();
		if (innerMessage.flagSendPush()) {
			flags |= ProtocolDefines.MESSAGE_FLAG_SEND_PUSH;
		}
		if (innerMessage.flagNoServerQueuing()) {
			flags |= ProtocolDefines.MESSAGE_FLAG_NO_SERVER_QUEUING;
		}
		if (innerMessage.flagNoServerAck()) {
			flags |= ProtocolDefines.MESSAGE_FLAG_NO_SERVER_ACK;
		}
		if (innerMessage.flagGroupMessage()) {
			flags |= ProtocolDefines.MESSAGE_FLAG_GROUP;
		}
		if (innerMessage.flagShortLivedServerQueuing()) {
			flags |= ProtocolDefines.MESSAGE_FLAG_SHORT_LIVED;
		}
		envelope.setMessageFlags(flags);
		envelope.setPushFromName(innerMessage.getPushFromName());
		envelope.setForwardSecurityMode(forwardSecurityMode);
		envelope.setAllowSendingProfile(innerMessage.allowUserProfileDistribution());

		return envelope;
	}

	public void addStatusListener(ForwardSecurityStatusListener listener) {
		this.statusListeners.add(listener);
	}

	public void removeStatusListener(ForwardSecurityStatusListener listener) {
		this.statusListeners.remove(listener);
	}

	/**
	 * Check if this contact has sent forward security messages before.
	 * @param contact the desired contact
	 */
	public boolean hasContactUsedForwardSecurity(Contact contact) {
		try {
			DHSession bestSession = dhSessionStoreInterface.getBestDHSession(identityStoreInterface.getIdentity(), contact.getIdentity());
			if (bestSession != null) {
				// Check if any 2DH or 4DH messages have been received by looking at the ratchet count
				if (bestSession.getPeerRatchet4DH() != null && bestSession.getPeerRatchet4DH().getCounter() > 1) {
					return true;
				} else if (bestSession.getPeerRatchet2DH() != null && bestSession.getPeerRatchet2DH().getCounter() > 1) {
					return true;
				}
			}
			return false;
		} catch (DHSessionStoreException e) {
			logger.error("Could not get best DH session", e);
			return false;
		}
	}

	private void processInit(Contact contact, ForwardSecurityDataInit init) throws ThreemaException {
		// Is there already a session with this ID?
		if (dhSessionStoreInterface.getDHSession(identityStoreInterface.getIdentity(), contact.getIdentity(), init.getSessionId()) != null) {
			// Silently discard init message for existing session
			return;
		}

		// The initiator will only send an Init if it does not have an existing session. This means
		// that any 4DH sessions that we have stored for this contact are obsolete and should be deleted.
		// We will keep 2DH sessions (which will have been initiated by us), as otherwise messages may
		// be lost during Init race conditions.
		boolean existingSessionPreempted = false;
		if (dhSessionStoreInterface.deleteAllSessionsExcept(identityStoreInterface.getIdentity(), contact.getIdentity(), init.getSessionId(), true) > 0) {
			existingSessionPreempted = true;
		}

		DHSession session = new DHSession(init.getSessionId(), init.getEphemeralPublicKey(), contact, identityStoreInterface);
		dhSessionStoreInterface.storeDHSession(session);
		logger.debug("Responding to new DH session ID {} request from {}", session.getId(), contact.getIdentity());
		for (ForwardSecurityStatusListener listener : statusListeners) {
			listener.responderSessionEstablished(session, contact, existingSessionPreempted);
		}

		// Create and send accept
		ForwardSecurityDataAccept accept = new ForwardSecurityDataAccept(init.getSessionId(), session.getMyEphemeralPublicKey());
		sendMessageToContact(contact, accept);
	}

	private void processAccept(Contact contact, ForwardSecurityDataAccept accept) throws ThreemaException {
		DHSession session = dhSessionStoreInterface.getDHSession(identityStoreInterface.getIdentity(), contact.getIdentity(), accept.getSessionId());
		if (session == null) {
			// Session not found, probably lost local data or old accept
			logger.warn("No DH session found for accepted session ID {} from {}", accept.getSessionId(), contact.getIdentity());

			// Send "terminate" message for this session ID
			ForwardSecurityDataTerminate terminate = new ForwardSecurityDataTerminate(accept.getSessionId());
			sendMessageToContact(contact, terminate);

			for (ForwardSecurityStatusListener listener : statusListeners) {
				listener.sessionNotFound(accept.getSessionId(), contact);
			}

			return;
		}

		session.processAccept(accept.getEphemeralPublicKey(), contact, identityStoreInterface);
		dhSessionStoreInterface.storeDHSession(session);
		logger.debug("Established 4DH session ID {} with {}", session.getId(), contact.getIdentity());
		for (ForwardSecurityStatusListener listener : statusListeners) {
			listener.initiatorSessionEstablished(session, contact);
		}
	}

	private void processReject(Contact contact, ForwardSecurityDataReject reject) throws DHSessionStoreException {
		logger.warn("Received reject for DH session ID {} from {}", reject.getSessionId(), contact.getIdentity());
		DHSession session = dhSessionStoreInterface.getDHSession(identityStoreInterface.getIdentity(), contact.getIdentity(), reject.getSessionId());
		if (session != null) {
			// Discard session
			dhSessionStoreInterface.deleteDHSession(identityStoreInterface.getIdentity(), contact.getIdentity(), reject.getSessionId());
		} else {
			// Session not found, probably lost local data or old reject
			logger.info("No DH session found for rejected session ID {} from {}", reject.getSessionId(), contact.getIdentity());
		}

		for (ForwardSecurityStatusListener listener : statusListeners) {
			listener.rejectReceived(reject.getSessionId(), contact, reject.getRejectedApiMessageId());
		}

		failureListener.notifyRejectReceived(contact, reject.getRejectedApiMessageId());
	}

	private @Nullable AbstractMessage processMessage(Contact contact, ForwardSecurityEnvelopeMessage envelopeMessage)
		throws ThreemaException, BadMessageException {

		ForwardSecurityDataMessage message = (ForwardSecurityDataMessage)envelopeMessage.getData();

		DHSession session = dhSessionStoreInterface.getDHSession(identityStoreInterface.getIdentity(), contact.getIdentity(), message.getSessionId());
		if (session == null) {
			// Session not found, probably lost local data or old message
			logger.warn("No DH session found for message in session ID {} from {}", message.getSessionId(), contact.getIdentity());

			// Send reject message
			ForwardSecurityDataReject reject = new ForwardSecurityDataReject(message.getSessionId(), envelopeMessage.getMessageId(), ForwardSecurityEnvelope.Reject.Cause.UNKNOWN_SESSION);
			sendMessageToContact(contact, reject);

			for (ForwardSecurityStatusListener listener : statusListeners) {
				listener.sessionNotFound(message.getSessionId(), contact);
			}

			return null;
		}

		// Obtain appropriate ratchet and turn to match the message's counter value
		KDFRatchet ratchet = null;
		ForwardSecurityMode mode = ForwardSecurityMode.NONE;
		switch (message.getType()) {
			case TWODH:
				ratchet = session.getPeerRatchet2DH();
				mode = ForwardSecurityMode.TWODH;
				break;
			case FOURDH:
				ratchet = session.getPeerRatchet4DH();
				mode = ForwardSecurityMode.FOURDH;
				break;
		}

		if (ratchet == null) {
			// This can happen if the Accept message from our peer has been lost. In that case
			// they will think they are in 4DH mode, but we are still in 2DH.
			ForwardSecurityDataReject reject = new ForwardSecurityDataReject(message.getSessionId(), envelopeMessage.getMessageId(), ForwardSecurityEnvelope.Reject.Cause.STATE_MISMATCH);
			sendMessageToContact(contact, reject);
			for (ForwardSecurityStatusListener listener : statusListeners) {
				listener.sessionBadDhState(message.getSessionId(), contact);
			}
			return null;
		}

		// We should already be at the correct ratchet count since we increment it after
		// processing a message. If we have missed any messages, we will need to increment further.
		try {
			int numTurns = ratchet.turnUntil(message.getCounter());
			if (numTurns > 0) {
				for (ForwardSecurityStatusListener listener : statusListeners) {
					listener.messagesSkipped(message.getSessionId(), contact, numTurns);
				}
			}
		} catch (KDFRatchet.RatchetRotationException e) {
			for (ForwardSecurityStatusListener listener : statusListeners) {
				listener.messageOutOfOrder(message.getSessionId(), contact, envelopeMessage.getMessageId());
			}
			throw new BadMessageException("Out of order FS message, cannot decrypt", true);
		}

		// Symmetrically decrypt message
		byte[] ciphertext = message.getMessage();
		// A new key is used for each message, so the nonce can be zero
		byte[] nonce = new byte[NaCl.NONCEBYTES];
		byte[] plaintext = NaCl.symmetricDecryptData(ciphertext, ratchet.getCurrentEncryptionKey(), nonce);
		if (plaintext == null) {
			ForwardSecurityDataReject reject = new ForwardSecurityDataReject(message.getSessionId(), envelopeMessage.getMessageId(), ForwardSecurityEnvelope.Reject.Cause.STATE_MISMATCH);
			sendMessageToContact(contact, reject);
			for (ForwardSecurityStatusListener listener : statusListeners) {
				listener.messageDecryptionFailed(message.getSessionId(), contact, envelopeMessage.getMessageId());
			}
			return null;
		}

		logger.debug("Decrypted {} message ID {} from {} in session {}", mode, envelopeMessage.getMessageId(), contact.getIdentity(), session.getId());

		// Turn the ratchet once, as we will not need the current encryption key anymore and the
		// next message from the peer must have a ratchet count of at least one higher
		ratchet.turn();

		if (mode == ForwardSecurityMode.FOURDH) {
			// If this was a 4DH message, then we should erase the 2DH peer ratchet, as we shall not
			// receive (or send) any further 2DH messages in this session
			if (session.getPeerRatchet2DH() != null) {
				session.discardPeerRatchet2DH();
			}

			// If this message was sent in what we also consider to be the "best" session (lowest ID),
			// then we can delete any other sessions.
			DHSession bestSession = dhSessionStoreInterface.getBestDHSession(identityStoreInterface.getIdentity(), contact.getIdentity());
			if (bestSession != null && bestSession.getId().equals(session.getId())) {
				dhSessionStoreInterface.deleteAllSessionsExcept(identityStoreInterface.getIdentity(), contact.getIdentity(), session.getId(), false);
			}

			// If this was the first 4DH message in this session, inform the user
			if (ratchet.getCounter() == 2) {
				for (ForwardSecurityStatusListener listener : statusListeners) {
					listener.first4DhMessageReceived(message.getSessionId(), contact);
				}
			}
		}

		// Save session, as ratchets have changed
		dhSessionStoreInterface.storeDHSession(session);

		// Decode inner message and pass it to processor
		AbstractMessage innerMsg = new MessageCoder(contactStore, identityStoreInterface).decodeEncapsulated(plaintext, envelopeMessage, contact);
		innerMsg.setForwardSecurityMode(mode);
		return innerMsg;
	}

	private void processTerminate(Contact contact, ForwardSecurityDataTerminate message) throws DHSessionStoreException {
		logger.debug("Terminating DH session ID {} with {}", message.getSessionId(), contact.getIdentity());
		dhSessionStoreInterface.deleteDHSession(identityStoreInterface.getIdentity(), contact.getIdentity(), message.getSessionId());

		for (ForwardSecurityStatusListener listener : statusListeners) {
			listener.sessionTerminated(message.getSessionId(), contact);
		}
	}

	private void sendMessageToContact(Contact contact, ForwardSecurityData data) throws ThreemaException {
		ForwardSecurityEnvelopeMessage message = new ForwardSecurityEnvelopeMessage(data);
		message.setToIdentity(contact.getIdentity());
		this.messageQueue.enqueue(message);
	}

	public static class UnknownMessageTypeException extends ThreemaException {
		public UnknownMessageTypeException(String msg) {
			super(msg);
		}
	}

	public static class BadDHStateException extends ThreemaException {
		public BadDHStateException(final String msg) {
			super(msg);
		}
	}
}
