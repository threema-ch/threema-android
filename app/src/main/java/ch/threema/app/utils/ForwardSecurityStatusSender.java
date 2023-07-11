/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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

package ch.threema.app.utils;

import org.slf4j.Logger;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.routines.UpdateFeatureLevelRoutine;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.MessageService;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.csp.fs.ForwardSecurityStatusListener;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataReject;
import ch.threema.protobuf.csp.e2e.fs.Version;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.status.ForwardSecurityStatusDataModel.ForwardSecurityStatusType;

public class ForwardSecurityStatusSender implements ForwardSecurityStatusListener {
	private final static Logger logger = LoggingUtil.getThreemaLogger("ForwardSecurityStatusSender");
	private final boolean debug;
	private final ContactService contactService;
	private final MessageService messageService;
	private final APIConnector apiConnector;

	public ForwardSecurityStatusSender(ContactService contactService, MessageService messageService, APIConnector apiConnector) {
		this.debug = ConfigUtils.isTestBuild();
		this.contactService = contactService;
		this.messageService = messageService;
		this.apiConnector = apiConnector;
	}

	@Override
	public void newSessionInitiated(@Nullable DHSession session, @NonNull Contact contact) {
		if (debug) {
			postStatusMessageDebug(getDebugString("New initiator DH session", session), contact);
		}
	}

	@Override
	public void responderSessionEstablished(@NonNull DHSession session, @NonNull Contact contact, boolean existingSessionPreempted) {
		if (debug) {
			postStatusMessageDebug(getDebugString("Responder DH session established", session), contact);
		}

		if (existingSessionPreempted) {
			postStatusMessage(contact, ForwardSecurityStatusType.FORWARD_SECURITY_RESET);
		} else if (session.getNegotiatedVersion().getNumber() >= Version.V1_1.getNumber()) {
			// If a new session has been established with V1.1 or higher, we display the message,
			// that forward security has been enabled (by both participants) in this chat.
			postStatusMessage(contact, ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED);
		}
	}

	@Override
	public void initiatorSessionEstablished(@NonNull DHSession session, @NonNull Contact contact) {
		if (debug) {
			postStatusMessageDebug(getDebugString("Initiator DH session established", session), contact);
		}

		if (session.getNegotiatedVersion().getNumber() >= Version.V1_1.getNumber()) {
			// If a new session has been established with V1.1 or higher, we display the message,
			// that forward security has been enabled (by both participants) in this chat.
			postStatusMessage(contact, ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED);
		}
	}

	@Override
	public void rejectReceived(@NonNull ForwardSecurityDataReject rejectData, @NonNull Contact contact, boolean sessionUnknown) {
		if (debug) {
			postStatusMessageDebug(
				"Reject received for DH session (ID " + rejectData.getSessionId()
					+ "), message ID " + rejectData.getRejectedApiMessageId()
					+ " with cause " + rejectData.getCause()
				, contact);
		}

		// Only show the forward security reset status message for sessions that are known.
		// Otherwise, all sent (and rejected) messages produce this status message, which is very
		// verbose.
		if (!sessionUnknown) {
			postStatusMessage(contact, ForwardSecurityStatusType.FORWARD_SECURITY_RESET);
		}
	}

	@Override
	public void sessionNotFound(@Nullable DHSessionId sessionId, @NonNull Contact contact) {
		if (debug) {
			postStatusMessageDebug("DH session not found (ID " + sessionId + ")", contact);
		}
	}

	@Override
	public void sessionForMessageNotFound(@Nullable DHSessionId sessionId, @Nullable MessageId messageId, @NonNull Contact contact) {
		if (debug) {
			postStatusMessageDebug("DH session not found (session ID " + sessionId + " message ID " + messageId + ")", contact);
		}
	}

	@Override
	public void sessionBadState(@Nullable DHSessionId sessionId, @NonNull Contact contact) {
		if (debug) {
			postStatusMessageDebug("Bad session state (ID " + sessionId + ")", contact);
		}
	}

	@Override
	public void sessionTerminated(@Nullable DHSessionId sessionId, @NonNull Contact contact) {
		if (debug) {
			postStatusMessageDebug("DH session terminated (ID " + sessionId + ")", contact);
		}

		postStatusMessage(contact, ForwardSecurityStatusType.FORWARD_SECURITY_RESET);
	}

