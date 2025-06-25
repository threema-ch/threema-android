/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.voip;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import ch.threema.domain.protocol.csp.messages.voip.features.CallFeature;
import ch.threema.domain.protocol.csp.messages.voip.features.FeatureList;
import ch.threema.domain.protocol.csp.messages.voip.features.SimpleCallFeature;
import ch.threema.domain.protocol.csp.messages.voip.features.UnknownCallFeature;
import ch.threema.domain.protocol.csp.messages.voip.features.VideoFeature;

public class FeatureListTest {
    @Test
    void testToJsonEmpty() {
        final String json = new FeatureList().toJSON().toString();
        Assertions.assertEquals("{}", json);
    }

    @Test
    void testToJsonSingle() {
        final String json = new FeatureList()
            .addFeature(new SimpleCallFeature("foo"))
            .toJSON()
            .toString();
        Assertions.assertEquals("{\"foo\":null}", json);
    }

    @Test
    void testToJsonWithParams() {
        final String json = new FeatureList()
            .addFeature(new UnknownCallFeature("bar", new JSONObject()))
            .toJSON()
            .toString();
        Assertions.assertEquals("{\"bar\":{}}", json);
    }

    @Test
    void testToJsonWithComplexParams() throws JSONException {
        final JSONObject values = new JSONObject();
        values.put("a", 42);
        values.put("b", true);
        final String json = new FeatureList()
            .addFeature(new UnknownCallFeature("values", values))
            .toJSON()
            .toString();
        Assertions.assertEquals("{\"values\":{\"a\":42,\"b\":true}}", json);
    }

    @Test
    void parseEmpty() throws JSONException {
        final JSONObject o = new JSONObject();
        final FeatureList list = FeatureList.parse(o);
        Assertions.assertTrue(list.isEmpty());
    }

    @Test
    void parseVideo() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("video", JSONObject.NULL);
        final FeatureList list = FeatureList.parse(o);
        Assertions.assertEquals(1, list.size());
        Assertions.assertInstanceOf(VideoFeature.class, list.getList().get(0));
    }

    @Test
    void parseUnknownNull() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("asdf", JSONObject.NULL);
        final FeatureList list = FeatureList.parse(o);
        Assertions.assertEquals(1, list.size());
        final CallFeature feature = list.getList().get(0);
        Assertions.assertInstanceOf(UnknownCallFeature.class, feature);
        Assertions.assertEquals("asdf", feature.getName());
        Assertions.assertNull(feature.getParams());
    }

    @Test
    void parseUnknownEmpty() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("asdf", new JSONObject());
        final FeatureList list = FeatureList.parse(o);
        Assertions.assertEquals(1, list.size());
        final CallFeature feature = list.getList().get(0);
        Assertions.assertInstanceOf(UnknownCallFeature.class, feature);
        Assertions.assertEquals("asdf", feature.getName());
        Assertions.assertEquals(0, feature.getParams().length());
    }

    @Test
    void parseUnknownWithParams() throws JSONException {
        final JSONObject o = new JSONObject();
        final JSONObject p = new JSONObject();
        p.put("xyz", false);
        p.put("aaa", 123);
        o.put("asdf", p);
        final FeatureList list = FeatureList.parse(o);
        Assertions.assertEquals(1, list.size());
        final CallFeature feature = list.getList().get(0);
        Assertions.assertInstanceOf(UnknownCallFeature.class, feature);
        Assertions.assertEquals("asdf", feature.getName());
        Assertions.assertEquals(2, feature.getParams().length());
    }

    /**
     * Smoke test.
     */
    @Test
    void parseMixed() throws JSONException {
        final JSONObject o = new JSONObject();

        final JSONObject p = new JSONObject();
        p.put("xyz", false);
        p.put("aaa", 123);

        o.put("unknown-null", JSONObject.NULL);
        o.put("video", JSONObject.NULL);
        o.put("unknown-empty", new JSONObject());
        o.put("unknown-params", p);

        final FeatureList list = FeatureList.parse(o);
        Assertions.assertEquals(4, list.size());
    }

    @Test
    void toStringEmpty() {
        final String string = new FeatureList().toString();
        Assertions.assertEquals("FeatureList[]", string);
    }

    @Test
    void toStringMixed() throws JSONException {
        final JSONObject params = new JSONObject();
        params.put("a", 3);
        params.put("b", false);
        final String string = new FeatureList()
            .addFeature(new VideoFeature())
            .addFeature(new UnknownCallFeature("hullo", params))
            .toString();
        Assertions.assertEquals("FeatureList[video, hullo({\"a\":3,\"b\":false})]", string);
    }

    @Test
    void hasFeature() throws JSONException {
        final JSONObject params = new JSONObject();
        params.put("a", 3);
        params.put("b", false);

        final FeatureList features = new FeatureList()
            .addFeature(new VideoFeature())
            .addFeature(new UnknownCallFeature("hullo", params))
            .addFeature(new SimpleCallFeature("argh"));
        Assertions.assertTrue(features.hasFeature("video"));
        Assertions.assertTrue(features.hasFeature("hullo"));
        Assertions.assertTrue(features.hasFeature("argh"));
        Assertions.assertFalse(features.hasFeature("arg"));
        Assertions.assertFalse(features.hasFeature(""));
        Assertions.assertFalse(features.hasFeature("videoo"));
    }
}
