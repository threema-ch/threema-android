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

import static ch.threema.storage.DatabaseExtensionsKt.fieldExists;

/**
 * Create forwardSecurityMode field in message model and forwardSecurityEnabled field in contact model
 */
public class DatabaseUpdateToVersion73 implements DatabaseUpdate {
    public static final int VERSION = 73;

    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion73(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        if (!fieldExists(this.sqLiteDatabase, "message", "forwardSecurityMode")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE message ADD COLUMN forwardSecurityMode TINYINT DEFAULT 0");
        }
        if (!fieldExists(this.sqLiteDatabase, "contacts", "forwardSecurityEnabled")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN forwardSecurityEnabled TINYINT DEFAULT 0");
        }
    }

    @Override
    public String getDescription() {
        return "forwardSecurity";
    }

    @Override
    public int getVersion() {
        return VERSION;
    }
}
