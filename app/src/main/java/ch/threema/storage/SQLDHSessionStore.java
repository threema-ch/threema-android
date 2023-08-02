/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2023 Threema GmbH
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
import android.database.Cursor;
import android.database.SQLException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.zetetic.database.sqlcipher.SQLiteConnection;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import org.slf4j.Logger;

import ch.threema.app.services.UpdateSystemService;
import ch.threema.app.services.systemupdate.FSDatabaseUpgradeToVersion2;
import ch.threema.app.services.systemupdate.FSDatabaseUpgradeToVersion3;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.fs.KDFRatchet;
import ch.threema.domain.stores.DHSessionStoreException;
import ch.threema.domain.stores.DHSessionStoreInterface;
import ch.threema.protobuf.csp.e2e.fs.Version;

public class SQLDHSessionStore extends SQLiteOpenHelper implements DHSessionStoreInterface {
	private static final Logger logger = LoggingUtil.getThreemaLogger("SQLDHSessionStore");

	public static final String DATABASE_NAME = "threema-fs.db";
	private static final int DATABASE_VERSION = FSDatabaseUpgradeToVersion3.VERSION;
	private static final String SESSION_TABLE = "session";

	public static final String COLUMN_MY_IDENTITY = "myIdentity";
	public static final String COLUMN_PEER_IDENTITY = "peerIdentity";
	public static final String COLUMN_SESSION_ID = "sessionId";
	// Note: Should be named `myCurrentVersion_4dh` but it's too late now
	public static final String COLUMN_MY_CURRENT_VERSION_4_DH = "negotiatedVersion";
	public static final String COLUMN_MY_CURRENT_CHAIN_KEY_2_DH = "myCurrentChainKey_2dh";
	public static final String COLUMN_MY_COUNTER_2_DH = "myCounter_2dh";
	public static final String COLUMN_MY_CURRENT_CHAIN_KEY_4_DH = "myCurrentChainKey_4dh";
	public static final String COLUMN_MY_COUNTER_4_DH = "myCounter_4dh";
	public static final String COLUMN_PEER_CURRENT_VERSION_4_DH = "peerCurrentVersion_4dh";
	public static final String COLUMN_PEER_CURRENT_CHAIN_KEY_2_DH = "peerCurrentChainKey_2dh";
	public static final String COLUMN_PEER_COUNTER_2_DH = "peerCounter_2dh";
	public static final String COLUMN_PEER_CURRENT_CHAIN_KEY_4_DH = "peerCurrentChainKey_4dh";
	public static final String COLUMN_PEER_COUNTER_4_DH = "peerCounter_4dh";
	public static final String COLUMN_MY_EPHEMERAL_PRIVATE_KEY = "myEphemeralPrivateKey";
	public static final String COLUMN_MY_EPHEMERAL_PUBLIC_KEY = "myEphemeralPublicKey";

	@NonNull
	private final UpdateSystemService updateSystemService;

	@Nullable
	private DHSessionStoreErrorHandler errorHandler = null;

	public SQLDHSessionStore(
		final Context context,
		final byte[] databaseKey,
		final String dbName,
		@NonNull UpdateSystemService updateSystemService
	) {
		super(
			context,
			dbName,
			databaseKey,
			null,
			DATABASE_VERSION,
			0,
			null,
			new SQLiteDatabaseHook() {
				@Override
				public void preKey(SQLiteConnection connection) {
					connection.execute("PRAGMA cipher_default_kdf_iter = 1;", new Object[]{}, null);
				}

				@Override
				public void postKey(SQLiteConnection connection) {
					connection.execute("PRAGMA kdf_iter = 1;", new Object[]{}, null);
				}
			},
			false);

		this.updateSystemService = updateSystemService;

		System.loadLibrary("sqlcipher");
	}

