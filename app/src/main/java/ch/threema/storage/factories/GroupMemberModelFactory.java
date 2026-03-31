package ch.threema.storage.factories;

import android.content.ContentValues;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import ch.threema.data.datatypes.IdColor;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseCreationProvider;
import ch.threema.storage.DatabaseProvider;
import ch.threema.storage.DatabaseUtil;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.group.GroupMemberModel;

public class GroupMemberModelFactory extends ModelFactory {

    public GroupMemberModelFactory(DatabaseProvider databaseProvider) {
        super(databaseProvider, GroupMemberModel.TABLE);
    }

    public GroupMemberModel getByGroupIdAndIdentity(int groupId, String identity) {
        return getFirst(
            GroupMemberModel.COLUMN_GROUP_ID + "=? "
                + " AND " + GroupMemberModel.COLUMN_IDENTITY + "=?",
            new String[]{
                String.valueOf(groupId),
                identity
            });
    }

    public List<GroupMemberModel> getByGroupId(int groupId) {
        return convertList(getReadableDatabase().query(this.getTableName(),
            null,
            GroupMemberModel.COLUMN_GROUP_ID + "=?",
            new String[]{
                String.valueOf(groupId)
            },
            null,
            null,
            null));
    }

    private GroupMemberModel convert(Cursor cursor) {
        if (cursor != null && cursor.getPosition() >= 0) {
            final GroupMemberModel groupMemberModel = new GroupMemberModel();
            new CursorHelper(cursor, getColumnIndexCache()).current(
                (CursorHelper.Callback) cursorHelper -> {
                    groupMemberModel
                        .setId(cursorHelper.getInt(GroupMemberModel.COLUMN_ID))
                        .setGroupId(cursorHelper.getInt(GroupMemberModel.COLUMN_GROUP_ID))
                        .setIdentity(cursorHelper.getString(GroupMemberModel.COLUMN_IDENTITY));
                    return false;
                }
            );
            return groupMemberModel;
        }
        return null;
    }

    public List<GroupMemberModel> convertList(Cursor cursor) {
        List<GroupMemberModel> result = new ArrayList<>();
        if (cursor != null) {
            try (cursor) {
                while (cursor.moveToNext()) {
                    result.add(convert(cursor));
                }
            }
        }
        return result;
    }

    public boolean createOrUpdate(GroupMemberModel groupMemberModel) {
        boolean insert = true;
        if (groupMemberModel.getId() > 0) {
            Cursor cursor = getReadableDatabase().query(
                this.getTableName(),
                null,
                GroupMemberModel.COLUMN_ID + "=?",
                new String[]{
                    String.valueOf(groupMemberModel.getId())
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
            return create(groupMemberModel);
        } else {
            return update(groupMemberModel);
        }
    }

    private ContentValues buildContentValues(GroupMemberModel groupMemberModel) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(GroupMemberModel.COLUMN_GROUP_ID, groupMemberModel.getGroupId());
        contentValues.put(GroupMemberModel.COLUMN_IDENTITY, groupMemberModel.getIdentity());
        return contentValues;
    }

    public boolean create(GroupMemberModel groupMemberModel) {
        ContentValues contentValues = buildContentValues(groupMemberModel);
        long newId = getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
        if (newId > 0) {
            groupMemberModel.setId((int) newId);
            return true;
        }
        return false;
    }

    public boolean update(GroupMemberModel groupMemberModel) {
        ContentValues contentValues = buildContentValues(groupMemberModel);
        getWritableDatabase().update(this.getTableName(),
            contentValues,
            GroupMemberModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(groupMemberModel.getId())
            });
        return true;
    }

    private GroupMemberModel getFirst(String selection, String[] selectionArgs) {
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

    public Map<String, IdColor> getIDColors(long groupId) {
        Cursor c = getReadableDatabase().rawQuery("SELECT c." + ContactModel.COLUMN_IDENTITY + ", c." + ContactModel.COLUMN_ID_COLOR_INDEX +
            " FROM " + GroupMemberModel.TABLE + " gm " +
            "INNER JOIN " + ContactModel.TABLE + " c " +
            "ON c." + ContactModel.COLUMN_IDENTITY + " = gm." + GroupMemberModel.COLUMN_IDENTITY + " " +
            "WHERE gm." + GroupMemberModel.COLUMN_GROUP_ID + " = ? AND LENGTH(c." + ContactModel.COLUMN_IDENTITY + ") > 0 AND LENGTH(c." + ContactModel.COLUMN_ID_COLOR_INDEX + ") > 0", new String[]{
            String.valueOf(groupId)
        });
        Map<String, IdColor> colors = new HashMap<>();
        if (c != null) {
            try {
                while (c.moveToNext()) {
                    colors.put(c.getString(0), new IdColor(c.getInt(1)));
                }
            } finally {
                c.close();
            }
        }

        return colors;
    }

    public List<Integer> getGroupIdsByIdentity(String identity) {
        Cursor c = getReadableDatabase().query(
            this.getTableName(),
            new String[]{
                GroupMemberModel.COLUMN_GROUP_ID
            },
            GroupMemberModel.COLUMN_IDENTITY + "=?",
            new String[]{
                identity
            },
            null, null, null);
        List<Integer> result = new ArrayList<>();
        if (c != null) {
            try {
                while (c.moveToNext()) {
                    result.add(c.getInt(0));
                }
            } finally {
                c.close();
            }
        }

        return result;
    }

    public int deleteByGroupId(long groupId) {
        return getWritableDatabase().delete(this.getTableName(),
            GroupMemberModel.COLUMN_GROUP_ID + "=?",
            new String[]{
                String.valueOf(groupId)
            });
    }

    public int delete(List<GroupMemberModel> modelsToRemove) {
        String[] args = new String[modelsToRemove.size()];
        for (int n = 0; n < modelsToRemove.size(); n++) {
            args[n] = String.valueOf(modelsToRemove.get(n).getId());
        }
        return getWritableDatabase().delete(this.getTableName(),
            GroupMemberModel.COLUMN_ID + " IN (" + DatabaseUtil.makePlaceholders(args.length) + ")",
            args);
    }

    public static class Creator implements DatabaseCreationProvider {
        @Override
        @NonNull
        public String [] getCreationStatements() {
            return new String[]{
                "CREATE TABLE `group_member` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `identity` VARCHAR , `groupId` INTEGER)"
            };
        }
    }
}
