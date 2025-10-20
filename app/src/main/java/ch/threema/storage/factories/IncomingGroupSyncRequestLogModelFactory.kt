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

package ch.threema.storage.factories

import android.database.Cursor
import android.database.SQLException
import ch.threema.domain.types.Identity
import ch.threema.storage.DatabaseService
import ch.threema.storage.buildContentValues
import ch.threema.storage.models.GroupModel
import ch.threema.storage.models.IncomingGroupSyncRequestLogModel
import ch.threema.storage.runQuery
import net.zetetic.database.sqlcipher.SQLiteDatabase

class IncomingGroupSyncRequestLogModelFactory(databaseService: DatabaseService) :
    ModelFactory(databaseService, IncomingGroupSyncRequestLogModel.TABLE) {
    /**
     * Insert the provided model into the database.
     *
     * @throws [SQLException] if the provided local db group id is not valid
     */
    @Synchronized
    fun createOrUpdate(groupSyncRequestLog: IncomingGroupSyncRequestLogModel) {
        writableDatabase.insert(
            tableName,
            SQLiteDatabase.CONFLICT_REPLACE,
            groupSyncRequestLog.toContentValues(),
        )
    }

    /**
     * Get an [IncomingGroupSyncRequestLogModel] by [localDbGroupId] and [senderIdentity]. If there
     * is no such entry in the database, a new model is returned where
     * [IncomingGroupSyncRequestLogModel.lastHandledRequest] is set to 0.
     */
    @Synchronized
    fun getByGroupIdAndSenderIdentity(
        localDbGroupId: Long,
        senderIdentity: Identity,
    ): IncomingGroupSyncRequestLogModel {
        readableDatabase.runQuery(
            table = tableName,
            selection = "${IncomingGroupSyncRequestLogModel.COLUMN_GROUP_ID} = ? AND ${IncomingGroupSyncRequestLogModel.COLUMN_SENDER_IDENTITY} = ?",
            selectionArgs = arrayOf(localDbGroupId.toString(), senderIdentity),
        )
            .use {
                return if (it.moveToFirst()) {
                    it.toGroupSyncRequestLogModel()
                } else {
                    IncomingGroupSyncRequestLogModel(localDbGroupId, senderIdentity, 0)
                }
            }
    }

    override fun getStatements(): Array<String> {
        return arrayOf(
            """
                CREATE TABLE `${IncomingGroupSyncRequestLogModel.TABLE}`(
                    `${IncomingGroupSyncRequestLogModel.COLUMN_GROUP_ID}` INTEGER,
                    `${IncomingGroupSyncRequestLogModel.COLUMN_SENDER_IDENTITY}` VARCHAR,
                    `${IncomingGroupSyncRequestLogModel.COLUMN_LAST_HANDLED_REQUEST}` DATETIME,
                    PRIMARY KEY (`${IncomingGroupSyncRequestLogModel.COLUMN_GROUP_ID}`, `${IncomingGroupSyncRequestLogModel.COLUMN_SENDER_IDENTITY}`),
                    FOREIGN KEY(`${IncomingGroupSyncRequestLogModel.COLUMN_GROUP_ID}`) REFERENCES `${GroupModel.TABLE}`(`${GroupModel.COLUMN_ID}`) ON UPDATE CASCADE ON DELETE CASCADE
                )
            """,
        )
    }

    private fun Cursor.toGroupSyncRequestLogModel(): IncomingGroupSyncRequestLogModel {
        val groupIdColumnIndex =
            getColumnIndexOrThrow(IncomingGroupSyncRequestLogModel.COLUMN_GROUP_ID)
        val senderIdentityColumnIndex =
            getColumnIndexOrThrow(IncomingGroupSyncRequestLogModel.COLUMN_SENDER_IDENTITY)
        val lastHandledRequestColumnIndex =
            getColumnIndexOrThrow(IncomingGroupSyncRequestLogModel.COLUMN_LAST_HANDLED_REQUEST)

        return IncomingGroupSyncRequestLogModel(
            getLong(groupIdColumnIndex),
            getString(senderIdentityColumnIndex),
            getLong(lastHandledRequestColumnIndex),
        )
    }

    private fun IncomingGroupSyncRequestLogModel.toContentValues() = buildContentValues {
        put(IncomingGroupSyncRequestLogModel.COLUMN_GROUP_ID, groupId)
        put(IncomingGroupSyncRequestLogModel.COLUMN_SENDER_IDENTITY, senderIdentity)
        put(IncomingGroupSyncRequestLogModel.COLUMN_LAST_HANDLED_REQUEST, lastHandledRequest)
    }
}
