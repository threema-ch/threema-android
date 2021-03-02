/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2021 Threema GmbH
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

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;

/**
 * A payload that is received from the server after sending an outgoing message.
 */
public class MessageAck {
	final private @NonNull byte[] recipientIdBytes;
	final private @NonNull String recipientId;
	final private @NonNull MessageId messageId;

	public MessageAck(@NonNull byte[] recipientId, @NonNull MessageId messageId) {
		this.recipientIdBytes = recipientId;
		this.recipientId = new String(recipientId, StandardCharsets.UTF_8);
		this.messageId = messageId;
	}

	public @NonNull byte[] getRecipientIdBytes() {
		return this.recipientIdBytes;
	}

	public @NonNull String getRecipientId() {
		return this.recipientId;
	}

	public @NonNull MessageId getMessageId() {
		return this.messageId;
	}
}
