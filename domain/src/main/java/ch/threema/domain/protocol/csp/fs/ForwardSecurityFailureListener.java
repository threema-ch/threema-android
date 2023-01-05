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

import ch.threema.domain.models.Contact;
import ch.threema.domain.models.MessageId;

/**
 * Listener for failures in Forward Security messaging.
 */
public interface ForwardSecurityFailureListener {
	/**
	 * Notifies the listener that a FS reject message has been received.
	 * The message with the given ID could not be decrypted by the recipient, and should be
	 * sent again.
	 * A warning message should be shown to the user, alerting them of the fact that a new FS
	 * key exchange has been initiated. Otherwise an attacker that knows the private key of
	 * one of the users can conduct an MitM attack at this point without being noticed.
	 *
	 * @param sender The contact who sent the rejection
	 * @param rejectedApiMessageId The ApiMessageID of the message that was rejected
	 */
	void notifyRejectReceived(Contact sender, MessageId rejectedApiMessageId);
}
