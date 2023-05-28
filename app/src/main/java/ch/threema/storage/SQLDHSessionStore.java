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

import net.sqlcipher.Cursor;
import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SQLiteOpenHelper;

import androidx.annotation.Nullable;
import ch.threema.base.utils.Utils;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.fs.KDFRatchet;
import ch.threema.domain.stores.DHSessionStoreException;
import ch.threema.domain.stores.DHSessionStoreInterface;

public class SQLDHSessionStore extends SQLiteOpenHelper implements DHSessionStoreInterface {
	private static final String DATABASE_NAME = "threema-fs.db";
	private static final int DATABASE_VERSION = 1;
	private static final String SESSION_TABLE = "session";

	private final byte[] key;

	public SQLDHSessionStore(final Context context, final byte[] databaseKey, final String dbName) {
		super(context, dbName,
			null,
			DATABASE_VERSION,
			new SQLiteDatabaseHook() {
				@Override
				public void preKey(SQLiteDatabase sqLiteDatabase) {
					sqLiteDatabase.rawExecSQL("PRAGMA cipher_default_kdf_iter = 1;");
				}

				@Override
				public void postKey(SQLiteDatabase sqLiteDatabase) {
					sqLiteDatabase.rawExecSQL("PRAGMA kdf_iter = 1;");
				}
			});

		this.key = databaseKey;

		SQLiteDatabase.loadLibs(context);
	}

	public SQLDHSessionStore(final Context context, final byte[] databaseKey) {
		this(context, databaseKey, DATABASE_NAME);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE `" + SESSION_TABLE + "` (" +
			"`myIdentity` TEXT, " +
			"`peerIdentity` TEXT, " +
			"`sessionId` BLOB, " +
			"`myCurrentChainKey_2dh` BLOB, " +
			"`myCounter_2dh` INTEGER, " +
			"`myCurrentChainKey_4dh` BLOB, " +
			"`myCounter_4dh` INTEGER, " +
			"`peerCurrentChainKey_2dh` BLOB, " +
			"`peerCounter_2dh` INTEGER, " +
			"`peerCurrentChainKey_4dh` BLOB, " +
			"`peerCounter_4dh` INTEGER, " +
			"`myEphemeralPrivateKey` BLOB, " +
			"`myEphemeralPublicKey` BLOB, " +
			"PRIMARY KEY(myIdentity, peerIdentity, sessionId)" +
			")");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// No upgrades at this time
	}

	@Nullable
	@Override
	public DHSession getDHSession(String myIdentity, String peerIdentity, @Nullable DHSessionId sessionId) throws DHSessionStoreException {
		try {
			String selection = "myIdentity=? and peerIdentity=?";

			if (sessionId != null) {
				selection += " and sessionId=x'" + Utils.byteArrayToHexString(sessionId.get()) + "'";
			}

			Cursor cursor = this.getReadableDatabase().query(
				SESSION_TABLE,
				new String[]{"sessionId", "myIdentity", "peerIdentity", "myEphemeralPrivateKey", "myEphemeralPublicKey", "myCurrentChainKey_2dh", "myCounter_2dh", "myCurrentChainKey_4dh", "myCounter_4dh", "peerCurrentChainKey_2dh", "peerCounter_2dh", "peerCurrentChainKey_4dh", "peerCounter_4dh"},
				selection,
				new String[] { myIdentity, peerIdentity },
				null,
				null,
				null
			);

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
		try {
			String selection = "myIdentity=? and peerIdentity=?";

			Cursor cursor = this.getReadableDatabase().query(
				SESSION_TABLE,
				new String[]{"sessionId", "myIdentity", "peerIdentity", "myEphemeralPrivateKey", "myEphemeralPublicKey", "myCurrentChainKey_2dh", "myCounter_2dh", "myCurrentChainKey_4dh", "myCounter_4dh", "peerCurrentChainKey_2dh", "peerCounter_2dh", "peerCurrentChainKey_4dh", "peerCounter_4dh"},
				selection,
				new String[] { myIdentity, peerIdentity },
				null,
				null,
				"iif(myCurrentChainKey_4dh is not null, 1, 0) desc, sessionId asc"
			);

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
		ContentValues cv = new ContentValues();
		cv.put("myIdentity", session.getMyIdentity());
		cv.put("peerIdentity", session.getPeerIdentity());
		cv.put("sessionId", session.getId().get());

		addRatchetValues(cv, "my", "_2dh", session.getMyRatchet2DH());
		addRatchetValues(cv, "my", "_4dh", session.getMyRatchet4DH());
		addRatchetValues(cv, "peer", "_2dh", session.getPeerRatchet2DH());
		addRatchetValues(cv, "peer", "_4dh", session.getPeerRatchet4DH());

		if (session.getMyEphemeralPrivateKey() != null) {
			cv.put("myEphemeralPrivateKey", session.getMyEphemeralPrivateKey());
		}
		cv.put("myEphemeralPublicKey", session.getMyEphemeralPublicKey());

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
				"myIdentity=? and peerIdentity=? and sessionId=x'" + Utils.byteArrayToHexString(sessionId.get()) + "'",
				new String[] { myIdentity, peerIdentity });
			return numDeleted > 0;
		} catch (SQLException e) {
			throw new DHSessionStoreException("Cannot delete record", e);
		}
	}

