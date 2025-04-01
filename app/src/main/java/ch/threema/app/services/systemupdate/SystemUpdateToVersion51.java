/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

import ch.threema.app.services.UpdateSystemService;
import ch.threema.storage.models.WebClientSessionModel;

import static ch.threema.app.services.systemupdate.SystemUpdateHelpersKt.fieldExists;

/**
 * add "created" column to webclient sessions table
 */
public class SystemUpdateToVersion51 implements UpdateSystemService.SystemUpdate {

    private final SQLiteDatabase sqLiteDatabase;

    public SystemUpdateToVersion51(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public boolean runDirectly() throws SQLException {
        if (!fieldExists(this.sqLiteDatabase, WebClientSessionModel.TABLE, WebClientSessionModel.COLUMN_CREATED)) {
            this.sqLiteDatabase.rawExecSQL(
                "ALTER TABLE " + WebClientSessionModel.TABLE
                    + " ADD COLUMN " + WebClientSessionModel.COLUMN_CREATED + " BIGINT NULL"
            );
        }
        return true;
    }

    @Override
    public boolean runAsync() {
        return true;
    }

    @Override
    public String getText() {
        return "version 51";
    }
}
