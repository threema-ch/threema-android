/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

import java.math.BigInteger;

public class UnsignedHelper {
    /**
     * Convert a signed int to an unsigned long.
     */
    public static long getUnsignedInt(int val) {
        return val & 0x00000000ffffffffL;
    }

    /**
     * Convert a "unsigned" long to a BigInteger.
     */
    public static BigInteger unsignedLongToBigInteger(long val) {
        if (val >= 0L) {
            return BigInteger.valueOf(val);
        } else {
            int upper = (int) (val >>> 32);
            int lower = (int) val;
            return (BigInteger.valueOf(Integer.toUnsignedLong(upper))).shiftLeft(32).
                add(BigInteger.valueOf(Integer.toUnsignedLong(lower)));
        }
    }
}
