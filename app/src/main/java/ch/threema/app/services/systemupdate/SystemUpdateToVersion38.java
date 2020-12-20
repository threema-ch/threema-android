/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2020 Threema GmbH
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

import ch.threema.app.services.UpdateSystemService;
import ch.threema.app.utils.LogUtil;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.WebClientSessionModel;


public class SystemUpdateToVersion38 extends  UpdateToVersion implements UpdateSystemService.SystemUpdate {
	private static final Logger logger = LoggerFactory.getLogger(SystemUpdateToVersion38.class);

	private final DatabaseServiceNew databaseService;
	private final SQLiteDatabase sqLiteDatabase;


	public SystemUpdateToVersion38(DatabaseServiceNew databaseService, SQLiteDatabase sqLiteDatabase) {
		this.databaseService = databaseService;
		this.sqLiteDatabase = sqLiteDatabase;
	}

	@Override
	public boolean runDirectly() throws SQLException {

		//only for beta testers!
		//append new fields
		if(!this.fieldExist(this.sqLiteDatabase,
				WebClientSessionModel.TABLE, WebClientSessionModel.COLUMN_SELF_HOSTED)) {
			this.sqLiteDatabase.execSQL("ALTER TABLE " + WebClientSessionModel.TABLE +
					" ADD COLUMN " +  WebClientSessionModel.COLUMN_SELF_HOSTED + " TINYINT NOT NULL DEFAULT 0");
		}


		if(!this.fieldExist(this.sqLiteDatabase,
				WebClientSessionModel.TABLE, WebClientSessionModel.COLUMN_PROTOCOL_VERSION)) {
			this.sqLiteDatabase.execSQL("ALTER TABLE " + WebClientSessionModel.TABLE +
					" ADD COLUMN " +  WebClientSessionModel.COLUMN_PROTOCOL_VERSION + " INT NOT NULL DEFAULT 1");
		}


		if(!this.fieldExist(this.sqLiteDatabase,
				WebClientSessionModel.TABLE, WebClientSessionModel.COLUMN_SALTY_RTC_HOST)) {
			this.sqLiteDatabase.execSQL("ALTER TABLE " + WebClientSessionModel.TABLE +
					" ADD COLUMN " +  WebClientSessionModel.COLUMN_SALTY_RTC_HOST + " VARCHAR NULL");

			//append defaults
			Cursor cursor = this.sqLiteDatabase.rawQuery("SELECT " +
					WebClientSessionModel.COLUMN_ID + "," +
					"HEX(" +WebClientSessionModel.COLUMN_KEY + ")" +
					" FROM " + WebClientSessionModel.TABLE, null);
			if(cursor != null) {
				try {
					while(cursor.moveToNext()) {
						int id = cursor.getInt(0);
						String keyAsHex = cursor.getString(1).toLowerCase();

						this.sqLiteDatabase.execSQL("UPDATE " + WebClientSessionModel.TABLE +
								" SET " +  WebClientSessionModel.COLUMN_SALTY_RTC_HOST + " = ? WHERE id=?", new String[]{
								"saltyrtc-" + keyAsHex.substring(0, 2) + ".threema.ch",
								String.valueOf(id)
						});

					}
				}
				catch (Exception x) {
					logger.error("failed to update existing sessions, continue", x);
				}
				finally {
					cursor.close();
				}
			}
		}

		if(!this.fieldExist(this.sqLiteDatabase,
				WebClientSessionModel.TABLE, WebClientSessionModel.COLUMN_SALTY_RTC_PORT)) {
			this.sqLiteDatabase.execSQL("ALTER TABLE " + WebClientSessionModel.TABLE +
					" ADD COLUMN " +  WebClientSessionModel.COLUMN_SALTY_RTC_PORT + " TINYINT NOT NULL DEFAULT 443");
		}


		if(!this.fieldExist(this.sqLiteDatabase,
				WebClientSessionModel.TABLE, WebClientSessionModel.COLUMN_SERVER_KEY)) {
			this.sqLiteDatabase.execSQL("ALTER TABLE " + WebClientSessionModel.TABLE +
					" ADD COLUMN " +  WebClientSessionModel.COLUMN_SERVER_KEY + " BLOB");

			//append defaults
			this.sqLiteDatabase.execSQL("UPDATE " + WebClientSessionModel.TABLE +
					" SET " +  WebClientSessionModel.COLUMN_SERVER_KEY + " = X'b1337fc8402f7db8ea639e05ed05d65463e24809792f91eca29e88101b4a2171'",
					new String[]{}
			);
		}

		return true;
	}

	@Override
	public boolean runASync() {
		return true;
	}

	@Override
	public String getText() {
		return "version 38";
	}
}
