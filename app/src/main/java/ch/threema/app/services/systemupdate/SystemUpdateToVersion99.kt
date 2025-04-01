/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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
import net.zetetic.database.sqlcipher.SQLiteDatabase

class SystemUpdateToVersion99(
    private val sqLiteDatabase: SQLiteDatabase,
) : UpdateSystemService.SystemUpdate {
    companion object {
        const val VERSION = 99
    }

    override fun runAsync() = true

    override fun runDirectly(): Boolean {
        // Create new table with on delete/update actions
        sqLiteDatabase.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `incoming_group_sync_request_log`(
                `groupId` INTEGER,
                `senderIdentity` VARCHAR,
                `lastHandledRequest` DATETIME,
                PRIMARY KEY(`groupId`, `senderIdentity`),
                FOREIGN KEY(`groupId`) REFERENCES `m_group`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
            )
            """
        )

        // Drop the old table
        sqLiteDatabase.execSQL("DROP TABLE `m_group_incoming_sync_request_log`")

        // Note that we omit the migration of the content of this table as it is not a problem if
        // the request log is emptied from time to time. The effect is only that a sync request
        // might be answered too early. The purpose of preventing infinite loops is still fulfilled.
        // The migration of the content would be somehow risky because the old table could contain
        // data that violates the foreign key constraint.

        return true
    }

    override fun getText() = "version $VERSION (fix incoming group sync request constraint)"
}
