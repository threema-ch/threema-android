/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

package ch.threema.storage.models;

/**
 * Internal message sending state.
 */
public enum MessageState {
	/**
	 * Message was created, but not yet sent.
	 */
	PENDING,

	/**
	 * Media is being transcoded.
	 */
	TRANSCODING,

	/**
	 * Message is being sent, but was not yet ACKed by the server.
	 */
	SENDING,

	/**
	 * Sending the message failed.
	 */
	SENDFAILED,

	/**
	 * Message was sent and ACKed by the server (but not yet delivered).
	 */
	SENT,

	/**
	 * Message was delivered to the recipient.
	 */
	DELIVERED,

	/**
	 * Message was read by the recipient.
	 */
	READ,

	/**
	 * Media mssage (e.g. audio message) was consumed by the recipient.
	 */
	CONSUMED,

	/**
	 * A "thumbs up" reaction was sent by the recipient.
	 */
	USERACK,

	/**
	 * A "thumbs down" reaction was sent by the recipient.
	 */
	USERDEC,

	/**
	 * The FS key has changed for this message.
	 */
	FS_KEY_MISMATCH,
}
