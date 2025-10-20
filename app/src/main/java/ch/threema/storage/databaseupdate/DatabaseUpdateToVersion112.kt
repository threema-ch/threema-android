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

import android.content.Context
import ch.threema.common.now
import ch.threema.storage.buildContentValues
import ch.threema.storage.runInsert
import ch.threema.storage.runQuery
import ch.threema.storage.runUpdate
import java.util.UUID
import net.zetetic.database.sqlcipher.SQLiteDatabase

class DatabaseUpdateToVersion112(
    private val sqLiteDatabase: SQLiteDatabase,
    private val context: Context,
) : DatabaseUpdate {
    override fun run() {
        val myIdentity = getMyIdentity(context)
            ?: return

        // Find all orphaned groups, i.e. groups where
        // - we have not left the group
        // - and we are not the creator
        // - and the creator is not a member
        val cursor = sqLiteDatabase.runQuery(
            table = "m_group",
            columns = arrayOf("id"),
            selection = """
                m_group.userState = 0
                AND m_group.creatorIdentity != ?
                AND (
                    SELECT COUNT(*)
                    FROM group_member
                    WHERE groupId = m_group.id
                    AND identity = m_group.creatorIdentity
                ) = 0
            """,
            selectionArgs = arrayOf(myIdentity),
        )

        cursor.use {
            val now = now().time
            while (cursor.moveToNext()) {
                val groupId = cursor.getInt(0)

                // Mark the group as 'kicked'
                sqLiteDatabase.runUpdate(
                    table = "m_group",
                    values = buildContentValues {
                        put("userState", "1") // 1 = "kicked"
                    },
                    whereClause = "id = ?",
                    whereArgs = arrayOf(groupId.toString()),
                )

                // Create a status message to inform users that the group can no longer be used to send messages in
                sqLiteDatabase.runInsert(
                    table = "m_group_message",
                    values = buildContentValues {
                        put("groupId", groupId)
                        put("uid", UUID.randomUUID().toString())
                        put("outbox", false)
                        put("type", 13) // 13 = group_status
                        put("body", """[4,{"status":13}]""") // 4 = group status, 13 = orphaned
                        put("isRead", true)
                        put("isSaved", true)
                        put("postedAtUtc", now)
                        put("createdAtUtc", now)
                        put("isStatusMessage", true)
                        put("messageContentsType", 15) // 15 = GROUP_STATUS
                        put("messageFlags", 0)
                        put("displayTags", 0)
                    },
                )
            }
        }
    }

    override fun getVersion() = VERSION

    override fun getDescription() = "turn 'orphaned' groups into 'kicked' groups"

    companion object {
        const val VERSION = 112
    }
}
