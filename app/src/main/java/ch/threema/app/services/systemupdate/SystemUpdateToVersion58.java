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

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import java.sql.SQLException;

import ch.threema.app.services.UpdateSystemService;
import ch.threema.storage.models.GroupMessagePendingMessageIdModel;

/**
 * update constraint for GroupMessagePendingMessageIdModel
 */
public class SystemUpdateToVersion58 extends UpdateToVersion implements UpdateSystemService.SystemUpdate {

	private final SQLiteDatabase sqLiteDatabase;

	public SystemUpdateToVersion58(SQLiteDatabase sqLiteDatabase) {
		this.sqLiteDatabase = sqLiteDatabase;
	}

	@Override
	public boolean runDirectly() throws SQLException {
		sqLiteDatabase.rawExecSQL("DROP TABLE IF EXISTS `m_group_message_pending_message_id`");
		sqLiteDatabase.rawExecSQL("DROP TABLE IF EXISTS `" + GroupMessagePendingMessageIdModel.TABLE + "`");
		sqLiteDatabase.rawExecSQL(
			"CREATE TABLE " + GroupMessagePendingMessageIdModel.TABLE
				+ "("
				+ "`" + GroupMessagePendingMessageIdModel.COLUMN_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT,"
				+ "`" + GroupMessagePendingMessageIdModel.COLUMN_GROUP_MESSAGE_ID + "` INTEGER,"
				+ "`" + GroupMessagePendingMessageIdModel.COLUMN_API_MESSAGE_ID +  "` VARCHAR"
				+ ")");

		return true;
	}

	@Override
	public boolean runASync() {
		return true;
	}

	@Override
	public String getText() {
		return "version 58 (change GroupMessagePendingMessageIdModel primary key)";
	}
}
