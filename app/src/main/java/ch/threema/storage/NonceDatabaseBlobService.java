/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2022 Threema GmbH
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

import android.content.ContentValues;
import android.content.Context;
import android.widget.Toast;

import net.sqlcipher.Cursor;
import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.slf4j.Logger;

import java.io.File;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import ch.threema.app.exceptions.DatabaseMigrationFailedException;
import ch.threema.app.utils.FileUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.base.crypto.NonceStoreInterface;
import ch.threema.base.utils.Utils;
import ch.threema.localcrypto.MasterKey;
import ch.threema.localcrypto.MasterKeyLockedException;

public class NonceDatabaseBlobService extends SQLiteOpenHelper
	implements NonceStoreInterface  {
	private static final Logger logger = LoggingUtil.getThreemaLogger("NonceDatabaseBlobService");

	public static final String DATABASE_NAME = "threema-nonce-blob.db";
	public static final String DATABASE_NAME_V4 = "threema-nonce-blob4.db";
	private static final int DATABASE_VERSION = 1;
	private final String key;
	private final IdentityStoreInterface identityStore;

	public NonceDatabaseBlobService(final Context context, final MasterKey masterKey, final int nonceSqlCipherVersion, IdentityStoreInterface identityStore) throws MasterKeyLockedException {
		super(context,
			nonceSqlCipherVersion == 4 ? DATABASE_NAME_V4 : DATABASE_NAME,
			null,
			DATABASE_VERSION,
			new SQLiteDatabaseHook() {
			@Override
			public void preKey(SQLiteDatabase sqLiteDatabase) {
				if (nonceSqlCipherVersion == 3) {
					sqLiteDatabase.rawExecSQL(
						"PRAGMA cipher_default_page_size = 1024;" +
						"PRAGMA cipher_default_kdf_iter = 4000;" +
						"PRAGMA cipher_default_hmac_algorithm = HMAC_SHA1;" +
						"PRAGMA cipher_default_kdf_algorithm = PBKDF2_HMAC_SHA1;");
				}
			}

			@Override
			public void postKey(SQLiteDatabase sqLiteDatabase) {
				if (nonceSqlCipherVersion == 4) {
					// turn off memory wiping for now due to https://github.com/sqlcipher/android-database-sqlcipher/issues/411
					sqLiteDatabase.rawExecSQL("PRAGMA cipher_memory_security = OFF;");
				} else {
					sqLiteDatabase.rawExecSQL(
						"PRAGMA cipher_page_size = 1024;" +
						"PRAGMA kdf_iter = 4000;" +
						"PRAGMA cipher_hmac_algorithm = HMAC_SHA1;" +
						"PRAGMA cipher_kdf_algorithm = PBKDF2_HMAC_SHA1;");
				}
			}
		});
		this.key = nonceSqlCipherVersion == 3 ? "x\"" + Utils.byteArrayToHexString(masterKey.getKey()) + "\"" : "";
		this.identityStore = identityStore;
	}

	public synchronized SQLiteDatabase getWritableDatabase()  {
		return super.getWritableDatabase(this.key);
	}
	public synchronized SQLiteDatabase getReadableDatabase()  {
		return super.getReadableDatabase(this.key);
	}

	@Override
	public void onCreate(SQLiteDatabase sqLiteDatabase) {
		sqLiteDatabase.execSQL("CREATE TABLE `threema_nonce` (`nonce` BLOB PRIMARY KEY)");
	}

	@Override
	public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

	}

	@Override
	public boolean exists(byte[] nonce) {
		boolean hasRecord = false;
		Cursor c = this.getReadableDatabase()
				.rawQuery("SELECT COUNT(*) FROM `threema_nonce` WHERE `nonce` = x'"
								+ Utils.byteArrayToHexString(nonce)
								+ "' OR `nonce` = x'"
								+ Utils.byteArrayToHexString(this.hashNonce(nonce))
								+ "'", null);
		if (c != null) {
			if(c.moveToFirst()) {
				hasRecord = c.getInt(0) > 0;
			}
			c.close();
		}
		return hasRecord;
	}

	@Override
	public boolean store(byte[] nonce) {
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

	public static void tryMigrateToV4(Context context, final String databaseKey) throws DatabaseMigrationFailedException {
		File oldDatabaseFile = context.getDatabasePath(DATABASE_NAME);
		File newDatabaseFile = context.getDatabasePath(DATABASE_NAME_V4);

		if (oldDatabaseFile.exists()) {
			if (!newDatabaseFile.exists()) {
				logger.debug("Nonce database migration to v4 required");

				long usableSpace = oldDatabaseFile.getUsableSpace();
				long fileSize = oldDatabaseFile.length();

				if (usableSpace < (fileSize * 2)) {
					throw new DatabaseMigrationFailedException("Not enough space left on device");
				}

				try {
					// migrate
					SQLiteDatabaseHook hook = new SQLiteDatabaseHook() {
						@Override
						public void preKey(SQLiteDatabase sqLiteDatabase) {
						}

						@Override
						public void postKey(SQLiteDatabase sqLiteDatabase) {
							// old settings
							sqLiteDatabase.rawExecSQL(
								"PRAGMA cipher_page_size = 1024;" +
									"PRAGMA kdf_iter = 4000;" +
									"PRAGMA cipher_hmac_algorithm = HMAC_SHA1;" +
									"PRAGMA cipher_kdf_algorithm = PBKDF2_HMAC_SHA1;");
						}
					};

					try (SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(oldDatabaseFile.getAbsolutePath(), databaseKey, null, hook)) {
						if (database.isOpen()) {
							database.rawExecSQL(
								"PRAGMA key = '" + databaseKey + "';" +
									"PRAGMA cipher_page_size = 1024;" +
									"PRAGMA kdf_iter = 4000;" +
									"PRAGMA cipher_hmac_algorithm = HMAC_SHA1;" +
									"PRAGMA cipher_kdf_algorithm = PBKDF2_HMAC_SHA1;" +
									"ATTACH DATABASE '" + newDatabaseFile.getAbsolutePath() + "' AS nonce4 KEY '';" +
									"PRAGMA nonce4.cipher_memory_security = OFF;" +
									"SELECT sqlcipher_export('nonce4');" +
									"PRAGMA nonce4.user_version = " + DATABASE_VERSION + ";" +
									"DETACH DATABASE nonce4;");
							database.close();

							logger.debug("Nonce database successfully migrated");

							// test new database
							try (SQLiteDatabase newDatabase = SQLiteDatabase.openDatabase(newDatabaseFile.getAbsolutePath(), "", null, 0, new SQLiteDatabaseHook() {
								@Override
								public void preKey(SQLiteDatabase sqLiteDatabase) {
								}

								@Override
								public void postKey(SQLiteDatabase sqLiteDatabase) {
									sqLiteDatabase.rawExecSQL("PRAGMA cipher_memory_security = OFF;");
								}
							})) {
								if (newDatabase.isOpen()) {
									newDatabase.rawExecSQL("SELECT NULL;");
									logger.debug("New nonce database successfully checked");
									Toast.makeText(context, "Database successfully migrated", Toast.LENGTH_LONG).show();
								} else {
									logger.debug("Could not open new nonce database");
									throw new DatabaseMigrationFailedException();
								}
							}
						}
					}
				} catch (Exception e) {
					logger.debug("Nonce database migration FAILED");
					logger.error("Exception", e);
					FileUtil.deleteFileOrWarn(newDatabaseFile, "New Nonce Database File", logger);
					throw new DatabaseMigrationFailedException();
				}
			} else {
				logger.debug("Delete old format nonce database");
				FileUtil.deleteFileOrWarn(oldDatabaseFile, "Old Nonce Database File", logger);
			}
		}
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
