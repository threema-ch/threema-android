/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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
import android.database.SQLException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import ch.threema.base.Result;
import ch.threema.domain.models.GroupId;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.group.OutgoingGroupJoinRequestModel;
import java8.util.Optional;

public class OutgoingGroupJoinRequestModelFactory extends ModelFactory {

    public OutgoingGroupJoinRequestModelFactory(DatabaseServiceNew databaseServiceNew) {
        super(databaseServiceNew, OutgoingGroupJoinRequestModel.TABLE);
    }

    @Override
    public String[] getStatements() {
        final String createTableStatement = "CREATE TABLE IF NOT EXISTS `" + OutgoingGroupJoinRequestModel.TABLE + "` ( " +
            "`" + OutgoingGroupJoinRequestModel.COLUMN_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "`" + OutgoingGroupJoinRequestModel.COLUMN_INVITE_TOKEN + "` VARCHAR, " +
            "`" + OutgoingGroupJoinRequestModel.COLUMN_GROUP_NAME + "` TEXT, " +
            "`" + OutgoingGroupJoinRequestModel.COLUMN_MESSAGE + "` TEXT, " +
            "`" + OutgoingGroupJoinRequestModel.COLUMN_INVITE_ADMIN_IDENTITY + "` VARCHAR, " +
            "`" + OutgoingGroupJoinRequestModel.COLUMN_REQUEST_TIME + "` DATETIME, " +
            "`" + OutgoingGroupJoinRequestModel.COLUMN_STATUS + "` VARCHAR, " +
            "`" + OutgoingGroupJoinRequestModel.COLUMN_GROUP_API_ID + "` INTEGER NULL " +
            ")";

        return new String[]{
            createTableStatement,
        };
    }

    public Result<OutgoingGroupJoinRequestModel, Exception> insert(final OutgoingGroupJoinRequestModel model) {
        final ContentValues contentValues = groupJoinRequestModelToContentValues(model);
        final long newId;
        try {
            newId = this.databaseService.getWritableDatabase().insertOrThrow(
                this.getTableName(),
                null,
                contentValues
            );
            model.setId((int) newId);
        } catch (SQLException e) {
            return Result.failure(e);
        }

        if (newId <= 0) {
            return Result.failure(new Exception("Database returned invalid id for new record: " + newId));
        }
        return Result.success(model);
    }

