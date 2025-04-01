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
import java.nio.charset.StandardCharsets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.protobuf.csp.e2e.fs.Version;
import ch.threema.protobuf.d2d.MdD2D;

/**
 * A message that confirms delivery of one or multiple other messages, listed with their
 * message IDs in {@code receiptMessageIds}. The {@code receiptType} specifies whether
 * this is a simple delivery receipt, a read receipt or even a user acknowledgment.
 */
public class GroupDeliveryReceiptMessage extends AbstractGroupMessage {

    private static final Logger logger = LoggingUtil.getThreemaLogger("GroupDeliveryReceiptMessage");

    private int receiptType;
    private MessageId[] receiptMessageIds;

    public GroupDeliveryReceiptMessage() {
        super();
    }

    @Override
    public int getType() {
        return ProtocolDefines.MSGTYPE_GROUP_DELIVERY_RECEIPT;
    }

    private boolean isReaction() {
        return DeliveryReceiptUtils.isReaction(this.receiptType);
    }

    @Override
    @Nullable
    public Version getMinimumRequiredForwardSecurityVersion() {
        return Version.V1_2;
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
    @Nullable
    public byte[] getBody() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            bos.write(getGroupCreator().getBytes(StandardCharsets.US_ASCII));
            bos.write(getApiGroupId().getGroupId());
            bos.write((byte) receiptType);

            for (MessageId messageId : receiptMessageIds) {
                bos.write(messageId.getMessageId());
            }
            return bos.toByteArray();

        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return null;
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
    public static GroupDeliveryReceiptMessage fromReflected(@NonNull MdD2D.IncomingMessage message) throws BadMessageException {
        GroupDeliveryReceiptMessage groupDeliveryReceiptMessage = fromByteArray(message.getBody().toByteArray());
        groupDeliveryReceiptMessage.initializeCommonProperties(message);
        return groupDeliveryReceiptMessage;
    }

    @NonNull
    public static GroupDeliveryReceiptMessage fromReflected(@NonNull MdD2D.OutgoingMessage message) throws BadMessageException {
        GroupDeliveryReceiptMessage groupDeliveryReceiptMessage = fromByteArray(message.getBody().toByteArray());
        groupDeliveryReceiptMessage.initializeCommonProperties(message);
        return groupDeliveryReceiptMessage;
    }

    @NonNull
    public static GroupDeliveryReceiptMessage fromByteArray(@NonNull byte[] data) throws BadMessageException {
        return fromByteArray(data, 0, data.length);
    }

    /**
     * Get the delivery receipt group message from the given array.
     *
     * @param data   the data that represents the group message
     * @param offset the offset where the data starts
     * @param length the length of the data (needed to ignore the padding)
     * @return the GroupDeliveryReceiptMessage
     * @throws BadMessageException if the length is invalid
     */
    @NonNull
    public static GroupDeliveryReceiptMessage fromByteArray(@NonNull byte[] data, int offset, int length) throws BadMessageException {

        if (data.length < offset + length) {
            throw new BadMessageException("Invalid byte array length (" + data.length + ") for offset " + offset + " and length " + length);
        }

        final int receiptTypeByteLength = 1;
        int groupHeaderLength = ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN;
        if (
            (length - groupHeaderLength) < (ProtocolDefines.MESSAGE_ID_LEN + receiptTypeByteLength) ||
                ((length - groupHeaderLength - receiptTypeByteLength) % ProtocolDefines.MESSAGE_ID_LEN) != 0
        ) {
            throw new BadMessageException("Bad length (" + length + ") for group delivery receipt");
        }

        GroupDeliveryReceiptMessage groupDeliveryReceiptMessage = new GroupDeliveryReceiptMessage();
        groupDeliveryReceiptMessage.setGroupCreator(
            new String(data, offset, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII)
        );
        groupDeliveryReceiptMessage.setApiGroupId(new GroupId(data, offset + ProtocolDefines.IDENTITY_LEN));
        groupDeliveryReceiptMessage.setReceiptType(data[groupHeaderLength + offset] & 0xFF);

        int messageIdsCount = ((length - groupHeaderLength - receiptTypeByteLength) / ProtocolDefines.MESSAGE_ID_LEN);
        MessageId[] receiptMessageIds = new MessageId[messageIdsCount];
        for (int i = 0; i < messageIdsCount; i++) {
            receiptMessageIds[i] = new MessageId(
                data,
                groupHeaderLength + receiptTypeByteLength + offset + (i * ProtocolDefines.MESSAGE_ID_LEN)
            );
        }

        groupDeliveryReceiptMessage.setReceiptMessageIds(receiptMessageIds);
        return groupDeliveryReceiptMessage;
    }
}
