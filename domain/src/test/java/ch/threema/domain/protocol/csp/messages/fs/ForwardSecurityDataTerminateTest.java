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
import ch.threema.protobuf.csp.e2e.fs.Envelope;
import ch.threema.protobuf.csp.e2e.fs.Terminate;

public class ForwardSecurityDataTerminateTest {
	private static final DHSessionId TEST_SESSION_ID = new DHSessionId();
	private static final Terminate.Cause TEST_CAUSE = Terminate.Cause.UNKNOWN_SESSION;

	private static final Envelope TEST_PROTOBUF_MESSAGE = Envelope.newBuilder()
		.setSessionId(ByteString.copyFrom(TEST_SESSION_ID.get()))
		.setTerminate(Terminate.newBuilder()
			.build())
		.build();

	private static void assertEqualsTestProperties(ForwardSecurityDataTerminate data) {
		Assert.assertEquals(TEST_SESSION_ID, data.getSessionId());
	}

	@Test
	public void testValidData() {
		final ForwardSecurityDataTerminate data = new ForwardSecurityDataTerminate(TEST_SESSION_ID, TEST_CAUSE);
		assertEqualsTestProperties(data);
	}

	@Test
	public void testFromProtobuf() throws BadMessageException {
		final byte[] rawMessage = TEST_PROTOBUF_MESSAGE.toByteArray();
		final ForwardSecurityDataTerminate data = (ForwardSecurityDataTerminate) ForwardSecurityData.fromProtobuf(rawMessage);
		assertEqualsTestProperties(data);
	}

	@Test
	public void testToProtobufMessage() {
		final ForwardSecurityDataTerminate data = new ForwardSecurityDataTerminate(TEST_SESSION_ID, TEST_CAUSE);
		final Envelope generatedProtobufMessage = data.toProtobufMessage();

		Assert.assertEquals(TEST_PROTOBUF_MESSAGE, generatedProtobufMessage);
	}
}
