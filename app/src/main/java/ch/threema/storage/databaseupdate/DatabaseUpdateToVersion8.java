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

public class DatabaseUpdateToVersion8 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion8(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        for (String statement : GroupModel.getStatements()) {
            sqLiteDatabase.execSQL(statement);
        }
        for (String statement : GroupMemberModel.getStatements()) {
            sqLiteDatabase.execSQL(statement);
        }
        for (String statement : GroupMessageModel.getStatements()) {
            sqLiteDatabase.execSQL(statement);
        }
    }

    @Override
    public int getVersion() {
        return 8;
    }

    private static class GroupModel {
        static final String TABLE = "m_group";
        static final String COLUMN_ID = "id";
        static final String COLUMN_API_GROUP_ID = "apiGroupId";
        static final String COLUMN_NAME = "name";
        static final String COLUMN_CREATOR_IDENTITY = "creatorIdentity";
        static final String COLUMN_CREATED_AT = "createdAt";
        static final String COLUMN_SYNCHRONIZED_AT = "synchronizedAt";
        static final String COLUMN_LAST_UPDATE = "lastUpdate";
        static final String COLUMN_IS_ARCHIVED = "isArchived";
        static final String COLUMN_GROUP_DESC = "groupDesc";
        static final String COLUMN_GROUP_DESC_CHANGED_TIMESTAMP = "changedGroupDescTimestamp";
        static final String COLUMN_COLOR_INDEX = "colorIndex";
        static final String COLUMN_USER_STATE = "userState";
        static final String COLUMN_NOTIFICATION_TRIGGER_POLICY_OVERRIDE = "notificationTriggerPolicyOverride";

        static String[] getStatements() {
            return new String[]{
                "CREATE TABLE `" + TABLE + "` (" +
                    "`" + COLUMN_ID + "` INTEGER " + "PRIMARY KEY AUTOINCREMENT , " +
                    "`" + COLUMN_API_GROUP_ID + "` VARCHAR , " +
                    "`" + COLUMN_NAME + "` VARCHAR , " +
                    "`" + COLUMN_CREATOR_IDENTITY + "` VARCHAR , " +
                    "`" + COLUMN_CREATED_AT + "` VARCHAR , " +
                    "`" + COLUMN_LAST_UPDATE + "` INTEGER, " +
                    "`" + COLUMN_SYNCHRONIZED_AT + "` BIGINT , " +
                    "`" + COLUMN_IS_ARCHIVED + "` TINYINT DEFAULT 0, " +
                    "`" + COLUMN_GROUP_DESC + "` VARCHAR DEFAULT NULL, " +
                    "`" + COLUMN_GROUP_DESC_CHANGED_TIMESTAMP + "` VARCHAR DEFAULT NULL, " +
                    "`" + COLUMN_COLOR_INDEX + "` INTEGER DEFAULT 0 NOT NULL, " +
                    "`" + COLUMN_USER_STATE + "` INTEGER DEFAULT 0 NOT NULL, " +
                    "`" + COLUMN_NOTIFICATION_TRIGGER_POLICY_OVERRIDE + "` BIGINT DEFAULT NULL " +
                    ");",
                "CREATE UNIQUE INDEX `apiGroupIdAndCreator` ON `" + TABLE + "` ( " +
                    "`" + COLUMN_API_GROUP_ID + "`, `" + COLUMN_CREATOR_IDENTITY + "` " +
                    ");"
            };
        }
    }

    private static class GroupMemberModel {
        static String[] getStatements() {
            return new String[]{
                "CREATE TABLE `group_member` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `identity` VARCHAR , `groupId` INTEGER)"
            };
        }
    }

    private static class GroupMessageModel {
        static final String TABLE = "m_group_message";
        static final String COLUMN_ID = "id";
        static final String COLUMN_UID = "uid";
        static final String COLUMN_API_MESSAGE_ID = "apiMessageId";
        static final String COLUMN_IDENTITY = "identity";
        static final String COLUMN_OUTBOX = "outbox";
        static final String COLUMN_TYPE = "type";
        static final String COLUMN_CORRELATION_ID = "correlationId";
        static final String COLUMN_BODY = "body";
        static final String COLUMN_CAPTION = "caption";
        static final String COLUMN_IS_READ = "isRead";
        static final String COLUMN_IS_SAVED = "isSaved";
        static final String COLUMN_STATE = "state";
        static final String COLUMN_CREATED_AT = "createdAtUtc";
        static final String COLUMN_POSTED_AT = "postedAtUtc";
        static final String COLUMN_MODIFIED_AT = "modifiedAtUtc";
        static final String COLUMN_IS_STATUS_MESSAGE = "isStatusMessage";
        static final String COLUMN_IS_QUEUED = "isQueued";
        static final String COLUMN_QUOTED_MESSAGE_API_MESSAGE_ID = "quotedMessageId";
        static final String COLUMN_MESSAGE_CONTENTS_TYPE = "messageContentsType";
        static final String COLUMN_MESSAGE_FLAGS = "messageFlags";
        static final String COLUMN_DELIVERED_AT = "deliveredAtUtc";
        static final String COLUMN_READ_AT = "readAtUtc";
        static final String COLUMN_FORWARD_SECURITY_MODE = "forwardSecurityMode";
        static final String COLUMN_DISPLAY_TAGS = "displayTags";
        static final String COLUMN_EDITED_AT = "editedAtUtc";
        static final String COLUMN_DELETED_AT = "deletedAtUtc";
        static final String COLUMN_GROUP_ID = "groupId";
        static final String COLUMN_GROUP_MESSAGE_STATES = "groupMessageStates";

        static String[] getStatements() {
            return new String[]{
                "CREATE TABLE `" + TABLE + "`" +
                    "(" +
                    "`" + COLUMN_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT , " +
                    "`" + COLUMN_UID + "` VARCHAR , " +
                    "`" + COLUMN_API_MESSAGE_ID + "` VARCHAR , " +
                    "`" + COLUMN_GROUP_ID + "` INTEGER NOT NULL , " +
                    "`" + COLUMN_IDENTITY + "` VARCHAR , " +
                    "`" + COLUMN_OUTBOX + "` SMALLINT , " +
                    "`" + COLUMN_TYPE + "` INTEGER ," +
                    "`" + COLUMN_CORRELATION_ID + "` VARCHAR ," +
                    "`" + COLUMN_BODY + "` VARCHAR ," +
                    "`" + COLUMN_CAPTION + "` VARCHAR ," +
                    "`" + COLUMN_IS_READ + "` SMALLINT ," +
                    "`" + COLUMN_IS_SAVED + "` SMALLINT ," +
                    "`" + COLUMN_IS_QUEUED + "` TINYINT ," +
                    "`" + COLUMN_STATE + "` VARCHAR , " +
                    "`" + COLUMN_POSTED_AT + "` BIGINT , " +
                    "`" + COLUMN_CREATED_AT + "` BIGINT , " +
                    "`" + COLUMN_MODIFIED_AT + "` BIGINT , " +
                    "`" + COLUMN_IS_STATUS_MESSAGE + "` SMALLINT ," +
                    "`" + COLUMN_QUOTED_MESSAGE_API_MESSAGE_ID + "` VARCHAR ," +
                    "`" + COLUMN_MESSAGE_CONTENTS_TYPE + "` TINYINT ," +
                    "`" + COLUMN_MESSAGE_FLAGS + "` INT ," +
                    "`" + COLUMN_DELIVERED_AT + "` DATETIME ," +
                    "`" + COLUMN_READ_AT + "` DATETIME ," +
                    "`" + COLUMN_FORWARD_SECURITY_MODE + "` TINYINT DEFAULT 0 ," +
                    "`" + COLUMN_GROUP_MESSAGE_STATES + "` VARCHAR ," +
                    "`" + COLUMN_DISPLAY_TAGS + "` TINYINT DEFAULT 0 ," +
                    "`" + COLUMN_EDITED_AT + "` DATETIME ," +
                    "`" + COLUMN_DELETED_AT + "` DATETIME );",
                "CREATE INDEX `group_message_uid_idx` ON `" + TABLE + "` ( `" + COLUMN_UID + "` )",
                "CREATE INDEX `m_group_message_outbox_idx` ON `" + TABLE + "` ( `" + COLUMN_OUTBOX + "` );",
                "CREATE INDEX `m_group_message_identity_idx` ON `" + TABLE + "` ( `" + COLUMN_IDENTITY + "` );",
                "CREATE INDEX `m_group_message_groupId_idx` ON `" + TABLE + "` ( `" + COLUMN_GROUP_ID + "` );",
                "CREATE INDEX `groupMessageApiMessageIdIdx` ON `" + TABLE + "` ( `" + COLUMN_API_MESSAGE_ID + "` );",
                "CREATE INDEX `groupMessageCorrelationIdIdx` ON `" + TABLE + "` ( `" + COLUMN_CORRELATION_ID + "` );",
                "CREATE INDEX `group_message_state_idx` ON `" + TABLE
                    + "`(`" + COLUMN_TYPE
                    + "`, `" + COLUMN_STATE
                    + "`, `" + COLUMN_OUTBOX
                    + "`)",
            };
        }
    }
}
