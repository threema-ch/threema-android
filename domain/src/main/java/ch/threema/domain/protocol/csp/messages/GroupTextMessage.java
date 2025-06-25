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
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.protobuf.csp.e2e.fs.Version;
import ch.threema.protobuf.d2d.MdD2D;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A group message that has plain text as its contents.
 */
public class GroupTextMessage extends AbstractGroupMessage {

    private static final Logger logger = LoggingUtil.getThreemaLogger("GroupTextMessage");

    private String text;

    public GroupTextMessage() {
        super();
    }

    @Override
    public int getType() {
        return ProtocolDefines.MSGTYPE_GROUP_TEXT;
    }

    @Override
    public boolean flagSendPush() {
        return true;
    }

    @Override
    @Nullable
    public Version getMinimumRequiredForwardSecurityVersion() {
        return Version.V1_2;
    }

    @Override
    public boolean allowUserProfileDistribution() {
        return true;
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
        return true;
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
        return true;
    }

    @Override
    public boolean sendAutomaticDeliveryReceipt() {
        return false;
    }

    @Override
    public boolean bumpLastUpdate() {
        return true;
    }

    @Override
    public byte[] getBody() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(getGroupCreator().getBytes(StandardCharsets.US_ASCII));
            bos.write(getApiGroupId().getGroupId());
            bos.write(text.getBytes(StandardCharsets.UTF_8));
            return bos.toByteArray();
        } catch (Exception e) {
            logger.error(e.getMessage());
            return null;
        }
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @NonNull
    public static GroupTextMessage fromReflected(MdD2D.IncomingMessage message) throws BadMessageException {
        GroupTextMessage textMessage = fromByteArray(message.getBody().toByteArray());
        textMessage.initializeCommonProperties(message);
        return textMessage;
    }

    @NonNull
    public static GroupTextMessage fromReflected(MdD2D.OutgoingMessage message) throws BadMessageException {
        GroupTextMessage textMessage = fromByteArray(message.getBody().toByteArray());
        textMessage.initializeCommonProperties(message);
        return textMessage;
    }

    @NonNull
    public static GroupTextMessage fromByteArray(@NonNull byte[] data) throws BadMessageException {
        return fromByteArray(data, 0, data.length);
    }

    /**
     * Get the group text message from the given array.
     *
     * @param data   the data that represents the message
     * @param offset the offset where the data starts
     * @param length the length of the data (needed to ignore the padding)
     * @return the group text message
     * @throws BadMessageException if the length is invalid
     */
    @NonNull
    public static GroupTextMessage fromByteArray(@NonNull byte[] data, int offset, int length) throws BadMessageException {
        if (data.length < offset + length) {
            throw new BadMessageException("Invalid byte array length (" + data.length + ") for " +
                "offset " + offset + " and length " + length);
        }

        int minTextLength = 1;
        int minByteArrayLength = minTextLength + ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN;
        if (length < minByteArrayLength) {
            throw new BadMessageException("Bad length (" + length + ") for group text message");
        }

        GroupTextMessage groupTextMessage = new GroupTextMessage();
        groupTextMessage.setGroupCreator(new String(data, offset, ProtocolDefines.IDENTITY_LEN, StandardCharsets.US_ASCII));
        groupTextMessage.setApiGroupId(new GroupId(data, offset + ProtocolDefines.IDENTITY_LEN));
        groupTextMessage.setText(new String(data, offset + ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN, length - ProtocolDefines.IDENTITY_LEN - ProtocolDefines.GROUP_ID_LEN, UTF_8));
        return groupTextMessage;
    }
}
