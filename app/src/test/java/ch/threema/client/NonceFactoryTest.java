/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2017-2021 Threema GmbH
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

package ch.threema.client;
import static org.mockito.Mockito.*;

import ch.threema.base.ThreemaException;
import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;

public class NonceFactoryTest {

	// Hack to "Mock" the Secure Random
	private class SecureRandomMocken extends SecureRandom {
		private byte[][] next;
		private int nextPos = 0;

		public void nextNonces(byte[]... next) {
			this.next = next;
		}

		@Override
		public void nextBytes(byte[] bytes) {
			// Check length
			Assert.assertEquals(bytes.length, 24);

			if (this.next != null && this.nextPos < this.next.length) {
				for(int n = 0; n < bytes.length; n++) {
					bytes[n] = this.next[this.nextPos][n];
				}
				this.nextPos++;
				return;
			}

			super.nextBytes(bytes);
		}
	}

	@Test
	public void testNext() throws Exception {
		NonceStoreInterface nonceStoreMock = mock(NonceStoreInterface.class);
		SecureRandomMocken secureRandomMock = new SecureRandomMocken();

		// Store always return true
		when(nonceStoreMock.store(any())).thenReturn(true);

		NonceFactory factory = new NonceFactory(secureRandomMock, nonceStoreMock);
		byte[] result = factory.next();

		// Check if store is called
		verify(nonceStoreMock, times(1)).store(any());

		// Verify the result
		Assert.assertEquals(24, result.length);
	}

	@Test
	public void testNext2Times() throws Exception {
		NonceStoreInterface nonceStoreMock = mock(NonceStoreInterface.class);
		SecureRandomMocken secureRandomMock = new SecureRandomMocken();

		byte[] existingNonce = new byte[]{0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,
				0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,
				0x01,0x01,0x01,0x01};

		byte[] newNonce = new byte[]{0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,
				0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,
				0x01,0x01,0x01,0x02};

		// Store always return true
		when(nonceStoreMock.store(eq(existingNonce))).thenReturn(false);
		when(nonceStoreMock.store(eq(newNonce))).thenReturn(true);

		NonceFactory factory = new NonceFactory(secureRandomMock, nonceStoreMock);
		secureRandomMock.nextNonces(existingNonce, newNonce);
		byte[] result = factory.next();

		// Check if store is called twice
		verify(nonceStoreMock, times(2)).store(any());

		// Verify the result
		Assert.assertEquals(24, result.length);
	}


	@Test(expected=ThreemaException.class)
	public void testNextXTimes() throws Exception {
		NonceStoreInterface nonceStoreMock = mock(NonceStoreInterface.class);

		// Store always return true
		when(nonceStoreMock.store(any())).thenReturn(false);

		NonceFactory factory = new NonceFactory(new SecureRandom(), nonceStoreMock);
		factory.next();
	}

	@Test
	public void testNextWithoutStore() throws Exception {
		NonceStoreInterface nonceStoreMock = mock(NonceStoreInterface.class);

		NonceFactory factory = new NonceFactory(new SecureRandom(), nonceStoreMock);
		factory.next(false);

		verify(nonceStoreMock, never()).store(any());
	}

	@Test
	public void textNextWithoutStore() throws Exception {
		byte[] existingNonce = new byte[]{0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,
				0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,0x01,
				0x01,0x01,0x01,0x01};

		NonceStoreInterface nonceStoreMock = mock(NonceStoreInterface.class);
		when(nonceStoreMock.exists(eq(existingNonce))).thenReturn(true);
		NonceFactory factory = new NonceFactory(new SecureRandom(), nonceStoreMock);
		Assert.assertTrue(factory.exists(existingNonce));


		when(nonceStoreMock.exists(eq(existingNonce))).thenReturn(false);
		Assert.assertFalse(factory.exists(existingNonce));
	}

}
