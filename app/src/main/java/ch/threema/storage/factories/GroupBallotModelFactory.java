package ch.threema.storage.factories;

import android.content.ContentValues;

import android.database.Cursor;

import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseService;
import ch.threema.storage.models.ballot.GroupBallotModel;

public class GroupBallotModelFactory extends ModelFactory {

    public GroupBallotModelFactory(DatabaseService databaseService) {
        super(databaseService, GroupBallotModel.TABLE);
    }

    public GroupBallotModel getByGroupIdAndBallotId(int groupId, int ballotId) {
        return getFirst(
            GroupBallotModel.COLUMN_GROUP_ID + "=? "
                + "AND " + GroupBallotModel.COLUMN_BALLOT_ID + "=?",
            new String[]{
                String.valueOf(groupId),
                String.valueOf(ballotId)
            });
    }


    public GroupBallotModel getByBallotId(int ballotModelId) {
        return getFirst(
            GroupBallotModel.COLUMN_BALLOT_ID + "=?",
            new String[]{
                String.valueOf(ballotModelId)
            });
    }

    private GroupBallotModel convert(Cursor cursor) {
        if (cursor != null && cursor.getPosition() >= 0) {
            final GroupBallotModel c = new GroupBallotModel();

            //convert default
            new CursorHelper(cursor, getColumnIndexCache()).current(new CursorHelper.Callback() {
                @Override
                public boolean next(CursorHelper cursorHelper) {
                    c
                        .setId(cursorHelper.getInt(GroupBallotModel.COLUMN_ID))
                        .setBallotId(cursorHelper.getInt(GroupBallotModel.COLUMN_BALLOT_ID))
                        .setGroupId(cursorHelper.getInt(GroupBallotModel.COLUMN_GROUP_ID));
                    return false;
                }
            });

            return c;
        }

        return null;
    }

    private ContentValues buildContentValues(GroupBallotModel groupBallotModel) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(GroupBallotModel.COLUMN_GROUP_ID, groupBallotModel.getGroupId());
        contentValues.put(GroupBallotModel.COLUMN_BALLOT_ID, groupBallotModel.getBallotId());

        return contentValues;
    }

    public boolean create(GroupBallotModel groupBallotModel) {
        ContentValues contentValues = buildContentValues(groupBallotModel);
        long newId = getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
        if (newId > 0) {
            groupBallotModel.setId((int) newId);
            return true;
        }
        return false;
    }

    public int deleteByBallotId(int ballotId) {
        return getWritableDatabase().delete(this.getTableName(),
            GroupBallotModel.COLUMN_BALLOT_ID + "=?",
            new String[]{
                String.valueOf(ballotId)
            });
    }

    private GroupBallotModel getFirst(String selection, String[] selectionArgs) {
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
            "CREATE TABLE `group_ballot` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `groupId` INTEGER NOT NULL , `ballotId` INTEGER NOT NULL )",
            "CREATE UNIQUE INDEX `groupBallotId` ON `group_ballot` ( `groupId`, `ballotId` )"
        };
    }
}
