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

import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;

import static ch.threema.storage.DatabaseExtensionsKt.fieldExists;

/**
 * Create forwardSecurityMode field in group and distribution list message model.
 */
public class DatabaseUpdateToVersion74 implements DatabaseUpdate {
    public static final int VERSION = 74;

    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion74(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        if (!fieldExists(this.sqLiteDatabase, GroupMessageModel.TABLE, AbstractMessageModel.COLUMN_FORWARD_SECURITY_MODE)) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE " + GroupMessageModel.TABLE + " ADD COLUMN " +
                AbstractMessageModel.COLUMN_FORWARD_SECURITY_MODE + " TINYINT DEFAULT 0");
        }
        if (!fieldExists(this.sqLiteDatabase, DistributionListMessageModel.TABLE, AbstractMessageModel.COLUMN_FORWARD_SECURITY_MODE)) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE " + DistributionListMessageModel.TABLE + " ADD COLUMN " +
                AbstractMessageModel.COLUMN_FORWARD_SECURITY_MODE + " TINYINT DEFAULT 0");
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
