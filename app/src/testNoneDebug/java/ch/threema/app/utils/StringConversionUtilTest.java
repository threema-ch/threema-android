/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class StringConversionUtilTest {

	@Test
	@SuppressWarnings("RedundantArrayCreation")
	public void testJoin() {
		assertEquals("1, 2, 3", StringConversionUtil.join(", ", "1", "2", "3"));
		assertEquals("1, 2, 3", StringConversionUtil.join(", ", new String[] {"1", "2", "3"}));
		assertEquals("a/b/cde", StringConversionUtil.join("/", "a", "b", "cde"));
	}

	@Test
	public void testJoinIgnoreEmpty() {
		assertEquals("1,3", StringConversionUtil.join(",", "1", "", "3", null));
		assertEquals("", StringConversionUtil.join(",", ""));
	}

	@Test
	public void testXDigit() {
		assertEquals("0000000009", StringConversionUtil.xDigit(9, 10));
		assertEquals("0000000123", StringConversionUtil.xDigit(123, 10));
		assertEquals("123456789", StringConversionUtil.xDigit(123456789, 3));
	}

	@Test
	public void testSecondsToString() {
		assertEquals("00:01", StringConversionUtil.secondsToString(1, false));
		assertEquals("01:01", StringConversionUtil.secondsToString(61, false));
		assertEquals("01:01:01", StringConversionUtil.secondsToString(3661, false));

		assertEquals("00:00:01", StringConversionUtil.secondsToString(1, true));
		assertEquals("00:01:01", StringConversionUtil.secondsToString(61, true));
		assertEquals("01:01:01", StringConversionUtil.secondsToString(3661, true));
	}
}
