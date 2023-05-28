/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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

public class SystemUpdateToVersion17 implements UpdateSystemService.SystemUpdate {
	private final SQLiteDatabase sqLiteDatabase;

	public SystemUpdateToVersion17(SQLiteDatabase sqLiteDatabase) {
		this.sqLiteDatabase = sqLiteDatabase;
	}

	@Override
	public boolean runDirectly() {
		//unique uid indexes

		this.run("CREATE INDEX IF NOT EXISTS `messageUidIdx` ON `message`(`uid`)");
		this.run("CREATE INDEX IF NOT EXISTS `groupMessageUidIdx` ON `m_group_message`(`uid`)");
		this.run("CREATE INDEX IF NOT EXISTS `distributionListMessageUidIdx` ON `distribution_list_message`(`uid`)");

		//index on apiMessageId
		this
				.run("CREATE INDEX IF NOT EXISTS `messageApiMessageIdIdx` ON `message`(`apiMessageId`)")
				.run("CREATE INDEX IF NOT EXISTS `groupMessageApiMessageIdIdx` ON `m_group_message`(`apiMessageId`)")
				.run("CREATE INDEX IF NOT EXISTS `distributionListMessageIdIdx` ON `distribution_list_message`(`apiMessageId`)");


		this
				.run("CREATE INDEX IF NOT EXISTS `distributionListDistributionListIdIdx` ON `distribution_list_message`(`distributionListId`)");

		return true;
	}

	private SystemUpdateToVersion17 run(String query) {
		this.sqLiteDatabase.rawExecSQL(query);
		return this;
	}
	@Override
	public boolean runASync() {
		return true;
	}

	@Override
	public String getText() {
		return "version 17";
	}
}
