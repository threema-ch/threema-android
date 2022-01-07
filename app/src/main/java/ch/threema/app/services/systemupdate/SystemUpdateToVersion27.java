/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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

import ch.threema.app.services.UpdateSystemService;

public class SystemUpdateToVersion27
		extends UpdateToVersion
		implements UpdateSystemService.SystemUpdate {
	private final SQLiteDatabase sqLiteDatabase;

	public SystemUpdateToVersion27(SQLiteDatabase sqLiteDatabase) {
		this.sqLiteDatabase = sqLiteDatabase;
	}


	@Override
	public boolean runDirectly() {
		if(!this.fieldExist(this.sqLiteDatabase, "contacts", "featureLevel")) {
			sqLiteDatabase.rawExecSQL("ALTER TABLE contacts ADD COLUMN featureLevel TINYINT(1) DEFAULT 1");
		}

		//update feature level
		sqLiteDatabase.rawExecSQL("UPDATE contacts SET featureLevel = 1 WHERE isGroupCapable = 1 and featureLevel < 1");

		return true;
	}

	@Override
	public boolean runASync() {
		return true;
	}

	@Override
	public String getText() {
		return "version 26";
	}
}
