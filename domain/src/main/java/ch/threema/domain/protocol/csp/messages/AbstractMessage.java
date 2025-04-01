/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages;

import java.util.Date;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.ThreemaException;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityMode;
import ch.threema.protobuf.csp.e2e.fs.Version;
import ch.threema.protobuf.d2d.MdD2D;

/**
 * Abstract base class for messages that can be sent via the Threema server interface,
 * in unencrypted form. For the encrypted version, see {@link MessageBox}.
 */
public abstract class AbstractMessage implements MessageTypeProperties, MessageFlags {
    private String fromIdentity;
    private String toIdentity;
    private MessageId messageId;
    private String nickname;
    private Date date;
    private int messageFlags;
    private ForwardSecurityMode forwardSecurityMode;

    public AbstractMessage() {
        this.date = new Date();
        this.messageId = new MessageId();
        this.forwardSecurityMode = ForwardSecurityMode.NONE;
        this.messageFlags = getMessageTypeDefaultFlags();
    }

    /**
     * Return the numeric message type (CspE2eMessageType in the protocol).
     */
    public abstract int getType();

    /**
     * Get the minimum version of forward security that is needed to send this message type with
     * perfect forward security. If the message type is currently not supported to be sent with
     * forward security, null is returned.
     *
     * @return the minimum version that is required in a session for this message type; null if it
     * is currently not supported
     */
    @Nullable
    public abstract Version getMinimumRequiredForwardSecurityVersion();

    /**
     * Return the body of this message in network format (i.e. formatted as a byte array). Note that
     * a valid message should not return null. If null is returned, this is an indication that the
     * message hasn't been initialized properly.
     *
     * @return message body
     */
    @Nullable
    public abstract byte[] getBody() throws ThreemaException;

    /* Getters/Setters */
    public String getFromIdentity() {
        return fromIdentity;
    }

    public void setFromIdentity(String fromIdentity) {
        this.fromIdentity = fromIdentity;
    }

    public String getToIdentity() {
        return toIdentity;
    }

    public void setToIdentity(String toIdentity) {
        this.toIdentity = toIdentity;
    }

    public MessageId getMessageId() {
        return messageId;
    }

    public void setMessageId(MessageId messageId) {
        this.messageId = messageId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    /**
     * Get the flags associated by this message. Note that the flags initially are set to the
     * default message flags of its type. For incoming messages, these flags are set to the actual
     * flags of the received message.
     */
    public int getMessageFlags() {
        return messageFlags;
    }

    /**
     * Set the message flags. Note that for outgoing messages this is not necessary, as the flags
     * are set to the default flags initially.
     */
    public void setMessageFlags(int messageFlags) {
        this.messageFlags = messageFlags;
    }

    /**
     * Check whether the message has the given flags.
     *
     * @param messageFlags the flags that are checked
     * @return true if all the flags are set, false if at least one is missing
     */
    public boolean hasFlags(int messageFlags) {
        return (this.messageFlags & messageFlags) != 0;
    }

    /**
     * Get the default message type flags. These flags are based on {@link #flagSendPush()},
     * {@link #flagNoServerQueuing}, {@link #flagNoServerAck()}, {@link #flagGroupMessage()}, and
     * {@link #flagShortLivedServerQueuing()}.
     *
     * @return the message flags that are set by default for the specific message type
     */
    private int getMessageTypeDefaultFlags() {
        return (flagSendPush() ? ProtocolDefines.MESSAGE_FLAG_SEND_PUSH : 0)
            | (flagNoServerQueuing() ? ProtocolDefines.MESSAGE_FLAG_NO_SERVER_QUEUING : 0)
            | (flagNoServerAck() ? ProtocolDefines.MESSAGE_FLAG_NO_SERVER_ACK : 0)
            | (flagGroupMessage() ? ProtocolDefines.MESSAGE_FLAG_GROUP : 0)
            | (flagShortLivedServerQueuing() ? ProtocolDefines.MESSAGE_FLAG_SHORT_LIVED : 0);
    }

    public ForwardSecurityMode getForwardSecurityMode() {
        return forwardSecurityMode;
    }

    public void setForwardSecurityMode(ForwardSecurityMode forwardSecurityMode) {
        this.forwardSecurityMode = forwardSecurityMode;
    }

    /**
     * Initialize common properties from a reflected incoming message.
     *
     * @param message the incoming MdD2D message
     */
    protected void initializeCommonProperties(@NonNull MdD2D.IncomingMessage message) {
        this.fromIdentity = message.getSenderIdentity();
        this.messageId = new MessageId(message.getMessageId());
        this.date = new Date(message.getCreatedAt());
    }

    /**
     * Initialize common properties for an outgoing message.
     *
     * @param message the outgoing MdD2D message
     */
    protected void initializeCommonProperties(@NonNull MdD2D.OutgoingMessage message) {
        this.messageId = new MessageId((message.getMessageId()));
        this.date = new Date(message.getCreatedAt());
    }
}
