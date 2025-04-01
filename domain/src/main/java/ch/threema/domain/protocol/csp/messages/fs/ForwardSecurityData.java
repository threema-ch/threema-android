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

import com.google.protobuf.InvalidProtocolBufferException;

import androidx.annotation.NonNull;
import ch.threema.base.ThreemaException;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.protobuf.ProtobufDataInterface;
import ch.threema.protobuf.csp.e2e.fs.Accept;
import ch.threema.protobuf.csp.e2e.fs.Encapsulated;
import ch.threema.protobuf.csp.e2e.fs.Envelope;
import ch.threema.protobuf.csp.e2e.fs.Init;
import ch.threema.protobuf.csp.e2e.fs.Reject;

public abstract class ForwardSecurityData implements ProtobufDataInterface<Envelope> {

    private final @NonNull DHSessionId sessionId;

    protected ForwardSecurityData(@NonNull DHSessionId sessionId) {
        this.sessionId = sessionId;
    }

    @NonNull
    public DHSessionId getSessionId() {
        return sessionId;
    }

    @NonNull
    public static ForwardSecurityData fromProtobuf(@NonNull byte[] rawProtobufMessage) throws BadMessageException {
        try {
            Envelope protobufMessage = Envelope.parseFrom(rawProtobufMessage);

            DHSessionId sessionId = new DHSessionId(protobufMessage.getSessionId().toByteArray());

            switch (protobufMessage.getContentCase()) {
                case INIT: {
                    final Init init = protobufMessage.getInit();
                    return new ForwardSecurityDataInit(sessionId, init.getSupportedVersion(), init.getFssk().toByteArray());
                }
                case ACCEPT: {
                    final Accept accept = protobufMessage.getAccept();
                    return new ForwardSecurityDataAccept(sessionId, accept.getSupportedVersion(), accept.getFssk().toByteArray());
                }
                case REJECT: {
                    final Reject reject = protobufMessage.getReject();
                    return new ForwardSecurityDataReject(
                        sessionId,
                        new MessageId(reject.getMessageId()),
                        reject.getGroupIdentity(),
                        reject.getCause()
                    );
                }
                case TERMINATE:
                    return new ForwardSecurityDataTerminate(sessionId, protobufMessage.getTerminate().getCause());
                case ENCAPSULATED: {
                    final Encapsulated encapsulated = protobufMessage.getEncapsulated();
                    return new ForwardSecurityDataMessage(sessionId,
                        encapsulated.getDhType(),
                        encapsulated.getCounter(),
                        encapsulated.getOfferedVersion(),
                        encapsulated.getAppliedVersion(),
                        encapsulated.hasGroupIdentity() ? encapsulated.getGroupIdentity() : null,
                        encapsulated.getEncryptedInner().toByteArray());
                }
                default:
                    throw new BadMessageException("Unknown forward security message type");
            }
        } catch (InvalidProtocolBufferException e) {
            throw new BadMessageException("Invalid forward security message protobuf data");
        } catch (DHSessionId.InvalidDHSessionIdException e) {
            throw new BadMessageException("Bad forward security session ID length");
        } catch (InvalidEphemeralPublicKeyException e) {
            throw new BadMessageException("Bad ephemeral public key length");
        }
    }

    public static class InvalidEphemeralPublicKeyException extends ThreemaException {
        public InvalidEphemeralPublicKeyException(final String msg) {
            super(msg);
        }
    }
}
