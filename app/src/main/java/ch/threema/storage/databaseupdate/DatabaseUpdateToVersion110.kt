/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.storage.databaseupdate

import android.database.Cursor
import ch.threema.base.crypto.NaCl
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.types.Identity
import net.zetetic.database.sqlcipher.SQLiteDatabase

private val logger = getThreemaLogger("DatabaseUpdateToVersion110")

class DatabaseUpdateToVersion110(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        // Get all problematic identities that have a public key with invalid length and that are invalid.
        val problematicIdentities = sqLiteDatabase.query(
            "SELECT `identity` FROM `contacts` WHERE length(cast(publicKey as blob)) != ? AND state = ?",
            arrayOf(NaCl.PUBLIC_KEY_BYTES, "INVALID"),
        ).toIdentities()

        // We can delete contacts if there are no 1:1 messages. Contacts where group messages still exist can be deleted as the group messages can
        // exist even without the contact being in the database. Note that this is ok because multi device cannot be active as the linking would have
        // failed with such contacts.
        val deletableIdentities = problematicIdentities.filter { !hasOneToOneMessages(it) }

        deletableIdentities.forEach { identityToDelete ->
            // Remove the contact from any groups.
            val numberOfGroups = sqLiteDatabase.delete(
                table = "group_member",
                whereClause = "identity = ?",
                whereArgs = arrayOf(identityToDelete),
            )
            logger.info("Removed contact {} from {} groups", identityToDelete, numberOfGroups)

            // Remove it also from distribution lists
            val numberOfDistributionLists = sqLiteDatabase.delete(
                table = "distribution_list_member",
                whereClause = "identity = ?",
                whereArgs = arrayOf(identityToDelete),
            )
            logger.info("Removed contact {} from {} distribution lists", identityToDelete, numberOfDistributionLists)

            // Remove the contact itself
            sqLiteDatabase.delete(
                table = "contacts",
                whereClause = "identity = ?",
                whereArgs = arrayOf(identityToDelete),
            )

            logger.info("Removed contact {}", identityToDelete)
        }

        if (deletableIdentities.isEmpty()) {
            logger.info("No contacts have been removed")
        }
    }

    private fun Cursor.toIdentities(): Set<Identity> {
        val identities = mutableSetOf<String>()
        val identityColumnIndex = getColumnIndexOrThrow("identity")
        while (moveToNext()) {
            identities.add(getString(identityColumnIndex))
        }
        return identities
    }

    private fun hasOneToOneMessages(identity: Identity): Boolean {
        val cursor = sqLiteDatabase.query("SELECT EXISTS(SELECT 1 FROM message WHERE identity = ?)", arrayOf(identity))
        return cursor.moveToFirst() && cursor.getInt(0) == 1
    }

    override fun getVersion() = VERSION

    override fun getDescription() = "remove invalid contacts that have no public key and no messages"

    companion object {
        const val VERSION = 110
    }
}
