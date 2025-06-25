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

import org.slf4j.Logger;

import android.database.SQLException;

import ch.threema.base.utils.LoggingUtil;

import static ch.threema.storage.DatabaseExtensionsKt.fieldExists;

/**
 * add contact restore state field to contact models
 */
public class DatabaseUpdateToVersion56 implements DatabaseUpdate {
    private static final Logger logger = LoggingUtil.getThreemaLogger("DatabaseUpdateToVersion56");

    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion56(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        logger.info("runDirectly");
        if (!fieldExists(this.sqLiteDatabase, "contacts", "isArchived")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN isArchived TINYINT DEFAULT 0");
        }
        if (!fieldExists(this.sqLiteDatabase, "m_group", "isArchived")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE m_group ADD COLUMN isArchived TINYINT DEFAULT 0");
        }
        if (!fieldExists(this.sqLiteDatabase, "distribution_list", "isArchived")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE distribution_list ADD COLUMN isArchived TINYINT DEFAULT 0");
        }
    }

    @Override
    public int getVersion() {
        return 56;
    }
}
