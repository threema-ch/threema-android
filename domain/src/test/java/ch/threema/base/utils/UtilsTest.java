/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2025 Threema GmbH
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

import java.util.Date;

public class UtilsTest {
    @Test
    public void truncateUTF8String() {
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

    @Test
    public void longToByteArrayBigEndian() {
        Assert.assertArrayEquals(
            new byte[]{0, 0, 0, 0, 0, 0, 0, (byte) 0x0A},
            Utils.longToByteArrayBigEndian(0x0AL)
        );

        Assert.assertArrayEquals(
            new byte[]{0, 0, 0, 0, 0, 0, 0, 0},
            Utils.longToByteArrayBigEndian(0L)
        );

        Assert.assertArrayEquals(
            new byte[]{(byte) 0x80, 0, 0, 0, 0, 0, 0, 0},
            Utils.longToByteArrayBigEndian(Long.MIN_VALUE)
        );

        Assert.assertArrayEquals(
            new byte[]{
                (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
            },
            Utils.longToByteArrayBigEndian(Long.MAX_VALUE)
        );

        Assert.assertArrayEquals(
            new byte[]{
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
            },
            Utils.longToByteArrayBigEndian(-1L)
        );
    }

    @Test
    public void bytesArrayToLongBigEndian() {
        Assert.assertEquals(
            0x0AL,
            Utils.byteArrayToLongBigEndian(new byte[]{0, 0, 0, 0, 0, 0, 0, (byte) 0x0A})
        );

        Assert.assertEquals(
            0L,
            Utils.byteArrayToLongBigEndian(new byte[]{0, 0, 0, 0, 0, 0, 0, 0})
        );

        Assert.assertEquals(
            Long.MIN_VALUE,
            Utils.byteArrayToLongBigEndian(new byte[]{(byte) 0x80, 0, 0, 0, 0, 0, 0, 0})
        );

        Assert.assertEquals(
            Long.MAX_VALUE,
            Utils.byteArrayToLongBigEndian(new byte[]{
                (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
            })
        );

        Assert.assertEquals(
            -1L,
            Utils.byteArrayToLongBigEndian(new byte[]{
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
            })
        );

        Assert.assertThrows("Cannot call byteArrayToLongBigEndian with 3-byte array", IllegalArgumentException.class, () -> {
            Utils.byteArrayToLongBigEndian(new byte[]{1, 2, 3});
        });
    }

    @Test
    public void longToByteArrayLittleEndian() {
        Assert.assertArrayEquals(
            new byte[]{(byte) 0x0A, 0, 0, 0, 0, 0, 0, 0},
            Utils.longToByteArrayLittleEndian(0x0AL)
        );

        Assert.assertArrayEquals(
            new byte[]{0, 0, 0, 0, 0, 0, 0, 0},
            Utils.longToByteArrayLittleEndian(0L)
        );

        Assert.assertArrayEquals(
            new byte[]{0, 0, 0, 0, 0, 0, 0, (byte) 0x80},
            Utils.longToByteArrayLittleEndian(Long.MIN_VALUE)
        );

        Assert.assertArrayEquals(
            new byte[]{
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x7F,
            },
            Utils.longToByteArrayLittleEndian(Long.MAX_VALUE)
        );

        Assert.assertArrayEquals(
            new byte[]{
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
            },
            Utils.longToByteArrayLittleEndian(-1L)
        );
    }

    @Test
    public void bytesArrayToLongLittleEndian() {
        Assert.assertEquals(
            0x0AL,
            Utils.byteArrayToLongLittleEndian(new byte[]{(byte) 0x0A, 0, 0, 0, 0, 0, 0, 0})
        );

        Assert.assertEquals(
            0L,
            Utils.byteArrayToLongLittleEndian(new byte[]{0, 0, 0, 0, 0, 0, 0, 0})
        );

        Assert.assertEquals(
            Long.MIN_VALUE,
            Utils.byteArrayToLongLittleEndian(new byte[]{0, 0, 0, 0, 0, 0, 0, (byte) 0x80})
        );

        Assert.assertEquals(
            Long.MAX_VALUE,
            Utils.byteArrayToLongLittleEndian(new byte[]{
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x7F,
            })
        );

        Assert.assertEquals(
            -1L,
            Utils.byteArrayToLongLittleEndian(new byte[]{
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            })
        );

        Assert.assertThrows("Cannot call byteArrayToLongLittleEndian with 7-byte array", IllegalArgumentException.class, () -> {
            Utils.byteArrayToLongLittleEndian(new byte[]{1, 2, 3, 4, 5, 6, 7});
        });
    }

    @Test
    public void getUnsignedTimestamp() {
        Assert.assertEquals(0, Utils.getUnsignedTimestamp(null));
        Assert.assertEquals(0, Utils.getUnsignedTimestamp(new Date(0)));
        Assert.assertEquals(0, Utils.getUnsignedTimestamp(new Date(-1)));
        Assert.assertEquals(0, Utils.getUnsignedTimestamp(new Date(-79200000)));
        Assert.assertEquals(0, Utils.getUnsignedTimestamp(new Date(Long.MIN_VALUE)));

        Assert.assertEquals(1, Utils.getUnsignedTimestamp(new Date(1)));
        Assert.assertEquals(1355270400000L, Utils.getUnsignedTimestamp(new Date(1355270400000L)));
        Assert.assertEquals(Long.MAX_VALUE, Utils.getUnsignedTimestamp(new Date(Long.MAX_VALUE)));
    }
}
