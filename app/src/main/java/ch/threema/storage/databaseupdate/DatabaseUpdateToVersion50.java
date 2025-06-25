/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

/**
 * add index for unread message count
 */
public class DatabaseUpdateToVersion50 implements DatabaseUpdate {

    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion50(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() {
        sqLiteDatabase.rawExecSQL("CREATE INDEX IF NOT EXISTS `message_count_idx` ON `message"
            + "`(`identity"
            + "`, `outbox"
            + "`, `isSaved"
            + "`, `isRead"
            + "`, `isStatusMessage"
            + "`)");

        sqLiteDatabase.rawExecSQL("CREATE INDEX IF NOT EXISTS `message_queue_idx` ON `message"
            + "`(`type"
            + "`, `isQueued"
            + "`, `outbox"
            + "`)");
    }

    @Override
    public int getVersion() {
        return 50;
    }
}
