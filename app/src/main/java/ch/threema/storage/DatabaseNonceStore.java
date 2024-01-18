/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

package ch.threema.storage;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;

import androidx.annotation.NonNull;

import net.zetetic.database.sqlcipher.SQLiteConnection;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import org.slf4j.Logger;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import ch.threema.base.crypto.NonceStoreInterface;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.stores.IdentityStoreInterface;

public class DatabaseNonceStore extends SQLiteOpenHelper
	implements NonceStoreInterface  {
	private static final Logger logger = LoggingUtil.getThreemaLogger("NonceDatabaseBlobService");

	public static final String DATABASE_NAME_V4 = "threema-nonce-blob4.db";
	private static final int DATABASE_VERSION = 1;
	private final IdentityStoreInterface identityStore;

	public DatabaseNonceStore(final Context context, IdentityStoreInterface identityStore) {
		super(
			context,
			DATABASE_NAME_V4,
			"",
			null,
			DATABASE_VERSION,
			0,
			null,
			new SQLiteDatabaseHook() {
				@Override
				public void preKey(SQLiteConnection connection) {
					// not used
				}

				@SuppressLint("DefaultLocale")
				@Override
				public void postKey(SQLiteConnection connection) {
					// turn off memory wiping for now due to https://github.com/sqlcipher/android-database-sqlcipher/issues/411
					connection.execute("PRAGMA cipher_memory_security = OFF;", new Object[]{}, null);
				}
			}
			,
			false);
		this.identityStore = identityStore;
	}

	@Override
	public void onCreate(SQLiteDatabase sqLiteDatabase) {
		sqLiteDatabase.execSQL("CREATE TABLE `threema_nonce` (`nonce` BLOB PRIMARY KEY)");
	}

	@Override
	public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
		// no special upgrade handling
	}

	public void executeNull() throws SQLiteException {
		try {
			getWritableDatabase().rawQuery("SELECT NULL").close();
		} catch (Exception e) {
			logger.error("Unable to execute initial query", e);
		}
	}

	@Override
	public boolean exists(@NonNull byte[] nonce) {
		boolean hasRecord = false;
		Cursor c = this.getReadableDatabase()
				.rawQuery("SELECT COUNT(*) FROM `threema_nonce` WHERE `nonce` = x'"
								+ Utils.byteArrayToHexString(nonce)
								+ "' OR `nonce` = x'"
								+ Utils.byteArrayToHexString(this.hashNonce(nonce))
								+ "'", null);
		if (c != null) {
			if (c.moveToFirst()) {
				hasRecord = c.getInt(0) > 0;
			}
			c.close();
		}
		return hasRecord;
	}

	@Override
	public boolean store(@NonNull byte[] nonce) {
		ContentValues c = new ContentValues();
		c.put("nonce", this.hashNonce(nonce));

		try {
			return this.getWritableDatabase()
					.insertOrThrow("threema_nonce", null, c) >= 1;
		} catch (SQLException x) {
			//ignore exception
			logger.error("Exception", x);
		}

		return false;
	}

	public long getCount() {
		long size = 0;
		Cursor c = this.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM `threema_nonce`", null);
		if (c != null) {
			if (c.moveToFirst()) {
				size = c.getLong(0);
			}
			c.close();
		}
		return size;
	}

	/**
	 * Get the hashed nonces of the provided chunk in their hex string representation.
	 * See {@link Utils#byteArrayToHexString(byte[])} for more information about their
	 * representation.
	 *
	 * @param chunkSize the number of nonces that is returned
	 * @param offset    the offset where reading the nonces starts
	 * @return a list of the hashed nonces in their hex string representation.
	 */
	public void addHashedNonceChunk(int chunkSize, int offset, List<String> nonces) {
		Cursor c = this.getReadableDatabase().rawQuery(
			"SELECT `nonce` FROM `threema_nonce` LIMIT ? OFFSET ?",
			new String[]{String.valueOf(chunkSize), String.valueOf(offset)}
		);
		if (c != null) {
			if (c.moveToFirst()) {
				int columnIndex = c.getColumnIndex("nonce");
				do {
					nonces.add(Utils.byteArrayToHexString(c.getBlob(columnIndex)));
				} while (c.moveToNext());
			}
			c.close();
		}
	}

	/**
	 * Insert hashed nonces to the database.
	 *
	 * @param hashedNonces the hashed nonces
	 * @return true if all nonces have been inserted successfully, false otherwise
	 */
	public boolean insertHashedNonces(@NonNull String[] hashedNonces) {
		boolean success = true;
		SQLiteDatabase database = getWritableDatabase();
		for (String hashedNonce : hashedNonces) {
			ContentValues values = new ContentValues();
			values.put("nonce", Utils.hexStringToByteArray(hashedNonce));
			try {
				long row = database.insertOrThrow("threema_nonce", null, values);
				if (row < 0) {
					logger.warn("Could not insert a nonce into the nonce database");
					success = false;
				}
			} catch (SQLException e) {
				logger.error("Could not insert a nonce into the nonce database", e);
				success = false;
			}
		}

		return success;
	}

	private byte[] hashNonce(byte[] nonce) {
		// Hash nonce with HMAC-SHA256 using the identity as the key if available.
		// This serves to make it impossible to correlate the nonce DBs of users to determine whether they have been communicating. */
		String identity = identityStore.getIdentity();
		if (identity == null) {
			return nonce;
		}

		try {
			Mac mobileNoMac = Mac.getInstance("HmacSHA256");
			mobileNoMac.init(new SecretKeySpec(identity.getBytes(), "HmacSHA256"));
			return mobileNoMac.doFinal(nonce);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new RuntimeException(e);
		}
	}
}
