/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.storage

import android.content.Context
import android.database.SQLException
import ch.threema.base.crypto.HashedNonce
import ch.threema.base.crypto.Nonce
import ch.threema.base.crypto.NonceScope
import ch.threema.base.crypto.NonceStore
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.toHexString
import ch.threema.domain.stores.IdentityStoreInterface
import com.neilalexander.jnacl.NaCl
import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import net.zetetic.database.sqlcipher.SQLiteOpenHelper

private val logger = LoggingUtil.getThreemaLogger("DatabaseNonceStore")

class DatabaseNonceStore(
    context: Context,
    private val identityStore: IdentityStoreInterface,
    databaseName: String,
) : NonceStore, SQLiteOpenHelper(
    context,
    databaseName,
    "",
    null,
    DATABASE_VERSION,
    0,
    null,
    object : SQLiteDatabaseHook {
        override fun preKey(connection: SQLiteConnection?) {
            // not used
        }

        override fun postKey(connection: SQLiteConnection?) {
            // turn off memory wiping for now due to https://github.com/sqlcipher/android-database-sqlcipher/issues/411
            connection!!.execute("PRAGMA cipher_memory_security = OFF;", emptyArray(), null)
        }
    },
    false,
) {
    constructor(
        context: Context,
        identityStore: IdentityStoreInterface,
    ) : this(context, identityStore, DATABASE_NAME_V4)

    companion object {
        const val DATABASE_NAME_V4 = "threema-nonce-blob4.db"

        // Versions:
        //  1: initial
        //  2: add nonce scope
        const val DATABASE_VERSION = 2

        private const val TABLE_NAME_CSP = "nonce_csp"
        private const val TABLE_NAME_D2D = "nonce_d2d"

        private const val COLUMN_NONCE = "nonce"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db!!.execSQL(
            "CREATE TABLE `$TABLE_NAME_CSP` (" +
                "`$COLUMN_NONCE` BLOB NOT NULL PRIMARY KEY);",
        )

        db.execSQL(
            "CREATE TABLE `$TABLE_NAME_D2D` (" +
                "`$COLUMN_NONCE` BLOB NOT NULL PRIMARY KEY);",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        logger.info("Upgrade nonce database from {} -> {}", oldVersion, newVersion)
        if (oldVersion < 2) {
            migrateToVersion2(db!!)
        }
    }

    fun executeNull() {
        try {
            readableDatabase.rawQuery("SELECT NULL").close()
        } catch (e: SQLException) {
            logger.error("Unable to execute initial query", e)
        }
    }

    override fun exists(scope: NonceScope, nonce: Nonce): Boolean {
        val tableName = scope.getTableName()
        return readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM `$tableName` WHERE nonce=? OR nonce=?;",
            nonce.bytes,
            nonce.hashIfIdentityAvailable(),
        ).use {
            if (it.moveToFirst()) {
                it.getInt(0) > 0
            } else {
                false
            }
        }
    }

    override fun store(scope: NonceScope, nonce: Nonce): Boolean {
        val insertNonce = createInsertNonce(scope, writableDatabase)
        if (logger.isTraceEnabled) {
            logger.trace("Store nonce {} for scope {}", nonce.bytes.toHexString(), scope)
        }
        val identity = identityStore.identity
        check(identity != null) {
            logger.error("Cannot store hashed nonce if identity is null")
        }
        return insertNonce(nonce.hashNonce(identity))
    }

    override fun getCount(scope: NonceScope): Long {
        val tableName = scope.getTableName()
        return readableDatabase
            .rawQuery("SELECT COUNT(*) FROM `$tableName`;")
            .use {
                if (it.moveToFirst()) {
                    it.getLong(0)
                } else {
                    0
                }
            }
    }

    override fun getAllHashedNonces(scope: NonceScope): List<HashedNonce> {
        val nonceCount = getCount(scope).toInt()
        val nonces = ArrayList<HashedNonce>(nonceCount)
        addHashedNoncesChunk(scope, nonceCount, 0, nonces)
        return nonces
    }

    override fun addHashedNoncesChunk(
        scope: NonceScope,
        chunkSize: Int,
        offset: Int,
        hashedNonces: MutableList<HashedNonce>,
    ) {
        val tableName = scope.getTableName()
        val rawNonces = mutableListOf<Nonce>()
        val invalidNonces = mutableListOf<ByteArray>()
        readableDatabase
            .rawQuery(
                "SELECT `$COLUMN_NONCE` FROM `$tableName` LIMIT ? OFFSET ?",
                chunkSize,
                offset,
            )
            .use {
                while (it.moveToNext()) {
                    val nonceBytes = it.getBlob(0)
                    when (nonceBytes.size) {
                        // In case the bytes has the length of a raw nonce, we treat it as a raw nonce
                        NaCl.NONCEBYTES -> rawNonces.add(Nonce(nonceBytes))
                        // Hashed nonces consist of 32 bytes and will therefore be added to the hashed nonce list
                        32 -> hashedNonces.add(HashedNonce(nonceBytes))
                        // If the bytes have a different length, we can safely discard them
                        else -> invalidNonces.add(nonceBytes)
                    }
                }
            }

        removeInvalidNonces(scope, invalidNonces.toSet())

        val newlyHashedNonces = hashAndReplaceRawNonces(scope, rawNonces.toSet())
        hashedNonces.addAll(newlyHashedNonces)
    }

    private fun removeInvalidNonces(scope: NonceScope, invalidNonces: Set<ByteArray>) {
        if (invalidNonces.isEmpty()) {
            return
        }

        logger.warn("Remove {} invalid nonces", invalidNonces.size)
        removeNonces(scope, invalidNonces)
    }

    private fun hashAndReplaceRawNonces(scope: NonceScope, rawNonces: Set<Nonce>): Set<HashedNonce> {
        if (rawNonces.isEmpty()) {
            return emptySet()
        }

        val identity = identityStore.identity
        if (identity == null) {
            logger.error("Cannot hash and replace raw nonces if identity is null")
            return emptySet()
        }

        logger.info("Hash and replace {} raw nonces", rawNonces.size)
        val hashedNonces = rawNonces.map { rawNonce -> rawNonce.hashNonce(identity) }

        // Insert hashed nonces into database again
        val insertionSuccess = insertHashedNonces(scope, hashedNonces)

        if (!insertionSuccess) {
            logger.warn("Could not insert hashed nonces into database")
            return hashedNonces.toSet()
        }

        // Remove raw nonces
        removeNonces(scope, rawNonces.map(Nonce::bytes).toSet())

        return hashedNonces.toSet()
    }

    private fun Nonce.hashIfIdentityAvailable(): ByteArray {
        val identity = identityStore.identity
        return if (identity == null) {
            logger.warn("Cannot hash nonce as identity is not available")
            // Just return the nonce bytes as it is not possible to hash them without identity
            this.bytes
        } else {
            // Hash the nonce and return the bytes
            hashNonce(identity).bytes
        }
    }

    private fun createInsertNonce(
        scope: NonceScope,
        database: SQLiteDatabase,
    ): (nonce: HashedNonce) -> Boolean {
        val tableName = scope.getTableName()
        val statement = database.compileStatement("INSERT INTO $tableName VALUES (?)")
        return { nonce ->
            try {
                statement.bindBlob(1, nonce.bytes)
                statement.executeInsert() >= 0
            } catch (e: SQLException) {
                logger.warn("Could not insert nonce", e)
                false
            } finally {
                statement.clearBindings()
            }
        }
    }

    private fun createRemoveNonce(
        scope: NonceScope,
        database: SQLiteDatabase,
    ): (nonceBytes: ByteArray) -> Unit {
        val tableName = scope.getTableName()
        val statement = database.compileStatement("DELETE FROM $tableName WHERE $COLUMN_NONCE = ?")
        return { nonce ->
            try {
                statement.bindBlob(1, nonce)
                statement.executeRaw()
            } catch (e: SQLException) {
                logger.warn("Could not remove nonce", e)
            } finally {
                statement.clearBindings()
            }
        }
    }

    override fun insertHashedNonces(scope: NonceScope, nonces: List<HashedNonce>): Boolean {
        val database = writableDatabase
        database.beginTransaction()
        return try {
            val insertNonce = createInsertNonce(scope, database)
            nonces
                .map { insertNonce(it) }
                .all { it }
                .also {
                    database.setTransactionSuccessful()
                }
        } finally {
            database.endTransaction()
        }
    }

    /**
     * Note: Only use this if the nonces already have been hashed and inserted.
     */
    private fun removeNonces(scope: NonceScope, nonces: Set<ByteArray>) {
        val database = writableDatabase
        database.beginTransaction()
        try {
            val removeNonce = createRemoveNonce(scope, database)
            nonces.forEach { removeNonce(it) }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private fun NonceScope.getTableName(): String {
        return when (this) {
            NonceScope.CSP -> TABLE_NAME_CSP
            NonceScope.D2D -> TABLE_NAME_D2D
        }
    }

    private fun migrateToVersion2(db: SQLiteDatabase) {
        logger.info("- Different tables for csp- and d2d-nonces")
        db.beginTransaction()
        try {
            db.execSQL("ALTER TABLE `threema_nonce` RENAME TO `nonce_csp`;")
            db.execSQL("CREATE TABLE `nonce_d2d` (`nonce` BLOB NOT NULL PRIMARY KEY);")
            logger.info("- Nonce scope added and data migrated")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            logger.info("- Nonce scope added and data migrated")
        }
    }
}
