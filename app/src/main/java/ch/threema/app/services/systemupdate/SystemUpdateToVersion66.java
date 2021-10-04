/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2021 Threema GmbH
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

import net.sqlcipher.database.SQLiteDatabase;

import java.sql.SQLException;

import ch.threema.app.services.UpdateSystemService;

/**
 * Add a messageFlags field
 */
public class SystemUpdateToVersion66 extends UpdateToVersion implements UpdateSystemService.SystemUpdate {

	private final SQLiteDatabase sqLiteDatabase;


	public SystemUpdateToVersion66(SQLiteDatabase sqLiteDatabase) {
		this.sqLiteDatabase = sqLiteDatabase;
	}

	@Override
	public boolean runDirectly() throws SQLException {
		if(!this.fieldExist(this.sqLiteDatabase, "contacts", "readReceipts")) {
			sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN readReceipts TINYINT DEFAULT 0");
		}
		if(!this.fieldExist(this.sqLiteDatabase, "contacts", "typingIndicators")) {
			sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN typingIndicators TINYINT DEFAULT 0");
		}
		return true;
	}


	@Override
	public boolean runASync() {
		return true;
	}

	@Override
	public String getText() {
		return "version 66 (add readReceipts and typingIndicators)";
	}
}
