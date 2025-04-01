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

import java.util.ArrayList;
import java.util.List;

import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.DatabaseUtil;
import ch.threema.storage.models.DistributionListMemberModel;

public class DistributionListMemberModelFactory extends ModelFactory {

    public DistributionListMemberModelFactory(DatabaseServiceNew databaseService) {
        super(databaseService, DistributionListMemberModel.TABLE);
    }

    public DistributionListMemberModel getByDistributionListIdAndIdentity(long distributionListId, String identity) {
        if (identity == null) {
            return null;
        }

        return getFirst(
            DistributionListMemberModel.COLUMN_DISTRIBUTION_LIST_ID + "=? "
                + " AND " + DistributionListMemberModel.COLUMN_IDENTITY + "=?",
            new String[]{
                String.valueOf(distributionListId),
                identity
            });
    }

    public List<DistributionListMemberModel> getByDistributionListId(long distributionListId) {
        return convertList(this.databaseService.getReadableDatabase().query(this.getTableName(),
            null,
            DistributionListMemberModel.COLUMN_DISTRIBUTION_LIST_ID + "=?",
            new String[]{
                String.valueOf(distributionListId)
            },
            null,
            null,
            null));
    }

    private DistributionListMemberModel convert(Cursor cursor) {
        if (cursor != null && cursor.getPosition() >= 0) {
            final DistributionListMemberModel distributionListMemberModel = new DistributionListMemberModel();
            new CursorHelper(cursor, columnIndexCache).current(new CursorHelper.Callback() {
                @Override
                public boolean next(CursorHelper cursorHelper) {
                    distributionListMemberModel
                        .setId(cursorHelper.getInt(DistributionListMemberModel.COLUMN_ID))
                        .setDistributionListId(cursorHelper.getInt(DistributionListMemberModel.COLUMN_DISTRIBUTION_LIST_ID))
                        .setIdentity(cursorHelper.getString(DistributionListMemberModel.COLUMN_IDENTITY))
                        .setActive(cursorHelper.getBoolean(DistributionListMemberModel.COLUMN_IS_ACTIVE));
                    return false;
                }
            });

            return distributionListMemberModel;
        }

        return null;
    }

    private List<DistributionListMemberModel> convertList(Cursor c) {
        List<DistributionListMemberModel> result = new ArrayList<>();
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

    public boolean createOrUpdate(DistributionListMemberModel distributionListMemberModel) {
        boolean insert = true;
        if (distributionListMemberModel.getId() > 0) {
            Cursor cursor = this.databaseService.getReadableDatabase().query(
                this.getTableName(),
                null,
                DistributionListMemberModel.COLUMN_ID + "=?",
                new String[]{
                    String.valueOf(distributionListMemberModel.getId())
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
            return create(distributionListMemberModel);
        } else {
            return update(distributionListMemberModel);
        }
    }

    private ContentValues buildContentValues(DistributionListMemberModel distributionListMemberModel) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DistributionListMemberModel.COLUMN_DISTRIBUTION_LIST_ID, distributionListMemberModel.getDistributionListId());
        contentValues.put(DistributionListMemberModel.COLUMN_IDENTITY, distributionListMemberModel.getIdentity());
        contentValues.put(DistributionListMemberModel.COLUMN_IS_ACTIVE, distributionListMemberModel.isActive());
        return contentValues;
    }

    public boolean create(DistributionListMemberModel distributionListMemberModel) {
        ContentValues contentValues = buildContentValues(distributionListMemberModel);
        long newId = this.databaseService.getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
        if (newId > 0) {
            distributionListMemberModel.setId((int) newId);
            return true;
        }
        return false;
    }

    public boolean update(DistributionListMemberModel distributionListMemberModel) {
        ContentValues contentValues = buildContentValues(distributionListMemberModel);
        this.databaseService.getWritableDatabase().update(this.getTableName(),
            contentValues,
            DistributionListMemberModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(distributionListMemberModel.getId())
            });
        return true;
    }

    private DistributionListMemberModel getFirst(String selection, String[] selectionArgs) {
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

    public int deleteByDistributionListId(long distributionListId) {
        return this.databaseService.getWritableDatabase().delete(this.getTableName(),
            DistributionListMemberModel.COLUMN_DISTRIBUTION_LIST_ID + "=?",
            new String[]{
                String.valueOf(distributionListId)
            });
    }

    public int delete(List<DistributionListMemberModel> modelsToRemove) {
        String[] args = new String[modelsToRemove.size()];
        for (int n = 0; n < modelsToRemove.size(); n++) {
            args[n] = String.valueOf(modelsToRemove.get(n).getId());
        }
        return this.databaseService.getWritableDatabase().delete(this.getTableName(),
            DistributionListMemberModel.COLUMN_ID + " IN (" + DatabaseUtil.makePlaceholders(args.length) + ")",
            args);
    }

    @Override
    public String[] getStatements() {
        return new String[]{
            "CREATE TABLE `" + DistributionListMemberModel.TABLE + "`(" +
                "`" + DistributionListMemberModel.COLUMN_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT , " +
                "`" + DistributionListMemberModel.COLUMN_IDENTITY + "` VARCHAR , " +
                "`" + DistributionListMemberModel.COLUMN_DISTRIBUTION_LIST_ID + "` INTEGER , " +
                "`" + DistributionListMemberModel.COLUMN_IS_ACTIVE + "` SMALLINT NOT NULL" +
                ")",

            "CREATE INDEX `distribution_list_member_dis_idx`" +
                " ON `" + DistributionListMemberModel.TABLE + "`(`" + DistributionListMemberModel.COLUMN_DISTRIBUTION_LIST_ID + "`)"
        };
    }
}
