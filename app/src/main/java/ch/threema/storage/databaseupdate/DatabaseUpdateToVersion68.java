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
 * Create readAt and deliveredAt fields in message model
 */
public class DatabaseUpdateToVersion68 implements DatabaseUpdate {
    public static final int VERSION = 68;
    private final SQLiteDatabase sqLiteDatabase;


    public DatabaseUpdateToVersion68(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        for (String table : new String[]{"message", "m_group_message", "distribution_list_message"}) {
            if (!fieldExists(this.sqLiteDatabase, table, "deliveredAtUtc")) {
                sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " ADD COLUMN deliveredAtUtc DATETIME DEFAULT NULL");
            }
            if (!fieldExists(this.sqLiteDatabase, table, "readAtUtc")) {
                sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " ADD COLUMN readAtUtc DATETIME DEFAULT NULL");
            }
        }
    }

    @Override
    public String getDescription() {
        return "correlationId";
    }

    @Override
    public int getVersion() {
        return VERSION;
    }
}
