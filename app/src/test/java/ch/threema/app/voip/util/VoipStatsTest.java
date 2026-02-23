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
