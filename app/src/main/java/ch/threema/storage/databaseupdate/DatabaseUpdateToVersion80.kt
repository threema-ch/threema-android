package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion80(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {

    override fun run() {
        if (!sqLiteDatabase.fieldExists("contacts", "forwardSecurityState")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE `contacts` ADD COLUMN `forwardSecurityState` TINYINT DEFAULT 0")
        }
    }

    override fun getDescription() = "Forward Security State Flag"

    override val version = 80
}
