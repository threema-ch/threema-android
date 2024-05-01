/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2024 Threema GmbH
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

package ch.threema.app.utils;

import java.security.SecureRandom;

import ch.threema.app.voip.util.UnsignedHelper;

public class RandomUtil {
	private final static SecureRandom secureRandom = new SecureRandom();

	/**
	 * Generate between `minBytes` (inclusive) and `maxBytes` (inclusive) random bytes.
	 */
	public static byte[] generateRandomPadding(int minBytes, int maxBytes) {
		int count = secureRandom.nextInt(maxBytes + 1 - minBytes) + minBytes;
		final byte[] bytes = new byte[count];
		secureRandom.nextBytes(bytes);
		return bytes;
	}

	/**
	 * Generate a random unsigned 32 integer (packed into a non-negative long, because Java)
	 */
	public static long generateRandomU32() {
		return UnsignedHelper.getUnsignedInt(secureRandom.nextInt());
	}
}
