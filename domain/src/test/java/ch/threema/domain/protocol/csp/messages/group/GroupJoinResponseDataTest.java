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

package ch.threema.domain.protocol.csp.messages.group;

import com.google.protobuf.ByteString;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.protobuf.Common;
import ch.threema.protobuf.csp.e2e.GroupJoinResponse;

public class GroupJoinResponseDataTest {
    static final GroupJoinResponseData.Response TEST_RESPONSE = new GroupJoinResponseData.Expired();
    static GroupInviteToken TEST_TOKEN_VALID;

    static {
        try {
            TEST_TOKEN_VALID = new GroupInviteToken(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15});
        } catch (GroupInviteToken.InvalidGroupInviteTokenException e) {
            e.printStackTrace();
        }
    }

    static final GroupJoinResponse TEST_PROTOBUF_MESSAGE = GroupJoinResponse.newBuilder()
        .setToken(ByteString.copyFrom(TEST_TOKEN_VALID.get()))
        .setResponse(GroupJoinResponse.Response.newBuilder().setExpired(Common.Unit.newBuilder()))
        .build();

    static final long TEST_GROUP_ID = 3;
    static final GroupJoinResponse TEST_PROTOBUF_MESSAGE_ACCEPT = GroupJoinResponse.newBuilder()
        .setToken(ByteString.copyFrom(TEST_TOKEN_VALID.get()))
        .setResponse(GroupJoinResponse.Response.newBuilder().setAccept(
            GroupJoinResponse.Response.Accept.newBuilder().setGroupId(TEST_GROUP_ID))
        )
        .build();


    static void assertEqualsTestProperties(GroupJoinResponseData data) {
        Assertions.assertEquals(TEST_TOKEN_VALID, data.getToken());
        Assertions.assertEquals(TEST_RESPONSE, data.getResponse());
    }

    @Test
    void testValidData() {
        final GroupJoinResponseData data = new GroupJoinResponseData(TEST_TOKEN_VALID, TEST_RESPONSE);
        assertEqualsTestProperties(data);
    }

    @Test
    void testFromProtobuf() throws BadMessageException {
        final byte[] rawMessage = TEST_PROTOBUF_MESSAGE.toByteArray();
        final GroupJoinResponseData data = GroupJoinResponseData.fromProtobuf(rawMessage);
        assertEqualsTestProperties(data);
    }

    @Test
    void testFromProtobufAccept() throws BadMessageException {
        final byte[] protobufRawMessage = TEST_PROTOBUF_MESSAGE_ACCEPT.toByteArray();
        final GroupJoinResponseData data = GroupJoinResponseData.fromProtobuf(protobufRawMessage);
        Assertions.assertEquals(TEST_TOKEN_VALID, data.getToken());
        Assertions.assertEquals(TEST_GROUP_ID, ((GroupJoinResponseData.Accept) data.getResponse()).getGroupId());
    }

    @Test
    void testToProtobufMessage() {
        final GroupJoinResponseData data = new GroupJoinResponseData(TEST_TOKEN_VALID, TEST_RESPONSE);
        final GroupJoinResponse generatedProtobufMessage = data.toProtobufMessage();

        Assertions.assertEquals(TEST_PROTOBUF_MESSAGE, generatedProtobufMessage);
    }

    @Test
    void testToProtobufMessageAccept() {
        final GroupJoinResponseData data = new GroupJoinResponseData(
            TEST_TOKEN_VALID,
            new GroupJoinResponseData.Accept(TEST_GROUP_ID)
        );
        final GroupJoinResponse generatedProtobufMessage = data.toProtobufMessage();

        Assertions.assertEquals(TEST_PROTOBUF_MESSAGE_ACCEPT, generatedProtobufMessage);
    }
}
