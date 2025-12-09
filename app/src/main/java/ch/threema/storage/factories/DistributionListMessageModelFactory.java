/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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

package ch.threema.storage.factories;

import android.content.ContentValues;
import android.database.Cursor;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.services.MessageService;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseService;
import ch.threema.storage.DatabaseUtil;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.MessageType;

public class DistributionListMessageModelFactory extends AbstractMessageModelFactory {
    private static final Logger logger = getThreemaLogger("DistributionListMessageModelFactory");

    public DistributionListMessageModelFactory(DatabaseService databaseService) {
        super(databaseService, DistributionListMessageModel.TABLE);
    }

    public List<DistributionListMessageModel> getAll() {
        return convertList(getReadableDatabase().query(this.getTableName(),
            null,
            null,
            null,
            null,
            null,
            null));
    }

    public @Nullable DistributionListMessageModel getById(long id) {
        return getFirst(
            DistributionListMessageModel.COLUMN_ID + "=?",
            new String[]{String.valueOf(id)}
        );
    }

    public @Nullable DistributionListMessageModel getByUid(@NonNull String uid) {
        return getFirst(
            DistributionListMessageModel.COLUMN_UID + "=?",
            new String[]{uid}
        );
    }

    private List<DistributionListMessageModel> convertList(Cursor c) {

        List<DistributionListMessageModel> result = new ArrayList<>();
        if (c != null) {
            try {
                while (c.moveToNext()) {
                    result.add(convert(c));
                }
            } finally {
                c.close();
            }
        }
        return result;
    }

    private DistributionListMessageModel convert(Cursor cursor) {
        if (cursor != null && cursor.getPosition() >= 0) {
            final DistributionListMessageModel c = new DistributionListMessageModel();

            //convert default
            super.convert(c, new CursorHelper(cursor, getColumnIndexCache()).current(new CursorHelper.Callback() {
                @Override
                public boolean next(CursorHelper cursorHelper) {
                    Long distributionListId = cursorHelper.getLong(DistributionListMessageModel.COLUMN_DISTRIBUTION_LIST_ID);
                    if (distributionListId != null) {
                        c.setDistributionListId(distributionListId);
                    } else {
                        logger.warn("Distribution list id is null");
                    }
                    return false;
                }
            }));

            return c;
        }

        return null;
    }

    public long countByTypes(MessageType[] messageTypes) {
        String[] args = new String[messageTypes.length];
        for (int n = 0; n < messageTypes.length; n++) {
            args[n] = String.valueOf(messageTypes[n].ordinal());
        }
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT COUNT(*) FROM " + this.getTableName() + " "
                + "WHERE " + DistributionListMessageModel.COLUMN_TYPE + " IN (" + DatabaseUtil.makePlaceholders(args.length) + ")",
            args
        );

