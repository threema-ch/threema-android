package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

class DatabaseUpdateToVersion90(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {

    override fun run() {
        sqLiteDatabase.execSQL("DROP INDEX IF EXISTS `message_queue_idx`")

        sqLiteDatabase.execSQL(
            "CREATE INDEX IF NOT EXISTS `message_state_idx` ON `message` ( " +
                "`type`, " +
                "`state`, " +
                "`outbox` " +
                ")",
        )

        sqLiteDatabase.execSQL(
            "CREATE INDEX IF NOT EXISTS `group_message_state_idx` ON `m_group_message` ( " +
                "`type`, " +
                "`state`, " +
                "`outbox` " +
                ")",
        )

        sqLiteDatabase.execSQL(
            "CREATE INDEX IF NOT EXISTS `distribution_list_message_state_idx` ON `distribution_list_message` ( " +
                "`type`, " +
                "`state`, " +
                "`outbox` " +
                ")",
        )
    }

    override fun getDescription() = "update message index"

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 90
    }
}
