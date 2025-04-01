/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2024 Threema GmbH
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

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import ch.threema.app.services.GroupService;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.GroupId;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.DatabaseUtil;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.models.GroupModel;

public class GroupModelFactory extends ModelFactory {
    private static final Logger logger = LoggingUtil.getThreemaLogger("GroupModelFactory");

    public GroupModelFactory(DatabaseServiceNew databaseService) {
        super(databaseService, GroupModel.TABLE);
    }

    public List<GroupModel> getAll() {
        return convertList(this.databaseService.getReadableDatabase().query(this.getTableName(),
            null,
            null,
            null,
            null,
            null,
            null));
    }

    public GroupModel getById(int id) {
        return getFirst(
            GroupModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(id)
            });
    }

    private List<GroupModel> convert(
        QueryBuilder queryBuilder,
        String[] args,
        String orderBy) {
        queryBuilder.setTables(this.getTableName());
        return convertList(queryBuilder.query(
            this.databaseService.getReadableDatabase(),
            null,
            null,
            args,
            null,
            null,
            orderBy));
    }

    public List<GroupModel> convertList(Cursor c) {

        List<GroupModel> result = new ArrayList<>();
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

    private GroupModel convert(Cursor cursor) {
        if (cursor != null && cursor.getPosition() >= 0) {
            final GroupModel c = new GroupModel();

            //convert default
            new CursorHelper(cursor, columnIndexCache).current(new CursorHelper.Callback() {
                @Override
                public boolean next(CursorHelper cursorHelper) {
                    c
                        .setId(cursorHelper.getInt(GroupModel.COLUMN_ID))
                        .setApiGroupId(new GroupId(cursorHelper.getString(GroupModel.COLUMN_API_GROUP_ID)))
                        .setName(cursorHelper.getString(GroupModel.COLUMN_NAME))
                        .setCreatorIdentity(cursorHelper.getString(GroupModel.COLUMN_CREATOR_IDENTITY))
                        .setSynchronizedAt(cursorHelper.getDate(GroupModel.COLUMN_SYNCHRONIZED_AT))
                        .setCreatedAt(cursorHelper.getDateByString(GroupModel.COLUMN_CREATED_AT))
                        .setLastUpdate(cursorHelper.getDate(GroupModel.COLUMN_LAST_UPDATE))
                        .setDeleted(cursorHelper.getBoolean(GroupModel.COLUMN_DELETED))
                        .setArchived(cursorHelper.getBoolean(GroupModel.COLUMN_IS_ARCHIVED))
                        .setGroupDesc(cursorHelper.getString(GroupModel.COLUMN_GROUP_DESC))
                        .setGroupDescTimestamp(cursorHelper.getDateByString(GroupModel.COLUMN_GROUP_DESC_CHANGED_TIMESTAMP))
                        .setColorIndex(cursorHelper.getInt(GroupModel.COLUMN_COLOR_INDEX))
                        .setUserState(GroupModel.UserState.valueOf(cursorHelper.getInt(GroupModel.COLUMN_USER_STATE)))
                    ;

                    return false;
                }
            });

            return c;
        }

        return null;
    }

    public boolean createOrUpdate(GroupModel groupModel) {
        boolean insert = true;
        if (groupModel.getId() > 0) {
            Cursor cursor = this.databaseService.getReadableDatabase().query(
                this.getTableName(),
                null,
                GroupModel.COLUMN_ID + "=?",
                new String[]{
                    String.valueOf(groupModel.getId())
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
            return create(groupModel);
        } else {
            return update(groupModel);
        }
    }

    private ContentValues buildContentValues(GroupModel groupModel) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(GroupModel.COLUMN_API_GROUP_ID, groupModel.getApiGroupId().toString());
        contentValues.put(GroupModel.COLUMN_CREATOR_IDENTITY, groupModel.getCreatorIdentity());
        contentValues.put(GroupModel.COLUMN_NAME, groupModel.getName());
        contentValues.put(GroupModel.COLUMN_CREATED_AT, groupModel.getCreatedAt() != null ? CursorHelper.dateAsStringFormat.get().format(groupModel.getCreatedAt()) : null);
        contentValues.put(GroupModel.COLUMN_LAST_UPDATE, groupModel.getLastUpdate() != null ? groupModel.getLastUpdate().getTime() : null);
        contentValues.put(GroupModel.COLUMN_SYNCHRONIZED_AT, groupModel.getSynchronizedAt() != null ? groupModel.getSynchronizedAt().getTime() : null);
        contentValues.put(GroupModel.COLUMN_DELETED, groupModel.isDeleted());
        contentValues.put(GroupModel.COLUMN_IS_ARCHIVED, groupModel.isArchived());
        contentValues.put(GroupModel.COLUMN_GROUP_DESC, groupModel.getGroupDesc());
        contentValues.put(GroupModel.COLUMN_GROUP_DESC_CHANGED_TIMESTAMP, groupModel.getGroupDescTimestamp() != null ? CursorHelper.dateAsStringFormat.get().format(groupModel.getGroupDescTimestamp()) : null);
        contentValues.put(GroupModel.COLUMN_COLOR_INDEX, groupModel.getColorIndex());
        // In case the user state is not set, we fall back to 'member'.
        contentValues.put(GroupModel.COLUMN_USER_STATE, groupModel.getUserState() != null ? groupModel.getUserState().value : GroupModel.UserState.MEMBER.value);

        return contentValues;
    }

    public boolean create(GroupModel groupModel) {
        logger.debug("create group {}", groupModel.getApiGroupId());
        ContentValues contentValues = buildContentValues(groupModel);
        try {
            long newId = this.databaseService.getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
            if (newId > 0) {
                logger.debug("create group success with id {}", newId);
                groupModel.setId((int) newId);
                return true;
            }
        } catch (SQLException e) {
            logger.debug("unable to create group: {}", e.getMessage());
        }
        return false;
    }

    public boolean update(GroupModel groupModel) {
        ContentValues contentValues = buildContentValues(groupModel);
        int rowAffected = this.databaseService.getWritableDatabase().update(this.getTableName(),
            contentValues,
            GroupModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(groupModel.getId())
            });


        logger.debug("done, affected rows = " + rowAffected);
        return true;
    }


    public int delete(GroupModel groupModel) {
        return this.databaseService.getWritableDatabase().delete(this.getTableName(),
            GroupModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(groupModel.getId())
            });
    }

    private GroupModel getFirst(String selection, String[] selectionArgs) {
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

    public List<GroupModel> filter(GroupService.GroupFilter filter) {
        QueryBuilder queryBuilder = new QueryBuilder();

        //sort by id!
        String orderBy = null;
        List<String> placeholders = new ArrayList<>();

        if (filter != null) {
            if (!filter.includeDeletedGroups()) {
                queryBuilder.appendWhere(GroupModel.COLUMN_DELETED + "=0");
            }

            String sortDirection = filter.sortAscending() ? "ASC" : "DESC";
            if (filter.sortByDate()) {
                orderBy = GroupModel.COLUMN_CREATED_AT + " " + sortDirection;
            } else if (filter.sortByName()) {
                orderBy = String.format("%s COLLATE NOCASE %s ",
                    GroupModel.COLUMN_NAME,
                    sortDirection);
            }
        }

        return convert(
            queryBuilder,
            placeholders.toArray(new String[placeholders.size()]),
            orderBy);

    }

    public GroupModel getByApiGroupIdAndCreator(String apiGroupId, String groupCreator) {
        return getFirst(
            GroupModel.COLUMN_API_GROUP_ID + "=? "
                + "AND " + GroupModel.COLUMN_CREATOR_IDENTITY + "=?",
            new String[]{
                apiGroupId,
                groupCreator
            });
    }

    public List<GroupModel> getInId(List<Integer> groupIds) {
        return convertList(this.databaseService.getReadableDatabase().query(this.getTableName(),
            null,
            GroupModel.COLUMN_ID + " IN (" + DatabaseUtil.makePlaceholders(groupIds.size()) + ")",
            DatabaseUtil.convertArguments(groupIds),
            null,
            null,
            null));
    }

    @Override
    public String[] getStatements() {
        return new String[]{
            "CREATE TABLE `" + GroupModel.TABLE + "` (" +
                "`" + GroupModel.COLUMN_ID + "` INTEGER " + "PRIMARY KEY AUTOINCREMENT , " +
                "`" + GroupModel.COLUMN_API_GROUP_ID + "` VARCHAR , " +
                "`" + GroupModel.COLUMN_NAME + "` VARCHAR , " +
                "`" + GroupModel.COLUMN_CREATOR_IDENTITY + "` VARCHAR , " +
                "`" + GroupModel.COLUMN_CREATED_AT + "` VARCHAR , " +
                "`" + GroupModel.COLUMN_LAST_UPDATE + "` INTEGER, " +
                "`" + GroupModel.COLUMN_SYNCHRONIZED_AT + "` BIGINT , " +
                "`" + GroupModel.COLUMN_DELETED + "` SMALLINT , " +
                "`" + GroupModel.COLUMN_IS_ARCHIVED + "` TINYINT DEFAULT 0, " +
                "`" + GroupModel.COLUMN_GROUP_DESC + "` VARCHAR DEFAULT NULL, " +
                "`" + GroupModel.COLUMN_GROUP_DESC_CHANGED_TIMESTAMP + "` VARCHAR DEFAULT NULL, " +
                "`" + GroupModel.COLUMN_COLOR_INDEX + "` INTEGER DEFAULT 0 NOT NULL, " +
                "`" + GroupModel.COLUMN_USER_STATE + "` INTEGER DEFAULT 0 NOT NULL " +
                ");",
            "CREATE UNIQUE INDEX `apiGroupIdAndCreator` ON `" + GroupModel.TABLE + "` ( " +
                "`" + GroupModel.COLUMN_API_GROUP_ID + "`, `" + GroupModel.COLUMN_CREATOR_IDENTITY + "` " +
                ");"
        };
    }
}