    public void updateStatusAndSentDate(@NonNull OutgoingGroupJoinRequestModel model, @NonNull OutgoingGroupJoinRequestModel.Status status) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(OutgoingGroupJoinRequestModel.COLUMN_STATUS, status.name());
        contentValues.put(OutgoingGroupJoinRequestModel.COLUMN_REQUEST_TIME, new Date().getTime());
        this.update(model.getId(), contentValues);
    }

    public boolean update(OutgoingGroupJoinRequestModel outgoingGroupJoinRequestModel) throws SQLException {
        ContentValues contentValues = buildContentValues(outgoingGroupJoinRequestModel);
        int rowsAffected = this.databaseService.getWritableDatabase().update(this.getTableName(),
            contentValues,
            OutgoingGroupJoinRequestModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(outgoingGroupJoinRequestModel.getId())
            });

        if (rowsAffected != 1) {
            throw new SQLException(NO_RECORD_MSG + outgoingGroupJoinRequestModel.getId());
        }
        return true;
    }

    /**
     * Update a (single) record. Throws a {@link SQLException} if no record matched the query.
     *
     * @param id     of the record that should be changed
     * @param values that should be changed
     */
    private void update(int id, final @NonNull ContentValues values) {
        int rowsAffected = this.databaseService.getWritableDatabase().update(
            this.getTableName(),
            values,
            OutgoingGroupJoinRequestModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(id)
            }
        );

        if (rowsAffected != 1) {
            throw new SQLException(NO_RECORD_MSG + id);
        }
    }

    public void delete(OutgoingGroupJoinRequestModel model) throws SQLException {
        int rowsAffected = this.databaseService.getWritableDatabase().delete(this.getTableName(),
            OutgoingGroupJoinRequestModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(model.getId())
            });

        if (rowsAffected != 1) {
            throw new SQLException(NO_RECORD_MSG + model.getId());
        }
    }

    public @NonNull List<OutgoingGroupJoinRequestModel> getAll() {
        return this.getCursorResultModelList(
            null,
            null
        );
    }

    public @NonNull Optional<OutgoingGroupJoinRequestModel> getByInviteToken(@NonNull String inviteToken) {
        final String selection = OutgoingGroupJoinRequestModel.COLUMN_INVITE_TOKEN + " =?";
        final String[] selectionArgs = new String[]{inviteToken};
        return this.getFirstCursorResultModel(selection, selectionArgs);
    }

    private @NonNull Optional<OutgoingGroupJoinRequestModel> getFirstCursorResultModel(
        @NonNull String selection,
        @NonNull String[] selectionArgs
    ) {
        final Cursor cursor = this.databaseService.getReadableDatabase().query(
            this.getTableName(),
            null,
            selection,
            selectionArgs,
            null,
            null,
            null
        );

        try (final CursorHelper cursorHelper = new CursorHelper(cursor, this.getColumnIndexCache())) {
            final Iterator<OutgoingGroupJoinRequestModel> modelIterator =
                cursorHelper.modelIterator(OutgoingGroupJoinRequestModelFactory::cursorHelperToGroupJoinRequestModel);

            if (modelIterator.hasNext()) {
                return Optional.of(modelIterator.next());
            }
        }

        return Optional.empty();
    }

    private @NonNull List<OutgoingGroupJoinRequestModel> getCursorResultModelList(
        @Nullable String selection,
        @Nullable String[] selectionArgs
    ) {
        final Cursor cursor = this.databaseService.getReadableDatabase().query(this.getTableName(),
            null,
            selection,
            selectionArgs,
            null,
            null,
            null);

        final CursorHelper cursorHelper = new CursorHelper(cursor, this.getColumnIndexCache());
        final Iterator<OutgoingGroupJoinRequestModel> modelIterator =
            cursorHelper.modelIterator(OutgoingGroupJoinRequestModelFactory::cursorHelperToGroupJoinRequestModel);
        final List<OutgoingGroupJoinRequestModel> groupInvites = new ArrayList<>(cursor.getCount());

        while (modelIterator.hasNext()) {
            groupInvites.add(modelIterator.next());
        }
        return groupInvites;
    }

    private static @NonNull ContentValues groupJoinRequestModelToContentValues(@NonNull OutgoingGroupJoinRequestModel groupJoinRequestModel) {
        final ContentValues contentValues = new ContentValues();
        if (groupJoinRequestModel.getId() >= 0) {
            contentValues.put(OutgoingGroupJoinRequestModel.COLUMN_ID, groupJoinRequestModel.getId());
        }
        contentValues.put(OutgoingGroupJoinRequestModel.COLUMN_INVITE_TOKEN, groupJoinRequestModel.getInviteToken());
        contentValues.put(OutgoingGroupJoinRequestModel.COLUMN_GROUP_NAME, groupJoinRequestModel.getGroupName());
        contentValues.put(OutgoingGroupJoinRequestModel.COLUMN_MESSAGE, groupJoinRequestModel.getMessage());
        contentValues.put(OutgoingGroupJoinRequestModel.COLUMN_INVITE_ADMIN_IDENTITY, groupJoinRequestModel.getAdminIdentity());
        contentValues.put(OutgoingGroupJoinRequestModel.COLUMN_REQUEST_TIME, groupJoinRequestModel.getRequestTime().getTime());
        contentValues.put(OutgoingGroupJoinRequestModel.COLUMN_STATUS, groupJoinRequestModel.getStatus().name());

        if (groupJoinRequestModel.getGroupApiId() != null) {
            contentValues.put(
                OutgoingGroupJoinRequestModel.COLUMN_GROUP_API_ID,
                groupJoinRequestModel.getGroupApiId().toLong()
            );
        }

        return contentValues;
    }

    private static @NonNull OutgoingGroupJoinRequestModel cursorHelperToGroupJoinRequestModel(CursorHelper cursorHelper) {
        final @Nullable String groupApiId = cursorHelper.getString(OutgoingGroupJoinRequestModel.COLUMN_GROUP_API_ID);

        return new OutgoingGroupJoinRequestModel(
            Objects.requireNonNull(cursorHelper.getInt(OutgoingGroupJoinRequestModel.COLUMN_ID)),
            Objects.requireNonNull(cursorHelper.getString(OutgoingGroupJoinRequestModel.COLUMN_INVITE_TOKEN)),
            Objects.requireNonNull(cursorHelper.getString(OutgoingGroupJoinRequestModel.COLUMN_GROUP_NAME)),
            Objects.requireNonNull(cursorHelper.getString(OutgoingGroupJoinRequestModel.COLUMN_MESSAGE)),
            Objects.requireNonNull(cursorHelper.getString(OutgoingGroupJoinRequestModel.COLUMN_INVITE_ADMIN_IDENTITY)),
            Objects.requireNonNull(cursorHelper.getDate(OutgoingGroupJoinRequestModel.COLUMN_REQUEST_TIME)),
            OutgoingGroupJoinRequestModel.Status.valueOf(
                cursorHelper.getString(OutgoingGroupJoinRequestModel.COLUMN_STATUS)
            ),
            groupApiId == null ? null : new GroupId(groupApiId)
        );
    }

    private ContentValues buildContentValues(OutgoingGroupJoinRequestModel outgoingGroupJoinRequestModel) {
        ContentValues contentValues = new ContentValues();
        if (outgoingGroupJoinRequestModel.getId() >= 0) {
            contentValues.put(OutgoingGroupJoinRequestModel.COLUMN_ID, outgoingGroupJoinRequestModel.getId());
        }
        contentValues.put(OutgoingGroupJoinRequestModel.COLUMN_INVITE_ADMIN_IDENTITY, outgoingGroupJoinRequestModel.getAdminIdentity());
        contentValues.put(OutgoingGroupJoinRequestModel.COLUMN_GROUP_NAME, outgoingGroupJoinRequestModel.getGroupName());
        contentValues.put(OutgoingGroupJoinRequestModel.COLUMN_REQUEST_TIME, outgoingGroupJoinRequestModel.getRequestTime().getTime());
        contentValues.put(OutgoingGroupJoinRequestModel.COLUMN_STATUS, outgoingGroupJoinRequestModel.getStatus().name());
        contentValues.put(OutgoingGroupJoinRequestModel.COLUMN_INVITE_TOKEN, outgoingGroupJoinRequestModel.getInviteToken());
        contentValues.put(OutgoingGroupJoinRequestModel.COLUMN_MESSAGE, outgoingGroupJoinRequestModel.getMessage()); // default false as this can only be called on available or new group invites

        if (outgoingGroupJoinRequestModel.getGroupApiId() != null) {
            contentValues.put(OutgoingGroupJoinRequestModel.COLUMN_GROUP_API_ID, outgoingGroupJoinRequestModel.getGroupApiId().toString());
        }

        return contentValues;
    }
}
