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

package ch.threema.app.services.systemupdate;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import java.sql.SQLException;

import ch.threema.app.services.UpdateSystemService;

/**
 * add index for unread message count
 */
public class SystemUpdateToVersion50 implements UpdateSystemService.SystemUpdate {

    private final SQLiteDatabase sqLiteDatabase;

    public SystemUpdateToVersion50(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public boolean runDirectly() throws SQLException {
        return true;
    }

    @Override
    public boolean runAsync() {
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

        return true;
    }

    @Override
    public String getText() {
        return "version 50 (db maintenance)";
    }
}
