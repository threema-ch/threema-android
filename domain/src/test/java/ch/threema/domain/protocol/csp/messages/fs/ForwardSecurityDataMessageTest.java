/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2023 Threema GmbH
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
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.protobuf.csp.e2e.fs.ForwardSecurityEnvelope;

public class ForwardSecurityDataMessageTest {
	static final DHSessionId TEST_SESSION_ID = new DHSessionId();
	static final ForwardSecurityEnvelope.Message.DHType TEST_DH_TYPE = ForwardSecurityEnvelope.Message.DHType.FOURDH;
	static final long TEST_COUNTER = 1;
	static final byte[] TEST_MESSAGE = new byte[] { 0x01, (byte)0x48, (byte)0x65, (byte)0x6c, (byte)0x6c, (byte)0x6f};

	static final ForwardSecurityEnvelope TEST_PROTOBUF_MESSAGE = ForwardSecurityEnvelope.newBuilder()
		.setSessionId(ByteString.copyFrom(TEST_SESSION_ID.get()))
		.setMessage(ForwardSecurityEnvelope.Message.newBuilder()
			.setDhType(TEST_DH_TYPE)
			.setCounter(TEST_COUNTER)
			.setMessage(ByteString.copyFrom(TEST_MESSAGE))
			.build())
		.build();

	static void assertEqualsTestProperties(ForwardSecurityDataMessage data) {
		Assert.assertEquals(TEST_SESSION_ID, data.getSessionId());
		Assert.assertEquals(TEST_DH_TYPE, data.getType());
		Assert.assertEquals(TEST_COUNTER, data.getCounter());
		Assert.assertArrayEquals(TEST_MESSAGE, data.getMessage());
	}

	public ForwardSecurityDataMessage makeForwardSecurityDataMessage() {
		return new ForwardSecurityDataMessage(TEST_SESSION_ID, TEST_DH_TYPE, TEST_COUNTER, TEST_MESSAGE);
	}

	@Test
	public void testValidData() {
		final ForwardSecurityDataMessage data = makeForwardSecurityDataMessage();
		assertEqualsTestProperties(data);
	}

	@Test
	public void testFromProtobuf() throws BadMessageException {
		final byte[] rawMessage = TEST_PROTOBUF_MESSAGE.toByteArray();
		final ForwardSecurityDataMessage data = (ForwardSecurityDataMessage) ForwardSecurityDataMessage.fromProtobuf(rawMessage);
		assertEqualsTestProperties(data);
	}

	@Test
	public void testToProtobufMessage() {
		final ForwardSecurityDataMessage data = makeForwardSecurityDataMessage();
		final ForwardSecurityEnvelope generatedProtobufMessage = data.toProtobufMessage();

		Assert.assertEquals(TEST_PROTOBUF_MESSAGE, generatedProtobufMessage);
	}
}
