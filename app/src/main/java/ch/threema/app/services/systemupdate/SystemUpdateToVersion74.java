/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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
import ch.threema.app.webclient.converter.DistributionList;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.MessageModel;

/**
 * Create forwardSecurityMode field in group and distribution list message model.
 */
public class SystemUpdateToVersion74 extends UpdateToVersion implements UpdateSystemService.SystemUpdate {
	public static final int VERSION = 74;

	private final SQLiteDatabase sqLiteDatabase;

	public SystemUpdateToVersion74(SQLiteDatabase sqLiteDatabase) {
		this.sqLiteDatabase = sqLiteDatabase;
	}

	@Override
	public boolean runDirectly() throws SQLException {
		if (!this.fieldExist(this.sqLiteDatabase, GroupMessageModel.TABLE, AbstractMessageModel.COLUMN_FORWARD_SECURITY_MODE)) {
			sqLiteDatabase.rawExecSQL("ALTER TABLE " + GroupMessageModel.TABLE + " ADD COLUMN " +
				AbstractMessageModel.COLUMN_FORWARD_SECURITY_MODE + " TINYINT DEFAULT 0");
		}
		if (!this.fieldExist(this.sqLiteDatabase, DistributionListMessageModel.TABLE, AbstractMessageModel.COLUMN_FORWARD_SECURITY_MODE)) {
			sqLiteDatabase.rawExecSQL("ALTER TABLE " + DistributionListMessageModel.TABLE + " ADD COLUMN " +
				AbstractMessageModel.COLUMN_FORWARD_SECURITY_MODE + " TINYINT DEFAULT 0");
		}

		return true;
	}

	@Override
	public boolean runASync() {
		return true;
	}

	@Override
	public String getText() {
		return "version 74 (forwardSecurity 2)";
	}
}
