/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages;

/**
 * This interface defines methods for all per-message-type properties that the
 * chat server protocol defines.
 */
public interface MessageTypeProperties {
	/**
	 * Return whether the user's profile information (nickname, picture etc.) is allowed to
	 * be sent along with this message. This should be set to true for user-initiated messages only.
	 */
	boolean allowUserProfileDistribution();

	/**
	 * Return whether this message should be exempted from blocking.
	 */
	boolean exemptFromBlocking();

	/**
	 * Return whether this message should implicitly create a <b>direct</b> contact.
	 */
	boolean createImplicitlyDirectContact();

	/**
	 * Return whether the message should be protected against replay attacks. This is used in the
	 * message processor to decide whether the nonce of the message should be stored or not.
	 */
	boolean protectAgainstReplay();

	/**
	 * Return whether this message should be reflected when incoming.
	 */
	boolean reflectIncoming();

	/**
	 * Return whether this message should be reflected when outgoing.
	 */
	boolean reflectOutgoing();

	/**
	 * Return whether an automatic delivery receipt should be send back when receiving a message of
	 * this type. Note that sending automatic delivery receipts must be prevented for messages that
	 * have flag 0x80 set.
	 */
	boolean sendAutomaticDeliveryReceipt();

	/**
	 * Return whether an incoming message should update the conversation timestamp. Note that this
	 * is currently not called for all incoming messages.
	 */
	boolean bumpLastUpdate();
}
