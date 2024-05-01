/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2024 Threema GmbH
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

import static ch.threema.app.services.systemupdate.SystemUpdateHelpersKt.fieldExists;

/**
 * Add a messageFlags field
 */
public class SystemUpdateToVersion67 implements UpdateSystemService.SystemUpdate {
	public static final int VERSION = 67;
	private final SQLiteDatabase sqLiteDatabase;

	public SystemUpdateToVersion67(SQLiteDatabase sqLiteDatabase) {
		this.sqLiteDatabase = sqLiteDatabase;
	}

	@Override
	public boolean runDirectly() throws SQLException {
		if(!fieldExists(this.sqLiteDatabase, "contacts", "readReceipts")) {
			sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN readReceipts TINYINT DEFAULT 0");
		}
		if(!fieldExists(this.sqLiteDatabase, "contacts", "typingIndicators")) {
			sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN typingIndicators TINYINT DEFAULT 0");
		}
		return true;
	}


	@Override
	public boolean runAsync() {
		return true;
	}

	@Override
	public String getText() {
		return "version 67 (add readReceipts and typingIndicators)";
	}
}