	public SQLDHSessionStore(
		final Context context,
		final byte[] databaseKey,
		@NonNull final UpdateSystemService updateSystemService
	) {
		this(context, databaseKey, DATABASE_NAME, updateSystemService);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE `" + SESSION_TABLE + "` (" +
			"`" + COLUMN_MY_IDENTITY + "` TEXT, " +
			"`" + COLUMN_PEER_IDENTITY + "` TEXT, " +
			"`" + COLUMN_SESSION_ID + "` BLOB, " +
			"`" + COLUMN_MY_CURRENT_VERSION_4_DH + "` INTEGER, " +
			"`" + COLUMN_MY_CURRENT_CHAIN_KEY_2_DH + "` BLOB, " +
			"`" + COLUMN_MY_COUNTER_2_DH + "` INTEGER, " +
			"`" + COLUMN_MY_CURRENT_CHAIN_KEY_4_DH + "` BLOB, " +
			"`" + COLUMN_MY_COUNTER_4_DH + "` INTEGER, " +
			"`" + COLUMN_PEER_CURRENT_VERSION_4_DH + "` INTEGER, " +
			"`" + COLUMN_PEER_CURRENT_CHAIN_KEY_2_DH + "` BLOB, " +
			"`" + COLUMN_PEER_COUNTER_2_DH + "` INTEGER, " +
			"`" + COLUMN_PEER_CURRENT_CHAIN_KEY_4_DH + "` BLOB, " +
			"`" + COLUMN_PEER_COUNTER_4_DH + "` INTEGER, " +
			"`" + COLUMN_MY_EPHEMERAL_PRIVATE_KEY + "` BLOB, " +
			"`" + COLUMN_MY_EPHEMERAL_PUBLIC_KEY + "` BLOB, " +
			"PRIMARY KEY(" + COLUMN_MY_IDENTITY + ", " + COLUMN_PEER_IDENTITY + ", " + COLUMN_SESSION_ID + ")" +
			")");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		logger.info("Upgrading DH session database from {} to {}", oldVersion, newVersion);

		if (oldVersion < FSDatabaseUpgradeToVersion2.VERSION) {
			updateSystemService.addUpdate(new FSDatabaseUpgradeToVersion2(db));
		}
		if (oldVersion < FSDatabaseUpgradeToVersion3.VERSION) {
			updateSystemService.addUpdate(new FSDatabaseUpgradeToVersion3(db));
		}
	}

	@Override
	public void setDHSessionStoreErrorHandler(@NonNull DHSessionStoreErrorHandler errorHandler) {
		this.errorHandler = errorHandler;
	}

	@Nullable
	@Override
	public DHSession getDHSession(String myIdentity, String peerIdentity, @Nullable DHSessionId sessionId) throws DHSessionStoreException {
		String selection = COLUMN_MY_IDENTITY + "=? and " + COLUMN_PEER_IDENTITY + "=?";

		if (sessionId != null) {
			selection += " and " + COLUMN_SESSION_ID + "=x'" + Utils.byteArrayToHexString(sessionId.get()) + "'";
		}

		try (Cursor cursor = this.getReadableDatabase().query(
			SESSION_TABLE,
			new String[]{
				COLUMN_SESSION_ID,
				COLUMN_MY_IDENTITY,
				COLUMN_PEER_IDENTITY,
				COLUMN_MY_CURRENT_VERSION_4_DH,
				COLUMN_MY_EPHEMERAL_PRIVATE_KEY,
				COLUMN_MY_EPHEMERAL_PUBLIC_KEY,
				COLUMN_MY_CURRENT_CHAIN_KEY_2_DH,
				COLUMN_MY_COUNTER_2_DH,
				COLUMN_MY_CURRENT_CHAIN_KEY_4_DH,
				COLUMN_MY_COUNTER_4_DH,
				COLUMN_PEER_CURRENT_VERSION_4_DH,
				COLUMN_PEER_CURRENT_CHAIN_KEY_2_DH,
				COLUMN_PEER_COUNTER_2_DH,
				COLUMN_PEER_CURRENT_CHAIN_KEY_4_DH,
				COLUMN_PEER_COUNTER_4_DH
			},
			selection,
			new String[] { myIdentity, peerIdentity },
			null,
			null,
			null
		)) {

			if (cursor != null) {
				if (cursor.moveToFirst()) {
					return dhSessionFromCursor(cursor);
				}
			}

			return null;
		} catch (SQLException e) {
			throw new DHSessionStoreException("Cannot load session", e);
		}
	}

