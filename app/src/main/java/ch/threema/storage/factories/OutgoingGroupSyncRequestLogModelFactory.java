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

import java.util.Date;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.data.models.GroupIdentity;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.OutgoingGroupSyncRequestLogModel;

public class OutgoingGroupSyncRequestLogModelFactory extends ModelFactory {
    public OutgoingGroupSyncRequestLogModelFactory(DatabaseServiceNew databaseService) {
        super(databaseService, OutgoingGroupSyncRequestLogModel.TABLE);
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
        long newId = this.databaseService.getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
        return newId > 0;
    }

    public boolean update(OutgoingGroupSyncRequestLogModel outgoingGroupSyncRequestLogModel) {
        ContentValues contentValues = buildValues(outgoingGroupSyncRequestLogModel);
        this.databaseService.getWritableDatabase().update(this.getTableName(),
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
            ? CursorHelper.dateAsStringFormat.get().format(outgoingGroupSyncRequestLogModel.getLastRequest())
            : null
        );
        return contentValues;
    }

    private ContentValues buildValues(@NonNull GroupIdentity groupIdentity, @Nullable Date lastRequest) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(OutgoingGroupSyncRequestLogModel.COLUMN_API_GROUP_ID, groupIdentity.getGroupIdHexString());
        contentValues.put(OutgoingGroupSyncRequestLogModel.COLUMN_CREATOR_IDENTITY, groupIdentity.getCreatorIdentity());
        contentValues.put(OutgoingGroupSyncRequestLogModel.COLUMN_LAST_REQUEST, lastRequest != null
            ? CursorHelper.dateAsStringFormat.get().format(lastRequest)
            : null
        );
        return contentValues;
    }

    private OutgoingGroupSyncRequestLogModel getFirst(String selection, String[] selectionArgs) {
        Cursor cursor = this.databaseService.getReadableDatabase().query(
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

        CursorHelper cursorHelper = new CursorHelper(cursor, columnIndexCache);

        return new OutgoingGroupSyncRequestLogModel(
            Objects.requireNonNull(cursorHelper.getInt(OutgoingGroupSyncRequestLogModel.COLUMN_ID)),
            Objects.requireNonNull(cursorHelper.getString(OutgoingGroupSyncRequestLogModel.COLUMN_API_GROUP_ID)),
            Objects.requireNonNull(cursorHelper.getString(OutgoingGroupSyncRequestLogModel.COLUMN_CREATOR_IDENTITY)),
            cursorHelper.getDateByString(OutgoingGroupSyncRequestLogModel.COLUMN_LAST_REQUEST)
        );
    }

    @Override
    public String[] getStatements() {
        return new String[]{
            "CREATE TABLE `m_group_request_sync_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `apiGroupId` VARCHAR , `creatorIdentity` VARCHAR , `lastRequest` VARCHAR )",
            "CREATE UNIQUE INDEX `apiGroupIdAndCreatorGroupRequestSyncLogModel` ON `m_group_request_sync_log` ( `apiGroupId`, `creatorIdentity` );"
        };
    }
}
