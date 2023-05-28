/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2023 Threema GmbH
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
 * Create readAt and deliveredAt fields in message model
 */
public class SystemUpdateToVersion68 extends UpdateToVersion implements UpdateSystemService.SystemUpdate {
	public static final int VERSION = 68;
	private final SQLiteDatabase sqLiteDatabase;


	public SystemUpdateToVersion68(SQLiteDatabase sqLiteDatabase) {
		this.sqLiteDatabase = sqLiteDatabase;
	}

	@Override
	public boolean runDirectly() throws SQLException {
		for(String table: new String[]{"message", "m_group_message", "distribution_list_message"}) {
			if(!this.fieldExist(this.sqLiteDatabase, table, "deliveredAtUtc")) {
				sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " ADD COLUMN deliveredAtUtc DATETIME DEFAULT NULL");
			}
			if(!this.fieldExist(this.sqLiteDatabase, table, "readAtUtc")) {
				sqLiteDatabase.rawExecSQL("ALTER TABLE " + table + " ADD COLUMN readAtUtc DATETIME DEFAULT NULL");
			}
		}

		return true;
	}


	@Override
	public boolean runASync() {
		return true;
	}

	@Override
	public String getText() {
		return "version 68 (correlationId)";
	}
}