	@Nullable
	@Override
	public DHSession getBestDHSession(String myIdentity, String peerIdentity) throws DHSessionStoreException {
		String selection = COLUMN_MY_IDENTITY + "=? and " + COLUMN_PEER_IDENTITY + "=?";

		try (Cursor cursor = this.getReadableDatabase().query(
			SESSION_TABLE,
			new String[]{
				COLUMN_SESSION_ID,
				COLUMN_MY_IDENTITY,
				COLUMN_PEER_IDENTITY,
				COLUMN_MY_CURRENT_VERSION_4_DH,
				COLUMN_MY_EPHEMERAL_PRIVATE_KEY,
				COLUMN_MY_EPHEMERAL_PUBLIC_KEY,
				COLUMN_MY_CURRENT_CHAIN_KEY_2_DH,
				COLUMN_MY_COUNTER_2_DH,
				COLUMN_MY_CURRENT_CHAIN_KEY_4_DH,
				COLUMN_MY_COUNTER_4_DH,
				COLUMN_PEER_CURRENT_VERSION_4_DH,
				COLUMN_PEER_CURRENT_CHAIN_KEY_2_DH,
				COLUMN_PEER_COUNTER_2_DH,
				COLUMN_PEER_CURRENT_CHAIN_KEY_4_DH,
				COLUMN_PEER_COUNTER_4_DH
			},
			selection,
			new String[] { myIdentity, peerIdentity },
			null,
			null,
			"iif(" + COLUMN_MY_CURRENT_CHAIN_KEY_4_DH + " is not null, 1, 0) desc, " + COLUMN_SESSION_ID + " asc"
		)) {

			if (cursor != null) {
				if (cursor.moveToFirst()) {
					return dhSessionFromCursor(cursor);
				}
			}

			return null;
		} catch (SQLException e) {
			throw new DHSessionStoreException("Cannot load session", e);
		}
	}

	@Override
	public void storeDHSession(DHSession session) throws DHSessionStoreException {
		DHSession.DHVersions current4DHVersions = session.getCurrent4DHVersions();

		ContentValues cv = new ContentValues();
		cv.put(COLUMN_MY_IDENTITY, session.getMyIdentity());
		cv.put(COLUMN_PEER_IDENTITY, session.getPeerIdentity());
		cv.put(COLUMN_SESSION_ID, session.getId().get());
		cv.put(COLUMN_MY_CURRENT_VERSION_4_DH, current4DHVersions == null ? null : current4DHVersions.local.getNumber());
		cv.put(COLUMN_PEER_CURRENT_VERSION_4_DH, current4DHVersions == null ? null : current4DHVersions.remote.getNumber());

		addMy2DHRatchet(cv, session.getMyRatchet2DH());
		addMy4DHRatchet(cv, session.getMyRatchet4DH());
		addPeer2DHRatchet(cv, session.getPeerRatchet2DH());
		addPeer4DHRatchet(cv, session.getPeerRatchet4DH());

		if (session.getMyEphemeralPrivateKey() != null) {
			cv.put(COLUMN_MY_EPHEMERAL_PRIVATE_KEY, session.getMyEphemeralPrivateKey());
		}
		cv.put(COLUMN_MY_EPHEMERAL_PUBLIC_KEY, session.getMyEphemeralPublicKey());

		try {
			this.getWritableDatabase()
				.replaceOrThrow(SESSION_TABLE, null, cv);
		} catch (SQLException e) {
			throw new DHSessionStoreException("Cannot insert record", e);
		}
	}

