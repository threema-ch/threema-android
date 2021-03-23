/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

import android.content.Context;
import android.content.SharedPreferences;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;
import ch.threema.app.listeners.PreferenceListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.StringConversionUtil;
import ch.threema.client.Utils;
import ch.threema.localcrypto.MasterKey;
import ch.threema.localcrypto.MasterKeyLockedException;

public class PreferenceStore implements PreferenceStoreInterface {
	private static final Logger logger = LoggerFactory.getLogger(PreferenceStore.class);

	public static final String PREFS_IDENTITY = "identity";
	public static final String PREFS_SERVER_GROUP = "server_group";
	public static final String PREFS_PUBLIC_KEY = "public_key";
	public static final String PREFS_PRIVATE_KEY = "private_key";
	public static final String PREFS_PUBLIC_NICKNAME = "nickname";
	public static final String PREFS_LINKED_EMAIL = "linked_email";
	public static final String PREFS_LINKED_MOBILE = "linked_mobile";
	public static final String PREFS_LINKED_EMAIL_PENDING = "linked_mobile_pending"; // typo
	public static final String PREFS_LINKED_MOBILE_PENDING = "linked_mobile_pending_since";
	public static final String PREFS_MOBILE_VERIFICATION_ID = "linked_mobile_verification_id";
	public static final String PREFS_LAST_REVOCATION_KEY_SET = "last_revocation_key_set";
	public static final String PREFS_REVOCATION_KEY_CHECKED = "revocation_key_checked";

	public static final String CRYPTED_FILE_PREFIX = ".crs-";

	private final Context context;
	private final MasterKey masterKey;
	private SharedPreferences sharedPreferences;

	public PreferenceStore(Context context, MasterKey masterKey) {
		this.context = context;
		this.masterKey = masterKey;

		this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
	}

	@Override
	public void remove(String key) {
		SharedPreferences.Editor e = this.sharedPreferences.edit();
		e.remove(key);
		e.commit();
	}

	@Override
	public void remove(List<String> keys) {
		SharedPreferences.Editor e = this.sharedPreferences.edit();
		for (String k : keys) {
			e.remove(k);

			//try to remove crypted file
			this.removeCryptedFile(k);
		}
		e.commit();
	}

	@Override
	public void remove(String key, boolean crypt) {
		if (crypt) {
			removeCryptedFile(key);
		} else {
			remove(key);
		}
	}

	@Override
	public void save(String key, String thing) {
		this.save(key, thing, false);
	}

	@Override
	public void save(String key, String thing, boolean crypt) {
		if (crypt) {
			//save into a file
			this.saveDataToCryptedFile(StringConversionUtil.stringToByteArray(thing), key);
		} else {
			SharedPreferences.Editor e = this.sharedPreferences.edit();
			e.putString(key, thing);
			e.commit();
		}

		this.fireOnChanged(key, thing);
	}

	@Override
	public void save(String key, String[] things) {
		this.save(key, things, false);
	}

	@Override
	public void save(String key, HashMap<Integer, String> things) {
		this.save(key, things, false);
	}

	@Override
	public void save(String key, HashMap<Integer, String> things, boolean crypt) {
		JSONArray json = new JSONArray();
		for (HashMap.Entry<Integer, String> kv : things.entrySet()) {
			JSONArray keyValueArray = new JSONArray();
			keyValueArray.put(kv.getKey());
			keyValueArray.put(kv.getValue());
			json.put(keyValueArray);
		}

		this.save(key, json, crypt);
	}

	@Override
	public void saveIntegerHashMap(String key, HashMap<Integer, Integer> things) {
		JSONArray json = new JSONArray();
		for (HashMap.Entry<Integer, Integer> kv : things.entrySet()) {
			JSONArray keyValueArray = new JSONArray();
			keyValueArray.put(kv.getKey());
			keyValueArray.put(kv.getValue());
			json.put(keyValueArray);
		}

		this.save(key, json);
	}

	@Override
	public void saveStringHashMap(String key, HashMap<String, String> things, boolean crypt) {
		JSONArray json = new JSONArray();
		for (HashMap.Entry<String, String> kv : things.entrySet()) {
			JSONArray keyValueArray = new JSONArray();
			keyValueArray.put(kv.getKey());
			keyValueArray.put(kv.getValue());
			json.put(keyValueArray);
		}

		this.save(key, json, crypt);
	}

	@Override
	public void save(String key, String[] things, boolean crypt) {
		StringBuilder sb = new StringBuilder();
		for(String s: things) {
			if(sb.length() > 0) {
				sb.append(';');
			}
			sb.append(s);
		}

		if (crypt) {
			//save into a file
			this.saveDataToCryptedFile(StringConversionUtil.stringToByteArray(sb.toString()), key);
		} else {
			SharedPreferences.Editor e = this.sharedPreferences.edit();
			e.putString(key, sb.toString());
			e.commit();
		}
		this.fireOnChanged(key, things);
	}

