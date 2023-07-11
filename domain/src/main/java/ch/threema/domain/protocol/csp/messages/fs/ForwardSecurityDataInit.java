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

import com.google.protobuf.ByteString;
import com.neilalexander.jnacl.NaCl;

import androidx.annotation.NonNull;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.protobuf.csp.e2e.fs.Envelope;
import ch.threema.protobuf.csp.e2e.fs.Init;
import ch.threema.protobuf.csp.e2e.fs.VersionRange;

public class ForwardSecurityDataInit extends ForwardSecurityData {
	private final @NonNull VersionRange versionRange;
	private final @NonNull byte[] ephemeralPublicKey;

	public ForwardSecurityDataInit(
		@NonNull DHSessionId sessionId,
		@NonNull VersionRange versionRange,
		@NonNull byte[] ephemeralPublicKey
	) throws InvalidEphemeralPublicKeyException {
		super(sessionId);
		this.versionRange = versionRange;
		if (ephemeralPublicKey.length != NaCl.PUBLICKEYBYTES) {
			throw new InvalidEphemeralPublicKeyException("Bad ephemeral public key length");
		}
		this.ephemeralPublicKey = ephemeralPublicKey;
	}

	@NonNull
	public VersionRange getVersionRange() {
		return versionRange;
	}

	@NonNull
	public byte[] getEphemeralPublicKey() {
		return ephemeralPublicKey;
	}

	@NonNull
	@Override
	public Envelope toProtobufMessage() {
		return Envelope.newBuilder()
			.setSessionId(ByteString.copyFrom(this.getSessionId().get()))
			.setInit(Init.newBuilder()
				.setSupportedVersion(this.versionRange)
				.setFssk(ByteString.copyFrom(this.ephemeralPublicKey))
				.build())
			.build();
	}
}