	@Override
	public void messagesSkipped(@Nullable DHSessionId sessionId, @NonNull Contact contact, int numSkipped) {
		if (debug) {
			postStatusMessageDebug(numSkipped + " messages skipped (ID " + sessionId + ")", contact);
		}
	}

	@Override
	public void messageOutOfOrder(@Nullable DHSessionId sessionId, @NonNull Contact contact, @Nullable MessageId messageId) {
		if (debug) {
			postStatusMessageDebug("Message out of order (ID " + sessionId + "), message ID " + messageId, contact);
		}

		if (messageId != null && hasLastMessageId(contact, messageId)) {
			// If the latest message of a contact is processed again, it cannot be decrypted again due to FS. It is very
			// likely that the message has been processed but could not be acknowledged on the server. Therefore we do
			// not show a warning if the message is already displayed in the chat.
			logger.warn("The latest message with id '{}' was processed twice. Ignoring the second message.", messageId);
			if (debug) {
				postStatusMessageDebug(String.format("The latest message with id '%s' was processed twice.", messageId), contact);
			}
		} else {
			postStatusMessage(contact, ForwardSecurityStatusType.FORWARD_SECURITY_MESSAGE_OUT_OF_ORDER);
		}
	}

	@Override
	public void messageDecryptionFailed(@Nullable DHSessionId sessionId, @NonNull Contact contact, @Nullable MessageId failedMessageId) {
		if (debug) {
			postStatusMessageDebug("Message decryption failed (ID " + sessionId + "), message ID " + failedMessageId, contact);
		}
	}

	@Override
	public void first4DhMessageReceived(@NonNull DHSession session, @NonNull Contact contact) {
		if (debug) {
			postStatusMessageDebug(getDebugString("First 4DH message received in session", session), contact);
		}

		// If we received a message with forward security in a session of version 1.0, then we
		// inform that forward security has been enabled (by both participants). Note that this is
		// only necessary for version 1.0, as forward security is enabled by default starting in
		// version 1.1 and therefore the status is shown as soon as the session has been established
		// TODO(ANDR-2452): Remove this status message when most of clients support 1.1 anyway
		if (session.getNegotiatedVersion() == Version.V1_0) {
			postStatusMessage(contact, ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED);

			// Set the forward security state to on (only required in version 1.0)
			ContactModel contactModel = contactService.getByIdentity(contact.getIdentity());
			if (contactModel != null) {
				contactService.save(contactModel.setForwardSecurityState(ContactModel.FS_ON));
			}
		}
	}

	@Override
	public void unexpectedAppliedVersion(@NonNull DHSession session, int appliedVersion, @NonNull Contact contact) {
		if (debug) {
			postStatusMessageDebug(
				String.format("%s applied version=%s",
					getDebugString("Unexpected applied version received", session),
					appliedVersion
				), contact);
		}
	}

	@Override
	public void negotiatedVersionUpdated(@NonNull DHSession session, @NonNull Version updatedNegotiatedVersion, @NonNull Contact contact) {
		if (debug) {
			postStatusMessageDebug(
				String.format("%s to version %s",
					getDebugString("Updated session", session),
					updatedNegotiatedVersion
				), contact);
		}

		ContactModel contactModel = contactService.getByIdentity(contact.getIdentity());

		// If we update a session from version 1.0 to 1.1 (or newer), then we show a status message,
		// that forward security has been enabled (by both participants). Note that this message is
		// only shown, when no 4DH message has been received in the session with version 1.0 because
		// the message has already been shown at this point.
		// TODO(ANDR-2452): Remove this status message when most of clients support 1.1 anyway
		if (session.getNegotiatedVersion() == Version.V1_0
			&& updatedNegotiatedVersion.getNumber() >= Version.V1_1.getNumber()
			&& contactModel != null
			&& contactModel.getForwardSecurityState() == ContactModel.FS_OFF
		) {
			postStatusMessage(contact, ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED);
		}
	}

