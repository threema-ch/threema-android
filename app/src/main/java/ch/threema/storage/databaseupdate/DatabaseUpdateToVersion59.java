/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

import static ch.threema.storage.DatabaseExtensionsKt.fieldExists;

/**
 * Create all correlationId fields
 */
public class DatabaseUpdateToVersion59 implements DatabaseUpdate {

    private final SQLiteDatabase sqLiteDatabase;


    public DatabaseUpdateToVersion59(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        //add new correlationId field to message model fields
        for (String table : new String[]{"message", "m_group_message", "distribution_list_message"}) {
            if (!fieldExists(this.sqLiteDatabase, table, "correlationId")) {
                sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " ADD COLUMN correlationId VARCHAR NULL");
            }
        }

        // Create index
        this.sqLiteDatabase.rawExecSQL("CREATE INDEX `messageCorrelationIdIx` ON `message` ( `correlationId` )");
        this.sqLiteDatabase.rawExecSQL("CREATE INDEX `groupMessageCorrelationIdIdx` ON `m_group_message` ( `correlationId` )");
        this.sqLiteDatabase.rawExecSQL("CREATE INDEX `distributionListCorrelationIdIdx` ON `distribution_list_message` ( `correlationId` )");
    }

    @Override
    public String getDescription() {
        return "correlationId";
    }

    @Override
    public int getVersion() {
        return 59;
    }
}
