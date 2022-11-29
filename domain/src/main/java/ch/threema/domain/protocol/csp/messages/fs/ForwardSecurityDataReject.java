/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2022 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.fs;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import java.util.Objects;

import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.models.MessageId;
import ch.threema.protobuf.csp.e2e.fs.ForwardSecurityEnvelope;

public class ForwardSecurityDataReject extends ForwardSecurityData {

	private final @NonNull MessageId rejectedMessageId;

	public ForwardSecurityDataReject(DHSessionId sessionId, MessageId rejectedMessageId) {
		super(sessionId);
		this.rejectedMessageId = rejectedMessageId;
	}

	@NonNull
	public MessageId getRejectedApiMessageId() {
		return rejectedMessageId;
	}

	@NonNull
	@Override
	public ForwardSecurityEnvelope toProtobufMessage() {
		return ForwardSecurityEnvelope.newBuilder()
			.setSessionId(ByteString.copyFrom(this.getSessionId().get()))
			.setReject(ForwardSecurityEnvelope.Reject.newBuilder()
				.setRejectedMessageId(this.rejectedMessageId.getMessageIdLong())
				.build())
			.build();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		if (!super.equals(o)) return false;
		ForwardSecurityDataReject that = (ForwardSecurityDataReject) o;
		return getRejectedApiMessageId().equals(that.getRejectedApiMessageId());
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), getRejectedApiMessageId());
	}
}
