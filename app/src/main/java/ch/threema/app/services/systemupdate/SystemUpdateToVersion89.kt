/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

internal class SystemUpdateToVersion89(
    private val db: SQLiteDatabase,
) : UpdateSystemService.SystemUpdate {
    companion object {
        const val VERSION = 89
        private val logger = LoggingUtil.getThreemaLogger("SystemUpdateToVersion89")
    }

    override fun runAsync() = true

    override fun runDirectly(): Boolean {
        val tables = arrayOf("contacts", "m_group", "distribution_list")
        val field = "lastUpdate"

        // Add field
        for (table in tables) {
            if (!fieldExists(db, table, field)) {
                logger.info("Adding $field field to table $table")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `$field` INTEGER")
            }
        }

        // Calculate lastUpdate based on existing data
        calculateLastUpdateContacts()
        calculateLastUpdateGroups()
        calculateLastUpdateDistributionLists()

        return true
    }

    override fun getText() = "version $VERSION (add lastUpdate field)"

    private fun calculateLastUpdateContacts() {
        logger.info("Calculate lastUpdate for contacts")

        // Consider all message types except date separators and forward security status messages.
        // Note that in a previous version of the update script (that has been applied for most
        // users), all message types have been used to determine the last update flag leading to
        // some chat reordering.
        db.execSQL("""
            UPDATE contacts
            SET lastUpdate = tmp.lastUpdate FROM (
                SELECT m.identity, max(m.createdAtUtc) as lastUpdate
                FROM message m
                WHERE m.isSaved = 1 AND type != 10 AND type != 12
                GROUP BY m.identity
            ) tmp
            WHERE contacts.identity = tmp.identity;
        """)
    }

    private fun calculateLastUpdateGroups() {
        logger.info("Calculate lastUpdate for groups")

        // Set lastUpdate to the create date of the latest message if present
        // Consider all message types except date separators and group status messages. Note that in
        // a previous version of the update script (that has been applied for most users), all
        // message types have been used to determine the last update flag leading to some chat
        // reordering.
        db.execSQL("""
            UPDATE m_group
            SET lastUpdate = tmp.lastUpdate FROM (
                SELECT m.groupId, max(m.createdAtUtc) as lastUpdate
                FROM m_group_message m
                WHERE m.isSaved = 1 AND type != 10 AND type != 13
                GROUP BY m.groupId
            ) tmp
            WHERE m_group.id = tmp.groupId;
        """)

        // Set lastUpdate for groups without messages.
        // `createdAt` is stored in localtime and therefore needs to be converted to UTC.
        db.execSQL("""
            UPDATE m_group
            SET lastUpdate = strftime('%s', createdAt, 'utc') * 1000
            WHERE lastUpdate IS NULL;
        """)
    }

    private fun calculateLastUpdateDistributionLists() {
        logger.info("Calculate lastUpdate for distribution lists")

        // Set lastUpdate to the create date of the latest message if present
        db.execSQL("""
            UPDATE distribution_list
            SET lastUpdate = tmp.lastUpdate FROM (
                SELECT m.distributionListId, max(m.createdAtUtc) as lastUpdate
                FROM distribution_list_message m
                WHERE m.isSaved = 1
                GROUP BY m.distributionListId
            ) tmp
            WHERE distribution_list.id = tmp.distributionListId;
        """)

        // Set lastUpdate for distribution lists without messages.
        // `createdAt` is stored in localtime and therefore needs to be converted to UTC.
        db.execSQL("""
            UPDATE distribution_list
            SET lastUpdate = strftime('%s', createdAt, 'utc') * 1000
            WHERE lastUpdate IS NULL;
        """)
    }

}
