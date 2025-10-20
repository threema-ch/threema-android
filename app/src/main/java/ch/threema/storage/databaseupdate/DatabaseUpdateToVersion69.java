/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

import java.util.stream.Stream;

public class DatabaseUpdateToVersion69 implements DatabaseUpdate {
    public static final int VERSION = 69;

    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion69(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() {
        // redo table init in case internal tester had different previous versions default flag, invalidated flag etc.
        dropTables();
        createGroupInviteTable();
        createOutgoingGroupJoinRequestTable();
        createIncomingGroupJoinRequestModelFactory();
    }

    private void dropTables() {
        // dropping the tables also removes the indices: https://www.sqlite.org/lang_droptable.html
        Stream.of(
            "group_invite_model",
            "incoming_group_join_request",
            "group_join_request"
        ).forEach(table -> sqLiteDatabase.rawExecSQL("DROP TABLE IF EXISTS `" + table + "`;"));
    }

    private void createGroupInviteTable() {
        sqLiteDatabase.rawExecSQL("CREATE TABLE `group_invite_model` ( " +
            "`group_invite_index_id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "`group_id` INTEGER, " +
            "`default_flag` BOOLEAN, " +
            "`token` VARCHAR, " +
            "`invite_name` TEXT, " +
            "`original_group_name` TEXT, " +
            "`manual_confirmation` BOOLEAN, " +
            "`expiration_date` DATETIME NULL, " +
            "`is_invalidated` BOOLEAN FALSE " +
            ")");
    }

    private void createOutgoingGroupJoinRequestTable() {
        sqLiteDatabase.rawExecSQL("CREATE TABLE IF NOT EXISTS `group_join_request` ( " +
            "`outgoing_request_index_id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "`token` VARCHAR, " +
            "`group_name` TEXT, " +
            "`message` TEXT, " +
            "`admin_identity` VARCHAR, " +
            "`request_time` DATETIME, " +
            "`status` VARCHAR, " +
            "`group_api_id` INTEGER NULL " +
            ")");
    }

    private void createIncomingGroupJoinRequestModelFactory() {
        sqLiteDatabase.rawExecSQL("CREATE TABLE IF NOT EXISTS`incoming_group_join_request` ( " +
            "`incoming_request_index_id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "`group_invite` INTEGER, " +
            "`message` TEXT, " +
            "`requesting_identity` VARCHAR, " +
            "`request_time` DATETIME, " +
            "`response_status` VARCHAR " +
            ")");
    }

    @Override
    public int getVersion() {
        return VERSION;
    }
}
