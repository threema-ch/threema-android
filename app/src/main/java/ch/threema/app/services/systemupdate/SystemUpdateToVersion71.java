/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

public class SystemUpdateToVersion71 extends UpdateToVersion implements UpdateSystemService.SystemUpdate {
	public static final int VERSION = 71;
	public static final String VERSION_STRING = "version " + VERSION;

	private final SQLiteDatabase sqLiteDatabase;

	public SystemUpdateToVersion71(SQLiteDatabase sqLiteDatabase) {
		this.sqLiteDatabase = sqLiteDatabase;
	}

	@Override
	public boolean runASync() { return true; }

	@Override
	public boolean runDirectly() throws SQLException {
		// add isHidden column if not present already
		String table = "distribution_list";
		String column = "isHidden";

		if (!this.fieldExist(this.sqLiteDatabase, table, column)) {
			sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " TINYINT DEFAULT 0");
		}
		return true;
	}

	@Override
	public String getText() { return VERSION_STRING; }
}
