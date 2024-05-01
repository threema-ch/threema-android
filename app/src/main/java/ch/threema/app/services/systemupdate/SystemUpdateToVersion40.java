/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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

import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UpdateSystemService;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.WebClientSessionModel;

import static ch.threema.app.services.systemupdate.SystemUpdateHelpersKt.fieldExists;


public class SystemUpdateToVersion40 implements UpdateSystemService.SystemUpdate {
	private final SQLiteDatabase sqLiteDatabase;

	public SystemUpdateToVersion40(SQLiteDatabase sqLiteDatabase) {
		this.sqLiteDatabase = sqLiteDatabase;
	}

	@Override
	public boolean runDirectly() throws SQLException {
		if(!fieldExists(this.sqLiteDatabase,
				WebClientSessionModel.TABLE, WebClientSessionModel.COLUMN_PUSH_TOKEN)) {
			this.sqLiteDatabase.execSQL("ALTER TABLE " + WebClientSessionModel.TABLE +
					" ADD COLUMN " +  WebClientSessionModel.COLUMN_PUSH_TOKEN + " VARCHAR(255) DEFAULT NULL");
		}

		return true;
	}

	@Override
	public boolean runAsync() {
		// Master Key is unlocked
		PreferenceService preferenceService = ThreemaApplication.getServiceManager().getPreferenceService();
		if (preferenceService != null) {
			String currentPushToken = preferenceService.getPushToken();

			if (!TestUtil.empty(currentPushToken)) {
				// update all
				this.sqLiteDatabase.execSQL("UPDATE " + WebClientSessionModel.TABLE + " "
						+ "SET " + WebClientSessionModel.COLUMN_PUSH_TOKEN + "=?",
						new String[]{
								currentPushToken
						});
			}
		}

		return true;
	}

	@Override
	public String getText() {
		return "version 40";
	}
}
