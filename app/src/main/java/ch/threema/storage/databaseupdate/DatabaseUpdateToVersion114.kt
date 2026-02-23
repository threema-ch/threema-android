package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

class DatabaseUpdateToVersion114(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        // dropping the tables also removes the indices: https://www.sqlite.org/lang_droptable.html
        listOf(
            "group_invite_model",
            "incoming_group_join_request",
            "group_join_request",
        ).forEach { table ->
            sqLiteDatabase.rawExecSQL("DROP TABLE IF EXISTS `$table`;")
        }
    }

    override fun getVersion() = VERSION

    override fun getDescription() = "remove data related to group join/invite"

    companion object {
        const val VERSION = 114
    }
}
