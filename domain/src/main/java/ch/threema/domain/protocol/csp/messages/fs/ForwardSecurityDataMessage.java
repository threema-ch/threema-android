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

import com.google.protobuf.ByteString;

import java.util.Arrays;
import java.util.Objects;

import androidx.annotation.NonNull;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.protobuf.csp.e2e.fs.ForwardSecurityEnvelope;

public class ForwardSecurityDataMessage extends ForwardSecurityData {

	private final @NonNull
	ForwardSecurityEnvelope.Message.DHType type;
	private final @NonNull long counter;
	private final @NonNull byte[] message;

	public ForwardSecurityDataMessage(DHSessionId sessionId, ForwardSecurityEnvelope.Message.DHType type, long counter, byte[] message) {
		super(sessionId);
		this.type = type;
		this.counter = counter;
		this.message = message;
	}

	@NonNull
	public ForwardSecurityEnvelope.Message.DHType getType() {
		return type;
	}

	public long getCounter() {
		return counter;
	}

	@NonNull
	public byte[] getMessage() {
		return message;
	}

	@NonNull
	@Override
	public ForwardSecurityEnvelope toProtobufMessage() {
		return ForwardSecurityEnvelope.newBuilder()
			.setSessionId(ByteString.copyFrom(this.getSessionId().get()))
			.setMessage(ForwardSecurityEnvelope.Message.newBuilder()
				.setDhType(type)
				.setCounter(this.counter)
				.setMessage(ByteString.copyFrom(this.message))
				.build())
			.build();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ForwardSecurityDataMessage that = (ForwardSecurityDataMessage) o;
		return getCounter() == that.getCounter() && getType() == that.getType() && Arrays.equals(getMessage(), that.getMessage());
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(getType(), getCounter());
		result = 31 * result + Arrays.hashCode(getMessage());
		return result;
	}
}
