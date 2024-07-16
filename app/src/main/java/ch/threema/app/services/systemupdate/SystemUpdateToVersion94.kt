/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.services.systemupdate

import ch.threema.app.services.UpdateSystemService
import ch.threema.base.utils.LoggingUtil
import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class SystemUpdateToVersion94(
    private val db: SQLiteDatabase,
) : UpdateSystemService.SystemUpdate {
    companion object {
        const val VERSION = 94
        private val logger = LoggingUtil.getThreemaLogger("SystemUpdateToVersion94")
    }

    override fun runAsync() = true

    override fun runDirectly(): Boolean {
        val table = "contacts"

        // Remove unused fields
        dropField(table, "threemaAndroidContactId")
        dropField(table, "isSynchronized")

        // Migrate "isHidden" to "acquaintanceLevel"
        if (!fieldExists(db, table, "acquaintanceLevel")) {
            // Values: 0: Direct, 1: Group
            logger.info("Renaming $table.isHidden to acquaintanceLevel")
            db.execSQL("ALTER TABLE `$table` RENAME COLUMN `isHidden` TO `acquaintanceLevel`")
        }

        // Add "syncState" field
        // Values: 0: Initial, 1: Imported, 2: Custom
        if (!fieldExists(db, table, "syncState")) {
            logger.info("Adding $table.syncState")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `syncState` INTEGER NOT NULL DEFAULT 0")
        }
        return true
    }

    override fun getText() = "version $VERSION (contact table cleanup and changes)"

    private fun dropField(table: String, field: String) {
        if (fieldExists(db, table, field)) {
            logger.info("Removing $field field from table $table")
            db.execSQL("ALTER TABLE `$table` DROP COLUMN `$field`")
        }
    }
}
