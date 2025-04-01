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

package ch.threema.app.voip.util;

import org.junit.Test;

import java.math.BigInteger;

import static junit.framework.Assert.assertEquals;

public class VoipStatsTest {
    @Test
    public void calculateVideoBitrate() {
        assertEquals(8000.0, VoipStats.Rtp.calculateVideoBitrate(
            0, BigInteger.valueOf(300),
            1000000, BigInteger.valueOf(1300)
        ));
        assertEquals(9600.0, VoipStats.Rtp.calculateVideoBitrate(
            1000000, BigInteger.valueOf(1300),
            2500000, BigInteger.valueOf(3100)
        ));
    }

    @Test(expected = RuntimeException.class)
    public void calculateVideoBitrateDivisionNoTimedelta() {
        // 0us timedelta
        VoipStats.Rtp.calculateVideoBitrate(
            0, BigInteger.valueOf(300),
            0, BigInteger.valueOf(1300)
        );
    }

    @Test(expected = RuntimeException.class)
    public void calculateVideoBitrateSmallTimedelta() {
        // 10ms timedelta
        VoipStats.Rtp.calculateVideoBitrate(
            0, BigInteger.valueOf(1000),
            10000, BigInteger.valueOf(2200)
        );
    }
}