	@Override
	public boolean deleteDHSession(String myIdentity, String peerIdentity, DHSessionId sessionId) throws DHSessionStoreException {
		try {
			int numDeleted = this.getWritableDatabase().delete(SESSION_TABLE,
				COLUMN_MY_IDENTITY + "=? and " + COLUMN_PEER_IDENTITY + "=? and " + COLUMN_SESSION_ID + "=x'" + Utils.byteArrayToHexString(sessionId.get()) + "'",
				new String[] { myIdentity, peerIdentity });
			return numDeleted > 0;
		} catch (SQLException e) {
			throw new DHSessionStoreException("Cannot delete record", e);
		}
	}

	@Override
	public int deleteAllDHSessions(String myIdentity, String peerIdentity) throws DHSessionStoreException {
		try {
			return this.getWritableDatabase().delete(SESSION_TABLE, COLUMN_MY_IDENTITY + "=? and " + COLUMN_PEER_IDENTITY + "=?", new Object[] {
				myIdentity, peerIdentity
			});
		} catch (SQLException e) {
			throw new DHSessionStoreException("Cannot delete record", e);
		}
	}

	@Override
	public int deleteAllSessionsExcept(String myIdentity, String peerIdentity, DHSessionId excludeSessionId, boolean fourDhOnly) throws DHSessionStoreException {
		try {
			String whereClause = COLUMN_MY_IDENTITY + "=? and " + COLUMN_PEER_IDENTITY + "=? and " + COLUMN_SESSION_ID + "!=x'" + Utils.byteArrayToHexString(excludeSessionId.get()) + "'";
			if (fourDhOnly) {
				whereClause += " and " + COLUMN_MY_CURRENT_CHAIN_KEY_4_DH + " is not null";
			}

			return this.getWritableDatabase().delete(SESSION_TABLE, whereClause, new Object[]{
				myIdentity, peerIdentity
			});
		} catch (SQLException e) {
			throw new DHSessionStoreException("Cannot delete record", e);
		}
	}

	@Override
	public void executeNull() {
		try {
			getWritableDatabase().rawQuery("SELECT NULL").close();
		} catch (Exception e) {
			logger.error("Unable to execute initial query", e);
		}
	}

