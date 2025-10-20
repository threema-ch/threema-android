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

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

/**
 * Create forwardSecurityMode field in group and distribution list message model.
 */
public class DatabaseUpdateToVersion74 implements DatabaseUpdate {
    public static final int VERSION = 74;

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion74(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        if (!fieldExists(this.sqLiteDatabase, "m_group_message", "forwardSecurityMode")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE m_group_message ADD COLUMN " +
                "forwardSecurityMode TINYINT DEFAULT 0");
        }
        if (!fieldExists(this.sqLiteDatabase, "distribution_list_message", "forwardSecurityMode")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE distribution_list_message ADD COLUMN " +
                "forwardSecurityMode TINYINT DEFAULT 0");
        }
    }

    @Override
    public String getDescription() {
        return "forwardSecurity 2";
    }

    @Override
    public int getVersion() {
        return VERSION;
    }
}
