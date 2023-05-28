/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2023 Threema GmbH
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

package ch.threema.app.utils;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.base.utils.Utils;

import static junit.framework.Assert.assertEquals;


public class MsgpackObjectBuilderTest {

	private MsgpackObjectBuilder builder;

	@Before
	public void setUp() throws Exception {

		this.builder = new MsgpackObjectBuilder();
	}

	private static String getMessage(byte[] data) {
		return "Result was "
				//+ Base64.encodeToString(data, Base64.DEFAULT)
				+ " (use https://sugendran.github.io/msgpack-visualizer/ to debug)";
	}

	private void doTest(MsgpackObjectBuilder objectBuilder, byte[] expected) {
		final byte[] result = objectBuilder.consume().array();
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
	 * @param objectBuilder
	 * @param hexString
	 */
	private void doTest(MsgpackObjectBuilder objectBuilder, String hexString) {
		doTest(objectBuilder,
				//replace spaces and convert
				Utils.hexStringToByteArray(hexString.replace(" ", "").trim()));
	}

	@Test
	public void testSimpleString() {
		doTest(
				this.builder.put("string", "value"),
				"81 a6 73 74 72 69 6e 67 a5 76 61 6c 75 65");
	}

	@Test
	public void testSimpleInteger() {
		doTest(
				this.builder.put("integer", 43),
				"81 a7 69 6e 74 65 67 65 72 2b");
	}

	@Test
	public void testSimpleFloat() {
		doTest(
				this.builder.put("float", 1.56),
				"81 a5 66 6c 6f 61 74 cb 3f f8 f5 c2 8f 5c 28 f6");
	}

	@Test
	public void testSimpleDouble() {
		doTest(
				this.builder.put("double", 0.100000000000000005551115123125782702118158340454101562),
				"81 a6 64 6f 75 62 6c 65 cb 3f b9 99 99 99 99 99 9a");
	}

	@Test
	public void testSimpleBoolean() {
		doTest(
				this.builder.put("boolean", true),
				"81 a7 62 6f 6f 6c 65 61 6e c3");
	}

	@Test
	public void testSimpleLong() {
		doTest(
				this.builder.put("long", 12345678901L),
				"81 a4 6c 6f 6e 67 cf 00 00 00 02 df dc 1c 35");
	}

	@Test
	public void testAllTypes() {
		doTest(
				this.builder
					.put("int", 123)
					.put("string", "test")
					.put("boolean", false)
					.put("float", 1.23)
					.put("double", 0.10000000000000000555111512312578270211815834045410156)
					.put("long", 12345678901L),
				"86 a3 69 6e 74 7b a6 73 74 72 69 6e 67 a4 74 65 73 74 a7 62 6f 6f 6c 65 61 6e c2 a5 66 6c 6f 61 74 cb 3f f3 ae 14 7a e1 47 ae a6 64 6f 75 62 6c 65 cb 3f b9 99 99 99 99 99 9a a4 6c 6f 6e 67 cf 00 00 00 02 df dc 1c 35"
		);
	}

	@Test
	public void testNestedObjects() {
		doTest(
				this.builder
						.put("string", "hello")
						.put("inner", (new MsgpackObjectBuilder())
								.put("value1", 1)
								.put("value2", "2")
								.put("value3", (new MsgpackObjectBuilder())
									.put("value31", "yes")))
						.put("end", true),
				"83 a6 73 74 72 69 6e 67 a5 68 65 6c 6c 6f a5 69 6e 6e 65 72 83 a6 76 61 6c 75 65 31 01 a6 76 61 6c 75 65 32 a1 32 a6 76 61 6c 75 65 33 81 a7 76 61 6c 75 65 33 31 a3 79 65 73 a3 65 6e 64 c3"
		);
	}

	@Test
	public void testDoubleConsume() {
		this.builder.consume();
		try {
			this.builder.consume();
			Assert.fail("Second MsgpackObjectBuilder.consume call should thrown a RuntimeException");
		}
		catch (RuntimeException x) {
			//success, consume double call not allowed
		}
	}
}
