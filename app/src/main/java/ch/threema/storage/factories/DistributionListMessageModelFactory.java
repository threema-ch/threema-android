package ch.threema.storage.factories;

import android.content.ContentValues;
import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.services.MessageService;

import static ch.threema.android.CursorExtensionsKt.readCountQueryResult;
import static ch.threema.common.DatabaseHelpers.makePlaceholders;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.data.datatypes.DistributionListMessageIdentifier;
import ch.threema.data.storage.SqliteDatabaseBackend;
import ch.threema.domain.models.MessageId;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseCreationProvider;
import ch.threema.storage.DatabaseProvider;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.MessageType;

public class DistributionListMessageModelFactory extends MessageModelFactory {
    private static final Logger logger = getThreemaLogger("DistributionListMessageModelFactory");

    public DistributionListMessageModelFactory(DatabaseProvider databaseProvider) {
        super(databaseProvider, DistributionListMessageModel.TABLE);
    }

    public List<DistributionListMessageModel> getAll() {
        return convertList(getReadableDatabase().query(this.getTableName(), null, null, null, null, null, null));
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

    @Nullable
    public DistributionListMessageModel getByMessageId(@NonNull MessageId messageId, long distributionListId) {
        String selection = DistributionListMessageModel.COLUMN_API_MESSAGE_ID + " = ? AND " +
            DistributionListMessageModel.COLUMN_DISTRIBUTION_LIST_ID + " = ?";

        return getFirst(
            selection,
            new String[]{messageId.toString(), Long.toString(distributionListId)}
        );
    }

    @NonNull
    private List<DistributionListMessageModel> convertList(Cursor cursor) {
        List<DistributionListMessageModel> result = new ArrayList<>();
        if (cursor != null) {
            try (cursor) {
                while (cursor.moveToNext()) {
                    result.add(convert(cursor));
                }
            }
        }
        return result;
    }

    private DistributionListMessageModel convert(Cursor cursor) {
        if (cursor != null && cursor.getPosition() >= 0) {
            final DistributionListMessageModel distributionListMessageModel = new DistributionListMessageModel();
            //convert default
            super.convert(
                distributionListMessageModel,
                new CursorHelper(cursor, getColumnIndexCache()).current(cursorHelper -> {
                    Long distributionListId = cursorHelper.getLong(DistributionListMessageModel.COLUMN_DISTRIBUTION_LIST_ID);
                    if (distributionListId != null) {
                        distributionListMessageModel.setDistributionListId(distributionListId);
                    } else {
                        logger.warn("Distribution list id is null");
                    }
                    return false;
                })
            );
            return distributionListMessageModel;
        }
        return null;
    }

    public long countByTypes(@NonNull MessageType[] messageTypes) {
        String[] args = new String[messageTypes.length];
        for (int n = 0; n < messageTypes.length; n++) {
            args[n] = String.valueOf(messageTypes[n].serializedValue);
        }
        Cursor cursor = getReadableDatabase().rawQuery(
            "SELECT COUNT(*) FROM " + this.getTableName() + " "
                + "WHERE " + DistributionListMessageModel.COLUMN_TYPE + " IN (" + makePlaceholders(args.length) + ")",
            args
        );
        return readCountQueryResult(cursor);
    }

    /**
     * Inserts or updates the message model into the database. Note that if the message is inserted,
     * the id of the message model will be updated.
     */
    public void createOrUpdate(@NonNull DistributionListMessageModel distributionListMessageModel) {
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
                try (cursor) {
                    insert = !cursor.moveToNext();
                }
            }
        }

