package ch.threema.base.utils;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

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
