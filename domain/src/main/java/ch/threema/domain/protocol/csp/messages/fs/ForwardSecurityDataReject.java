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

package ch.threema.domain.protocol.csp.messages.fs;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import java.util.Objects;

import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.models.MessageId;
import ch.threema.protobuf.csp.e2e.fs.ForwardSecurityEnvelope;

public class ForwardSecurityDataReject extends ForwardSecurityData {

	private final @NonNull MessageId rejectedMessageId;
	private final @NonNull ForwardSecurityEnvelope.Reject.Cause cause;

	public ForwardSecurityDataReject(@NonNull DHSessionId sessionId, @NonNull MessageId rejectedMessageId, @NonNull ForwardSecurityEnvelope.Reject.Cause cause) {
		super(sessionId);
		this.rejectedMessageId = rejectedMessageId;
		this.cause = cause;
	}

	@NonNull
	public MessageId getRejectedApiMessageId() {
		return rejectedMessageId;
	}

	@NonNull
	public ForwardSecurityEnvelope.Reject.Cause getCause() {
		return cause;
	}

	@NonNull
	@Override
	public ForwardSecurityEnvelope toProtobufMessage() {
		return ForwardSecurityEnvelope.newBuilder()
			.setSessionId(ByteString.copyFrom(this.getSessionId().get()))
			.setReject(ForwardSecurityEnvelope.Reject.newBuilder()
				.setRejectedMessageId(this.rejectedMessageId.getMessageIdLong())
				.setCause(this.cause)
				.build())
			.build();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ForwardSecurityDataReject that = (ForwardSecurityDataReject) o;
		return rejectedMessageId.equals(that.rejectedMessageId) && getCause() == that.getCause();
	}

	@Override
	public int hashCode() {
		return Objects.hash(rejectedMessageId, getCause());
	}
}
