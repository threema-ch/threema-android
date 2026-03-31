package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion82(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        sqLiteDatabase.rawExecSQL("ALTER TABLE `contacts` ADD COLUMN `profilePicBlobID` BLOB DEFAULT NULL")
        sqLiteDatabase.rawExecSQL("ALTER TABLE `contacts` DROP COLUMN `profilePicSent`")
    }

    override fun getDescription() = "Profile Picture Blob ID"

    override val version = 82
}
