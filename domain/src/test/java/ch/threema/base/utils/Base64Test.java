package ch.threema.base.utils;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Random;

import ch.threema.base.utils.Base64;

public class Base64Test {
    @Test
    public void testRoundtripEncodeDecode() throws IOException {
        // Encoding something and decoding the result should return the original value.
        for (int i = 0; i < 1000; i++) {
            // Note: We don't need a CSRNG here
            final Random rd = new Random();
            final byte[] bytes = new byte[i + 1];
            rd.nextBytes(bytes);
            final String encoded = Base64.encodeBytes(bytes);
            final byte[] decoded = Base64.decode(encoded);
            Assert.assertArrayEquals(bytes, decoded);
        }
    }

    @Test
    public void testEncodeMatchesJavaBase64() {
        // Java ships a Base64 implementation starting with Java 8.
        // We can't use it in Android because it requires API level >=26,
        // but we can use it to validate our own class.
        // Encoding something and decoding the result should return the original value.
        for (int i = 0; i < 5000; i++) {
            // Note: We don't need a CSRNG here
            final Random rd = new Random();
            final byte[] bytes = new byte[i + 1];
            rd.nextBytes(bytes);
            final String encodedThreema = Base64.encodeBytes(bytes);
            final String encodedJava = java.util.Base64.getEncoder().encodeToString(bytes);
            Assert.assertEquals(encodedThreema, encodedJava);
        }
    }
}