        return DatabaseUtil.count(c);
    }

    public boolean createOrUpdate(DistributionListMessageModel distributionListMessageModel) {
        boolean insert = true;
        if (distributionListMessageModel.getId() > 0) {
            Cursor cursor = getReadableDatabase().query(
                this.getTableName(),
                null,
                DistributionListMessageModel.COLUMN_ID + "=?",
                new String[]{
                    String.valueOf(distributionListMessageModel.getId())
                },
                null,
                null,
                null
            );

            if (cursor != null) {
                try {
                    insert = !cursor.moveToNext();
                } finally {
                    cursor.close();
                }
            }
        }

        if (insert) {
            return create(distributionListMessageModel);
        } else {
            return update(distributionListMessageModel);
        }
    }

    private boolean create(DistributionListMessageModel distributionListMessageModel) {
        ContentValues contentValues = this.buildContentValues(distributionListMessageModel);
        contentValues.put(DistributionListMessageModel.COLUMN_DISTRIBUTION_LIST_ID, distributionListMessageModel.getDistributionListId());
        long newId = getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
        if (newId > 0) {
            distributionListMessageModel.setId((int) newId);
            return true;
        }
        return false;
    }

    private boolean update(DistributionListMessageModel distributionListMessageModel) {
        ContentValues contentValues = this.buildContentValues(distributionListMessageModel);
        getWritableDatabase().update(this.getTableName(),
            contentValues,
            DistributionListMessageModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(distributionListMessageModel.getId())
            });
        return true;
    }

    public long countMessages(long distributionListId) {
        return DatabaseUtil.count(getReadableDatabase().rawQuery(
            "SELECT COUNT(*) FROM " + this.getTableName()
                + " WHERE " + DistributionListMessageModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(distributionListId)
            }
        ));
    }

    public List<DistributionListMessageModel> find(long distributionListId, MessageService.MessageFilter filter) {
        QueryBuilder queryBuilder = new QueryBuilder();

        //sort by id!
        String orderBy = DistributionListMessageModel.COLUMN_ID + " DESC";
        List<String> placeholders = new ArrayList<>();

        queryBuilder.appendWhere(DistributionListMessageModel.COLUMN_DISTRIBUTION_LIST_ID + "=?");
        placeholders.add(String.valueOf(distributionListId));

        //default filters
        this.appendFilter(queryBuilder, filter, placeholders);

        queryBuilder.setTables(this.getTableName());
        List<DistributionListMessageModel> messageModels = convertList(queryBuilder.query(
            getReadableDatabase(),
            null,
            null,
            placeholders.toArray(new String[placeholders.size()]),
            null,
            null,
            orderBy,
            this.limitFilter(filter)));

        this.postFilter(messageModels, filter);

        return messageModels;
    }

    public List<DistributionListMessageModel> getByDistributionListIdUnsorted(long distributionListId) {
        return convertList(getReadableDatabase().query(this.getTableName(),
            null,
            DistributionListMessageModel.COLUMN_DISTRIBUTION_LIST_ID + "=?",
            new String[]{
                String.valueOf(distributionListId)
            },
            null,
            null,
            null));
    }

    public int delete(DistributionListMessageModel distributionListMessageModel) {
        return getWritableDatabase().delete(this.getTableName(),
            DistributionListMessageModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(distributionListMessageModel.getId())
            });
    }

    private @Nullable DistributionListMessageModel getFirst(String selection, String[] selectionArgs) {
        Cursor cursor = getReadableDatabase().query(
            this.getTableName(),
            null,
            selection,
            selectionArgs,
            null,
            null,
            null
        );

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return convert(cursor);
                }
            } finally {
                cursor.close();
            }
        }

        return null;
    }

    @Override
    public String[] getStatements() {
        return new String[]{
            "CREATE TABLE `" + DistributionListMessageModel.TABLE + "`" +
                "(" +
                "`" + DistributionListMessageModel.COLUMN_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT ," +
                "`" + DistributionListMessageModel.COLUMN_UID + "` VARCHAR ," +
                "`" + DistributionListMessageModel.COLUMN_API_MESSAGE_ID + "` VARCHAR ," +
                "`" + DistributionListMessageModel.COLUMN_DISTRIBUTION_LIST_ID + "` INTEGER NOT NULL ," +
                "`" + DistributionListMessageModel.COLUMN_IDENTITY + "` VARCHAR ," +
                "`" + DistributionListMessageModel.COLUMN_OUTBOX + "` SMALLINT ," +
                "`" + DistributionListMessageModel.COLUMN_TYPE + "` INTEGER ," +
                "`" + DistributionListMessageModel.COLUMN_CORRELATION_ID + "` VARCHAR ," +
                "`" + DistributionListMessageModel.COLUMN_BODY + "` VARCHAR ," +
                "`" + DistributionListMessageModel.COLUMN_CAPTION + "` VARCHAR ," +
                "`" + DistributionListMessageModel.COLUMN_IS_READ + "` SMALLINT ," +
                "`" + DistributionListMessageModel.COLUMN_IS_SAVED + "` SMALLINT ," +
                "`" + DistributionListMessageModel.COLUMN_IS_QUEUED + "` TINYINT ," +
                "`" + DistributionListMessageModel.COLUMN_STATE + "` VARCHAR ," +
                "`" + DistributionListMessageModel.COLUMN_POSTED_AT + "` BIGINT ," +
                "`" + DistributionListMessageModel.COLUMN_CREATED_AT + "` BIGINT ," +
                "`" + DistributionListMessageModel.COLUMN_MODIFIED_AT + "` BIGINT ," +
                "`" + DistributionListMessageModel.COLUMN_IS_STATUS_MESSAGE + "` SMALLINT ," +
                "`" + DistributionListMessageModel.COLUMN_QUOTED_MESSAGE_API_MESSAGE_ID + "` VARCHAR ," +
                "`" + DistributionListMessageModel.COLUMN_MESSAGE_CONTENTS_TYPE + "` TINYINT ," +
                "`" + DistributionListMessageModel.COLUMN_MESSAGE_FLAGS + "` INT ," +
                "`" + DistributionListMessageModel.COLUMN_DELIVERED_AT + "` DATETIME ," +
                "`" + DistributionListMessageModel.COLUMN_READ_AT + "` DATETIME ," +
                "`" + DistributionListMessageModel.COLUMN_FORWARD_SECURITY_MODE + "` TINYINT DEFAULT 0 ," +
                "`" + DistributionListMessageModel.COLUMN_DISPLAY_TAGS + "` TINYINT DEFAULT 0 ," +
                "`" + DistributionListMessageModel.COLUMN_EDITED_AT + "` DATETIME ," +
                "`" + DistributionListMessageModel.COLUMN_DELETED_AT + "` DATETIME );",

            //indices
            "CREATE INDEX `distributionListDistributionListIdIdx` ON `" + DistributionListMessageModel.TABLE + "` ( `" + DistributionListMessageModel.COLUMN_DISTRIBUTION_LIST_ID + "` )",
            "CREATE INDEX `distribution_list_message_outbox_idx` ON `" + DistributionListMessageModel.TABLE + "` ( `" + DistributionListMessageModel.COLUMN_OUTBOX + "` )",
            "CREATE INDEX `distributionListMessageIdIdx` ON `" + DistributionListMessageModel.TABLE + "` ( `" + DistributionListMessageModel.COLUMN_API_MESSAGE_ID + "` )",
            "CREATE INDEX `distributionListMessageUidIdx` ON `" + DistributionListMessageModel.TABLE + "` ( `" + DistributionListMessageModel.COLUMN_UID + "` )",
            "CREATE INDEX `distribution_list_message_identity_idx` ON `" + DistributionListMessageModel.TABLE + "` ( `" + DistributionListMessageModel.COLUMN_IDENTITY + "` )",
            "CREATE INDEX `distributionListCorrelationIdIdx` ON `" + DistributionListMessageModel.TABLE + "` ( `" + DistributionListMessageModel.COLUMN_CORRELATION_ID + "` )",
            "CREATE INDEX `distribution_list_message_state_idx` ON `" + DistributionListMessageModel.TABLE
                + "`(`" + AbstractMessageModel.COLUMN_TYPE
                + "`, `" + AbstractMessageModel.COLUMN_STATE
                + "`, `" + AbstractMessageModel.COLUMN_OUTBOX
                + "`)",
        };
    }
}