	@Override
	public void save(String key, long thing) {
		this.save(key, thing, false);
	}

	@Override
	public void save(String key, long thing, boolean crypt) {
		if (crypt) {
			//save into a file
			this.saveDataToCryptedFile(Utils.hexStringToByteArray(String.valueOf(thing)), key);
		} else {
			SharedPreferences.Editor e = this.sharedPreferences.edit();
			e.putLong(key, thing);
			e.commit();
		}
		this.fireOnChanged(key, thing);
	}


	@Override
	public void save(String key, int thing) {
		this.save(key, thing, false);
	}

	@Override
	public void save(String key, int thing, boolean crypt) {
		if (crypt) {
			//save into a file
			this.saveDataToCryptedFile(Utils.hexStringToByteArray(String.valueOf(thing)), key);
		} else {
			SharedPreferences.Editor e = this.sharedPreferences.edit();
			e.putInt(key, thing);
			e.apply();
		}
		this.fireOnChanged(key, thing);
	}

	@Override
	public void save(String key, boolean thing) {
		SharedPreferences.Editor e = this.sharedPreferences.edit();
		e.putBoolean(key, thing);
		e.apply();
		this.fireOnChanged(key, thing);
	}

	@Override
	public void save(String key, byte[] thing) {
		this.save(key, thing, false);
	}

	@Override
	public void save(String key, byte[] thing, boolean crypt) {
		if (crypt) {
			//save into a file
			this.saveDataToCryptedFile(thing, key);
		} else {
			SharedPreferences.Editor e = this.sharedPreferences.edit();
			e.putString(key, Utils.byteArrayToHexString(thing));
			e.apply();
		}
		this.fireOnChanged(key, thing);
	}

	@Override
	public void save(String key, Date date) {
		this.save(key, date, false);
	}

	@Override
	public void save(String key, Date date, boolean crypt) {
		//save as long
		this.save(key, date != null ? date.getTime() : 0, crypt);
	}

	@Override
	public void save(String key, Long thing) {
		this.save(key, thing, false);
	}

	@Override
	public void save(String key, Long thing, boolean crypt) {
		if (crypt) {
			//save into a file
			try {
				//TODO
				//this.saveDataToCryptedFile(thing, key);
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		} else {
			SharedPreferences.Editor e = this.sharedPreferences.edit();
			e.putLong(key, thing);
			e.apply();
		}
		this.fireOnChanged(key, thing);
	}

	@Override
	public void save(String key, JSONArray array) {
		save(key, array, false);
	}

	@Override
	public void save(String key, Serializable object, boolean crypt) throws IOException {

		try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
			ObjectOutput out = new ObjectOutputStream(bos);
			out.writeObject(object);
			out.flush();
			this.save(key, bos.toByteArray(), crypt);

		}
		// ignore close exception
	}

	public void save(String key, JSONArray array, boolean crypt) {
		if (crypt) {
			if (array != null) {
				this.saveDataToCryptedFile(array.toString().getBytes(), key);
			}
		} else {
			SharedPreferences.Editor e = this.sharedPreferences.edit();
			e.putString(key, array.toString());
			e.apply();
		}
		this.fireOnChanged(key, array);
	}

	@Override
	public void save(String key, float thing) {
		SharedPreferences.Editor e = this.sharedPreferences.edit();
		e.putFloat(key, thing);
		e.apply();
	}

	@Override
	public void save(String key, JSONObject object, boolean crypt) {
		if (crypt) {
			if (object != null) {
				this.saveDataToCryptedFile(object.toString().getBytes(), key);
			}
		} else {
			SharedPreferences.Editor e = this.sharedPreferences.edit();
			e.putString(key, object.toString());
			e.apply();
		}
		this.fireOnChanged(key, object);
	}

	@Override
	@Nullable
	public String getString(String key) {
		return this.getString(key, false);
	}

	@Override
	@Nullable
	public String getString(String key, boolean crypt) {
		if (crypt) {
			byte[] r = this.getDataFromCryptedFile(key);
			if (r != null) {
				return StringConversionUtil.byteArrayToString(r);
			} else {
				return null;
			}
		} else {
			String value = null;
			try {
				value = this.sharedPreferences.getString(key, null);
			} catch (ClassCastException e) {
				logger.error("Class cast exception", e);
			}
			return value;
		}
	}

	@Override
	public String[] getStringArray(String key) {
		return this.getStringArray(key, false);
	}

