/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.storage.databaseupdate

import ch.threema.base.utils.LoggingUtil
import net.zetetic.database.sqlcipher.SQLiteDatabase

private val logger = LoggingUtil.getThreemaLogger("DatabaseUpdateToVersion109")

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
