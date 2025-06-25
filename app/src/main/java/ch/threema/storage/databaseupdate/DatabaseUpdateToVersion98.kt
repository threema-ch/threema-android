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

package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

class DatabaseUpdateToVersion98(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        sqLiteDatabase.execSQL(
            """
                CREATE TABLE `m_group_incoming_sync_request_log`(
                    `groupId` INTEGER,
                    `senderIdentity` VARCHAR,
                    `lastHandledRequest` VARCHAR,
                    PRIMARY KEY(`groupId`, `senderIdentity`),
                    FOREIGN KEY(`groupId`) REFERENCES `m_group`(`id`)
                )
            """,
        )
    }

    override fun getDescription() = "add incoming group sync request log table"

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 98
    }
}
