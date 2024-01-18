/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

package ch.threema.base.crypto;

import org.junit.Assert;
import org.junit.Test;

import ch.threema.base.crypto.ThreemaKDF;
import ch.threema.base.utils.Utils;

public class ThreemaKDFTest {
	// First three vectors taken from multidevice-kdf/test-vectors-blake2b.csv
	public static TestVector[] TEST_VECTORS = {
		new TestVector(
			"101692161c717bc3fe893b3dbcfe7424c725fd06624940a1046895fb83960240",
			"492e",
			"a390519d083d07c5",
			"ae810e70c16cc45692c1d4fedf323ca2ca0218d90dc0f969ab1a7aeb6d3039a8"
		),
		new TestVector(
			"f8e2fcb4369c164e1cdfff82cb7a2c970f1b9a1553c143bf6aef588c1343c2da",
			"aaf4ad",
			"9f4c909b3b27f8e5",
			"5f316d0be440fc40b60bd1c90aab60f1de6f9e2de57d9d0f24b3a3fa02eda76a"
		),
		new TestVector(
			"88f7c68a72c76747494fec2d9783e2948906d86b2458818b7e9ee7fce856cb72",
			"00",
			"ef2a504bdb217992",
			"b4da86a0622262e4f8f8bce44aa6a3ee994b61997c1895af2803a98901914cc0"
		)
	};

	@Test
	public void testDeriveKey() {
		for (TestVector testVector : TEST_VECTORS) {
			ThreemaKDF kdf = new ThreemaKDF(testVector.personal);
			byte[] derived = kdf.deriveKey(testVector.salt, testVector.secretKey);
			Assert.assertArrayEquals(testVector.derived, derived);
		}
	}

	static public class TestVector {
		public final byte[] secretKey;
		public final byte[] salt;
		public final byte[] personal;
		public final byte[] derived;

		public TestVector(String secretKey, String salt, String personal, String derived) {
			this.secretKey = Utils.hexStringToByteArray(secretKey);
			this.salt = Utils.hexStringToByteArray(salt);
			this.personal = Utils.hexStringToByteArray(personal);
			this.derived = Utils.hexStringToByteArray(derived);
		}
	}
}
