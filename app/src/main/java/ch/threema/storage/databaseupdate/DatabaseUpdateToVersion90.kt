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

class DatabaseUpdateToVersion90(
    private val db: SQLiteDatabase,
) : DatabaseUpdate {

    override fun run() {
        db.execSQL("DROP INDEX IF EXISTS `message_queue_idx`")

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `message_state_idx` ON `message` ( " +
                "`type`, " +
                "`state`, " +
                "`outbox` " +
                ")",
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `group_message_state_idx` ON `m_group_message` ( " +
                "`type`, " +
                "`state`, " +
                "`outbox` " +
                ")",
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `distribution_list_message_state_idx` ON `distribution_list_message` ( " +
                "`type`, " +
                "`state`, " +
                "`outbox` " +
                ")",
        )
    }

    override fun getDescription() = "update message index"

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 90
    }
}
