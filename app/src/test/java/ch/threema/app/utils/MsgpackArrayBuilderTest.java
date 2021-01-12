/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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
import ch.threema.app.webclient.converter.MsgpackArrayBuilder;
import ch.threema.client.Utils;

import static junit.framework.Assert.*;

public class MsgpackArrayBuilderTest {

	private MsgpackArrayBuilder builder;

	@Before
	public void setUp() throws Exception {
		this.builder = new MsgpackArrayBuilder();
	}

	private static String getMessage(byte[] data) {
		return "Result was "
				//+ Base64.encodeToString(data, Base64.DEFAULT)
				+ " (use https://sugendran.github.io/msgpack-visualizer/ to debug)";
	}

	private void doTest(MsgpackArrayBuilder arrayBuilder, byte[] expected) {
		final byte[] result = arrayBuilder.consume().array();
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
	 * @param arrayBuilder
	 * @param hexString
	 */
	private void doTest(MsgpackArrayBuilder arrayBuilder, String hexString) {
		doTest(arrayBuilder,
				//replace spaces and convert
				Utils.hexStringToByteArray(hexString.replace(" ", "").trim()));
	}

	@Test
	public void testSimpleString() {
		doTest(
				this.builder.put("value"),
				"91 a5 76 61 6c 75 65");
	}

	@Test
	public void testSimpleInteger() {
		doTest(
				this.builder.put(4),
				"91 04");
	}

	@Test
	public void testSimpleFloat() {
		doTest(
				this.builder.put(1.56),
				"91 cb 3f f8 f5 c2 8f 5c 28 f6");
	}

	@Test
	public void testSimpleDouble() {
		doTest(
				this.builder.put(0.100000000000000005551115123125782702118158340454101562),
				"91 cb 3f b9 99 99 99 99 99 9a");
	}

	@Test
	public void testSimpleBoolean() {
		doTest(
				this.builder.put(true),
				"91 c3");
	}

	@Test
	public void testSimpleLong() {
		doTest(
				this.builder.put(12345678901L),
				"91 cf 00 00 00 02 df dc 1c 35");
	}

	@Test
	public void testAllTypes() {
		doTest(
				this.builder
					.put(123)
					.put("test")
					.put(false)
					.put(1.23)
					.put(0.10000000000000000555111512312578270211815834045410156)
					.put(12345678901L),
				"96 7b a4 74 65 73 74 c2 cb 3f f3 ae 14 7a e1 47 ae cb 3f b9 99 99 99 99 99 9a cf 00 00 00 02 df dc 1c 35"
		);
	}

	@Test
	public void testNestedObjects() {
		doTest(
				this.builder
						.put("hello")
						.put((new MsgpackArrayBuilder())
								.put(1)
								.put("2")
								.put((new MsgpackArrayBuilder())
									.put("yes")))
						.put(true),
				"93 a5 68 65 6c 6c 6f 93 01 a1 32 91 a3 79 65 73 c3"
		);
	}

	@Test
	public void testDoubleConsume() {
		this.builder.consume();
		try {
			this.builder.consume();
			Assert.fail("Second MsgpackArrayBuilder.consume call should thrown a RuntimeException");
		}
		catch (RuntimeException x) {
			//success, consume double call not allowed
		}
	}
}