        if (insert) {
            create(distributionListMessageModel);
        } else {
            update(distributionListMessageModel);
        }
    }

    /**
     * Inserts the message model into the database. Note that the id of the message model will be updated.
     */
    private void create(DistributionListMessageModel distributionListMessageModel) {
        ContentValues contentValues = this.buildContentValues(distributionListMessageModel);
        contentValues.put(DistributionListMessageModel.COLUMN_DISTRIBUTION_LIST_ID, distributionListMessageModel.getDistributionListId());
        long newId = getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
        distributionListMessageModel.setId((int) newId);
    }

    /**
     * Inserts the message models into the database. Note that the ids of the message models will be updated.
     */
    public void create(@NonNull List<DistributionListMessageModel> distributionListMessageModels) {
        if (distributionListMessageModels.isEmpty()) {
            return;
        }
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            String tableName = getTableName();
            for (DistributionListMessageModel distributionListMessageModel : distributionListMessageModels) {
                ContentValues contentValues = buildContentValues(distributionListMessageModel);
                contentValues.put(DistributionListMessageModel.COLUMN_DISTRIBUTION_LIST_ID, distributionListMessageModel.getDistributionListId());
                long id = database.insertOrThrow(
                    tableName,
                    null,
                    contentValues
                );
                distributionListMessageModel.setId((int) id);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    private void update(DistributionListMessageModel distributionListMessageModel) {
        ContentValues contentValues = this.buildContentValues(distributionListMessageModel);
        getWritableDatabase().update(this.getTableName(),
            contentValues,
            DistributionListMessageModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(distributionListMessageModel.getId())
            });
    }

    public long countMessages(long distributionListId) {
        return readCountQueryResult(getReadableDatabase().rawQuery(
            "SELECT COUNT(*) FROM " + this.getTableName()
                + " WHERE " + DistributionListMessageModel.COLUMN_DISTRIBUTION_LIST_ID + "=?",
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

    /**
     * Read message models from the database into the given list.
     *
     * @param messageModels the list where the message models are appended to
     * @param count         the number of message models that are read
     * @param offset        the offset where the message models are started to be read
     */
    public void loadMessageModelsIntoList(List<DistributionListMessageModel> messageModels, int count, int offset) {
        String query = "SELECT * FROM `" + getTableName() + "` LIMIT ? OFFSET ?";
        String[] queryArgs = new String[]{String.valueOf(count), String.valueOf(offset)};
        try (Cursor cursor = getReadableDatabase().query(query, queryArgs)) {
            while (cursor.moveToNext()) {
                messageModels.add(convert(cursor));
            }
        }
    }

    public int delete(@NonNull DistributionListMessageModel distributionListMessageModel) {
        return getWritableDatabase().delete(this.getTableName(),
            DistributionListMessageModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(distributionListMessageModel.getId())
            });
    }

    private @Nullable DistributionListMessageModel getFirst(String selection, String[] selectionArgs) {
        Cursor cursor = getReadableDatabase().query(this.getTableName(), null, selection, selectionArgs, null, null, null);
        if (cursor != null) {
            try (cursor) {
                if (cursor.moveToFirst()) {
                    return convert(cursor);
                }
            }
        }
        return null;
    }

    /**
     * Returns {@code true} if any of the given messages exist.
     * <p>
     * Messages are checked in batches of at most {@link SqliteDatabaseBackend#MAX_VARIABLE_NUMBER} / 2 to stay within SQLite's host-parameter limit.
     * <p>
     * Query:
     * <pre>{@code
     * SELECT EXISTS (
     *   SELECT 1 FROM distribution_list_message
     *    WHERE (apiMessageId, distributionListId) IN ((?, ?), (?, ?), ...)
     *    LIMIT 1
     * )
     * }</pre>
     */
    public boolean anyExists(@NonNull Set<DistributionListMessageIdentifier> distributionListMessageIdentifiers) {
        if (distributionListMessageIdentifiers.isEmpty()) {
            return false;
        }
        // Copy to a java.util.List to have the subList function available for chunking
        final List<DistributionListMessageIdentifier> messageIdentifiersList = new ArrayList<>(distributionListMessageIdentifiers);
        final int chunkSize = SqliteDatabaseBackend.MAX_VARIABLE_NUMBER / 2;
        for (int start = 0; start < messageIdentifiersList.size(); start += chunkSize) {
            final List<DistributionListMessageIdentifier> messageIdentifiersChunk = messageIdentifiersList.subList(
                start,
                Math.min(start + chunkSize, messageIdentifiersList.size())
            );
            final String placeholders = messageIdentifiersChunk.stream()
                .map(identifier -> "(?, ?)")
                .collect(Collectors.joining(", "));
            final String sql = "SELECT EXISTS(SELECT 1 FROM " + getTableName() + " WHERE ("
                + DistributionListMessageModel.COLUMN_API_MESSAGE_ID + ", " + DistributionListMessageModel.COLUMN_DISTRIBUTION_LIST_ID + ") IN ("
                + placeholders + ") LIMIT 1)";
            final List<Object> bindArgs = new ArrayList<>(messageIdentifiersChunk.size() * 2);
            for (DistributionListMessageIdentifier messageIdentifier : messageIdentifiersChunk) {
                bindArgs.add(messageIdentifier.getMessageId().toString());
                bindArgs.add(messageIdentifier.getDistributionListId());
            }
            final Cursor cursor = getReadableDatabase().query(sql, bindArgs.toArray());
            try (cursor) {
                final boolean exists = cursor.moveToFirst() && cursor.getInt(0) == 1;
                if (exists) {
                    return true;
                }
            }
        }
        return false;
    }

    public static class Creator implements DatabaseCreationProvider {
        @Override
        @NonNull
        public String[] getCreationStatements() {
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

                // indices
                "CREATE INDEX `distributionListDistributionListIdIdx` ON `" + DistributionListMessageModel.TABLE + "` ( `" + DistributionListMessageModel.COLUMN_DISTRIBUTION_LIST_ID + "` )",
                "CREATE INDEX `distribution_list_message_outbox_idx` ON `" + DistributionListMessageModel.TABLE + "` ( `" + DistributionListMessageModel.COLUMN_OUTBOX + "` )",
                "CREATE INDEX `distributionListMessageIdIdx` ON `" + DistributionListMessageModel.TABLE + "` ( `" + DistributionListMessageModel.COLUMN_API_MESSAGE_ID + "` )",
                "CREATE INDEX `distributionListMessageUidIdx` ON `" + DistributionListMessageModel.TABLE + "` ( `" + DistributionListMessageModel.COLUMN_UID + "` )",
                "CREATE INDEX `distribution_list_message_identity_idx` ON `" + DistributionListMessageModel.TABLE + "` ( `" + DistributionListMessageModel.COLUMN_IDENTITY + "` )",
                "CREATE INDEX `distributionListCorrelationIdIdx` ON `" + DistributionListMessageModel.TABLE + "` ( `" + DistributionListMessageModel.COLUMN_CORRELATION_ID + "` )",
                "CREATE INDEX `distribution_list_message_state_idx` ON `" + DistributionListMessageModel.TABLE
                    + "`(`" + MessageModel.COLUMN_TYPE
                    + "`, `" + MessageModel.COLUMN_STATE
                    + "`, `" + MessageModel.COLUMN_OUTBOX
                    + "`)",
            };
        }
    }
}
