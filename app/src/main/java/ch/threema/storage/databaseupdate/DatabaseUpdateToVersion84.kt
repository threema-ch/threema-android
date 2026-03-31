package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion84(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        if (sqLiteDatabase.fieldExists("contacts", "forwardSecurityEnabled")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE `contacts` DROP COLUMN `forwardSecurityEnabled`")
        }
    }

    override fun getDescription() = "remove forward security enabled field"

    override val version = 84
}
