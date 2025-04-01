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

package ch.threema.storage.factories

import ch.threema.data.storage.DbEditHistoryEntry.Companion.COLUMN_EDITED_AT
import ch.threema.data.storage.DbEditHistoryEntry.Companion.COLUMN_MESSAGE_ID
import ch.threema.data.storage.DbEditHistoryEntry.Companion.COLUMN_MESSAGE_UID
import ch.threema.data.storage.DbEditHistoryEntry.Companion.COLUMN_TEXT
import ch.threema.data.storage.DbEditHistoryEntry.Companion.COLUMN_UID
import ch.threema.storage.DatabaseServiceNew
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel

abstract class EditHistoryEntryModelFactory(dbService: DatabaseServiceNew, tableName: String) :
    ModelFactory(dbService, tableName) {

    // This statement represents the first version if this table
    // It has been changed. See SystemUpdateToVersion102
    override fun getStatements(): Array<String> = arrayOf(
        "CREATE TABLE IF NOT EXISTS `$tableName` (" +
            "`$COLUMN_UID` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`$COLUMN_MESSAGE_UID` VARCHAR NOT NULL, " +
            "`$COLUMN_MESSAGE_ID` INTEGER NOT NULL, " +
            "`$COLUMN_TEXT` VARCHAR DEFAULT NULL, " +
            "`$COLUMN_EDITED_AT` DATETIME NOT NULL, " +
            getConstraints() +
            ")"
    )

    protected abstract fun getConstraints(): String
}

class ContactEditHistoryEntryModelFactory(dbService: DatabaseServiceNew) :
    EditHistoryEntryModelFactory(dbService, TABLE) {

    companion object {
        const val TABLE = "contact_edit_history_entries"
    }

    // This statement represents the first version if this constraint
    // It has been changed. See SystemUpdateToVersion102
    override fun getConstraints(): String {
        return "CONSTRAINT fk_contact_message_id_new FOREIGN KEY($COLUMN_MESSAGE_ID) " +
            "REFERENCES ${MessageModel.TABLE} (${MessageModel.COLUMN_ID}) ON UPDATE CASCADE ON DELETE CASCADE "
    }
}

class GroupEditHistoryEntryModelFactory(dbService: DatabaseServiceNew) :
    EditHistoryEntryModelFactory(dbService, TABLE) {

    companion object {
        const val TABLE = "group_edit_history_entries"
    }

    // This statement represents the first version if this constraint
    // It has been changed. See SystemUpdateToVersion102
    override fun getConstraints(): String {
        return "CONSTRAINT fk_group_message_id_new FOREIGN KEY($COLUMN_MESSAGE_ID) " +
            "REFERENCES ${GroupMessageModel.TABLE} (${GroupMessageModel.COLUMN_ID}) ON UPDATE CASCADE ON DELETE CASCADE "
    }
}
