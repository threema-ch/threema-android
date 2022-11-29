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

package ch.threema.domain.protocol.csp.messages.group;

import ch.threema.base.ThreemaException;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException;

import org.junit.Assert;
import org.junit.Test;

import static ch.threema.domain.testhelpers.TestHelpers.*;

public class GroupJoinResponseMessageTest {

	static GroupJoinResponseData getDataTestInstance() throws BadMessageException {
		return new GroupJoinResponseData(
			GroupJoinResponseDataTest.TEST_TOKEN_VALID,
			GroupJoinResponseDataTest.TEST_RESPONSE
		);
	}
	static GroupJoinResponseMessage getMessageTestInstance() throws BadMessageException {
		final GroupJoinResponseMessage msg = new GroupJoinResponseMessage(getDataTestInstance());
		setMessageDefaults(msg);
		return msg;
	}

	@Test
	public void makeBoxTest() throws ThreemaException, BadMessageException {
		final MessageBox boxedMessage = boxMessage(getMessageTestInstance());
		Assert.assertNotNull(boxedMessage);
	}

	@Test
	public void decodeFromBoxTest() throws ThreemaException, BadMessageException, MissingPublicKeyException {
		final MessageBox boxedMessage = boxMessage(getMessageTestInstance());

		final AbstractMessage decodedMessage = decodeMessageFromBox(boxedMessage);
		Assert.assertTrue(decodedMessage instanceof GroupJoinResponseMessage);
		final GroupJoinResponseMessage msg = (GroupJoinResponseMessage) decodedMessage;
		Assert.assertEquals(msg.getData(), getDataTestInstance());
	}
}
