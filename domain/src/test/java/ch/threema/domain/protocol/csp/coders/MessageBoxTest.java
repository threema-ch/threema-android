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

package ch.threema.domain.protocol.csp.coders;

import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.MessageId;

import org.apache.commons.io.output.NullOutputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class MessageBoxTest {

    private static final long TEST_DATE_UNIX = 1620000000;

    private static final String TEST_FROM_IDENTITY = "AAAAAAAA";
    private static final String TEST_TO_IDENTITY = "BBBBBBBB";
    private static final byte[] TEST_MESSAGE_ID = Utils.hexStringToByteArray("0001020304050607");
    private static final Date TEST_DATE = new Date(TEST_DATE_UNIX * 1000);
    private static final String TEST_NICKNAME = "Nickname";
    private static final byte[] TEST_METADATA_BOX = Utils.hexStringToByteArray("000102030405060708090a0b0c0d0e0f101112");
    private static final byte[] TEST_NONCE = Utils.hexStringToByteArray("000102030405060708090a0b0c0d0e0f1011121314151617");
    private static final byte[] TEST_BOX = Utils.hexStringToByteArray("000102030405060708090a0b0c0d0e0f10111213");

    private static final byte[] EXPECTED_BINARY = Utils.hexStringToByteArray(
        "414141414141414142424242424242420001020304050607003d8f6000001300" +
            "4e69636b6e616d65000000000000000000000000000000000000000000000000" +
            "000102030405060708090a0b0c0d0e0f101112000102030405060708090a0b0c" +
            "0d0e0f1011121314151617000102030405060708090a0b0c0d0e0f10111213"
    );

    @Test
    void testSerializeDeserialize() throws Exception {
        // Encode message and check expected binary output
        MessageBox testMessage = getTestMessage();

        byte[] binary = testMessage.makeBinary();
        Assertions.assertArrayEquals(EXPECTED_BINARY, binary);

        // Decode message again
        MessageBox decodedMessage = MessageBox.parseBinary(binary);

        // Check decoded fields
        Assertions.assertEquals(TEST_FROM_IDENTITY, decodedMessage.getFromIdentity());
        Assertions.assertEquals(TEST_TO_IDENTITY, decodedMessage.getToIdentity());
        Assertions.assertArrayEquals(TEST_MESSAGE_ID, decodedMessage.getMessageId().getMessageId());
        Assertions.assertEquals(TEST_DATE, decodedMessage.getDate());
        Assertions.assertEquals(0, decodedMessage.getFlags());
        Assertions.assertEquals(TEST_NICKNAME, decodedMessage.getPushFromName());
        Assertions.assertArrayEquals(TEST_METADATA_BOX, decodedMessage.getMetadataBox().getBox());
        Assertions.assertArrayEquals(TEST_NONCE, decodedMessage.getNonce());
        Assertions.assertArrayEquals(TEST_BOX, decodedMessage.getBox());
    }

    @Test
    void testJavaSerializable() {
        // Ensure BoxedMessage objects are Serializable
        assertDoesNotThrow(() -> {
            MessageBox testMessage = getTestMessage();
            ObjectOutputStream oos = new ObjectOutputStream(new NullOutputStream());
            oos.writeObject(testMessage);
            oos.close();
        });
    }

    private MessageBox getTestMessage() throws ThreemaException {
        // Encode message and check expected binary output
        MessageBox testMessage = new MessageBox();

        MessageId messageId = new MessageId(TEST_MESSAGE_ID);

        testMessage.setFromIdentity(TEST_FROM_IDENTITY);
        testMessage.setToIdentity(TEST_TO_IDENTITY);
        testMessage.setMessageId(messageId);
        testMessage.setDate(TEST_DATE);
        testMessage.setFlags(0);
        testMessage.setPushFromName(TEST_NICKNAME);
        testMessage.setMetadataBox(new MetadataBox(TEST_METADATA_BOX));
        testMessage.setNonce(TEST_NONCE);
        testMessage.setBox(TEST_BOX);

        return testMessage;
    }
}
