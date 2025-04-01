/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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
import androidx.annotation.Nullable;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.protobuf.AbstractProtobufMessage;
import ch.threema.protobuf.csp.e2e.fs.Version;

public class ForwardSecurityEnvelopeMessage extends AbstractProtobufMessage<ForwardSecurityData> {

    @Nullable
    private final AbstractMessage innerMessage;

    private final boolean isForwardSecurityControlMessage;

    /**
     * Use this constructor for incoming forward security envelope messages.
     *
     * @param payloadData the forward security payload
     */
    public ForwardSecurityEnvelopeMessage(@NonNull ForwardSecurityData payloadData) {
        this(payloadData, false);
    }

    public ForwardSecurityEnvelopeMessage(@NonNull ForwardSecurityData payloadData, boolean isForwardSecurityControlMessage) {
        super(ProtocolDefines.MSGTYPE_FS_ENVELOPE, payloadData);
        this.innerMessage = null;
        this.isForwardSecurityControlMessage = isForwardSecurityControlMessage;
    }

    /**
     * Use this for outgoing forward security envelope messages. The inner message is used to set
     * message flags and type specific properties.
     *
     * @param payloadData         the forward security payload
     * @param innerMessage        the inner message
     * @param forwardSecurityMode the forward security mode
     */
    public ForwardSecurityEnvelopeMessage(
        @NonNull ForwardSecurityData payloadData,
        @NonNull AbstractMessage innerMessage,
        @NonNull ForwardSecurityMode forwardSecurityMode
    ) {
        super(ProtocolDefines.MSGTYPE_FS_ENVELOPE, payloadData);
        this.innerMessage = innerMessage;
        this.isForwardSecurityControlMessage = false;

        setFromIdentity(innerMessage.getFromIdentity());
        setToIdentity(innerMessage.getToIdentity());
        setMessageId(innerMessage.getMessageId());
        setDate(innerMessage.getDate());
        setMessageFlags(innerMessage.getMessageFlags());
        setNickname(innerMessage.getNickname());
        setForwardSecurityMode(forwardSecurityMode);
    }

    @Nullable
    @Override
    public Version getMinimumRequiredForwardSecurityVersion() {
        // Do not allow encapsulating forward security envelope messages
        return null;
    }

    @Override
    public boolean allowUserProfileDistribution() {
        if (isForwardSecurityControlMessage) {
            return false;
        }
        if (innerMessage == null) {
            throw new IllegalStateException("Cannot check for user profile distribution on incoming fs envelopes");
        }
        return innerMessage.allowUserProfileDistribution();
    }

    @Override
    public boolean exemptFromBlocking() {
        // Note that checking for exemption from blocking should never happen on forward security
        // envelope messages.
        throw new IllegalStateException("Cannot check for exemption from blocking of fs envelopes");
    }

    @Override
    public boolean createImplicitlyDirectContact() {
        // Note that checking for implicit direct contact creation must never happen on forward
        // security envelope messages.
        throw new IllegalStateException("Cannot check for implicit direct contact creation on fs envelopes");
    }

    @Override
    public boolean protectAgainstReplay() {
        if (isForwardSecurityControlMessage) {
            return true;
        }
        if (innerMessage == null) {
            throw new IllegalStateException("Cannot check for replay protection on incoming fs envelopes");
        }
        return innerMessage.protectAgainstReplay();
    }

    @Override
    public boolean reflectIncoming() {
        throw new IllegalStateException("Cannot check incoming reflection of incoming fs envelopes before decryption");
    }

    @Override
    public boolean reflectOutgoing() {
        if (innerMessage == null) {
            throw new IllegalStateException("Cannot check outgoing reflection of incoming fs envelopes");
        }
        return false;
    }

    @Override
    public boolean reflectSentUpdate() {
        if (innerMessage == null) {
            throw new IllegalStateException("Cannot check sent update reflection of incoming fs envelopes");
        }
        return innerMessage.reflectSentUpdate();
    }

    @Override
    public boolean sendAutomaticDeliveryReceipt() {
        throw new IllegalStateException("Cannot check for sending automatic delivery receipt on fs envelopes");
    }

    @Override
    public boolean bumpLastUpdate() {
        throw new IllegalStateException("Cannot check bumpLastUpdate on fs envelopes");
    }

    @Override
    public boolean flagSendPush() {
        // Note that a forward security envelope message initially has no flags set
        return false;
    }

    @Override
    public boolean flagNoServerQueuing() {
        // Note that a forward security envelope message initially has no flags set
        return false;
    }

    @Override
    public boolean flagNoServerAck() {
        // Note that a forward security envelope message initially has no flags set
        return false;
    }

    @Override
    public boolean flagGroupMessage() {
        // Note that a forward security envelope message initially has no flags set
        return false;
    }

    @Override
    public boolean flagShortLivedServerQueuing() {
        // Note that a forward security envelope message initially has no flags set
        return false;
    }

}
