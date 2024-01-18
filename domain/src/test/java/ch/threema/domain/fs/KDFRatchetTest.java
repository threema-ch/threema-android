/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
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

package ch.threema.domain.fs;

import org.junit.Assert;
import org.junit.Test;

public class KDFRatchetTest {

	private static final byte[] INITIAL_CHAIN_KEY = new byte[] {(byte)0x42, (byte)0x1e, (byte)0x73, (byte)0xcf, (byte)0x32, (byte)0x47, (byte)0x85, (byte)0xde, (byte)0xe4, (byte)0xc4, (byte)0x83, (byte)0x0f, (byte)0x2e, (byte)0xfb, (byte)0xb8, (byte)0xcd, (byte)0x4b, (byte)0x25, (byte)0x8e, (byte)0xd8, (byte)0x52, (byte)0x0a, (byte)0x60, (byte)0x8a, (byte)0x6c, (byte)0xe3, (byte)0x40, (byte)0xaa, (byte)0xa7, (byte)0x40, (byte)0x00, (byte)0x24};
	private static final byte[] EXPECTED_ENCRYPTION_KEY_1 = new byte[] {(byte)0x60, (byte)0xd3, (byte)0xde, (byte)0x2d, (byte)0x84, (byte)0x9f, (byte)0xa8, (byte)0xb3, (byte)0xd9, (byte)0x79, (byte)0x9e, (byte)0x8e, (byte)0x50, (byte)0xb0, (byte)0x9a, (byte)0x7e, (byte)0xf1, (byte)0xd4, (byte)0xe1, (byte)0xe8, (byte)0x55, (byte)0xc9, (byte)0x9f, (byte)0xdb, (byte)0x71, (byte)0x1b, (byte)0xfe, (byte)0x29, (byte)0x46, (byte)0x6c, (byte)0xda, (byte)0xd3};
	private static final byte[] EXPECTED_ENCRYPTION_KEY_100 = new byte[] {(byte)0xd2, (byte)0xac, (byte)0xf6, (byte)0xe1, (byte)0xc7, (byte)0x26, (byte)0x2e, (byte)0xb3, (byte)0x60, (byte)0xad, (byte)0x92, (byte)0xb6, (byte)0xa7, (byte)0x4e, (byte)0x0f, (byte)0x9a, (byte)0xd6, (byte)0xee, (byte)0x12, (byte)0x92, (byte)0x78, (byte)0x74, (byte)0x2f, (byte)0xc2, (byte)0x46, (byte)0x2a, (byte)0xe7, (byte)0x29, (byte)0x16, (byte)0xea, (byte)0xa3, (byte)0x34, };
	private static final long TOO_MANY_TURNS = 1000000;

	@Test
	public void testTurnOnce() {
		KDFRatchet ratchet = new KDFRatchet(0, INITIAL_CHAIN_KEY);
		ratchet.turn();

		Assert.assertEquals(1, ratchet.getCounter());
		Assert.assertArrayEquals(EXPECTED_ENCRYPTION_KEY_1, ratchet.getCurrentEncryptionKey());
	}

	@Test
	public void testTurnMany() throws KDFRatchet.RatchetRotationException {
		KDFRatchet ratchet = new KDFRatchet(0, INITIAL_CHAIN_KEY);
		ratchet.turnUntil(100);

		Assert.assertEquals(100, ratchet.getCounter());
		Assert.assertArrayEquals(EXPECTED_ENCRYPTION_KEY_100, ratchet.getCurrentEncryptionKey());
	}

	@Test
	public void testTurnTooMany() {
		KDFRatchet ratchet = new KDFRatchet(0, INITIAL_CHAIN_KEY);

		Assert.assertThrows(KDFRatchet.RatchetRotationException.class, () -> {
			ratchet.turnUntil(TOO_MANY_TURNS);
		});
	}
}
