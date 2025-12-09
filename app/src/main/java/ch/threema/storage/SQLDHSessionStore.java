/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

import net.zetetic.database.sqlcipher.SQLiteConnection;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.base.utils.Utils;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.fs.DHSessionId;
import ch.threema.domain.fs.KDFRatchet;
import ch.threema.domain.stores.DHSessionStoreException;
import ch.threema.domain.stores.DHSessionStoreInterface;
import ch.threema.domain.taskmanager.ActiveTaskCodec;
import ch.threema.protobuf.csp.e2e.fs.Version;
import ch.threema.storage.databaseupdate.DatabaseUpdate;
import ch.threema.storage.databaseupdate.FSDatabaseUpgradeToVersion2;
import ch.threema.storage.databaseupdate.FSDatabaseUpgradeToVersion3;
import ch.threema.storage.databaseupdate.FSDatabaseUpgradeToVersion4;

import static ch.threema.storage.databaseupdate.DatabaseUpdateKt.getFullDescription;

public class SQLDHSessionStore extends PermanentlyCloseableSQLiteOpenHelper implements DHSessionStoreInterface {
    private static final Logger logger = getThreemaLogger("SQLDHSessionStore");

    public static final String DATABASE_NAME = "threema-fs.db";
    private static final int DATABASE_VERSION = FSDatabaseUpgradeToVersion4.VERSION;
    private static final String SESSION_TABLE = "session";

    public static final String COLUMN_MY_IDENTITY = "myIdentity";
    public static final String COLUMN_PEER_IDENTITY = "peerIdentity";
    public static final String COLUMN_SESSION_ID = "sessionId";
    // Note: Should be named `myCurrentVersion_4dh` but it's too late now
    public static final String COLUMN_MY_CURRENT_VERSION_4_DH = "negotiatedVersion";
    public static final String COLUMN_LAST_OUTGOING_MESSAGE_TIMESTAMP = "lastOutgoingMessageTimestamp";
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

    @Nullable
    private DHSessionStoreErrorHandler errorHandler = null;

    public SQLDHSessionStore(
        final Context context,
        final byte[] password,
        final String dbName
    ) {
        super(
            context,
            dbName,
            password,
            DATABASE_VERSION,
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
            false
        );

        System.loadLibrary("sqlcipher");
    }

    public SQLDHSessionStore(
        final Context context,
        final byte[] password
    ) {
        this(context, password, DATABASE_NAME);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE `" + SESSION_TABLE + "` (" +
            "`" + COLUMN_MY_IDENTITY + "` TEXT, " +
            "`" + COLUMN_PEER_IDENTITY + "` TEXT, " +
            "`" + COLUMN_SESSION_ID + "` BLOB, " +
            "`" + COLUMN_MY_CURRENT_VERSION_4_DH + "` INTEGER, " +
            "`" + COLUMN_LAST_OUTGOING_MESSAGE_TIMESTAMP + "` INTEGER DEFAULT 0, " +
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
            runDatabaseUpdate(new FSDatabaseUpgradeToVersion2(db));
        }
        if (oldVersion < FSDatabaseUpgradeToVersion3.VERSION) {
            runDatabaseUpdate(new FSDatabaseUpgradeToVersion3(db));
        }
        if (oldVersion < FSDatabaseUpgradeToVersion4.VERSION) {
            runDatabaseUpdate(new FSDatabaseUpgradeToVersion4(db));
        }
    }

    private void runDatabaseUpdate(DatabaseUpdate databaseUpdate) {
        try {
            logger.info("Running DB update to {}", getFullDescription(databaseUpdate));
            databaseUpdate.run();
        } catch (SQLException e) {
            throw new RuntimeException("DB update failed", e);
        }
    }

    @Override
    public void setDHSessionStoreErrorHandler(@NonNull DHSessionStoreErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    @Nullable
    @Override
    public DHSession getDHSession(String myIdentity, String peerIdentity, @Nullable DHSessionId sessionId, @NonNull ActiveTaskCodec handle) throws DHSessionStoreException {
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
                COLUMN_LAST_OUTGOING_MESSAGE_TIMESTAMP,
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
            new String[]{myIdentity, peerIdentity},
            null,
            null,
            null
        )) {

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    return dhSessionFromCursor(cursor, handle);
                }
            }

            return null;
        } catch (SQLException e) {
            throw new DHSessionStoreException("Cannot load session", e);
        }
    }

    @Nullable
    @Override
    public DHSession getBestDHSession(String myIdentity, String peerIdentity, @NonNull ActiveTaskCodec handle) throws DHSessionStoreException {
        String selection = COLUMN_MY_IDENTITY + "=? and " + COLUMN_PEER_IDENTITY + "=?";

        try (Cursor cursor = this.getReadableDatabase().query(
            SESSION_TABLE,
            new String[]{
                COLUMN_SESSION_ID,
                COLUMN_MY_IDENTITY,
                COLUMN_PEER_IDENTITY,
                COLUMN_MY_CURRENT_VERSION_4_DH,
                COLUMN_LAST_OUTGOING_MESSAGE_TIMESTAMP,
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
            new String[]{myIdentity, peerIdentity},
            null,
            null,
            "iif(" + COLUMN_MY_CURRENT_CHAIN_KEY_4_DH + " is not null, 1, 0) desc, " + COLUMN_SESSION_ID + " asc"
        )) {

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    return dhSessionFromCursor(cursor, handle);
                }
            }

            return null;
        } catch (SQLException e) {
            throw new DHSessionStoreException("Cannot load session", e);
        }
    }

    @NonNull
    @Override
    public List<DHSession> getAllDHSessions(
        @NonNull String myIdentity,
        @NonNull String peerIdentity,
        @NonNull ActiveTaskCodec handle
    ) throws DHSessionStoreException {
        String selection = COLUMN_MY_IDENTITY + "=? and " + COLUMN_PEER_IDENTITY + "=?";
        try (Cursor cursor = this.getReadableDatabase().query(
            SESSION_TABLE,
            new String[]{
                COLUMN_SESSION_ID,
                COLUMN_MY_IDENTITY,
                COLUMN_PEER_IDENTITY,
                COLUMN_MY_CURRENT_VERSION_4_DH,
                COLUMN_LAST_OUTGOING_MESSAGE_TIMESTAMP,
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
            new String[]{myIdentity, peerIdentity},
            null,
            null,
            null
        )) {

            if (cursor != null) {
                List<DHSession> sessions = new ArrayList<>(cursor.getCount());

                while (cursor.moveToNext()) {
                    sessions.add(dhSessionFromCursor(cursor, handle));
                }

                return sessions;
            }

            return new ArrayList<>(0);
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
        cv.put(COLUMN_LAST_OUTGOING_MESSAGE_TIMESTAMP, session.getLastOutgoingMessageTimestamp());

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
                new String[]{myIdentity, peerIdentity});
            return numDeleted > 0;
        } catch (SQLException e) {
            throw new DHSessionStoreException("Cannot delete record", e);
        }
    }

    @Override
    public int deleteAllDHSessions(String myIdentity, String peerIdentity) throws DHSessionStoreException {
        try {
            return this.getWritableDatabase().delete(SESSION_TABLE, COLUMN_MY_IDENTITY + "=? and " + COLUMN_PEER_IDENTITY + "=?", new Object[]{
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

    private DHSession dhSessionFromCursor(Cursor cursor, @NonNull ActiveTaskCodec handle) throws DHSessionStoreException {
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
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LAST_OUTGOING_MESSAGE_TIMESTAMP)),
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
                errorHandler.onInvalidDHSessionState(peerIdentity, sessionId, handle);
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
