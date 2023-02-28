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

package ch.threema.domain.protocol.csp.fs;

import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.MessageId;

/**
 * Interface for classes that receive updates when a forward security session changes status.
 */
public interface ForwardSecurityStatusListener {
	void newSessionInitiated(DHSession session, Contact contact);
	void responderSessionEstablished(DHSession session, Contact contact, boolean existingSessionPreempted);
	void initiatorSessionEstablished(DHSession session, Contact contact);
	void rejectReceived(DHSessionId sessionId, Contact contact, MessageId rejectedMessageId);
	void sessionNotFound(DHSessionId sessionId, Contact contact);
	void sessionBadDhState(DHSessionId sessionId, Contact contact);
	void sessionTerminated(DHSessionId sessionId, Contact contact);
	void messagesSkipped(DHSessionId sessionId, Contact contact, int numSkipped);
	void messageOutOfOrder(DHSessionId sessionId, Contact contact, MessageId messageId);
	void messageDecryptionFailed(DHSessionId sessionId, Contact contact, MessageId failedMessageId);
	void first4DhMessageReceived(DHSessionId sessionId, Contact contact);
}
