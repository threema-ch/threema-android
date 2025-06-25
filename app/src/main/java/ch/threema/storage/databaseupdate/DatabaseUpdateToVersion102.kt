/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion102(private val sqLiteDatabase: SQLiteDatabase) :
    DatabaseUpdate {
    override fun run() {
        /**
         *  We want to change the data type of column `text` from `VARCHAR NOT NULL` to `VARCHAR DEFAULT NULL`.
         *  Because SqLite does not support this with the `ALTER TABLE` command we have to do these steps:
         *  1. Create a new table with the new schema (updated column data type)
         *  2. Copy over all data from the existing to the new table
         *  3. Delete the old table
         *  4. Rename the new table to the old tables name
         */

        makeColumnTextFromContactEditHistoryEntriesNullable()
        makeColumnTextFromGroupEditHistoryEntriesNullable()
    }

    /**
     *  Note that there is no way in SqLite to rename a constraint.
     *  So the constraint `fk_contact_message_id_new` has to stay like this.
     */
    private fun makeColumnTextFromContactEditHistoryEntriesNullable() {
        sqLiteDatabase.execSQL(
            """
                CREATE TABLE `contact_edit_history_entries_new` (
                    `uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `messageUid` VARCHAR NOT NULL,
                    `messageId` INTEGER NOT NULL,
                    `text` VARCHAR DEFAULT NULL,
                    `editedAt` DATETIME NOT NULL,
                    CONSTRAINT `fk_contact_message_id_new` FOREIGN KEY(messageId) REFERENCES message (id) ON UPDATE CASCADE ON DELETE CASCADE
                )
                """,
        )
        sqLiteDatabase.execSQL(
            """
                INSERT INTO `contact_edit_history_entries_new` (`uid`, `messageUid`, `messageId`, `text`, `editedAt`)
                    SELECT `uid`, `messageUid`, `messageId`, `text`, `editedAt`
                    FROM `contact_edit_history_entries`
                """,
        )
        sqLiteDatabase.execSQL("DROP TABLE `contact_edit_history_entries`")
        sqLiteDatabase.execSQL("ALTER TABLE `contact_edit_history_entries_new` RENAME TO `contact_edit_history_entries`")
    }

    /**
     *  Note that there is no way in SqLite to rename a constraint.
     *  So the constraint `fk_group_message_id_new` has to stay like this.
     */
    private fun makeColumnTextFromGroupEditHistoryEntriesNullable() {
        sqLiteDatabase.execSQL(
            """
                CREATE TABLE `group_edit_history_entries_new` (
                    `uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `messageUid` VARCHAR NOT NULL,
                    `messageId` INTEGER NOT NULL,
                    `text` VARCHAR DEFAULT NULL,
                    `editedAt` DATETIME NOT NULL,
                    CONSTRAINT fk_group_message_id_new FOREIGN KEY(messageId) REFERENCES m_group_message (id) ON UPDATE CASCADE ON DELETE CASCADE
                )
                """,
        )
        sqLiteDatabase.execSQL(
            """
                INSERT INTO `group_edit_history_entries_new` (`uid`, `messageUid`, `messageId`, `text`, `editedAt`)
                    SELECT `uid`, `messageUid`, `messageId`, `text`, `editedAt`
                    FROM `group_edit_history_entries`
                """,
        )
        sqLiteDatabase.execSQL("DROP TABLE `group_edit_history_entries`")
        sqLiteDatabase.execSQL("ALTER TABLE `group_edit_history_entries_new` RENAME TO `group_edit_history_entries`")
    }

    override fun getDescription() = "made column `text` nullable in tables `contact_edit_history_entries` and `group_edit_history_entries`"

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 102
    }
}
