/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.protobuf.Common;
import ch.threema.protobuf.csp.e2e.fs.Encapsulated;
import ch.threema.protobuf.csp.e2e.fs.Envelope;

public class ForwardSecurityDataMessage extends ForwardSecurityData {
	private final @NonNull
	Encapsulated.DHType type;
	private final long counter;
	private final int offeredVersion;
	private final int appliedVersion;
	private final @Nullable Common.GroupIdentity groupIdentity;
	private final @NonNull byte[] message;

	public ForwardSecurityDataMessage(
		@NonNull DHSessionId sessionId,
		@NonNull Encapsulated.DHType type,
		long counter,
		int offeredVersion,
		int appliedVersion,
		@Nullable Common.GroupIdentity groupIdentity,
		@NonNull byte[] message
	) {
		super(sessionId);
		this.type = type;
		this.counter = counter;
		this.offeredVersion = offeredVersion;
		this.appliedVersion = appliedVersion;
		this.groupIdentity = groupIdentity;
		this.message = message;
	}

	@NonNull
	public Encapsulated.DHType getType() {
		return type;
	}

	public long getCounter() {
		return counter;
	}

	public int getOfferedVersion() {
		return offeredVersion;
	}

	public int getAppliedVersion() {
		return appliedVersion;
	}

	@Nullable
	public Common.GroupIdentity getGroupIdentity() {
		return this.groupIdentity;
	}

	@NonNull
	public byte[] getMessage() {
		return message;
	}

	@NonNull
	@Override
	public Envelope toProtobufMessage() {
		// Build the encapsulated message
		Encapsulated.Builder encapsulatedBuilder = Encapsulated.newBuilder()
			.setDhType(type)
			.setCounter(this.counter)
			.setOfferedVersion(this.offeredVersion)
			.setAppliedVersion(this.appliedVersion)
			.setEncryptedInner(ByteString.copyFrom(this.message));

		// Only set group identity if available
		if (groupIdentity != null) {
			encapsulatedBuilder.setGroupIdentity(groupIdentity);
		}

		// Build and return the envelope
		return Envelope.newBuilder()
			.setSessionId(ByteString.copyFrom(this.getSessionId().get()))
			.setEncapsulated(encapsulatedBuilder.build())
			.build();
	}
}
