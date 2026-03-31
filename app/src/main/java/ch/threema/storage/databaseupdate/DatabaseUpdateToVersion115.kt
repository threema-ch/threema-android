package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

class DatabaseUpdateToVersion115(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        migrateContacts()
        migrateGroups()
        migrateDistributionLists()
        migrateGroupRequestSyncLog()
    }

    override val version = 115

    override fun getDescription() = "Migrate timestamps in database from string to unix epoch timestamps"

    private fun migrateContacts() {
        renameLastUpdateColumn("contacts")
    }

    private fun migrateGroups() {
        val groupTable = "m_group"
        renameLastUpdateColumn(groupTable)
        migrateDateStringsToUnixTimestamp(
            table = groupTable,
            "createdAt",
            "changedGroupDescTimestamp",
        )
    }

    private fun migrateDistributionLists() {
        val distributionListTable = "distribution_list"
        renameLastUpdateColumn(distributionListTable)
        migrateDateStringsToUnixTimestamp(
            table = distributionListTable,
            "createdAt",
        )
    }

    private fun migrateGroupRequestSyncLog() {
        val table = "m_group_request_sync_log"
        renameColumn(table = table, currentName = "lastRequest", newName = "lastRequestAt")
        migrateDateStringsToUnixTimestamp(
            table = table,
            "lastRequestAt",
        )
    }

    private fun migrateDateStringsToUnixTimestamp(table: String, vararg columnNames: String) {
        val migrationColumns = columnNames.map { MigrationColumn(name = it, legacyName = "${it}Legacy") }

        // Rename current value column and create new column with type bigint
        migrationColumns.forEach {
            renameColumn(table = table, currentName = it.name, newName = it.legacyName)
            addBigintColumn(table = table, columnName = it.name)
        }

        // migrate values
        val updates = migrationColumns.joinToString(", ") {
            "${it.name} = strftime('%s', ${it.legacyName}, 'utc', 'subsec') * 1000"
        }
        sqLiteDatabase.rawExecSQL("UPDATE `$table` SET $updates")

        // drop legacy columns
        migrationColumns.forEach {
            dropColumn(table = table, columnName = it.legacyName)
        }
    }

    data class MigrationColumn(val name: String, val legacyName: String)

    private fun renameLastUpdateColumn(table: String) {
        renameColumn(table = table, currentName = "lastUpdate", newName = "lastUpdateAt")
    }

    private fun renameColumn(table: String, currentName: String, newName: String) {
        if (sqLiteDatabase.fieldExists(table = table, fieldName = currentName) &&
            !sqLiteDatabase.fieldExists(table = table, fieldName = newName)
        ) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE `$table` RENAME COLUMN `$currentName` TO `$newName`")
        } else {
            throw DatabaseUpdateException("Could not rename `$currentName` to `$newName` in table `$table`")
        }
    }

    private fun addBigintColumn(table: String, columnName: String) {
        if (!sqLiteDatabase.fieldExists(table = table, fieldName = columnName)) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE `$table` ADD COLUMN `$columnName` BIGINT")
        } else {
            throw DatabaseUpdateException("Could not add bigint column `$columnName` to table `$table` (column already exists)")
        }
    }

    private fun dropColumn(table: String, columnName: String) {
        if (sqLiteDatabase.fieldExists(table = table, fieldName = columnName)) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE `$table` DROP COLUMN `$columnName`")
        } else {
            throw DatabaseUpdateException("Could not drop column `$columnName` from table `$table` (no such column)")
        }
    }
}
