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

package ch.threema.app.services.systemupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import java.sql.SQLException;
import java.util.Arrays;

import ch.threema.app.collections.Functional;
import ch.threema.app.services.UpdateSystemService;
import ch.threema.storage.models.ballot.BallotModel;

public class SystemUpdateToVersion70 implements UpdateSystemService.SystemUpdate {
    public static final int VERSION = 70;
    public static final String VERSION_STRING = "version " + VERSION;

    private final SQLiteDatabase sqLiteDatabase;

    public SystemUpdateToVersion70(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public boolean runAsync() {
        return true;
    }

    @Override
    public boolean runDirectly() throws SQLException {
        //update displayType column if not present already
        String[] messageTableColumnNames = sqLiteDatabase.rawQuery("SELECT * FROM " + BallotModel.TABLE + " LIMIT 1", null).getColumnNames();

        boolean hasField = Functional.select(Arrays.asList(messageTableColumnNames), type -> type.equals(BallotModel.COLUMN_DISPLAY_TYPE)) != null;


        if (!hasField) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE " + BallotModel.TABLE + " ADD COLUMN " + BallotModel.COLUMN_DISPLAY_TYPE + " VARCHAR");
        }

        return true;
    }

    @Override
    public String getText() {
        return VERSION_STRING;
    }
}
