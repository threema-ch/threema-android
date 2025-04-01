/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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
import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val logger = LoggingUtil.getThreemaLogger("DatabaseNonceStore")

class DatabaseNonceStore(
    context: Context,
    private val identityStore: IdentityStoreInterface,
    databaseName: String
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
            connection!!.execute("PRAGMA cipher_memory_security = OFF;", arrayOf(), null)
        }
    },
    false
) {
    constructor(
        context: Context,
        identityStore: IdentityStoreInterface
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
                "`$COLUMN_NONCE` BLOB NOT NULL PRIMARY KEY);"
        )

        db.execSQL(
            "CREATE TABLE `$TABLE_NAME_D2D` (" +
                "`$COLUMN_NONCE` BLOB NOT NULL PRIMARY KEY);"
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
            nonce.hashNonce().bytes
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
        return insertNonce(nonce.hashNonce())
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
        nonces: MutableList<HashedNonce>
    ) {
        val tableName = scope.getTableName()
        readableDatabase
            .rawQuery(
                "SELECT `$COLUMN_NONCE` FROM `$tableName` LIMIT ? OFFSET ?",
                chunkSize,
                offset
            )
            .use {
                while (it.moveToNext()) {
                    nonces.add(HashedNonce(it.getBlob(0)))
                }
            }
    }

    private fun createInsertNonce(
        scope: NonceScope,
        database: SQLiteDatabase
    ): (nonce: HashedNonce) -> Boolean {
        val tableName = scope.getTableName()
        val stmt = database.compileStatement("INSERT INTO $tableName VALUES (?)")
        return { nonce ->
            try {
                stmt.bindBlob(1, nonce.bytes)
                stmt.executeInsert() >= 0
            } catch (e: SQLException) {
                logger.warn("Could not insert nonce", e)
                false
            } finally {
                stmt.clearBindings()
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

    private fun NonceScope.getTableName(): String {
        return when (this) {
            NonceScope.CSP -> TABLE_NAME_CSP
            NonceScope.D2D -> TABLE_NAME_D2D
        }
    }

    /**
     * Hash nonce with HMAC-SHA256 using the identity as the key if available.
     * This serves to make it impossible to correlate the nonce DBs of users to
     * determine whether they have been communicating.
     */
    private fun Nonce.hashNonce(): HashedNonce {
        val identity = identityStore.identity
        return if (identity == null) {
            HashedNonce(this.bytes)
        } else {
            try {
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(identity.encodeToByteArray(), "HmacSHA256"))
                HashedNonce(mac.doFinal(this.bytes))
            } catch (e: Exception) {
                when (e) {
                    is NoSuchAlgorithmException, is InvalidKeyException -> throw RuntimeException(e)
                    else -> throw e
                }
            }
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
