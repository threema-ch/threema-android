/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

/**
 * For ID colors we store the first byte of the SHA-256 hash of the contact identity.
 */
public class DatabaseUpdateToVersion72 implements DatabaseUpdate {
    public static final int VERSION = 72;

    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion72(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        final String table = "contacts";
        final String columnColor = "color";
        final String columnColorIndex = "idColorIndex";

        // Rename color column to id color index column
        sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " RENAME " + columnColor + " TO " + columnColorIndex);
        // Temporarily set value to -1 to prevent null pointer exception when loading the contacts
        sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET " + columnColorIndex + " = -1");
    }

    @Override
    public int getVersion() {
        return VERSION;
    }
}
