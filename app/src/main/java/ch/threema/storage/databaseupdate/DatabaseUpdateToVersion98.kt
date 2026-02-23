package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

class DatabaseUpdateToVersion98(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        sqLiteDatabase.execSQL(
            """
                CREATE TABLE `m_group_incoming_sync_request_log`(
                    `groupId` INTEGER,
                    `senderIdentity` VARCHAR,
                    `lastHandledRequest` VARCHAR,
                    PRIMARY KEY(`groupId`, `senderIdentity`),
                    FOREIGN KEY(`groupId`) REFERENCES `m_group`(`id`)
                )
            """,
        )
    }

    override fun getDescription() = "add incoming group sync request log table"

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 98
    }
}
