package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion78(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        sqLiteDatabase.execSQL("ALTER TABLE `group_call` ADD COLUMN `protocolVersion` INTEGER DEFAULT 0")
    }

    override fun getDescription() = "GroupCalls"

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 78
    }
}
