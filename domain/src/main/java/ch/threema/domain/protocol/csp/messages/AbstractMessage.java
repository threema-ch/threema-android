/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import androidx.annotation.Nullable;
import ch.threema.base.ThreemaException;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.models.QueueMessageId;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.coders.MessageBox;

/**
 * Abstract base class for messages that can be sent via the Threema server interface,
 * in unencrypted form. For the encrypted version, see {@link MessageBox}.
 */
public abstract class AbstractMessage {

	private static final Logger logger = LoggerFactory.getLogger(AbstractMessage.class);

	private String fromIdentity;
	private String toIdentity;
	private MessageId messageId;
	private String pushFromName;
	private Date date;
	private int messageFlags;

	public AbstractMessage() {
		this.date = new Date();
		this.messageId = new MessageId();
	}

	/* Methods to be overridden by subclasses */
	public abstract int getType();

	/**
	 * Return whether this message should be pushed to the recipient. Do not use for
	 * internal messages (like delivery reports etc.).
	 *
	 * @return should push true/false
	 */
	public boolean shouldPush() {
		return false;
	}

	/**
	 * Return whether this is in an immediate message, i.e. whether it should be discarded
	 * if the recipient is not currently online.
	 *
	 * @return immediate message true/false
	 */
	public boolean isImmediate() {
		return false;
	}

	/**
	 * Return whether the sender should expect an ACK from the other party after transmitting
	 * this message via the server connection. This flag affects both client and server.
	 *
	 * @return if true, no ACK is expected
	 */
	public boolean isNoAck() {
		return false;
	}

	/**
	 * Return whether this is a group message. The server uses this to decide which push
	 * text to use.
	 *
	 * @return if true, this is a group message
	 */
	public boolean isGroup() {
		return false;
	}

	/**
	 * Return whether this is a VoIP signaling message.
	 *
	 * If the VoIP flag is set, then the VoIP push token will be used instead of the regular
	 * push token. Furthermore, messages that have the VoIP flag set will only remain queued
	 * for 60 seconds, rather than the normal two weeks.
	 *
	 * @return if true, this is a VoIP signaling message
	 */
	public boolean isVoip() {
		return false;
	}

	/**
	 * Return whether the no delivery receipts flag is set in this message
	 * @return true if no delivery receipts are to be sent to the sender of the message
	 */
	public boolean isNoDeliveryReceipts() {
		return (getMessageFlags() & ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS) == ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS;
	}

	/**
	 * Return whether the user's profile information (nickname, picture etc.) is allowed to
	 * be sent along with this message. This should be set to true for user-initiated messages only.
	 */
	public boolean allowSendingProfile() {
		return false;
	}

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
}
