/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.client;

import ch.threema.base.ThreemaException;

import java.io.Serializable;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Wrapper class for message IDs (consisting of 8 bytes, chosen by the sender and not guaranteed
 * to be unique across multiple senders).
 */
public class MessageId implements Serializable {

	private final byte[] messageId;

	/**
	 * Create a new random MessageId.
	 */
	public MessageId() {
		messageId = new byte[ProtocolDefines.MESSAGE_ID_LEN];
		SecureRandom rnd = new SecureRandom();
		rnd.nextBytes(messageId);
	}

	/**
	 * Create a MessageId from an 8-byte array.
	 * @throws ThreemaException If the source array has the wrong length.
	 */
	public MessageId(byte[] messageId) throws ThreemaException {
		if (messageId.length != ProtocolDefines.MESSAGE_ID_LEN) {
			throw new ThreemaException("Invalid message ID length");
		}
		this.messageId = messageId;
	}

	/**
	 * Create a MessageId from an array, starting at the specified offset.
	 */
	public MessageId(byte[] data, int offset) {
		messageId = new byte[ProtocolDefines.MESSAGE_ID_LEN];
		System.arraycopy(data, offset, messageId, 0, ProtocolDefines.MESSAGE_ID_LEN);
	}

	public byte[] getMessageId() {
		return messageId;
	}

	@Override
	public String toString() {
		return Utils.byteArrayToHexString(messageId);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (obj == this)
			return true;
		if (obj.getClass() != getClass())
			return false;

		return Arrays.equals(messageId, ((MessageId)obj).messageId);
	}

	@Override
	public int hashCode() {
		/* message IDs are usually random, so just taking the first four bytes is fine */
		return messageId[0] << 24 | (messageId[1] & 0xFF) << 16 | (messageId[2] & 0xFF) << 8 | (messageId[3] & 0xFF);
	}
}
