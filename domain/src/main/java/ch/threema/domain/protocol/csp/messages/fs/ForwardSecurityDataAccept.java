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
import com.neilalexander.jnacl.NaCl;

import java.util.Arrays;

import androidx.annotation.NonNull;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.protobuf.csp.e2e.fs.ForwardSecurityEnvelope;

public class ForwardSecurityDataAccept extends ForwardSecurityData {

	private final @NonNull byte[] ephemeralPublicKey;

	public ForwardSecurityDataAccept(DHSessionId sessionId, byte[] ephemeralPublicKey) throws InvalidEphemeralPublicKeyException {
		super(sessionId);
		if (ephemeralPublicKey.length != NaCl.PUBLICKEYBYTES) {
			throw new InvalidEphemeralPublicKeyException("Bad ephemeral public key length");
		}
		this.ephemeralPublicKey = ephemeralPublicKey;
	}

	@NonNull
	public byte[] getEphemeralPublicKey() {
		return ephemeralPublicKey;
	}

	@NonNull
	@Override
	public ForwardSecurityEnvelope toProtobufMessage() {
		return ForwardSecurityEnvelope.newBuilder()
			.setSessionId(ByteString.copyFrom(this.getSessionId().get()))
			.setAccept(ForwardSecurityEnvelope.Accept.newBuilder()
				.setEphemeralPublicKey(ByteString.copyFrom(this.ephemeralPublicKey))
				.build())
			.build();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		if (!super.equals(o)) return false;
		ForwardSecurityDataAccept that = (ForwardSecurityDataAccept) o;
		return Arrays.equals(getEphemeralPublicKey(), that.getEphemeralPublicKey());
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + Arrays.hashCode(getEphemeralPublicKey());
		return result;
	}
}
