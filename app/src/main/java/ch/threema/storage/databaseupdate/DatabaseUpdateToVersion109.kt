package ch.threema.storage.databaseupdate

import ch.threema.base.utils.getThreemaLogger
import net.zetetic.database.sqlcipher.SQLiteDatabase

private val logger = getThreemaLogger("DatabaseUpdateToVersion109")

internal class DatabaseUpdateToVersion109(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        if (sqLiteDatabase.fieldExists(table = "m_group", fieldName = "deleted")) {
            logger.info("Removing group members of groups that were marked as deleted")
            sqLiteDatabase.execSQL("DELETE FROM `group_member` WHERE `groupId` IN (SELECT `id` FROM `m_group` WHERE `deleted` = 1)")

            logger.info("Removing groups that were marked as deleted")
            sqLiteDatabase.execSQL("DELETE FROM `m_group` WHERE `deleted` = 1")

            logger.info("Removing `deleted` field from table `m_group`")
            sqLiteDatabase.execSQL("ALTER TABLE `m_group` DROP COLUMN `deleted`")
        }
    }

    override fun getDescription() = "remove deleted groups and drop the 'deleted' flag"

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 109
    }
}
