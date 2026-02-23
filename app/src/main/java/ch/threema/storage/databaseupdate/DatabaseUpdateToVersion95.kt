package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion95(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        val table = "distribution_list_message"
        if (!sqLiteDatabase.fieldExists(table, "editedAtUtc")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE `$table` ADD COLUMN `editedAtUtc` DATETIME")
        }
    }

    override fun getDescription() = "add editedAt field for distribution messages"

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 95
    }
}
