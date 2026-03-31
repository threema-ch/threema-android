package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion88(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS `m_group_message_pending_msg_id`")
    }

    override fun getDescription() = "remove pending group message table"

    override val version = 88
}
