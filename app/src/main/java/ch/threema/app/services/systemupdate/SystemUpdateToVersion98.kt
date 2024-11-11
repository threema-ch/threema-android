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
import net.zetetic.database.sqlcipher.SQLiteDatabase

class SystemUpdateToVersion98(
    private val sqLiteDatabase: SQLiteDatabase,
) : UpdateSystemService.SystemUpdate {
    companion object {
        const val VERSION = 98
    }

    override fun runAsync() = true

    override fun runDirectly(): Boolean {
        sqLiteDatabase.execSQL(
            """
                CREATE TABLE `m_group_incoming_sync_request_log`(
                    `groupId` INTEGER,
                    `senderIdentity` VARCHAR,
                    `lastHandledRequest` VARCHAR,
                    PRIMARY KEY(`groupId`, `senderIdentity`),
                    FOREIGN KEY(`groupId`) REFERENCES `m_group`(`id`)
                )
            """
        )

        return true
    }

    override fun getText() = "version $VERSION (add incoming group sync request log table)"
}
