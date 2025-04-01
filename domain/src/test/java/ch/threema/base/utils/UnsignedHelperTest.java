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

package ch.threema.base.utils;

import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Random;

import ch.threema.base.utils.UnsignedHelper;

public class UnsignedHelperTest {
    @Test
    public void testGetUnsignedInt() {
        Assert.assertEquals(0L, UnsignedHelper.getUnsignedInt(0));
        Assert.assertEquals(1L, UnsignedHelper.getUnsignedInt(1));
        Assert.assertEquals((1L << 15) - 1, UnsignedHelper.getUnsignedInt(32767));
        Assert.assertEquals((1L << 31) - 1, UnsignedHelper.getUnsignedInt(2147483647));
        Assert.assertEquals((1L << 32) - 1, UnsignedHelper.getUnsignedInt(-1));
        Assert.assertEquals((1L << 31), UnsignedHelper.getUnsignedInt(-2147483648));
    }

    @Test
    public void testGetUnsignedIntNeverNegative() {
        final Random random = new Random();
        for (int i = 0; i < 10000; i++) {
            Assert.assertTrue(UnsignedHelper.getUnsignedInt(random.nextInt()) >= 0);
        }
    }

    @Test
    public void testUnsignedLongToBigInteger() {
        final BigInteger bigInteger = new BigInteger("18446744073709551610");
        final long unsignedLong = bigInteger.longValue();
        Assert.assertTrue(unsignedLong < 0);
        final BigInteger bigInteger2 = UnsignedHelper.unsignedLongToBigInteger(unsignedLong);
        Assert.assertEquals(bigInteger, bigInteger2);
    }
}
