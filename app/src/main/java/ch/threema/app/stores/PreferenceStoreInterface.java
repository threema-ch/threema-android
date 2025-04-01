/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface PreferenceStoreInterface {

    void remove(String key);

    void remove(List<String> keys);

    void remove(String key, boolean crypt);

    void save(String key, String thing);

    void save(String key, String thing, boolean crypt);

    void save(String key, String[] things);

    void save(String key, HashMap<Integer, String> things);

    void save(String key, HashMap<Integer, String> things, boolean crypt);

    void saveStringHashMap(String key, HashMap<String, String> things, boolean crypt);

    void saveIntegerHashMap(String key, HashMap<Integer, Integer> things);

    void save(String key, @NonNull String[] things, boolean crypt);

    void saveQuietly(String key, @NonNull String[] things, boolean crypt);

    void save(String key, long thing);

    void save(String key, long thing, boolean crypt);

    void save(String key, int thing);

    void save(String key, int thing, boolean crypt);

    void save(String key, boolean thing);

    void save(String key, byte[] thing);

    void save(String key, byte[] thing, boolean crypt);

    void save(String key, Date date);

    void save(String key, Date date, boolean crypt);

    void save(String key, Long thing);

    void save(String key, Long thing, boolean crypt);

    void save(String key, JSONArray thing, boolean crypt);

    void save(String key, float thing);

    void save(String key, JSONArray array);

    void save(String key, Serializable object, boolean crypt) throws IOException;

    void save(String key, JSONObject object, boolean crypt);

    @Nullable
    String getString(String key);

    @Nullable
    String getString(String key, boolean crypt);

    String getHexString(String key, boolean crypt);

    /**
     * Get the value as long. Note that 0 is returned if no preference with the provided key is
     * stored.
     *
     * @param key the key of the value
     * @return the value as long or 0 if no value for the provided key exists
     */
    long getLong(String key);

    Long getLong(String key, boolean crypt);

    Date getDate(String key);

    Date getDate(String key, boolean crypt);

    long getDateAsLong(String key);

    Integer getInt(String key);

    Integer getInt(String key, boolean crypt);

    float getFloat(String key, float defValue);

    boolean getBoolean(String key);

    boolean getBoolean(String key, boolean defValue);

    byte[] getBytes(String key);

    byte[] getBytes(String key, boolean crypted);

    /**
     * Get encrypted string preferences in a backwards compatible way.
     * If no encrypted prefs with given key exist, the current (unencrypted) value will be migrated
     *
     * @param key Key of preference
     * @return Value of preference or null if neither an encrypted or an unencrypted value is found
     */
    @Nullable
    default String getStringCompat(String key) {
        return null;
    }

    String[] getStringArray(String key);

    String[] getStringArray(String key, boolean crypted);

    HashMap<Integer, String> getHashMap(String key, boolean encrypted);

    HashMap<String, String> getStringHashMap(String key, boolean encrypted);

    HashMap<Integer, Integer> getHashMap(String key);

    JSONArray getJSONArray(String key, boolean crypt);

    JSONObject getJSONObject(String key, boolean crypt);

    void clear();

    Map<String, ?> getAllNonCrypted();

    Set<String> getStringSet(String key, int defaultRes);

    /**
     * @return `true` if the unencrypted settings contain a mapping with the provided key
     */
    boolean containsKey(String key);

    /**
     * @param crypt Set to `true` if the key should be searched in encrypted settings
     * @return `true` if there is a setting with the provided key
     */
    boolean containsKey(String key, boolean crypt);
}
