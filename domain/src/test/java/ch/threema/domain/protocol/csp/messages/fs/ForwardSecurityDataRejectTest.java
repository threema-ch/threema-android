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

import com.google.protobuf.ByteString;

import org.junit.Assert;
import org.junit.Test;

import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.protobuf.Common;
import ch.threema.protobuf.csp.e2e.fs.Envelope;
import ch.threema.protobuf.csp.e2e.fs.Reject;

public class ForwardSecurityDataRejectTest {
    private static final DHSessionId TEST_SESSION_ID = new DHSessionId();
    private static final MessageId TEST_REJECTED_MESSAGE_ID = new MessageId();
    private static final Reject.Cause TEST_CAUSE = Reject.Cause.UNKNOWN_SESSION;

    private static final Envelope TEST_PROTOBUF_MESSAGE = Envelope.newBuilder()
        .setSessionId(ByteString.copyFrom(TEST_SESSION_ID.get()))
        .setReject(Reject.newBuilder()
            .setMessageId(TEST_REJECTED_MESSAGE_ID.getMessageIdLong())
            .setGroupIdentity(Common.GroupIdentity.newBuilder().build())
            .setCause(TEST_CAUSE)
            .build())
        .build();

    private static void assertEqualsTestProperties(ForwardSecurityDataReject data) {
        Assert.assertEquals(TEST_SESSION_ID, data.getSessionId());
        Assert.assertEquals(TEST_REJECTED_MESSAGE_ID, data.getRejectedApiMessageId());
        Assert.assertEquals(TEST_CAUSE, data.getCause());
    }

    @Test
    public void testValidData() {
        final ForwardSecurityDataReject data = new ForwardSecurityDataReject(TEST_SESSION_ID, TEST_REJECTED_MESSAGE_ID, null, null, TEST_CAUSE);
        assertEqualsTestProperties(data);
    }

    @Test
    public void testFromProtobuf() throws BadMessageException {
        final byte[] rawMessage = TEST_PROTOBUF_MESSAGE.toByteArray();
        final ForwardSecurityDataReject data = (ForwardSecurityDataReject) ForwardSecurityData.fromProtobuf(rawMessage);
        assertEqualsTestProperties(data);
    }

    @Test
    public void testToProtobufMessage() {
        final ForwardSecurityDataReject data = new ForwardSecurityDataReject(TEST_SESSION_ID, TEST_REJECTED_MESSAGE_ID, null, null, TEST_CAUSE);
        final Envelope generatedProtobufMessage = data.toProtobufMessage();

        Assert.assertEquals(TEST_PROTOBUF_MESSAGE, generatedProtobufMessage);
    }
}
