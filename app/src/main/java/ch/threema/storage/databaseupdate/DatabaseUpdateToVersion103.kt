/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

internal class DatabaseUpdateToVersion103(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        unStarStarredStatusMessages(messageTableName = "message")
        unStarStarredStatusMessages(messageTableName = "m_group_message")
        unStarStarredStatusMessages(messageTableName = "distribution_list_message")
    }

    /**
     *  This will un-star every status message that could be starred in previous app-versions.
     *
     *  The flag `DisplayTag.DISPLAY_TAG_STARRED` gets removed from cell value `displayTags` if set.
     *
     *  `1` here stands for the flag `DisplayTag.DISPLAY_TAG_STARRED`
     */
    private fun unStarStarredStatusMessages(messageTableName: String) {
        sqLiteDatabase.execSQL(
            """
                UPDATE $messageTableName
                SET displayTags = (displayTags & ~1)
                WHERE isStatusMessage = 1
                  AND (displayTags & 1) = 1;
            """,
        )
    }

    override fun getDescription() = "correct starred status message to not-starred"

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 103
    }
}
