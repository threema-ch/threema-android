/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

public class DatabaseUpdateToVersion71 implements DatabaseUpdate {
    public static final int VERSION = 71;

    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion71(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        // add isHidden column if not present already
        String table = "distribution_list";
        String column = "isHidden";

        if (!fieldExists(this.sqLiteDatabase, table, column)) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " TINYINT DEFAULT 0");
        }
    }

    @Override
    public int getVersion() {
        return VERSION;
    }
}
