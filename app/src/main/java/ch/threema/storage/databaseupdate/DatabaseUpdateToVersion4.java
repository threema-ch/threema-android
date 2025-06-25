/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

import static ch.threema.storage.DatabaseExtensionsKt.fieldExists;

public class DatabaseUpdateToVersion4 implements DatabaseUpdate {
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion4(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() {
        //dirty stuff, but the only way to fix the update mess in beta phase

        //create postedAt field if not exists
        if (!fieldExists(sqLiteDatabase, "message", "postedAt")) {
            this.sqLiteDatabase.rawExecSQL("ALTER TABLE message ADD COLUMN postedAt DATETIME DEFAULT NULL");
            this.sqLiteDatabase.rawExecSQL("UPDATE message SET postedAt = createdAt");
        }

        //create isSaved field if not exists
        if (!fieldExists(sqLiteDatabase, "message", "isSaved")) {
            this.sqLiteDatabase.rawExecSQL("ALTER TABLE message ADD COLUMN isSaved INT DEFAULT 0");
            this.sqLiteDatabase.rawExecSQL("UPDATE message SET isSaved = 1");
        }
    }

    @Override
    public int getVersion() {
        return 4;
    }
}
