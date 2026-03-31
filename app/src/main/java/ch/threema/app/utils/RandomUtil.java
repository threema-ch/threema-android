package ch.threema.app.utils;

import ch.threema.base.utils.UnsignedHelper;

import static ch.threema.common.SecureRandomExtensionsKt.generateRandomBytes;
import static ch.threema.common.SecureRandomExtensionsKt.secureRandom;

public class RandomUtil {
    /**
     * Generate between `minBytes` (inclusive) and `maxBytes` (inclusive) random bytes.
     */
    public static byte[] generateRandomPadding(int minBytes, int maxBytes) {
        int count = secureRandom().nextInt(maxBytes + 1 - minBytes) + minBytes;
        return generateRandomBytes(secureRandom(), count);
    }

    /**
     * Generate a random unsigned 32 integer (packed into a non-negative long, because Java)
     */
    public static long generateRandomU32() {
        return UnsignedHelper.getUnsignedInt(secureRandom().nextInt());
    }
}
