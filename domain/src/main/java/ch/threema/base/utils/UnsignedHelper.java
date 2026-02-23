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