	@Override
	public String[] getStringArray(String key, boolean crypted) {
		String value = null;
		if (crypted) {
			byte[] r = this.getDataFromCryptedFile(key);
			if (r != null) {
				value = StringConversionUtil.byteArrayToString(r);
			} else {
				return null;
			}
		} else {
			value = this.sharedPreferences.getString(key, null);
		}

		if(value != null && value.length() > 0) {
			return value.split(";");
		}

		return null;
	}

	@Override
	public HashMap<Integer, String> getHashMap(String key, boolean encrypted) {
		HashMap<Integer, String> result = new HashMap<>();

		try {
			JSONArray jsonArray;
			if (encrypted) {
				jsonArray = new JSONArray(new String(getDataFromCryptedFile(key)));
			} else {
				jsonArray = new JSONArray(this.sharedPreferences.getString(key, "[]"));
			}

			for (int n = 0; n < jsonArray.length(); n++) {
				JSONArray keyValuePair = jsonArray.getJSONArray(n);

				result.put(
						keyValuePair.getInt(0),
						keyValuePair.getString(1));
			}

		} catch (Exception e) {
			logger.error("Exception", e);
		}
		return result;
	}

	@Override
	public HashMap<String, String> getStringHashMap(String key, boolean encrypted) {
		HashMap<String, String> result = new HashMap<>();

		try {
			JSONArray jsonArray = null;
			if (encrypted) {
				byte[] data = getDataFromCryptedFile(key);
				if (data.length > 0) {
					jsonArray = new JSONArray(new String(data));
				}
			} else {
				jsonArray = new JSONArray(this.sharedPreferences.getString(key, "[]"));
			}

			if (jsonArray != null) {
				for (int n = 0; n < jsonArray.length(); n++) {
					JSONArray keyValuePair = jsonArray.getJSONArray(n);

					result.put(
						keyValuePair.getString(0),
						keyValuePair.getString(1));
				}
			}
		} catch (Exception e) {
			logger.error("Exception", e);
		}
		return result;
	}

	@Override
	public HashMap<Integer, Integer> getHashMap(String key) {
		HashMap<Integer, Integer> result = new HashMap<>();

		try {
			JSONArray jsonArray = new JSONArray(this.sharedPreferences.getString(key, "[]"));

			for (int n = 0; n < jsonArray.length(); n++) {
				JSONArray keyValuePair = jsonArray.getJSONArray(n);

				result.put(
						keyValuePair.getInt(0),
						keyValuePair.getInt(1));
			}

		} catch (Exception e) {
			logger.error("Exception", e);
		}
		return result;
	}

	@Override
	public String getHexString(String key, boolean crypt) {
		// for compatibility with old PIN storage format (used in release 1.2)
		// can be removed in a few years :)
		if (crypt) {
			byte[] r = this.getDataFromCryptedFile(key);
			if (r != null) {
				return Utils.byteArrayToHexString(r);
			} else {
				return null;
			}
		} else {
			return this.sharedPreferences.getString(key, null);
		}
	}

	@Override
	public Long getLong(String key) {
		return this.getLong(key, false);
	}

	@Override
	public Long getLong(String key, boolean crypt) {
		if (crypt) {
			byte[] r = this.getDataFromCryptedFile(key);
			if (r != null) {
				return Long.getLong(Utils.byteArrayToHexString(r));
			} else {
				return null;
			}
		} else {
			return this.sharedPreferences.getLong(key, 0);
		}
	}

	@Override
	public Date getDate(String key) {
		return this.getDate(key, false);
	}

	@Override
	public Date getDate(String key, boolean crypt) {
		Long l = this.getLong(key, crypt);
		if(l != null && l > 0) {
			return new Date(l);
		}

		return null;
	}

	@Override
	public long getDateAsLong(String key) {
		Long l = this.getLong(key, false);
		if (l != null && l > 0) {
			return l;
		}
		return 0L;
	}

	@Override
	public Integer getInt(String key) {
		return this.getInt(key, false);
	}

	@Override
	public Integer getInt(String key, boolean crypt) {
		if (crypt) {
			byte[] r = this.getDataFromCryptedFile(key);
			if (r != null) {
				return Integer.getInteger(Utils.byteArrayToHexString(r));
			} else {
				return null;
			}
		} else {
			return this.sharedPreferences.getInt(key, 0);
		}
	}

	@Override
	public float getFloat(String key, float defValue) {
		return this.sharedPreferences.getFloat(key, defValue);
	}

	public boolean getBoolean(String key) {
		return this.sharedPreferences.getBoolean(key, false);
	}

	public boolean getBoolean(String key, boolean defValue) {
		return this.sharedPreferences.getBoolean(key, defValue);
	}

	@Override
	public byte[] getBytes(String key) {
		return this.getBytes(key, false);
	}

