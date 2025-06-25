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

import android.database.SQLException;

import ch.threema.storage.DatabaseService;
import ch.threema.storage.factories.ModelFactory;

public class DatabaseUpdateToVersion8 implements DatabaseUpdate {

    private final DatabaseService databaseService;
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion8(DatabaseService databaseService, SQLiteDatabase sqLiteDatabase) {
        this.databaseService = databaseService;

        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        for (ModelFactory f : new ModelFactory[]{
            this.databaseService.getGroupModelFactory(),
            this.databaseService.getGroupMemberModelFactory(),
            this.databaseService.getGroupMessageModelFactory()
        }) {
            for (String s : f.getStatements()) {
                this.sqLiteDatabase.execSQL(s);
            }
        }
    }

    @Override
    public int getVersion() {
        return 8;
    }
}
