/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.storage.databaseupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

public class DatabaseUpdateToVersion17 implements DatabaseUpdate {
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion17(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() {
        //unique uid indexes
        run("CREATE INDEX IF NOT EXISTS `messageUidIdx` ON `message`(`uid`)");
        run("CREATE INDEX IF NOT EXISTS `groupMessageUidIdx` ON `m_group_message`(`uid`)");
        run("CREATE INDEX IF NOT EXISTS `distributionListMessageUidIdx` ON `distribution_list_message`(`uid`)");

        //index on apiMessageId
        run("CREATE INDEX IF NOT EXISTS `messageApiMessageIdIdx` ON `message`(`apiMessageId`)");
        run("CREATE INDEX IF NOT EXISTS `groupMessageApiMessageIdIdx` ON `m_group_message`(`apiMessageId`)");
        run("CREATE INDEX IF NOT EXISTS `distributionListMessageIdIdx` ON `distribution_list_message`(`apiMessageId`)");

        run("CREATE INDEX IF NOT EXISTS `distributionListDistributionListIdIdx` ON `distribution_list_message`(`distributionListId`)");
    }

    private void run(String query) {
        sqLiteDatabase.rawExecSQL(query);
    }

    @Override
    public int getVersion() {
        return 17;
    }
}
