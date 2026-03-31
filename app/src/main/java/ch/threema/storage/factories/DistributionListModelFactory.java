package ch.threema.storage.factories;

import android.content.ContentValues;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.services.DistributionListService;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseCreationProvider;
import ch.threema.storage.DatabaseProvider;
import ch.threema.storage.DatabaseUtil;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.models.DistributionListModel;

public class DistributionListModelFactory extends ModelFactory {
    public DistributionListModelFactory(DatabaseProvider databaseProvider) {
        super(databaseProvider, DistributionListModel.TABLE);
    }

    @NonNull
    public List<DistributionListModel> getAll() {
        return convertList(
            getReadableDatabase().query(getTableName(), null, null, null, null, null, null)
        );
    }

    public DistributionListModel getById(long id) {
        return getFirstOrNull(
            DistributionListModel.COLUMN_ID + "=?",
            String.valueOf(id)
        );
    }

    @NonNull
    public List<DistributionListModel> getByIds(@NonNull List<Long> ids) {
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        final @NonNull String placeholders = DatabaseUtil.makePlaceholders(ids.size());
        final @NonNull String[] selectionArgs = DatabaseUtil.convertArguments(ids);
        final @NonNull String selection = DistributionListModel.COLUMN_ID + " IN (" + placeholders + ")";
        return convertList(
            getReadableDatabase().query(getTableName(), null, selection, selectionArgs, null, null, null)
        );
    }

    @NonNull
    private List<DistributionListModel> convert(@NonNull QueryBuilder queryBuilder, @Nullable String orderBy) {
        queryBuilder.setTables(getTableName());
        return convertList(
            queryBuilder.query(getReadableDatabase(), null, null, null, null, null, orderBy)
        );
    }

    @NonNull
    private List<DistributionListModel> convertList(@Nullable Cursor cursor) {
        final @NonNull List<DistributionListModel> results = new ArrayList<>();
        if (cursor == null) {
            return results;
        }
        try (cursor) {
            while (cursor.moveToNext()) {
                final @Nullable DistributionListModel distributionListModel = convert(cursor);
                if (distributionListModel != null) {
                    results.add(distributionListModel);
                }
            }
        }
        return results;
    }

    @Nullable
    private DistributionListModel convert(@Nullable Cursor cursor) {
        if (cursor == null || cursor.getPosition() < 0) {
            return null;
        }
        final @NonNull DistributionListModel distributionListModel = new DistributionListModel();
        //convert default
        new CursorHelper(cursor, getColumnIndexCache()).current(
            (CursorHelper.Callback) cursorHelper -> {
                distributionListModel
                    .setId(cursorHelper.getLong(DistributionListModel.COLUMN_ID))
                    .setName(cursorHelper.getString(DistributionListModel.COLUMN_NAME))
                    .setCreatedAt(cursorHelper.getDate(DistributionListModel.COLUMN_CREATED_AT))
                    .setLastUpdate(cursorHelper.getDate(DistributionListModel.COLUMN_LAST_UPDATE))
                    .setArchived(cursorHelper.getBoolean(DistributionListModel.COLUMN_IS_ARCHIVED))
                    .setAdHocDistributionList(cursorHelper.getBoolean(DistributionListModel.COLUMN_IS_ADHOC_DISTRIBUTION_LIST));

                return false;
            }
        );
        return distributionListModel;
    }

    public boolean createOrUpdate(@NonNull DistributionListModel distributionListModel) {
        boolean insert = true;
        if (distributionListModel.getId() > 0) {
            Cursor cursor = getReadableDatabase().query(
                this.getTableName(),
                null,
                DistributionListModel.COLUMN_ID + "=?",
                new String[]{
                    String.valueOf(distributionListModel.getId())
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
            return create(distributionListModel);
        } else {
            return update(distributionListModel);
        }
    }

    @NonNull
    private ContentValues buildContentValues(@NonNull DistributionListModel distributionListModel) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DistributionListModel.COLUMN_NAME, distributionListModel.getName());
        contentValues.put(DistributionListModel.COLUMN_CREATED_AT, distributionListModel.getCreatedAt() != null ? distributionListModel.getCreatedAt().getTime() : null);
        contentValues.put(DistributionListModel.COLUMN_LAST_UPDATE, distributionListModel.getLastUpdate() != null ? distributionListModel.getLastUpdate().getTime() : null);
        contentValues.put(DistributionListModel.COLUMN_IS_ARCHIVED, distributionListModel.isArchived());
        contentValues.put(DistributionListModel.COLUMN_IS_ADHOC_DISTRIBUTION_LIST, distributionListModel.isAdHocDistributionList());
        return contentValues;
    }

