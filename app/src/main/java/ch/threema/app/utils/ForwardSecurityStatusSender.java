/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.MessageService;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.fs.ForwardSecurityStatusListener;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.data.status.ForwardSecurityStatusDataModel;

public class ForwardSecurityStatusSender implements ForwardSecurityStatusListener {
	private final boolean debug;
	private final ContactService contactService;
	private final MessageService messageService;

	public ForwardSecurityStatusSender(ContactService contactService, MessageService messageService) {
		this.debug = ConfigUtils.isTestBuild();
		this.contactService = contactService;
		this.messageService = messageService;
	}

	@Override
	public void newSessionInitiated(DHSession session, Contact contact) {
		if (debug) {
			postStatusMessageDebug("New initiator DH session (ID " + session.getId() + ")", contact);
		}
	}

	@Override
	public void responderSessionEstablished(DHSession session, Contact contact, boolean existingSessionPreempted) {
		if (debug) {
			postStatusMessageDebug("Responder DH session established (ID " + session.getId() + ")", contact);
		}

		if (existingSessionPreempted) {
			postStatusMessage(contact, ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_RESET);
		}
	}

	@Override
	public void initiatorSessionEstablished(DHSession session, Contact contact) {
		if (debug) {
			postStatusMessageDebug("Initiator DH session established (ID " + session.getId() + ")", contact);
		}
	}

	@Override
	public void rejectReceived(DHSessionId sessionId, Contact contact, MessageId rejectedMessageId) {
		if (debug) {
			postStatusMessageDebug("Reject received for DH session (ID " + sessionId + "), message ID " + rejectedMessageId, contact);
		}

		postStatusMessage(contact, ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_RESET);
	}

	@Override
	public void sessionNotFound(DHSessionId sessionId, Contact contact) {
		if (debug) {
			postStatusMessageDebug("DH session not found (ID " + sessionId + ")", contact);
		}
	}

	@Override
	public void sessionBadDhState(DHSessionId sessionId, Contact contact) {
		if (debug) {
			postStatusMessageDebug("Bad DH session state (ID " + sessionId + ")", contact);
		}
	}

	@Override
	public void sessionTerminated(DHSessionId sessionId, Contact contact) {
		if (debug) {
			postStatusMessageDebug("DH session terminated (ID " + sessionId + ")", contact);
		}
	}

	@Override
	public void messagesSkipped(DHSessionId sessionId, Contact contact, int numSkipped) {
		if (debug) {
			postStatusMessageDebug(numSkipped + " messages skipped (ID " + sessionId + ")", contact);
		}

		postStatusMessage(contact, ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_MESSAGES_SKIPPED, numSkipped);
	}

	@Override
	public void messageOutOfOrder(DHSessionId sessionId, Contact contact) {
		if (debug) {
			postStatusMessageDebug("Message out of order (ID " + sessionId + ")", contact);
		}

		postStatusMessage(contact, ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_MESSAGE_OUT_OF_ORDER);
	}

	@Override
	public void first4DhMessageReceived(DHSessionId sessionId, Contact contact) {
		ContactModel contactModel = contactService.getByIdentity(contact.getIdentity());
		if (contactModel != null) {
			contactService.setForwardSecurityState(contactModel, ContactModel.FS_ON);

			if (debug) {
				postStatusMessageDebug("First 4DH message received in session (ID " + sessionId + ")", contact);
			}

			// Check if FS for sent messages is also enabled on this contact
			if (contactModel.isForwardSecurityEnabled()) {
				postStatusMessage(contact, ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED);
			} else {
				postStatusMessage(contact, ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED_RX);
			}
		}
	}

	private void postStatusMessageDebug(String message, Contact contact) {
		ContactModel contactModel = contactService.getByIdentity(contact.getIdentity());
		if (contactModel != null) {
			ContactMessageReceiver receiver = contactService.createReceiver(contactModel);
			messageService.createForwardSecurityStatus(receiver, ForwardSecurityStatusDataModel.ForwardSecurityStatusType.STATIC_TEXT, 0, "PFS: " + message);
		}
	}

	private void postStatusMessage(Contact contact, @ForwardSecurityStatusDataModel.ForwardSecurityStatusType int type) {
		postStatusMessage(contact, type, 0);
	}

	private void postStatusMessage(Contact contact, @ForwardSecurityStatusDataModel.ForwardSecurityStatusType int type, int quantity) {
		ContactModel contactModel = contactService.getByIdentity(contact.getIdentity());
		if (contactModel != null) {
			ContactMessageReceiver receiver = contactService.createReceiver(contactModel);
			messageService.createForwardSecurityStatus(receiver, type, quantity, null);
		}
	}
}
