/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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

package ch.threema.storage.databaseupdate;

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

public class DatabaseUpdateToVersion33 implements DatabaseUpdate {

    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion33(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        this.sqLiteDatabase.execSQL(
            "CREATE TABLE `m_group_message_pending_message_id`"
                + "("
                + "`id` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupMessageId` INTEGER,"
                + "`apiMessageId` VARCHAR"
                + ")");

        //add new isQueued field to message model fields
        for (String table : new String[]{
            "message",
            "m_group_message",
            "distribution_list_message"
        }) {
            if (!fieldExists(this.sqLiteDatabase, table, "isQueued")) {
                sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " ADD COLUMN isQueued TINYINT NOT NULL DEFAULT 0");

                //update the existing records
                sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET isQueued=1");
            }
        }
    }

    @Override
    public int getVersion() {
        return 33;
    }
}
