/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2016-2021 Threema GmbH
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

public class UtilsTest {
	@Test
	public void truncateUTF8String() throws Exception {
		Assert.assertEquals("0000000000111111111122222222223",
				Utils.truncateUTF8String("0000000000111111111122222222223", 32));
		Assert.assertEquals("hello my best friend",
				Utils.truncateUTF8String("hello my best friend", 32));
		Assert.assertEquals("coco",
				Utils.truncateUTF8String("coco", 4));

		//with multi byte characters
		Assert.assertEquals("0000000000111111111122222222223",
				Utils.truncateUTF8String("0000000000111111111122222222223Ç", 32));
		Assert.assertEquals("Aj aj aj Çoc",
				Utils.truncateUTF8String("Aj aj aj Çoco Jambo", 13));
		Assert.assertEquals("Çoc",
				Utils.truncateUTF8String("Çoco Jambo", 4));
	}

	@Test
	public void byteToHex() {
		for (int i = 0; i < 0xff; i++) {
			Assert.assertEquals(String.format("%02X", i), Utils.byteToHex((byte) i, true, false));
			Assert.assertEquals(String.format("%02x", i), Utils.byteToHex((byte) i, false, false));
			Assert.assertEquals(String.format("0x%02X", i), Utils.byteToHex((byte) i, true, true));
		}
	}
}
