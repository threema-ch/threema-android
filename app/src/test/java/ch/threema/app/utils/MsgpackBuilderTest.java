package ch.threema.app.utils;


import org.junit.Test;

import java.util.Arrays;

import ch.threema.app.webclient.converter.MsgpackArrayBuilder;
import ch.threema.app.webclient.converter.MsgpackBuilder;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.base.utils.Utils;

import static junit.framework.Assert.*;

public class MsgpackBuilderTest {

    private static String getMessage(byte[] data) {
        return "Result was "
            //+ Base64.encodeToString(data, Base64.DEFAULT)
            + " (use https://sugendran.github.io/msgpack-visualizer/ to debug)";
    }

    private void doTest(MsgpackBuilder builder, byte[] expected) {
        final byte[] result = builder.consume().array();
        assertEquals(
            getMessage(result),
            Arrays.toString(expected),
            Arrays.toString(result)
        );
    }

    /**
     * Test with a hex string
     * to generate use https://msgpack.org/ (click Try!)
     *
     * @param builder
     * @param hexString
     */
    private void doTest(MsgpackBuilder builder, String hexString) {
        doTest(builder,
            //replace spaces and convert
            Utils.hexStringToByteArray(hexString.replace(" ", "").trim()));
    }

    @Test
    public void testObjectInArray() {
        doTest(
            (new MsgpackArrayBuilder())
                .put("value1")
                .put((new MsgpackObjectBuilder())
                    .put("object.value", 2)),
            "92 a6 76 61 6c 75 65 31 81 ac 6f 62 6a 65 63 74 2e 76 61 6c 75 65 02");
    }

    @Test
    public void testArrayInObject() {
        doTest(
            (new MsgpackObjectBuilder())
                .put("key1", "value1")
                .put("array1", (new MsgpackArrayBuilder())
                    .put("arrayValue1")
                    .put("arrayValue2")),
            "82 a4 6b 65 79 31 a6 76 61 6c 75 65 31 a6 61 72 72 61 79 31 92 ab 61 72 72 61 79 56 61 6c 75 65 31 ab 61 72 72 61 79 56 61 6c 75 65 32");
    }


}
