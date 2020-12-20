/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2020 Threema GmbH
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

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;

import ch.threema.app.services.UpdateSystemService;
import ch.threema.app.utils.ColorUtil;

public class SystemUpdateToVersion23 implements UpdateSystemService.SystemUpdate {

	private final SQLiteDatabase sqLiteDatabase;

	public SystemUpdateToVersion23(SQLiteDatabase sqLiteDatabase) {
		this.sqLiteDatabase = sqLiteDatabase;
	}

	@Override
	public boolean runDirectly() {
		Cursor identityCursor = this.sqLiteDatabase.rawQuery("SELECT identity FROM contacts", null);
		if(identityCursor != null) {
			int[] colors = ColorUtil.getInstance().generateGoogleColorPalette(identityCursor.getCount());
			for(int n = 0; n < colors.length; n++) {
				identityCursor.moveToPosition(n);

				//update!
				this.sqLiteDatabase.execSQL("UPDATE contacts SET color = ? " +
						"WHERE identity = ?", new String[]{
						String.valueOf(colors[n]),
						identityCursor.getString(0)
				});
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
		return "version 21";
	}
}
