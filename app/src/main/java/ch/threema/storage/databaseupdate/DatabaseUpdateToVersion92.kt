package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

class DatabaseUpdateToVersion92(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        for (table in arrayOf(
            "message",
            "m_group_message",
            "distribution_list_message",
        )) {
            if (sqLiteDatabase.fieldExists(table, "displayTags")) {
                sqLiteDatabase.execSQL("UPDATE $table SET `displayTags` = 0 WHERE `type` = 12 AND `displayTags` = 1")
            }
        }
    }

    override fun getDescription() = "remove star from fs status messages"

    override val version = 92
}
