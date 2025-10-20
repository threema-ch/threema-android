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

import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;


public class DatabaseUpdateToVersion45 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion45(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        for (String statement : ConversationTagModel.getStatements()) {
            sqLiteDatabase.execSQL(statement);
        }
    }

    @Override
    public int getVersion() {
        return 45;
    }

    private static class ConversationTagModel {
        static final String TABLE = "conversation_tag";
        static final String COLUMN_CONVERSATION_UID = "conversationUid";
        static final String COLUMN_TAG = "tag";
        static final String COLUMN_CREATED_AT = "createdAt";

        static String[] getStatements() {
            return new String[]{
                "CREATE TABLE IF NOT EXISTS `" + TABLE + "` (" +
                    "`" + COLUMN_CONVERSATION_UID + "` VARCHAR NOT NULL, " +
                    "`" + COLUMN_TAG + "` BLOB NULL," +
                    "`" + COLUMN_CREATED_AT + "` BIGINT, " +
                    "PRIMARY KEY (`" + COLUMN_CONVERSATION_UID + "`, `" + COLUMN_TAG + "`) " +
                    ");",
                "CREATE UNIQUE INDEX IF NOT EXISTS `conversationTagKeyConversationTag` ON `" + TABLE
                    + "` ( `" + COLUMN_CONVERSATION_UID + "`, `" + COLUMN_TAG + "` );",
                "CREATE INDEX IF NOT EXISTS `conversationTagConversation` ON `" + TABLE + "` ( `" + COLUMN_CONVERSATION_UID + "` );",
                "CREATE INDEX IF NOT EXISTS`conversationTagTag` ON `" + TABLE + "` ( `" + COLUMN_TAG + "` );"
            };
        }
    }
}
