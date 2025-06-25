/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

internal class DatabaseUpdateToVersion79(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {

    override fun run() {
        val tmpTableName = "group_call_old"
        sqLiteDatabase.execSQL("ALTER TABLE `group_call` RENAME TO `$tmpTableName`")

        sqLiteDatabase.execSQL(
            "CREATE TABLE `group_call` (" +
                "`callId` TEXT PRIMARY KEY NOT NULL, " +
                "`groupId` INTEGER NOT NULL, " +
                "`sfuBaseUrl` TEXT NOT NULL, " +
                "`gck` TEXT NOT NULL, " +
                "`protocolVersion` INTEGER NOT NULL, " +
                "`startedAt` BIGINT NOT NULL)",
        )

        sqLiteDatabase.execSQL(
            "INSERT INTO `group_call` " +
                "SELECT callId, groupId, sfuBaseUrl, gck, protocolVersion, CURRENT_TIMESTAMP as startedAt " +
                "FROM `$tmpTableName`",
        )

        sqLiteDatabase.execSQL("DROP TABLE `$tmpTableName`")
    }

    override fun getDescription() = "GroupCalls"

    override fun getVersion() = VERSION

    companion object {
        const val VERSION = 79
    }
}
