/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

import androidx.annotation.NonNull;

import net.sqlcipher.database.SQLiteDatabase;

import java.util.Arrays;

import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.services.UpdateSystemService;

public class SystemUpdateToVersion19 implements UpdateSystemService.SystemUpdate {
	private final SQLiteDatabase sqLiteDatabase;

	public SystemUpdateToVersion19(SQLiteDatabase sqLiteDatabase) {
		this.sqLiteDatabase = sqLiteDatabase;
	}

	@Override
	public boolean runDirectly() {
		//update db first
		String[] messageTableColumnNames = sqLiteDatabase.rawQuery("SELECT * FROM m_group LIMIT 0", null).getColumnNames();

		boolean hasField = Functional.select(Arrays.asList(messageTableColumnNames), new IPredicateNonNull<String>() {
			@Override
			public boolean apply(@NonNull String type) {
				return type.equals("deleted");
			}
		}) != null;


		if(!hasField) {
			sqLiteDatabase.execSQL("ALTER TABLE m_group ADD COLUMN deleted TINYINT DEFAULT 0");
		}

		return true;
	}
	@Override
	public boolean runASync() {
		return true;
	}

	@Override
	public String getText() {
		return "version 19";
	}
}