	@Override
	public int deleteAllDHSessions(String myIdentity, String peerIdentity) throws DHSessionStoreException {
		try {
			return this.getWritableDatabase().delete(SESSION_TABLE, "myIdentity=? and peerIdentity=?", new Object[] {
				myIdentity, peerIdentity
			});
		} catch (SQLException e) {
			throw new DHSessionStoreException("Cannot delete record", e);
		}
	}

	@Override
	public int deleteAllSessionsExcept(String myIdentity, String peerIdentity, DHSessionId excludeSessionId, boolean fourDhOnly) throws DHSessionStoreException {
		try {
			String whereClause = "myIdentity=? and peerIdentity=? and sessionId!=x'" + Utils.byteArrayToHexString(excludeSessionId.get()) + "'";
			if (fourDhOnly) {
				whereClause += " and myCurrentChainKey_4dh is not null";
			}

			return this.getWritableDatabase().delete(SESSION_TABLE, whereClause, new Object[]{
				myIdentity, peerIdentity
			});
		} catch (SQLException e) {
			throw new DHSessionStoreException("Cannot delete record", e);
		}
	}

	private DHSession dhSessionFromCursor(Cursor cursor) throws DHSessionStoreException {
		try (cursor) {
			return new DHSession(
				new DHSessionId(cursor.getBlob(0)),
				cursor.getString(1),
				cursor.getString(2),
				cursor.getBlob(3),
				cursor.getBlob(4),
				ratchetFromCursor(cursor, 5, 6),
				ratchetFromCursor(cursor, 7, 8),
				ratchetFromCursor(cursor, 9, 10),
				ratchetFromCursor(cursor, 11, 12)
			);
		} catch (DHSessionId.InvalidDHSessionIdException e) {
			throw new DHSessionStoreException("Invalid session ID", e);
		}
	}

	private void addRatchetValues(ContentValues cv, String prefix, String suffix, @Nullable KDFRatchet ratchet) {
		if (ratchet != null) {
			cv.put(prefix + "CurrentChainKey" + suffix, ratchet.getCurrentChainKey());
			cv.put(prefix + "Counter" + suffix, ratchet.getCounter());
		} else {
			cv.put(prefix + "CurrentChainKey" + suffix, (Byte)null);
			cv.put(prefix + "Counter" + suffix, (Byte)null);
		}
	}


	private synchronized SQLiteDatabase getWritableDatabase() throws SQLiteException {
		return super.getWritableDatabase(this.key);
	}

	private synchronized SQLiteDatabase getReadableDatabase()  {
		return super.getReadableDatabase(this.key);
	}

	private KDFRatchet ratchetFromCursor(Cursor cursor, int keyColumn, int counterColumn) {
		if (cursor.getBlob(keyColumn) != null) {
			return new KDFRatchet(cursor.getInt(counterColumn), cursor.getBlob(keyColumn));
		}
		return null;
	}
}
