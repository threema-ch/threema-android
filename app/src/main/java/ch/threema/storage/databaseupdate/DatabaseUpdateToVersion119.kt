package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion119(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        sqLiteDatabase.rawExecSQL(
            """
                ALTER TABLE `contacts`
                ADD COLUMN `workLastFullSyncAt` DATETIME DEFAULT NULL;
            """.trimIndent(),
        )
    }

    override fun getDescription() = "add field workLastFullSyncAt to contact"

    override val version = 119
}
