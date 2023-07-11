/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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

import ch.threema.app.services.UpdateSystemService;

/**
 * creates the state column
 */
public class SystemUpdateToVersion28
		extends UpdateToVersion
		implements UpdateSystemService.SystemUpdate {
	private final SQLiteDatabase sqLiteDatabase;

	public SystemUpdateToVersion28(SQLiteDatabase sqLiteDatabase) {
		this.sqLiteDatabase = sqLiteDatabase;
	}

	@Override
	public boolean runDirectly() {

		if(!this.fieldExist(this.sqLiteDatabase, "contacts", "state")) {
			sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN state VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'");
			return true;
		}

		return false;
	}

	@Override
	public boolean runASync() {
		return true;
	}

	@Override
	public String getText() {
		return "version 28";
	}
}
