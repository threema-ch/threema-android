/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2022 Threema GmbH
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

public class ForwardSecurityDataAcceptTest {
	static final DHSessionId TEST_SESSION_ID = new DHSessionId();
	static final byte[] TEST_EPHEMERAL_PUBLIC_KEY = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31 };

	static final ForwardSecurityEnvelope TEST_PROTOBUF_MESSAGE = ForwardSecurityEnvelope.newBuilder()
		.setSessionId(ByteString.copyFrom(TEST_SESSION_ID.get()))
		.setAccept(ForwardSecurityEnvelope.Accept.newBuilder()
			.setEphemeralPublicKey(ByteString.copyFrom(TEST_EPHEMERAL_PUBLIC_KEY))
			.build())
		.build();

	static void assertEqualsTestProperties(ForwardSecurityDataAccept data) {
		Assert.assertEquals(TEST_SESSION_ID, data.getSessionId());
		Assert.assertArrayEquals(TEST_EPHEMERAL_PUBLIC_KEY, data.getEphemeralPublicKey());
	}

	@Test
	public void testValidData() throws ForwardSecurityData.InvalidEphemeralPublicKeyException {
		final ForwardSecurityDataAccept data = new ForwardSecurityDataAccept(TEST_SESSION_ID, TEST_EPHEMERAL_PUBLIC_KEY);
		assertEqualsTestProperties(data);
	}

	@Test
	public void testFromProtobuf() throws BadMessageException {
		final byte[] rawMessage = TEST_PROTOBUF_MESSAGE.toByteArray();
		final ForwardSecurityDataAccept data = (ForwardSecurityDataAccept) ForwardSecurityData.fromProtobuf(rawMessage);
		assertEqualsTestProperties(data);
	}

	@Test
	public void testToProtobufMessage() throws ForwardSecurityData.InvalidEphemeralPublicKeyException {
		final ForwardSecurityDataAccept data = new ForwardSecurityDataAccept(TEST_SESSION_ID, TEST_EPHEMERAL_PUBLIC_KEY);
		final ForwardSecurityEnvelope generatedProtobufMessage = data.toProtobufMessage();

		Assert.assertEquals(TEST_PROTOBUF_MESSAGE, generatedProtobufMessage);
	}
}
