/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2023 Threema GmbH
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
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.MessageModel;

/**
 * add caption field to normal, group and distribution list message models
 */
public class SystemUpdateToVersion35 extends  UpdateToVersion implements UpdateSystemService.SystemUpdate {

	private final SQLiteDatabase sqLiteDatabase;


	public SystemUpdateToVersion35(SQLiteDatabase sqLiteDatabase) {
		this.sqLiteDatabase = sqLiteDatabase;
	}

	@Override
	public boolean runDirectly() throws SQLException {

		//add new isQueued field to message model fields
		for(String table: new String[]{
				MessageModel.TABLE,
				GroupMessageModel.TABLE,
				DistributionListMessageModel.TABLE
		}) {
			if(!this.fieldExist(this.sqLiteDatabase, table, AbstractMessageModel.COLUMN_CAPTION)) {
				sqLiteDatabase.rawExecSQL("ALTER TABLE " + table
						+ " ADD COLUMN " + AbstractMessageModel.COLUMN_CAPTION + " VARCHAR NULL");
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
		return "version 35)";
	}
}
