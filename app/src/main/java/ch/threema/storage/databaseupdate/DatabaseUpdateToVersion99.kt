package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

class DatabaseUpdateToVersion99(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        // Create new table with on delete/update actions
        sqLiteDatabase.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `incoming_group_sync_request_log`(
                `groupId` INTEGER,
                `senderIdentity` VARCHAR,
                `lastHandledRequest` DATETIME,
                PRIMARY KEY(`groupId`, `senderIdentity`),
                FOREIGN KEY(`groupId`) REFERENCES `m_group`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """,
        )

        // Drop the old table
        sqLiteDatabase.execSQL("DROP TABLE `m_group_incoming_sync_request_log`")

        // Note that we omit the migration of the content of this table as it is not a problem if
        // the request log is emptied from time to time. The effect is only that a sync request
        // might be answered too early. The purpose of preventing infinite loops is still fulfilled.
        // The migration of the content would be somehow risky because the old table could contain
        // data that violates the foreign key constraint.
    }

    override fun getDescription() = "fix incoming group sync request constraint"

    override val version = 99
}
