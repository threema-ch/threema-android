/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import androidx.annotation.Nullable;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.protobuf.csp.e2e.fs.Version;

/**
 * A message that confirms delivery of one or multiple other messages, listed with their
 * message IDs in {@code receiptMessageIds}. The {@code receiptType} specifies whether
 * this is a simple delivery receipt, a read receipt or even a user acknowledgment.
 */
public class DeliveryReceiptMessage extends AbstractMessage {

	private static final Logger logger = LoggingUtil.getThreemaLogger("DeliveryReceiptMessage");

	private int receiptType;
	private MessageId[] receiptMessageIds;

	public DeliveryReceiptMessage() {
		super();
	}

	@Override
	public int getType() {
		return ProtocolDefines.MSGTYPE_DELIVERY_RECEIPT;
	}

	@Nullable
	@Override
	public Version getMinimumRequiredForwardSecurityVersion() {
		return Version.V1_1;
	}

	private boolean isReaction() {
		return DeliveryReceiptUtils.isReaction(this.receiptType);
	}

	@Override
	public boolean allowUserProfileDistribution() {
		return this.isReaction();
	}

	@Override
	public byte[] getBody() {

		ByteArrayOutputStream bos = new ByteArrayOutputStream();

		bos.write((byte)receiptType);

		for (MessageId messageId : receiptMessageIds) {
			try {
				bos.write(messageId.getMessageId());
			} catch (IOException e) {
				logger.error(e.getMessage());
			}
		}

		return bos.toByteArray();
	}

	public int getReceiptType() {
		return receiptType;
	}

	public void setReceiptType(int receiptType) {
		this.receiptType = receiptType;
	}

	public MessageId[] getReceiptMessageIds() {
		return receiptMessageIds;
	}

	public void setReceiptMessageIds(MessageId[] receiptMessageIds) {
		this.receiptMessageIds = receiptMessageIds;
	}
}
