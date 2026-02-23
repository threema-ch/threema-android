package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion100(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        sqLiteDatabase.execSQL(
            "CREATE TABLE IF NOT EXISTS `contact_edit_history_entries` (" +
                "`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`messageUid` VARCHAR NOT NULL, " +
                "`messageId` INTEGER NOT NULL, " +
                "`text` VARCHAR NOT NULL, " +
                "`editedAt` DATETIME NOT NULL, " +
                "CONSTRAINT fk_contact_message_id FOREIGN KEY(messageId) " +
                "REFERENCES message (id) ON UPDATE CASCADE ON DELETE CASCADE " +
                ")",
        )

        sqLiteDatabase.execSQL(
            "CREATE TABLE IF NOT EXISTS `group_edit_history_entries` (" +
                "`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`messageUid` VARCHAR NOT NULL, " +
                "`messageId` INTEGER NOT NULL, " +
                "`text` VARCHAR NOT NULL, " +
                "`editedAt` DATETIME NOT NULL, " +
                "CONSTRAINT fk_group_message_id FOREIGN KEY(messageId) " +
                "REFERENCES m_group_message (id) ON UPDATE CASCADE ON DELETE CASCADE " +
                ")",
        )
    }

    override fun getDescription() = "create edit message history entries tables"

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 100
    }
}
