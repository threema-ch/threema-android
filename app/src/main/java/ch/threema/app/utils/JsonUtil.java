/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Convert Json to X and X to Json
 */
public class JsonUtil {
	public static List<Object> convertArray(String jsonArrayInputString) throws JSONException {
		JSONArray ja = new JSONArray(jsonArrayInputString);
		if (ja != null) {
			return convert(ja);
		}

		return null;
	}
	public static List<Object> convert(JSONArray jsonArray) {
		List<Object> l = new ArrayList<>();

		for(int i = 0; i < jsonArray.length(); i++) {
			try {
				l.add(jsonArray.isNull(i) ? null : convert(jsonArray.get(i)));
			} catch (JSONException e) {
				// Should not happen
			}
		}

		return l;
	}

	public static Map<String, Object> convertObject(String jsonObjectInputString) throws JSONException {
		JSONObject jo = new JSONObject(jsonObjectInputString);

		return convert(jo);
	}

	public static Map<String, Object> convert(JSONObject jsonObjectInput) {
		Map<String, Object> map = new HashMap<>();

		Iterator<String> keys = jsonObjectInput.keys();
		while(keys.hasNext()) {
			String key = keys.next();

			try {
				map.put(key,jsonObjectInput.isNull(key) ? null : convert(jsonObjectInput.get(key)));
			} catch (JSONException e) {
				// Ignore, next!
			}
		}

		return map;
	}

	private static Object convert(Object input) {
		if (input instanceof JSONArray) {
			return convert((JSONArray)input);
		} else if (input instanceof JSONObject) {
			return convert((JSONObject)input);
		}
		return input;
	}

}