	@Override
	public void messageWithoutFSReceived(@NonNull Contact contact, @NonNull DHSession session, @NonNull AbstractMessage message) {
		logger.warn("Received message {} from {} without forward security of type {} despite having a session with negotiated version {}",
			message.getMessageId(),
			contact.getIdentity(),
			message.getClass().getSimpleName(),
			session.getNegotiatedVersion()
		);

		if (session.getNegotiatedVersion() == Version.V1_0) {
			// TODO(ANDR-2452): Do not distinguish between 1.0 and newer versions when enough
			// clients have updated. Show this status message for every message without FS.

			// For sessions of version 1.0 show warning only once
			ContactModel contactModel = contactService.getByIdentity(contact.getIdentity());
			if (contactModel != null && contactModel.getForwardSecurityState() == ContactModel.FS_ON) {
				contactService.save(contactModel.setForwardSecurityState(ContactModel.FS_OFF));
				postStatusMessage(contact, ForwardSecurityStatusType.MESSAGE_WITHOUT_FORWARD_SECURITY);
			}
		} else if (session.getNegotiatedVersion().getNumber() >= Version.V1_1.getNumber()) {
			// For sessions with version 1.1 or newer, inform for every message without fs
			postStatusMessage(contact, ForwardSecurityStatusType.MESSAGE_WITHOUT_FORWARD_SECURITY);
		}
	}

	@Override
	public boolean hasForwardSecuritySupport(@NonNull Contact contact) {
		ContactModel contactModel = contactService.getByIdentity(contact.getIdentity());
		if (contactModel == null) {
			return false;
		}
		return ThreemaFeature.canForwardSecurity(contactModel.getFeatureMask());
	}

	@Override
	public void updateFeatureMask(@NonNull Contact contact) {
		ContactModel contactModel = contactService.getByIdentity(contact.getIdentity());
		if (contactModel == null) {
			return;
		}

		UpdateFeatureLevelRoutine.removeTimeCache(contactModel);
		new UpdateFeatureLevelRoutine(
			contactService,
			apiConnector,
			Collections.singletonList(contactModel)
		).run();
	}

	private void postStatusMessageDebug(@NonNull String message, @NonNull Contact contact) {
		ContactModel contactModel = contactService.getByIdentity(contact.getIdentity());
		if (contactModel != null) {
			ContactMessageReceiver receiver = contactService.createReceiver(contactModel);
			messageService.createForwardSecurityStatus(receiver, ForwardSecurityStatusType.STATIC_TEXT, 0, "PFS: " + message);
		}
	}

	private void postStatusMessage(@NonNull Contact contact, @ForwardSecurityStatusType int type) {
		postStatusMessage(contact, type, 0);
	}

	private void postStatusMessage(@NonNull Contact contact, @ForwardSecurityStatusType int type, int quantity) {
		ContactModel contactModel = contactService.getByIdentity(contact.getIdentity());
		if (contactModel != null) {
			ContactMessageReceiver receiver = contactService.createReceiver(contactModel);
			messageService.createForwardSecurityStatus(receiver, type, quantity, null);
		}
	}

	private boolean hasLastMessageId(@NonNull Contact contact, @NonNull MessageId messageId) {
		ContactMessageReceiver r = contactService.createReceiver(contactService.getByIdentity(contact.getIdentity()));

		List<AbstractMessageModel> messageModels = this.messageService.getMessagesForReceiver(r, new MessageService.MessageFilter() {
			@Override
			public long getPageSize() {
				return 1;
			}

			@Override
			public Integer getPageReferenceId() {
				return null;
			}

			@Override
			public boolean withStatusMessages() {
				return false;
			}

			@Override
			public boolean withUnsaved() {
				return false;
			}

			@Override
			public boolean onlyUnread() {
				return false;
			}

			@Override
			public boolean onlyDownloaded() {
				return false;
			}

			@Override
			public MessageType[] types() {
				return null;
			}

			@Override
			public int[] contentTypes() {
				return null;
			}
		});

		if (messageModels != null && !messageModels.isEmpty()) {
			return messageId.toString().equals(messageModels.get(0).getApiMessageId());
		}

		return false;
	}

	private String getDebugString(@NonNull String message, @Nullable DHSession session) {
		if (session == null) {
			return message;
		}
		return String.format(
			"%s %s",
			message,
			session.toDebugString()
		);
	}

}
