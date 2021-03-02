/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2017-2021 Threema GmbH
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

package ch.threema.client;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

public class JSONUtilTest {

	@Test
	public void testGetStringOrNull() throws JSONException {
		final JSONObject o = new JSONObject();
		o.put("foo", "bar");
		o.put("baz", JSONObject.NULL);
		o.put("bla", 123);

		// Existing key
		Assert.assertEquals("bar", JSONUtil.getStringOrNull(o, "foo"));

		// Missing key
		Assert.assertNull(JSONUtil.getStringOrNull(o, "bar"));

		// Null value
		Assert.assertNull(JSONUtil.getStringOrNull(o, "baz"));

		// Non-String value
		Assert.assertNull(JSONUtil.getStringOrNull(o, "bla"));
	}

	@Test
	public void testGetIntegerOrNull() throws JSONException {
		final JSONObject o = new JSONObject();
		o.put("foo", 42);
		o.put("baz", JSONObject.NULL);
		o.put("bla", "123");

		// Existing key
		Assert.assertEquals(Integer.valueOf(42), JSONUtil.getIntegerOrNull(o, "foo"));

		// Missing key
		Assert.assertNull(JSONUtil.getIntegerOrNull(o, "bar"));

		// Null value
		Assert.assertNull(JSONUtil.getIntegerOrNull(o, "baz"));

		// Non-Integer value
		Assert.assertNull(JSONUtil.getIntegerOrNull(o, "bla"));
	}

	@Test
	public void testGetLongOrThrow() throws JSONException {
		final JSONObject o = new JSONObject();
		o.put("foo", 42); // Valid, in range
		o.put("bar", JSONObject.NULL); // Null
		o.put("baz", "123"); // Invalid type
		o.put("bigint", new BigInteger("9999999999999999999999")); // Out of range for a long

		// Valid, in range
		Assert.assertEquals(Long.valueOf(42), JSONUtil.getLongOrThrow(o, "foo"));

		// Null value
		Assert.assertNull(JSONUtil.getLongOrThrow(o, "bar"));

		// Missing value
		Assert.assertNull(JSONUtil.getLongOrThrow(o, "XYZ"));

		// Out of range
		try {
			Assert.assertNull(JSONUtil.getLongOrThrow(o, "bigint"));
			Assert.fail("Expected RuntimeException, but none thrown");
		} catch (RuntimeException e) {
			// OK
		}

		// Invalid type
		try {
			JSONUtil.getLongOrThrow(o, "baz");
			Assert.fail("Expected RuntimeException, but none thrown");
		} catch (RuntimeException e) {
			// OK
		}
	}

}
