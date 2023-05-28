/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023 Threema GmbH
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
import android.database.sqlite.SQLiteException
import ch.threema.base.utils.LoggingUtil
import ch.threema.storage.CursorHelper
import ch.threema.storage.DatabaseServiceNew
import ch.threema.storage.models.ServerMessageModel
import java.sql.SQLException

private val logger = LoggingUtil.getThreemaLogger("ServerMessageModelFactory")

class ServerMessageModelFactory(databaseService: DatabaseServiceNew) :
    ModelFactory(databaseService, ServerMessageModel.TABLE) {

    fun storeServerMessageModel(serverMessageModel: ServerMessageModel) {
        val contentValues = ContentValues()
        contentValues.put(ServerMessageModel.COLUMN_MESSAGE, serverMessageModel.message)
        contentValues.put(ServerMessageModel.COLUMN_TYPE, serverMessageModel.type)
        try {
            databaseService.writableDatabase.insertOrThrow(tableName, null, contentValues)
        } catch (e: SQLException) {
            logger.error("Could not store server message", e)
        }
    }

    fun popServerMessageModel(): ServerMessageModel? {
        val cursor = databaseService.readableDatabase.query(
            ServerMessageModel.TABLE,
            arrayOf(ServerMessageModel.COLUMN_MESSAGE, ServerMessageModel.COLUMN_TYPE),
            null,
            null,
            null,
            null,
            null,
            "1"
        )
        if (cursor != null && cursor.moveToFirst()) {
            val cursorHelper = CursorHelper(cursor, columnIndexCache)
            return convertAndDelete(cursorHelper)
        }
        return null
    }

    fun delete(message: String) {
        databaseService.writableDatabase.delete(
            ServerMessageModel.TABLE,
            "${ServerMessageModel.COLUMN_MESSAGE}=?",
            arrayOf(message)
        )
    }

    private fun convertAndDelete(c: CursorHelper): ServerMessageModel? {
        return try {
            val message = c.getString(ServerMessageModel.COLUMN_MESSAGE) ?: ""
            val type = c.getInt(ServerMessageModel.COLUMN_TYPE) ?: -1
            val messageModel = if (message.isNotBlank() && type >= 0) {
                ServerMessageModel(message, type)
            } else {
                logger.info("Invalid message '{}' or type '{}'", message, type)
                null
            }
            delete(message)
            messageModel
        } catch (e: SQLiteException) {
            logger.error("Could not load server message model", e)
            null
        }
    }

    override fun getStatements(): Array<String> = arrayOf(
        "CREATE TABLE `${ServerMessageModel.TABLE}` (" +
                "`${ServerMessageModel.COLUMN_MESSAGE}` VARCHAR PRIMARY KEY ON CONFLICT REPLACE," +
                "`${ServerMessageModel.COLUMN_TYPE}` INTEGER" +
                ")"
    )
}
