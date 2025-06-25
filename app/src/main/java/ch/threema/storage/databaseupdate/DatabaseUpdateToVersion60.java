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

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import android.database.SQLException;

import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.MessageModel;

import static ch.threema.storage.DatabaseExtensionsKt.fieldExists;

/**
 * add caption field to normal, group and distribution list message models
 */
public class DatabaseUpdateToVersion60 implements DatabaseUpdate {

    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion60(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        //add new quote field to message model fields
        for (String table : new String[]{
            MessageModel.TABLE,
            GroupMessageModel.TABLE,
            DistributionListMessageModel.TABLE
        }) {
            if (!fieldExists(this.sqLiteDatabase, table, AbstractMessageModel.COLUMN_QUOTED_MESSAGE_API_MESSAGE_ID)) {
                sqLiteDatabase.rawExecSQL("ALTER TABLE " + table
                    + " ADD COLUMN " + AbstractMessageModel.COLUMN_QUOTED_MESSAGE_API_MESSAGE_ID + " VARCHAR NULL");
            }
        }
    }

    @Override
    public String getDescription() {
        return "add quoted message id";
    }

    @Override
    public int getVersion() {
        return 60;
    }
}
