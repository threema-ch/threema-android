package ch.threema.storage.databaseupdate

import ch.threema.base.utils.getThreemaLogger
import net.zetetic.database.sqlcipher.SQLiteDatabase

private val logger = getThreemaLogger("DatabaseUpdateToVersion94")

internal class DatabaseUpdateToVersion94(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        val table = "contacts"

        // Remove unused fields
        dropField(table, "threemaAndroidContactId")
        dropField(table, "isSynchronized")

        // Migrate "isHidden" to "acquaintanceLevel"
        if (!sqLiteDatabase.fieldExists(table, "acquaintanceLevel")) {
            // Values: 0: Direct, 1: Group
            logger.info("Renaming $table.isHidden to acquaintanceLevel")
            sqLiteDatabase.execSQL("ALTER TABLE `$table` RENAME COLUMN `isHidden` TO `acquaintanceLevel`")
        }

        // Add "syncState" field
        // Values: 0: Initial, 1: Imported, 2: Custom
        if (!sqLiteDatabase.fieldExists(table, "syncState")) {
            logger.info("Adding $table.syncState")
            sqLiteDatabase.execSQL("ALTER TABLE `$table` ADD COLUMN `syncState` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private fun dropField(table: String, field: String) {
        if (sqLiteDatabase.fieldExists(table, field)) {
            logger.info("Removing $field field from table $table")
            sqLiteDatabase.execSQL("ALTER TABLE `$table` DROP COLUMN `$field`")
        }
    }

    override fun getDescription() = "contact table cleanup and changes"

    override val version = 94
}
