/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2020-2021 Threema GmbH
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

package ch.threema.client.voip;

import ch.threema.client.voip.features.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public class FeatureListTest {
	@Test
	public void testToJsonEmpty() throws JSONException {
		final String json = new FeatureList().toJSON().toString();
		Assert.assertEquals("{}", json);
	}

	@Test
	public void testToJsonSingle() throws JSONException {
		final String json = new FeatureList()
			.addFeature(new SimpleCallFeature("foo"))
			.toJSON()
			.toString();
		Assert.assertEquals("{\"foo\":null}", json);
	}

	@Test
	public void testToJsonWithParams() throws JSONException {
		final String json = new FeatureList()
			.addFeature(new UnknownCallFeature("bar", new JSONObject()))
			.toJSON()
			.toString();
		Assert.assertEquals("{\"bar\":{}}", json);
	}

	@Test
	public void testToJsonWithComplexParams() throws JSONException {
		final JSONObject values = new JSONObject();
		values.put("a", 42);
		values.put("b", true);
		final String json = new FeatureList()
			.addFeature(new UnknownCallFeature("values", values))
			.toJSON()
			.toString();
		Assert.assertEquals("{\"values\":{\"a\":42,\"b\":true}}", json);
	}

	@Test
	public void parseEmpty() throws JSONException {
		final JSONObject o = new JSONObject();
		final FeatureList list = FeatureList.parse(o);
		Assert.assertTrue(list.isEmpty());
	}

	@Test
	public void parseVideo() throws JSONException {
		final JSONObject o = new JSONObject();
		o.put("video", JSONObject.NULL);
		final FeatureList list = FeatureList.parse(o);
		Assert.assertEquals(1, list.size());
		Assert.assertTrue(list.getList().get(0) instanceof VideoFeature);
	}

	@Test
	public void parseUnknownNull() throws JSONException {
		final JSONObject o = new JSONObject();
		o.put("asdf", JSONObject.NULL);
		final FeatureList list = FeatureList.parse(o);
		Assert.assertEquals(1, list.size());
		final CallFeature feature = list.getList().get(0);
		Assert.assertTrue(feature instanceof UnknownCallFeature);
		Assert.assertEquals("asdf", feature.getName());
		Assert.assertNull(feature.getParams());
	}

	@Test
	public void parseUnknownEmpty() throws JSONException {
		final JSONObject o = new JSONObject();
		o.put("asdf", new JSONObject());
		final FeatureList list = FeatureList.parse(o);
		Assert.assertEquals(1, list.size());
		final CallFeature feature = list.getList().get(0);
		Assert.assertTrue(feature instanceof UnknownCallFeature);
		Assert.assertEquals("asdf", feature.getName());
		Assert.assertEquals(0, feature.getParams().length());
	}

	@Test
	public void parseUnknownWithParams() throws JSONException {
		final JSONObject o = new JSONObject();
		final JSONObject p = new JSONObject();
		p.put("xyz", false);
		p.put("aaa", 123);
		o.put("asdf", p);
		final FeatureList list = FeatureList.parse(o);
		Assert.assertEquals(1, list.size());
		final CallFeature feature = list.getList().get(0);
		Assert.assertTrue(feature instanceof UnknownCallFeature);
		Assert.assertEquals("asdf", feature.getName());
		Assert.assertEquals(2, feature.getParams().length());
	}

	/**
	 * Smoke test.
	 */
	@Test
	public void parseMixed() throws JSONException {
		final JSONObject o = new JSONObject();

		final JSONObject p = new JSONObject();
		p.put("xyz", false);
		p.put("aaa", 123);

		o.put("unknown-null", JSONObject.NULL);
		o.put("video", JSONObject.NULL);
		o.put("unknown-empty", new JSONObject());
		o.put("unknown-params", p);

		final FeatureList list = FeatureList.parse(o);
		Assert.assertEquals(4, list.size());
	}

	@Test
	public void toStringEmpty() {
		final String string = new FeatureList().toString();
		Assert.assertEquals("FeatureList[]", string);
	}

	@Test
	public void toStringMixed() throws JSONException {
		final JSONObject params = new JSONObject();
		params.put("a", 3);
		params.put("b", false);
		final String string = new FeatureList()
			.addFeature(new VideoFeature())
			.addFeature(new UnknownCallFeature("hullo", params))
			.toString();
		Assert.assertEquals("FeatureList[video, hullo({\"a\":3,\"b\":false})]", string);
	}

	@Test
	public void hasFeature() throws JSONException {
		final JSONObject params = new JSONObject();
		params.put("a", 3);
		params.put("b", false);

		final FeatureList features = new FeatureList()
			.addFeature(new VideoFeature())
			.addFeature(new UnknownCallFeature("hullo", params))
			.addFeature(new SimpleCallFeature("argh"));
		Assert.assertTrue(features.hasFeature("video"));
		Assert.assertTrue(features.hasFeature("hullo"));
		Assert.assertTrue(features.hasFeature("argh"));
		Assert.assertFalse(features.hasFeature("arg"));
		Assert.assertFalse(features.hasFeature(""));
		Assert.assertFalse(features.hasFeature("videoo"));
	}
}
