/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2025 Threema GmbH
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

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;


public class DatabaseUpdateToVersion37 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion37(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        for (String statement : WebClientSessionModel.getStatements()) {
            sqLiteDatabase.execSQL(statement);
        }
    }

    @Override
    public int getVersion() {
        return 37;
    }

    private static class WebClientSessionModel {
        static final String TABLE = "wc_session";
        static final String COLUMN_ID = "id";
        static final String COLUMN_KEY = "key";
        static final String COLUMN_KEY256 = "key256";
        static final String COLUMN_PRIVATE_KEY = "private_key";
        static final String COLUMN_STATE = "state";
        static final String COLUMN_CREATED = "created";
        static final String COLUMN_LAST_CONNECTION = "last_connection";
        static final String COLUMN_IS_PERSISTENT = "is_persistent";
        static final String COLUMN_CLIENT_DESCRIPTION = "client";
        static final String COLUMN_LABEL = "label";
        static final String COLUMN_SELF_HOSTED = "self_hosted";
        static final String COLUMN_PROTOCOL_VERSION = "protocol_version";
        static final String COLUMN_SALTY_RTC_HOST = "salty_host";
        static final String COLUMN_SALTY_RTC_PORT = "salty_port";
        static final String COLUMN_SERVER_KEY = "server_key";
        static final String COLUMN_PUSH_TOKEN = "push_token";

        static String[] getStatements() {
            return new String[]{
                "CREATE TABLE `" + TABLE + "` (" +
                    "`" + COLUMN_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT , " +
                    "`" + COLUMN_KEY + "` BLOB NULL," +
                    "`" + COLUMN_KEY256 + "` VARCHAR NULL," +
                    "`" + COLUMN_PRIVATE_KEY + "` BLOB NULL," +
                    "`" + COLUMN_CREATED + "` BIGINT NULL," +
                    "`" + COLUMN_LAST_CONNECTION + "` BIGINT NULL," +
                    "`" + COLUMN_CLIENT_DESCRIPTION + "` VARCHAR, " +
                    "`" + COLUMN_STATE + "` VARCHAR NOT NULL, " +
                    "`" + COLUMN_IS_PERSISTENT + "` TINYINT NOT NULL DEFAULT 0," +
                    "`" + COLUMN_LABEL + "` VARCHAR NULL," +
                    "`" + COLUMN_SELF_HOSTED + "` TINYINT NOT NULL DEFAULT 0," +
                    "`" + COLUMN_PROTOCOL_VERSION + "` INT NOT NULL," +
                    "`" + COLUMN_SALTY_RTC_HOST + "` VARCHAR NOT NULL," +
                    "`" + COLUMN_SALTY_RTC_PORT + "` INT NOT NULL," +
                    "`" + COLUMN_SERVER_KEY + "` BLOB NULL," +
                    "`" + COLUMN_PUSH_TOKEN + "` VARCHAR(255) NULL" +
                    ");",
                "CREATE UNIQUE INDEX `webClientSessionKey` ON `" + TABLE + "` ( `" + COLUMN_KEY + "` );",
                "CREATE UNIQUE INDEX `webClientSessionKey256` ON `" + TABLE + "` ( `" + COLUMN_KEY256 + "` );"
            };
        }
    }
}
