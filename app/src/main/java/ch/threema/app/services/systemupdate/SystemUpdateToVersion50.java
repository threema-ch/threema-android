/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2021 Threema GmbH
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
import ch.threema.storage.models.MessageModel;

/**
 * add index for unread message count
 */
public class SystemUpdateToVersion50 extends UpdateToVersion implements UpdateSystemService.SystemUpdate {

	private final SQLiteDatabase sqLiteDatabase;

	public SystemUpdateToVersion50(SQLiteDatabase sqLiteDatabase) {
		this.sqLiteDatabase = sqLiteDatabase;
	}

	@Override
	public boolean runDirectly() throws SQLException {
		return true;
	}

	@Override
	public boolean runASync() {
		sqLiteDatabase.rawExecSQL("CREATE INDEX IF NOT EXISTS `message_count_idx` ON `" + MessageModel.TABLE
				+ "`(`"  + MessageModel.COLUMN_IDENTITY
				+ "`, `" + MessageModel.COLUMN_OUTBOX
				+ "`, `" + MessageModel.COLUMN_IS_SAVED
				+ "`, `" + MessageModel.COLUMN_IS_READ
				+ "`, `" + MessageModel.COLUMN_IS_STATUS_MESSAGE
				+ "`)");

		sqLiteDatabase.rawExecSQL("CREATE INDEX IF NOT EXISTS `message_queue_idx` ON `" + MessageModel.TABLE
				+ "`(`"  + MessageModel.COLUMN_TYPE
				+ "`, `" + MessageModel.COLUMN_IS_QUEUED
				+ "`, `" + MessageModel.COLUMN_OUTBOX
				+ "`)");

		return true;
	}

	@Override
	public String getText() {
		return "version 50 (db maintenance)";
	}
}
