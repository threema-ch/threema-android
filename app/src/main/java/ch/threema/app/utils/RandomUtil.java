package ch.threema.app.utils;

import java.security.SecureRandom;

import ch.threema.base.utils.UnsignedHelper;

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
