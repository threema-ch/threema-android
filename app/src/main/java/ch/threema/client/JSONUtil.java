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

/**
 * Helpers for sane JSON parsing.
 *
 * Reason why this is required: By default `jsonObj.getString(key)` returns the string "null" for
 * null values, instead of returning null.
 *
 * Also reason why this is required: When converting JSON numbers to (long) integers, the values are
 * silently truncated if out of range.
 */
public class JSONUtil {

	/**
	 * Return the String value for the specified key. If the field is missing or if the field value
	 * is null, return null. If the field value is not a string, return null.
	 */
	public static String getStringOrNull(JSONObject o, String key) {
		if (o.isNull(key)) {
			return null;
		}
		try {
			final Object value = o.get(key);
			if (value instanceof String) {
				return (String) value;
			}
			return null;
		} catch (JSONException e) {
			return null;
		}
	}

	/**
	 * Return the Integer value for the specified key. If the field is missing or if the field value
	 * is null, return null. If the field value is not an integer, return null.
	 */
	public static Integer getIntegerOrNull(JSONObject o, String key) {
		if (o.isNull(key)) {
			return null;
		}
		try {
			final Object value = o.get(key);
			if (value instanceof Integer) {
				return (Integer) value;
			}
			return null;
		} catch (JSONException e) {
			return null;
		}
	}

	/**
	 * Return the Long value for the specified key. If the field is missing or if the field value
	 * is null, return null. If the field value is out of range or not an integer, throw an exception.
	 */
	public static Long getLongOrThrow(JSONObject o, String key) throws RuntimeException {
		if (o.isNull(key)) {
			return null;
		}
		final Object value = o.opt(key);
		if (value instanceof Number) {
			// Range check
			final double doubleValue = ((Number) value).doubleValue();
			if (doubleValue > Long.MAX_VALUE || doubleValue < Long.MIN_VALUE) {
				throw new RuntimeException("Number at key \"" + key + "\" out of range");
			}
			// Convert to long
			return ((Number) value).longValue();
		} else {
			throw new RuntimeException("Invalid object type for key " + key);
		}
	}

}