	private DHSession dhSessionFromCursor(Cursor cursor) throws DHSessionStoreException {
		DHSessionId sessionId = null;
		String peerIdentity = null;
		try {
			DHSession.DHVersions current4DHVersions = DHSession.DHVersions.restored(
				Version.forNumber(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MY_CURRENT_VERSION_4_DH))),
				Version.forNumber(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PEER_CURRENT_VERSION_4_DH)))
			);
			sessionId = new DHSessionId(cursor.getBlob(cursor.getColumnIndexOrThrow(COLUMN_SESSION_ID)));
			peerIdentity = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PEER_IDENTITY));
			return new DHSession(
				sessionId,
				cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MY_IDENTITY)),
				peerIdentity,
				cursor.getBlob(cursor.getColumnIndexOrThrow(COLUMN_MY_EPHEMERAL_PRIVATE_KEY)),
				cursor.getBlob(cursor.getColumnIndexOrThrow(COLUMN_MY_EPHEMERAL_PUBLIC_KEY)),
				current4DHVersions,
				getMy2DHRatchetFromCursor(cursor),
				getMy4DHRatchetFromCursor(cursor),
				getPeer2DHRatchetFromCursor(cursor),
				getPeer4DHRatchetFromCursor(cursor)
			);
		} catch (DHSessionId.InvalidDHSessionIdException e) {
			throw new DHSessionStoreException("Invalid session ID", e);
		} catch (IllegalArgumentException e) {
			// Illegal argument exception is thrown if the column index is invalid
			throw new DHSessionStoreException("Could not load session from store", e);
		} catch (DHSession.IllegalDHSessionStateException e) {
			logger.error("Could not load DH session", e);
			if (errorHandler != null && sessionId != null && peerIdentity != null) {
				errorHandler.onInvalidDHSessionState(peerIdentity, sessionId);
			}
			return null;
		}
	}

	@Nullable
	private KDFRatchet getMy2DHRatchetFromCursor(@NonNull Cursor cursor) {
		int keyColumn = cursor.getColumnIndexOrThrow(COLUMN_MY_CURRENT_CHAIN_KEY_2_DH);
		int counterColumn = cursor.getColumnIndexOrThrow(COLUMN_MY_COUNTER_2_DH);
		return ratchetFromCursor(cursor, keyColumn, counterColumn);
	}

	@Nullable
	private KDFRatchet getMy4DHRatchetFromCursor(@NonNull Cursor cursor) {
		int keyColumn = cursor.getColumnIndexOrThrow(COLUMN_MY_CURRENT_CHAIN_KEY_4_DH);
		int counterColumn = cursor.getColumnIndexOrThrow(COLUMN_MY_COUNTER_4_DH);
		return ratchetFromCursor(cursor, keyColumn, counterColumn);
	}

	@Nullable
	private KDFRatchet getPeer2DHRatchetFromCursor(@NonNull Cursor cursor) {
		int keyColumn = cursor.getColumnIndexOrThrow(COLUMN_PEER_CURRENT_CHAIN_KEY_2_DH);
		int counterColumn = cursor.getColumnIndexOrThrow(COLUMN_PEER_COUNTER_2_DH);
		return ratchetFromCursor(cursor, keyColumn, counterColumn);
	}

	@Nullable
	private KDFRatchet getPeer4DHRatchetFromCursor(@NonNull Cursor cursor) {
		int keyColumn = cursor.getColumnIndexOrThrow(COLUMN_PEER_CURRENT_CHAIN_KEY_4_DH);
		int counterColumn = cursor.getColumnIndexOrThrow(COLUMN_PEER_COUNTER_4_DH);
		return ratchetFromCursor(cursor, keyColumn, counterColumn);
	}

	@Nullable
	private KDFRatchet ratchetFromCursor(@NonNull Cursor cursor, int keyColumn, int counterColumn) {
		if (cursor.getBlob(keyColumn) != null) {
			return new KDFRatchet(cursor.getInt(counterColumn), cursor.getBlob(keyColumn));
		}
		return null;
	}

	private void addMy2DHRatchet(@NonNull ContentValues cv, @Nullable KDFRatchet ratchet) {
		addRatchetValues(cv, COLUMN_MY_CURRENT_CHAIN_KEY_2_DH, COLUMN_MY_COUNTER_2_DH, ratchet);
	}

	private void addMy4DHRatchet(@NonNull ContentValues cv, @Nullable KDFRatchet ratchet) {
		addRatchetValues(cv, COLUMN_MY_CURRENT_CHAIN_KEY_4_DH, COLUMN_MY_COUNTER_4_DH, ratchet);
	}

	private void addPeer2DHRatchet(@NonNull ContentValues cv, @Nullable KDFRatchet ratchet) {
		addRatchetValues(cv, COLUMN_PEER_CURRENT_CHAIN_KEY_2_DH, COLUMN_PEER_COUNTER_2_DH, ratchet);
	}

	private void addPeer4DHRatchet(@NonNull ContentValues cv, @Nullable KDFRatchet ratchet) {
		addRatchetValues(cv, COLUMN_PEER_CURRENT_CHAIN_KEY_4_DH, COLUMN_PEER_COUNTER_4_DH, ratchet);
	}

	private void addRatchetValues(@NonNull ContentValues cv, @NonNull String keyColumn, @NonNull String counterColumn, @Nullable KDFRatchet ratchet) {
		byte[] chainKey = null;
		Long counter = null;
		if (ratchet != null) {
			chainKey = ratchet.getCurrentChainKey();
			counter = ratchet.getCounter();
		}
		cv.put(keyColumn, chainKey);
		cv.put(counterColumn, counter);
	}
}
