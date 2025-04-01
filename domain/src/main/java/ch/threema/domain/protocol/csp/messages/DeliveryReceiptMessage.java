/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.protobuf.csp.e2e.fs.Version;
import ch.threema.protobuf.d2d.MdD2D;

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
    public boolean exemptFromBlocking() {
        return false;
    }

    @Override
    public boolean createImplicitlyDirectContact() {
        return false;
    }

    @Override
    public boolean protectAgainstReplay() {
        return isReaction();
    }

    @Override
    public boolean reflectIncoming() {
        return true;
    }

    @Override
    public boolean reflectOutgoing() {
        return true;
    }

    @Override
    public boolean reflectSentUpdate() {
        return false;
    }

    @Override
    public boolean sendAutomaticDeliveryReceipt() {
        return false;
    }

    @Override
    public boolean bumpLastUpdate() {
        return false;
    }

    @Override
    @NonNull
    public byte[] getBody() {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        bos.write((byte) receiptType);

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

    @NonNull
    public static DeliveryReceiptMessage fromReflected(@NonNull MdD2D.IncomingMessage message) throws BadMessageException {
        DeliveryReceiptMessage deliveryReceiptMessage = fromByteArray(message.getBody().toByteArray());
        deliveryReceiptMessage.initializeCommonProperties(message);
        return deliveryReceiptMessage;
    }

    @NonNull
    public static DeliveryReceiptMessage fromReflected(@NonNull MdD2D.OutgoingMessage message) throws BadMessageException {
        DeliveryReceiptMessage deliveryReceiptMessage = fromByteArray(message.getBody().toByteArray());
        deliveryReceiptMessage.initializeCommonProperties(message);
        return deliveryReceiptMessage;
    }

    @NonNull
    public static DeliveryReceiptMessage fromByteArray(@NonNull byte[] data) throws BadMessageException {
        return fromByteArray(data, 0, data.length);
    }

    /**
     * Get the delivery receipt message from the given array.
     *
     * @param data   the data that represents the message
     * @param offset the offset where the data starts
     * @param length the length of the data (needed to ignore the padding)
     * @return the delivery receipt message
     * @throws BadMessageException if the length is invalid
     */
    @NonNull
    public static DeliveryReceiptMessage fromByteArray(@NonNull byte[] data, int offset, int length) throws BadMessageException {
        if (data.length < offset + length) {
            throw new BadMessageException("Invalid byte array length (" + data.length + ") for " +
                "offset " + offset + " and length " + length);
        }

        int deliveryReceiptTypeLength = 1;
        int minDataLength = ProtocolDefines.MESSAGE_ID_LEN + deliveryReceiptTypeLength;
        int messageIdsLength = length - deliveryReceiptTypeLength;

        if (length < minDataLength || (messageIdsLength % ProtocolDefines.MESSAGE_ID_LEN) != 0) {
            throw new BadMessageException("Bad length (" + length + ") for delivery receipt");
        }

        DeliveryReceiptMessage deliveryReceiptMessage = new DeliveryReceiptMessage();
        deliveryReceiptMessage.setReceiptType(data[offset] & 0xFF);

        // The offset where the message ids start
        int messageIdsOffset = deliveryReceiptTypeLength + offset;

        int numMsgIds = (messageIdsLength / ProtocolDefines.MESSAGE_ID_LEN);
        MessageId[] receiptMessageIds = new MessageId[numMsgIds];
        for (int i = 0; i < numMsgIds; i++) {
            receiptMessageIds[i] = new MessageId(data, messageIdsOffset + i * ProtocolDefines.MESSAGE_ID_LEN);
        }

        deliveryReceiptMessage.setReceiptMessageIds(receiptMessageIds);

        return deliveryReceiptMessage;
    }
}