	@Override
	public byte[] getBytes(String key, boolean crypt) {
		if (crypt) {
			return this.getDataFromCryptedFile(key);
		} else {
			String v = this.sharedPreferences.getString(key, null);
			if (v != null) {
				return Utils.hexStringToByteArray(v);
			}
		}

		return new byte[0];
	}

	@Override
	public JSONArray getJSONArray(String key, boolean crypt) {
		try {
			if (crypt) {
				byte[] data = this.getDataFromCryptedFile(key);
				return new JSONArray(new String(data));
			} else {
				return new JSONArray(this.sharedPreferences.getString(key, "[]"));
			}
		} catch (Exception e) {
			logger.error("Exception", e);
		}
		return new JSONArray();
	}

	@Override
	public JSONObject getJSONObject(String key, boolean crypt) {
		try {
			if (crypt) {
				byte[] data = this.getDataFromCryptedFile(key);
				return new JSONObject(new String(data));
			} else {
				String data = this.sharedPreferences.getString(key, "[]");
				return  new JSONObject(data);
			}
		} catch (Exception e) {
			logger.error("Exception", e);
		}
		return null;
	}

	@Override
	public <T> T getRealObject(String key, boolean crypt) {
		try {
			if (crypt) {
				byte[] data = this.getDataFromCryptedFile(key);

				ByteArrayInputStream bis = new ByteArrayInputStream(data);
				ObjectInput in = null;
				T o = null;
				try {
					in = new ObjectInputStream(bis);
					o = (T)in.readObject();
				} finally {
					try {
						if (in != null) {
							in.close();
						}
					} catch (IOException ex) {
						// ignore close exception
					}
				}
				return o;
			} else {
				// not implemented
			}
		} catch (Exception e) {
			logger.error("Exception", e);
		}
		return null;
	}

	@Override
	public void clear() {
		SharedPreferences.Editor editor = this.sharedPreferences.edit();
		editor.clear();
		editor.apply();

		try {
			for (File f : this.context.getFilesDir().listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String filename) {
					return filename.startsWith(CRYPTED_FILE_PREFIX);
				}
			})) {
				FileUtil.deleteFileOrWarn(f, "clear", logger);
			}
		} catch (Exception e) {
			logger.error("Exception", e);
		}
	}

	@Override
	public Map<String, ?> getAllNonCrypted() {
		return this.sharedPreferences.getAll();
	}

	@Override
	public Set<String> getStringSet(final String key, final int defaultRes) {
		if (this.sharedPreferences.contains(key)) {
			return this.sharedPreferences.getStringSet(key, Collections.emptySet());
		} else {
			return new HashSet<>(Arrays.asList(context.getResources().getStringArray(defaultRes)));
		}
	}

	private void fireOnChanged(final String key, final  Object value) {
		ListenerManager.preferenceListeners.handle(new ListenerManager.HandleListener<PreferenceListener>() {
			@Override
			public void handle(PreferenceListener listener) {
				listener.onChanged(key, value);
			}
		});
	}

	@WorkerThread
	private void removeCryptedFile(String filename) {
		File f = new File(this.context.getFilesDir(), CRYPTED_FILE_PREFIX + filename);
		if (f.exists()) {
			FileUtil.deleteFileOrWarn(f, "removeCryptedFile", logger);
		}
	}

	@AnyThread
	private void saveDataToCryptedFile(byte[] data, String filename) {
		File f = new File(context.getFilesDir(), CRYPTED_FILE_PREFIX + filename);
		if (!f.exists()) {
			try {
				FileUtil.createNewFileOrLog(f, logger);
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		}

		try (FileOutputStream fileOutputStream = new FileOutputStream(f);
		    CipherOutputStream cipherOutputStream = masterKey.getCipherOutputStream(fileOutputStream)) {
			cipherOutputStream.write(data);
		} catch (IOException | MasterKeyLockedException e) {
			logger.error("Unable to store prefs", e);
		}
	}

	@WorkerThread
	private byte[] getDataFromCryptedFile(String filename) {
		File f = new File(this.context.getFilesDir(), CRYPTED_FILE_PREFIX + filename);
		if (f.exists()) {
			CipherInputStream cis = null;
			FileInputStream fis = null;
			try {
				fis = new FileInputStream(f);
				cis = masterKey.getCipherInputStream(fis);
				return IOUtils.toByteArray(cis);
			} catch (Exception x) {
				//do nothing
				logger.error("getDataFromCryptedFile: " + filename, x);
			} finally {
				if (cis != null) {
					try {
						cis.close();
					} catch (IOException e) {
						/**/
					}
				}
				if (fis != null) {
					try {
						fis.close();
					} catch (IOException e) {
						/**/
					}
				}
			}
		}

		return new byte[0];
	}
}
