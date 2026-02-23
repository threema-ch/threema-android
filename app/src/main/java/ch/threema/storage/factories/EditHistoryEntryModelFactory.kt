package ch.threema.storage.factories

import ch.threema.data.storage.DbEditHistoryEntry.Companion.COLUMN_EDITED_AT
import ch.threema.data.storage.DbEditHistoryEntry.Companion.COLUMN_MESSAGE_ID
import ch.threema.data.storage.DbEditHistoryEntry.Companion.COLUMN_MESSAGE_UID
import ch.threema.data.storage.DbEditHistoryEntry.Companion.COLUMN_TEXT
import ch.threema.data.storage.DbEditHistoryEntry.Companion.COLUMN_UID
import ch.threema.storage.DatabaseService
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel

abstract class EditHistoryEntryModelFactory(dbService: DatabaseService, tableName: String) :
    ModelFactory(dbService, tableName) {
    // This statement represents the first version if this table
    // It has been changed. See DatabaseUpdateToVersion102
    override fun getStatements(): Array<String> = arrayOf(
        "CREATE TABLE IF NOT EXISTS `$tableName` (" +
            "`$COLUMN_UID` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`$COLUMN_MESSAGE_UID` VARCHAR NOT NULL, " +
            "`$COLUMN_MESSAGE_ID` INTEGER NOT NULL, " +
            "`$COLUMN_TEXT` VARCHAR DEFAULT NULL, " +
            "`$COLUMN_EDITED_AT` DATETIME NOT NULL, " +
            getConstraints() +
            ")",
    )

    protected abstract fun getConstraints(): String
}

class ContactEditHistoryEntryModelFactory(dbService: DatabaseService) :
    EditHistoryEntryModelFactory(dbService, TABLE) {
    companion object {
        const val TABLE = "contact_edit_history_entries"
    }

    // This statement represents the first version if this constraint
    // It has been changed. See DatabaseUpdateToVersion102
    override fun getConstraints(): String {
        return "CONSTRAINT fk_contact_message_id_new FOREIGN KEY($COLUMN_MESSAGE_ID) " +
            "REFERENCES ${MessageModel.TABLE} (${AbstractMessageModel.COLUMN_ID}) ON UPDATE CASCADE ON DELETE CASCADE "
    }
}

class GroupEditHistoryEntryModelFactory(dbService: DatabaseService) :
    EditHistoryEntryModelFactory(dbService, TABLE) {
    companion object {
        const val TABLE = "group_edit_history_entries"
    }

    // This statement represents the first version if this constraint
    // It has been changed. See DatabaseUpdateToVersion102
    override fun getConstraints(): String {
        return "CONSTRAINT fk_group_message_id_new FOREIGN KEY($COLUMN_MESSAGE_ID) " +
            "REFERENCES ${GroupMessageModel.TABLE} (${AbstractMessageModel.COLUMN_ID}) ON UPDATE CASCADE ON DELETE CASCADE "
    }
}
