package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion79(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {

    override fun run() {
        val tmpTableName = "group_call_old"
        sqLiteDatabase.execSQL("ALTER TABLE `group_call` RENAME TO `$tmpTableName`")

        sqLiteDatabase.execSQL(
            "CREATE TABLE `group_call` (" +
                "`callId` TEXT PRIMARY KEY NOT NULL, " +
                "`groupId` INTEGER NOT NULL, " +
                "`sfuBaseUrl` TEXT NOT NULL, " +
                "`gck` TEXT NOT NULL, " +
                "`protocolVersion` INTEGER NOT NULL, " +
                "`startedAt` BIGINT NOT NULL)",
        )

        sqLiteDatabase.execSQL(
            "INSERT INTO `group_call` " +
                "SELECT callId, groupId, sfuBaseUrl, gck, protocolVersion, CURRENT_TIMESTAMP as startedAt " +
                "FROM `$tmpTableName`",
        )

        sqLiteDatabase.execSQL("DROP TABLE `$tmpTableName`")
    }

    override fun getDescription() = "GroupCalls"

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 79
    }
}
