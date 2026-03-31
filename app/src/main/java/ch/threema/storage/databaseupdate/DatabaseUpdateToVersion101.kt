package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion101(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        if (!sqLiteDatabase.fieldExists("contacts", "jobTitle")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE `contacts` ADD COLUMN `jobTitle` VARCHAR DEFAULT NULL")
        }
        if (!sqLiteDatabase.fieldExists("contacts", "department")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE `contacts` ADD COLUMN `department` VARCHAR DEFAULT NULL")
        }
    }

    override fun getDescription() = "add contacts job title & department field"

    override val version = 101
}