    /**
     * Create a new distribution list model. If the ID of the given model is <= 0 or already used in
     * the database, a random ID is chosen and the model is updated accordingly.
     *
     * @param distributionListModel the distribution list model that is inserted into the database
     * @return true on success, false otherwise
     */
    public boolean create(@NonNull DistributionListModel distributionListModel) {
        ContentValues contentValues = buildContentValues(distributionListModel);

        long distributionListId = distributionListModel.getId();

        if (distributionListId <= 0 || doesIdExist(distributionListId)) {
            distributionListId = getUniqueId();
        }

        contentValues.put(DistributionListModel.COLUMN_ID, distributionListId);

        long newId = getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
        distributionListModel.setId(newId);
        return newId > 0;
    }

    public boolean update(@NonNull DistributionListModel distributionListModel) {
        ContentValues contentValues = buildContentValues(distributionListModel);
        getWritableDatabase().update(
            getTableName(),
            contentValues,
            DistributionListModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(distributionListModel.getId())
            }
        );
        return true;
    }

    public int delete(@NonNull DistributionListModel distributionListModel) {
        return getWritableDatabase().delete(
            getTableName(),
            DistributionListModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(distributionListModel.getId())
            }
        );
    }

    @Nullable
    private DistributionListModel getFirstOrNull(@Nullable String selection, @Nullable String... selectionArgs) {
        final @Nullable Cursor cursor = getReadableDatabase().query(getTableName(), null, selection, selectionArgs, null, null, null);
        if (cursor == null) {
            return null;
        }
        try (cursor) {
            if (cursor.moveToFirst()) {
                return convert(cursor);
            } else {
                return null;
            }
        }
    }

    @NonNull
    public List<DistributionListModel> filter(@Nullable DistributionListService.DistributionListFilter filter) {
        //sort by id!
        @Nullable String orderBy = null;
        // do not show hidden distribution lists by default
        @Nullable String where = DistributionListModel.COLUMN_IS_ADHOC_DISTRIBUTION_LIST + " !=1";

        if (filter != null) {
            if (!filter.sortingByDate()) {
                orderBy = DistributionListModel.COLUMN_CREATED_AT + " " + (filter.sortingAscending() ? "ASC" : "DESC");
            }
            if (filter.showHidden()) {
                where = null;
            }
        }

        final @NonNull QueryBuilder queryBuilder = new QueryBuilder();
        if (where != null) {
            queryBuilder.appendWhere(where);
        }
        return convert(queryBuilder, orderBy);
    }

    private long getUniqueId() {
        int attemptsLeft = 100;
        long distributionListId;
        do {
            distributionListId = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
        } while (doesIdExist(distributionListId) && attemptsLeft-- > 0);
        return distributionListId;
    }

    private boolean doesIdExist(long id) {
        final @Nullable Cursor cursor = getReadableDatabase().query(
            getTableName(),
            null,
            DistributionListModel.COLUMN_ID + "=?",
            new String[]{String.valueOf(id)},
            null,
            null,
            null,
            null
        );
        if (cursor == null) {
            return false;
        }
        try (cursor) {
            return cursor.getCount() > 0;
        }
    }

    public static class Creator implements DatabaseCreationProvider {
        @Override
        @NonNull
        public String[] getCreationStatements() {
            return new String[]{
                "CREATE TABLE `" + DistributionListModel.TABLE + "` (" +
                    "`" + DistributionListModel.COLUMN_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "`" + DistributionListModel.COLUMN_NAME + "` VARCHAR, " +
                    "`" + DistributionListModel.COLUMN_CREATED_AT + "` BIGINT, " +
                    "`" + DistributionListModel.COLUMN_LAST_UPDATE + "` INTEGER, " +
                    "`" + DistributionListModel.COLUMN_IS_ARCHIVED + "` TINYINT DEFAULT 0, " +
                    "`" + DistributionListModel.COLUMN_IS_ADHOC_DISTRIBUTION_LIST + "` TINYINT DEFAULT 0 " +
                    ");"
            };
        }
    }
}
