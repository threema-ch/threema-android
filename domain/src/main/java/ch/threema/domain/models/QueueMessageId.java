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

package ch.threema.domain.models;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * A tuple `(messageId, recipientId)` that identifies a message in the message queue.
 *
 * Because a message ID may be used for multiple messages (when sending a group message),
 * the ID alone is not a unique identifier. It needs to be paired with a recipient identity
 * to become unique.
 */
public class QueueMessageId {
	final private @NonNull String recipientId;
	final private @NonNull
	MessageId messageId;

	public QueueMessageId(@NonNull MessageId messageId, @NonNull String recipientId) {
		this.messageId = messageId;
		this.recipientId = recipientId;
	}

	public @NonNull String getRecipientId() {
		return this.recipientId;
	}

	public @NonNull MessageId getMessageId() {
		return this.messageId;
	}

	@Override
	public String toString() {
		return this.messageId + "=>" + this.recipientId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		QueueMessageId that = (QueueMessageId) o;
		return recipientId.equals(that.recipientId) &&
			messageId.equals(that.messageId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(recipientId, messageId);
	}
}
