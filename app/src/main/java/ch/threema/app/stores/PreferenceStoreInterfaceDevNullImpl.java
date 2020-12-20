/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2020 Threema GmbH
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PreferenceStoreInterfaceDevNullImpl implements PreferenceStoreInterface {
	@Override
	public void remove(String key) {

	}

	@Override
	public void remove(List<String> keys) {

	}

	@Override
	public void remove(String key, boolean crypt) {}

	@Override
	public void save(String key, String thing) {

	}

	@Override
	public void save(String key, String thing, boolean crypt) {

	}

	@Override
	public void save(String key, String[] things) {

	}

	@Override
	public void save(String key, HashMap<Integer, String> things) {

	}

	@Override
	public void save(String key, HashMap<Integer, String> things, boolean crypt) {

	}

	@Override
	public void saveStringHashMap(String key, HashMap<String, String> things, boolean crypt) {

	}

	@Override
	public void saveIntegerHashMap(String key, HashMap<Integer, Integer> things) {

	}

	@Override
	public void save(String key, String[] things, boolean crypt) {

	}

	@Override
	public void save(String key, long thing) {

	}

	@Override
	public void save(String key, long thing, boolean crypt) {

	}

	@Override
	public void save(String key, int thing) {

	}

	@Override
	public void save(String key, int thing, boolean crypt) {

	}

	@Override
	public void save(String key, boolean thing) {

	}

	@Override
	public void save(String key, byte[] thing) {

	}

	@Override
	public void save(String key, byte[] thing, boolean crypt) {

	}

	@Override
	public void save(String key, Date date) {

	}

	@Override
	public void save(String key, Date date, boolean crypt) {

	}

	@Override
	public void save(String key, Long thing) {

	}

	@Override
	public void save(String key, Long thing, boolean crypt) {

	}

	@Override
	public void save(String key, JSONArray thing, boolean crypt) {

	}

	@Override
	public void save(String key, float thing) {

	}

	@Override
	public void save(String key, JSONArray thing) {

	}

	@Override
	public void save(String key, JSONObject object, boolean crypt) {

	}

	@Override
	public String getString(String key) {
		return null;
	}

	@Override
	public String getString(String key, boolean crypt) {
		return null;
	}

	@Override
	public String getHexString(String key, boolean crypt) {
		return null;
	}

	@Override
	public Long getLong(String key) {
		return null;
	}

	@Override
	public Long getLong(String key, boolean crypt) {
		return null;
	}

	@Override
	public Date getDate(String key) {
		return null;
	}

	@Override
	public Date getDate(String key, boolean crypt) {
		return null;
	}

	@Override
	public long getDateAsLong(String key) {
		return 0;
	}

	@Override
	public Integer getInt(String key) {
		return null;
	}

	@Override
	public Integer getInt(String key, boolean crypt) {
		return null;
	}

	@Override
	public float getFloat(String key, float defValue) {
		return defValue;
	}

	@Override
	public boolean getBoolean(String key) {
		return false;
	}

	@Override
	public boolean getBoolean(String key, boolean defValue) {
		return defValue;
	}

	@Override
	public void save(String key, Serializable object, boolean crypt) throws IOException {

	}

	@Override
	public byte[] getBytes(String key) {
		return new byte[0];
	}

	@Override
	public byte[] getBytes(String key, boolean crypted) {
		return new byte[0];
	}

	@Override
	public String[] getStringArray(String key) {
		return new String[0];
	}

	@Override
	public String[] getStringArray(String key, boolean crypted) {
		return new String[0];
	}

	@Override
	public HashMap<Integer, String> getHashMap(String key, boolean encrypted) {
		return null;
	}

	@Override
	public HashMap<String, String> getStringHashMap(String key, boolean encrypted) {
		return null;
	}

	@Override
	public HashMap<Integer, Integer> getHashMap(String key) {
		return null;
	}

	@Override
	public JSONArray getJSONArray(String key, boolean crypt) {
		return null;
	}

	@Override
	public JSONObject getJSONObject(String key, boolean crypt) {
		return null;
	}

	@Override
	public <T> T getRealObject(String key, boolean crypt) {
		return null;
	}

	@Override
	public void clear() {

	}

	@Override
	public Map<String, ?> getAllNonCrypted() {
		return null;
	}

	@Override
	public Set<String> getStringSet(String key, int defaultRes) {
		return new HashSet<String>();
	}
}
