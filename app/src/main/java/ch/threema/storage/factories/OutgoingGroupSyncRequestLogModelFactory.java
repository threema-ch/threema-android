package ch.threema.storage.factories;

import android.content.ContentValues;

import android.database.Cursor;

import java.util.Date;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.data.models.GroupIdentity;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseCreationProvider;
import ch.threema.storage.DatabaseProvider;
import ch.threema.storage.models.OutgoingGroupSyncRequestLogModel;

public class OutgoingGroupSyncRequestLogModelFactory extends ModelFactory {
    public OutgoingGroupSyncRequestLogModelFactory(DatabaseProvider databaseProvider) {
        super(databaseProvider, OutgoingGroupSyncRequestLogModel.TABLE);
    }

    @Nullable
    public OutgoingGroupSyncRequestLogModel get(@NonNull GroupIdentity groupIdentity) {
        return get(groupIdentity.getGroupIdHexString(), groupIdentity.getCreatorIdentity());
    }

    @Nullable
    public OutgoingGroupSyncRequestLogModel get(@Nullable String apiGroupId, @Nullable String groupCreator) {
        return getFirst(
            OutgoingGroupSyncRequestLogModel.COLUMN_API_GROUP_ID + "=?"
                + " AND " + OutgoingGroupSyncRequestLogModel.COLUMN_CREATOR_IDENTITY + "=?",
            new String[]{
                apiGroupId,
                groupCreator
            });
    }

    public boolean createOrUpdate(@NonNull GroupIdentity groupIdentity, @Nullable Date lastRequest) {
        OutgoingGroupSyncRequestLogModel existingModel = get(groupIdentity);

        if (existingModel == null) {
            return create(groupIdentity, lastRequest);
        } else {
            return update(new OutgoingGroupSyncRequestLogModel(
                existingModel.getId(),
                existingModel.getApiGroupId(),
                existingModel.getCreatorIdentity(),
                lastRequest
            ));
        }
    }

    public boolean create(@NonNull GroupIdentity groupIdentity, @Nullable Date lastRequest) {
        ContentValues contentValues = buildValues(groupIdentity, lastRequest);
        long newId = getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
        return newId > 0;
    }

    public boolean update(OutgoingGroupSyncRequestLogModel outgoingGroupSyncRequestLogModel) {
        ContentValues contentValues = buildValues(outgoingGroupSyncRequestLogModel);
        getWritableDatabase().update(this.getTableName(),
            contentValues,
            OutgoingGroupSyncRequestLogModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(outgoingGroupSyncRequestLogModel.getId())
            });
        return true;
    }

    private ContentValues buildValues(OutgoingGroupSyncRequestLogModel outgoingGroupSyncRequestLogModel) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(OutgoingGroupSyncRequestLogModel.COLUMN_API_GROUP_ID, outgoingGroupSyncRequestLogModel.getApiGroupId());
        contentValues.put(OutgoingGroupSyncRequestLogModel.COLUMN_CREATOR_IDENTITY, outgoingGroupSyncRequestLogModel.getCreatorIdentity());
        contentValues.put(OutgoingGroupSyncRequestLogModel.COLUMN_LAST_REQUEST, outgoingGroupSyncRequestLogModel.getLastRequest() != null
            ? outgoingGroupSyncRequestLogModel.getLastRequest().getTime()
            : null
        );
        return contentValues;
    }

    private ContentValues buildValues(@NonNull GroupIdentity groupIdentity, @Nullable Date lastRequest) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(OutgoingGroupSyncRequestLogModel.COLUMN_API_GROUP_ID, groupIdentity.getGroupIdHexString());
        contentValues.put(OutgoingGroupSyncRequestLogModel.COLUMN_CREATOR_IDENTITY, groupIdentity.getCreatorIdentity());
        contentValues.put(OutgoingGroupSyncRequestLogModel.COLUMN_LAST_REQUEST, lastRequest != null
            ? lastRequest.getTime()
            : null
        );
        return contentValues;
    }

    private OutgoingGroupSyncRequestLogModel getFirst(String selection, String[] selectionArgs) {
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
            try (cursor) {
                if (cursor.moveToFirst()) {
                    return convert(cursor);
                }
            }
        }

        return null;
    }

    private OutgoingGroupSyncRequestLogModel convert(Cursor cursor) {
        if (cursor == null || cursor.getPosition() < 0) {
            return null;
        }

        CursorHelper cursorHelper = new CursorHelper(cursor, getColumnIndexCache());

        return new OutgoingGroupSyncRequestLogModel(
            Objects.requireNonNull(cursorHelper.getInt(OutgoingGroupSyncRequestLogModel.COLUMN_ID)),
            Objects.requireNonNull(cursorHelper.getString(OutgoingGroupSyncRequestLogModel.COLUMN_API_GROUP_ID)),
            Objects.requireNonNull(cursorHelper.getString(OutgoingGroupSyncRequestLogModel.COLUMN_CREATOR_IDENTITY)),
            cursorHelper.getDate(OutgoingGroupSyncRequestLogModel.COLUMN_LAST_REQUEST)
        );
    }

    public static class Creator implements DatabaseCreationProvider {
        @Override
        @NonNull
        public String[] getCreationStatements() {
                return new String[]{
                    "CREATE TABLE `m_group_request_sync_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `apiGroupId` VARCHAR , `creatorIdentity` VARCHAR , `lastRequestAt` BIGINT )",
                    "CREATE UNIQUE INDEX `apiGroupIdAndCreatorGroupRequestSyncLogModel` ON `m_group_request_sync_log` ( `apiGroupId`, `creatorIdentity` );"
                };
            }
        }
}
