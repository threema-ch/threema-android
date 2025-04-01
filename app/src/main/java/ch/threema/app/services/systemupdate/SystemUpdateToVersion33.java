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

package ch.threema.app.services.systemupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import java.sql.SQLException;

import ch.threema.app.services.UpdateSystemService;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.MessageModel;

import static ch.threema.app.services.systemupdate.SystemUpdateHelpersKt.fieldExists;

public class SystemUpdateToVersion33 implements UpdateSystemService.SystemUpdate {

    private final DatabaseServiceNew databaseService;
    private final SQLiteDatabase sqLiteDatabase;


    public SystemUpdateToVersion33(DatabaseServiceNew databaseService, SQLiteDatabase sqLiteDatabase) {
        this.databaseService = databaseService;
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public boolean runDirectly() throws SQLException {
        this.sqLiteDatabase.execSQL(
            "CREATE TABLE `m_group_message_pending_message_id`"
                + "("
                + "`id` INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "`groupMessageId` INTEGER,"
                + "`apiMessageId` VARCHAR"
                + ")");

        //add new isQueued field to message model fields
        for (String table : new String[]{
            MessageModel.TABLE,
            GroupMessageModel.TABLE,
            DistributionListMessageModel.TABLE
        }) {
            if (!fieldExists(this.sqLiteDatabase, table, "isQueued")) {
                sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " ADD COLUMN isQueued TINYINT NOT NULL DEFAULT 0");

                //update the existing records
                sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET isQueued=1");
            }
        }
        return true;
    }

    @Override
    public boolean runAsync() {
        return true;
    }

    @Override
    public String getText() {
        return "version 33";
    }
}
