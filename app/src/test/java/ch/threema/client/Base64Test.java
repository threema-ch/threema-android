/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2020-2021 Threema GmbH
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

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Random;

public class Base64Test {
	@Test
	public void testRoundtripEncodeDecode() throws IOException {
		// Encoding something and decoding the result should return the original value.
		for (int i = 0; i < 1000; i++) {
			// Note: We don't need a CSRNG here
			final Random rd = new Random();
			final byte[] bytes = new byte[i + 1];
			rd.nextBytes(bytes);
			final String encoded = Base64.encodeBytes(bytes);
			final byte[] decoded = Base64.decode(encoded);
			Assert.assertArrayEquals(bytes, decoded);
		}
	}

	@Test
	public void testEncodeMatchesJavaBase64() {
		// Java ships a Base64 implementation starting with Java 8.
		// We can't use it in Android because it requires API level >=26,
		// but we can use it to validate our own class.
		// Encoding something and decoding the result should return the original value.
		for (int i = 0; i < 5000; i++) {
			// Note: We don't need a CSRNG here
			final Random rd = new Random();
			final byte[] bytes = new byte[i + 1];
			rd.nextBytes(bytes);
			final String encodedThreema = Base64.encodeBytes(bytes);
			final String encodedJava = java.util.Base64.getEncoder().encodeToString(bytes);
			Assert.assertEquals(encodedThreema, encodedJava);
		}
	}
}
