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

package ch.threema.domain.protocol.csp.messages.protobuf;

import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;

public class AbstractProtobufMessageTest {

	@Test
	public void testGetBody() {
		final ProtobufDataInterface<?> protobufDataStub = Mockito.mock(ProtobufDataInterface.class, Mockito.RETURNS_DEEP_STUBS);
		Mockito.when(protobufDataStub.toProtobufBytes()).thenReturn(new byte[]{0, 1, 2});

		final AbstractProtobufMessage<ProtobufDataInterface<?>> message =
			new AbstractProtobufMessage<ProtobufDataInterface<?>>(3, protobufDataStub) {
			@Override
			public int getType() {
				return 0;
			}
		};

		assertArrayEquals(
			protobufDataStub.toProtobufBytes(),
			message.getBody()
		);
	}
}
