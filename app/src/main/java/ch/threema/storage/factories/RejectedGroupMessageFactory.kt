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

package ch.threema.storage.factories

import android.content.ContentValues
import ch.threema.domain.models.MessageId
import ch.threema.storage.DatabaseServiceNew
import ch.threema.storage.models.GroupModel

class RejectedGroupMessageFactory(databaseService: DatabaseServiceNew) :
    ModelFactory(databaseService, "rejected_group_messages") {

    companion object {
        private const val COLUMN_MESSAGE_ID = "messageId"
        private const val COLUMN_REJECTED_IDENTITY = "rejectedIdentity"
        private const val COLUMN_GROUP_ID = "groupId"
    }

    fun insertMessageReject(
        rejectedMessageId: MessageId,
        rejectedIdentity: String,
        groupModel: GroupModel,
    ) {
        val contentValues = ContentValues().apply {
            put(COLUMN_MESSAGE_ID, rejectedMessageId.toString())
            put(COLUMN_REJECTED_IDENTITY, rejectedIdentity)
            put(COLUMN_GROUP_ID, groupModel.id)
        }
        writableDatabase.insert(tableName, null, contentValues)
    }

    fun getMessageRejects(
        messageId: MessageId,
        groupModel: GroupModel,
    ): Set<String> {
        val selection = "$COLUMN_MESSAGE_ID=? AND $COLUMN_GROUP_ID=?"
        val selectionArgs = arrayOf(messageId.toString(), groupModel.id.toString())
        readableDatabase.query(
            tableName,
            null,
            selection,
            selectionArgs,
            null,
            null,
            null,
        ).use {
            val rejectedIdentities = mutableSetOf<String>()
            val rejectedIdentityColumnIndex = it.getColumnIndexOrThrow(COLUMN_REJECTED_IDENTITY)
            while (it.moveToNext()) {
                rejectedIdentities.add(it.getString(rejectedIdentityColumnIndex))
            }
            return rejectedIdentities
        }
    }

    fun removeMessageReject(
        rejectedMessageId: MessageId,
        rejectedIdentity: String,
        groupModel: GroupModel,
    ) {
        val whereClause =
            "$COLUMN_MESSAGE_ID=? AND $COLUMN_REJECTED_IDENTITY=? AND $COLUMN_GROUP_ID=?"
        val whereArgs = arrayOf(rejectedMessageId.toString(), rejectedIdentity, groupModel.id)
        writableDatabase.delete(tableName, whereClause, whereArgs)
    }

    fun removeAllMessageRejectsInGroup(group: GroupModel) {
        val whereClause = "$COLUMN_GROUP_ID=?"
        val whereArgs = arrayOf(group.id)
        writableDatabase.delete(tableName, whereClause, whereArgs)
    }

    fun removeMessageRejectByGroupAndIdentity(group: GroupModel, identity: String) {
        val whereClause = "$COLUMN_REJECTED_IDENTITY=? AND $COLUMN_GROUP_ID=?"
        val whereArgs = arrayOf(identity, group.id)
        writableDatabase.delete(tableName, whereClause, whereArgs)
    }

    override fun getStatements(): Array<String> = arrayOf(
        "CREATE TABLE `$tableName` (" +
            "`$COLUMN_MESSAGE_ID` INTEGER NOT NULL, " +
            "`$COLUMN_REJECTED_IDENTITY` VARCHAR NOT NULL, " +
            "`$COLUMN_GROUP_ID` INTEGER NOT NULL, " +
            "PRIMARY KEY (`$COLUMN_MESSAGE_ID`, `$COLUMN_REJECTED_IDENTITY`, `$COLUMN_GROUP_ID`) ON CONFLICT IGNORE " +
            ")"
    )
}
