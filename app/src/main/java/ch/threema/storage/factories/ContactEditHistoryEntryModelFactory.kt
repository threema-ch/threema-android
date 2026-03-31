package ch.threema.storage.factories

import ch.threema.data.storage.DbEditHistoryEntry.Companion.COLUMN_EDITED_AT
import ch.threema.data.storage.DbEditHistoryEntry.Companion.COLUMN_MESSAGE_ID
import ch.threema.data.storage.DbEditHistoryEntry.Companion.COLUMN_MESSAGE_UID
import ch.threema.data.storage.DbEditHistoryEntry.Companion.COLUMN_TEXT
import ch.threema.data.storage.DbEditHistoryEntry.Companion.COLUMN_UID
import ch.threema.storage.DatabaseCreationProvider
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageModel

class ContactEditHistoryEntryModelFactory(databaseProvider: DatabaseProvider) :
    ModelFactory(databaseProvider, TABLE) {

    object Creator : DatabaseCreationProvider {
        override fun getCreationStatements() = arrayOf(
            "CREATE TABLE IF NOT EXISTS `$TABLE` (" +
                "`$COLUMN_UID` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`$COLUMN_MESSAGE_UID` VARCHAR NOT NULL, " +
                "`$COLUMN_MESSAGE_ID` INTEGER NOT NULL, " +
                "`$COLUMN_TEXT` VARCHAR DEFAULT NULL, " +
                "`$COLUMN_EDITED_AT` DATETIME NOT NULL, " +
                getConstraints() +
                ")",
        )

        private fun getConstraints(): String =
            "CONSTRAINT fk_contact_message_id_new FOREIGN KEY($COLUMN_MESSAGE_ID) " +
                "REFERENCES ${MessageModel.TABLE} (${AbstractMessageModel.COLUMN_ID}) ON UPDATE CASCADE ON DELETE CASCADE "
    }

    companion object {
        const val TABLE = "contact_edit_history_entries"
    }
}
