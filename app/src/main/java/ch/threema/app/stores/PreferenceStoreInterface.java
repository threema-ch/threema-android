/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

package ch.threema.app.stores;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.Nullable;

public interface PreferenceStoreInterface {

	public void remove(String key);

	public void remove(List<String> keys);

	void remove(String key, boolean crypt);

	public void save(String key, String thing);

	public void save(String key, String thing, boolean crypt);

	public void save(String key, String[] things);

	void save(String key, HashMap<Integer, String> things);

	void save(String key, HashMap<Integer, String> things, boolean crypt);

	void saveStringHashMap(String key, HashMap<String, String> things, boolean crypt);

	void saveIntegerHashMap(String key, HashMap<Integer, Integer> things);

	public void save(String key, String[] things, boolean crypt);

	public void save(String key, long thing);

	public void save(String key, long thing, boolean crypt);

	public void save(String key, int thing);

	public void save(String key, int thing, boolean crypt);

	public void save(String key, boolean thing);

	public void save(String key, byte[] thing);

	public void save(String key, byte[] thing, boolean crypt);

	public void save(String key, Date date);

	public void save(String key, Date date, boolean crypt);

	public void save(String key, Long thing);

	public void save(String key, Long thing, boolean crypt);

	public void save(String key, JSONArray thing, boolean crypt);

	public void save(String key, float thing);

	void save(String key, JSONArray array);

	void save(String key, Serializable object, boolean crypt) throws IOException;

	void save(String key, JSONObject object, boolean crypt);

	@Nullable String getString(String key);

	@Nullable String getString(String key, boolean crypt);

	public String getHexString(String key, boolean crypt);

	public Long getLong(String key);

	public Long getLong(String key, boolean crypt);

	public Date getDate(String key);

	public Date getDate(String key, boolean crypt);

	long getDateAsLong(String key);

	public Integer getInt(String key);

	public Integer getInt(String key, boolean crypt);

	public float getFloat(String key, float defValue);

	public boolean getBoolean(String key);

	public boolean getBoolean(String key, boolean defValue);

	public byte[] getBytes(String key);

	public byte[] getBytes(String key, boolean crypted);

	public String[] getStringArray(String key);

	public String[] getStringArray(String key, boolean crypted);

	public HashMap<Integer, String> getHashMap(String key, boolean encrypted);

	public HashMap<String, String> getStringHashMap(String key, boolean encrypted);

	public HashMap<Integer, Integer> getHashMap(String key);

	JSONArray getJSONArray(String key, boolean crypt);

	JSONObject getJSONObject(String key, boolean crypt);

	<T> T getRealObject(String key, boolean crypt);

	public void clear();

	public Map<String, ?> getAllNonCrypted();

	Set<String> getStringSet(String key, int defaultRes);
}
