package ch.threema.base.utils;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

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
}
