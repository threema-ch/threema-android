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

import androidx.annotation.Nullable;
import ch.threema.base.ThreemaException;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.models.QueueMessageId;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityMode;
import ch.threema.protobuf.csp.e2e.fs.Version;

/**
 * Abstract base class for messages that can be sent via the Threema server interface,
 * in unencrypted form. For the encrypted version, see {@link MessageBox}.
 */
public abstract class AbstractMessage {

	private String fromIdentity;
	private String toIdentity;
	private MessageId messageId;
	private String pushFromName;
	private Date date;
	private int messageFlags;
	private ForwardSecurityMode forwardSecurityMode;

	public AbstractMessage() {
		this.date = new Date();
		this.messageId = new MessageId();
		this.forwardSecurityMode = ForwardSecurityMode.NONE;
	}

	/* Methods to be overridden by subclasses */
	public abstract int getType();

	/**
	 * Flag 0x01: Send push notification
	 *
	 * The server will send a push message to the receiver of the message.
	 * Only use this for messages that require a notification. For example, do not
	 * set this for delivery receipts.
	 */
	public boolean flagSendPush() {
		return false;
	}

	/**
	 * Flag 0x02: No server queuing
	 *
	 * Use this for messages that can be discarded by the chat server in case the receiver
	 * is not connected to the chat server, e.g. the typing indicator.
	 */
	public boolean flagNoServerQueuing() {
		return false;
	}

	/**
	 * Flag 0x04: No server acknowledgement
	 *
	 * Use this for messages where reliable delivery and acknowledgement is not essential,
	 * e.g. the typing indicator. Will not be acknowledged by the chat server when sending.
	 * No acknowledgement should be sent by the receiver to the chat server.
	 */
	public boolean flagNoServerAck() {
		return false;
	}

	/**
	 * Flag 0x10: Group message marker (DEPRECATED)
	 *
	 * Use this for all group messages. In iOS clients, this will be used for notifications
	 * to reflect that a group message has been received in case no connection to the server
	 * could be established.
	 */
	public boolean flagGroupMessage() {
		return false;
	}

	/**
	 * Flag 0x20: Short-lived server queuing
	 *
	 * Messages with this flag will only be queued for 60 seconds.
	 */
	public boolean flagShortLivedServerQueuing() {
		return false;
	}

	/**
	 * Flag 0x80: Don't send delivery receipts
	 *
	 * This may not be used by the apps but can be used by Threema Gateway IDs
	 * which do not necessarily want a delivery receipt for a message.
	 */
	public boolean flagNoDeliveryReceipts() {
		return (getMessageFlags() & ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS) == ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS;
	}

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
	 * Return whether the user's profile information (nickname, picture etc.) is allowed to
	 * be sent along with this message. This should be set to true for user-initiated messages only.
	 */
	public boolean allowUserProfileDistribution() {
		return false;
	}

	/**
	 * Return whether this message should be exempted from blocking.
	 */
	public abstract boolean exemptFromBlocking();

	/**
	 * Return the body of this message in network format (i.e. formatted as a byte array).
	 *
	 * @return message body
	 */
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

	/**
	 * Return the {@link QueueMessageId} for this message.
	 *
	 * If the `toIdentity` or `messageId` fields are not set, return null.
	 */
	public @Nullable QueueMessageId getQueueMessageId() {
		if (this.toIdentity != null && this.messageId != null) {
			return new QueueMessageId(this.getMessageId(), this.toIdentity);
		}
		return null;
	}

	public String getPushFromName() {
		return pushFromName;
	}

	public void setPushFromName(String pushFromName) {
		this.pushFromName = pushFromName;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public int getMessageFlags() {
		return messageFlags;
	}

	public void setMessageFlags(int messageFlags) {
		this.messageFlags = messageFlags;
	}

	/**
	 * Get the default message type flags. These flags are based on {@link #flagSendPush()},
	 * {@link #flagNoServerQueuing}, {@link #flagNoServerAck()}, {@link #flagGroupMessage()}, and
	 * {@link #flagShortLivedServerQueuing()}.
	 *
	 * @return the message flags that are set by default for the specific message type
	 */
	public int getMessageTypeDefaultFlags() {
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
}
