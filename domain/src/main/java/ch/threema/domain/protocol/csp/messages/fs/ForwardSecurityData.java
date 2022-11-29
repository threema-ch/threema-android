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

import com.google.protobuf.InvalidProtocolBufferException;

import androidx.annotation.NonNull;
import ch.threema.base.ThreemaException;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.protobuf.ProtobufDataInterface;
import ch.threema.protobuf.csp.e2e.fs.ForwardSecurityEnvelope;

public abstract class ForwardSecurityData implements ProtobufDataInterface<ForwardSecurityEnvelope> {

	private final @NonNull DHSessionId sessionId;

	protected ForwardSecurityData(DHSessionId sessionId) {
		this.sessionId = sessionId;
	}

	@NonNull
	public DHSessionId getSessionId() {
		return sessionId;
	}

	@NonNull
	public static ForwardSecurityData fromProtobuf(@NonNull byte[] rawProtobufMessage) throws BadMessageException {
		try {
			ForwardSecurityEnvelope protobufMessage = ForwardSecurityEnvelope.parseFrom(rawProtobufMessage);

			DHSessionId sessionId = new DHSessionId(protobufMessage.getSessionId().toByteArray());

			switch (protobufMessage.getContentCase()) {
				case INIT:
					return new ForwardSecurityDataInit(sessionId, protobufMessage.getInit().getEphemeralPublicKey().toByteArray());
				case ACCEPT:
					return new ForwardSecurityDataAccept(sessionId, protobufMessage.getAccept().getEphemeralPublicKey().toByteArray());
				case REJECT:
					return new ForwardSecurityDataReject(sessionId, new MessageId(protobufMessage.getReject().getRejectedMessageId()));
				case TERMINATE:
					return new ForwardSecurityDataTerminate(sessionId);
				case MESSAGE:
					return new ForwardSecurityDataMessage(sessionId,
						protobufMessage.getMessage().getDhType(),
						protobufMessage.getMessage().getCounter(),
						protobufMessage.getMessage().getMessage().toByteArray());
				default:
					throw new BadMessageException("Unknown forward security message type");
			}
		} catch (InvalidProtocolBufferException e) {
			throw new BadMessageException("Invalid forward security message protobuf data", true);
		} catch (DHSessionId.InvalidDHSessionIdException e) {
			throw new BadMessageException("Bad forward security session ID length", true);
		} catch (InvalidEphemeralPublicKeyException e) {
			throw new BadMessageException("Bad ephemeral public key length", true);
		}
	}

	public static class InvalidEphemeralPublicKeyException extends ThreemaException {
		public InvalidEphemeralPublicKeyException(final String msg) {
			super(msg);
		}
	}
}
