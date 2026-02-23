package ch.threema.storage.factories;

import android.content.ContentValues;

import android.database.Cursor;

import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseService;
import ch.threema.storage.models.ballot.IdentityBallotModel;

public class IdentityBallotModelFactory extends ModelFactory {

    public IdentityBallotModelFactory(DatabaseService databaseService) {
        super(databaseService, IdentityBallotModel.TABLE);
    }

    public IdentityBallotModel getByIdentityAndBallotId(String identity, int ballotId) {
        return getFirst(
            IdentityBallotModel.COLUMN_IDENTITY + "=? "
                + "AND " + IdentityBallotModel.COLUMN_BALLOT_ID + "=?",
            new String[]{
                identity,
                String.valueOf(ballotId)
            });
    }

    public IdentityBallotModel getByBallotId(int ballotModelId) {
        return getFirst(
            IdentityBallotModel.COLUMN_BALLOT_ID + "=?",
            new String[]{
                String.valueOf(ballotModelId)
            });
    }

    private IdentityBallotModel convert(Cursor cursor) {
        if (cursor != null && cursor.getPosition() >= 0) {
            final IdentityBallotModel c = new IdentityBallotModel();

            //convert default
            new CursorHelper(cursor, getColumnIndexCache()).current(new CursorHelper.Callback() {
                @Override
                public boolean next(CursorHelper cursorHelper) {
                    c
                        .setId(cursorHelper.getInt(IdentityBallotModel.COLUMN_ID))
                        .setBallotId(cursorHelper.getInt(IdentityBallotModel.COLUMN_BALLOT_ID))
                        .setIdentity(cursorHelper.getString(IdentityBallotModel.COLUMN_IDENTITY));
                    return false;
                }
            });

            return c;
        }

        return null;
    }

    private ContentValues buildContentValues(IdentityBallotModel identityBallotModel) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(IdentityBallotModel.COLUMN_IDENTITY, identityBallotModel.getIdentity());
        contentValues.put(IdentityBallotModel.COLUMN_BALLOT_ID, identityBallotModel.getBallotId());

        return contentValues;
    }

    public boolean create(IdentityBallotModel identityBallotModel) {
        ContentValues contentValues = buildContentValues(identityBallotModel);
        long newId = getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
        if (newId > 0) {
            identityBallotModel.setId((int) newId);
            return true;
        }
        return false;
    }

    public int deleteByBallotId(int ballotId) {
        return getWritableDatabase().delete(this.getTableName(),
            IdentityBallotModel.COLUMN_BALLOT_ID + "=?",
            new String[]{
                String.valueOf(ballotId)
            });
    }

    private IdentityBallotModel getFirst(String selection, String[] selectionArgs) {
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
            "CREATE TABLE `identity_ballot` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `identity` VARCHAR NOT NULL , `ballotId` INTEGER NOT NULL )",
            "CREATE UNIQUE INDEX `identityBallotId` ON `identity_ballot` ( `identity`, `ballotId` )"
        };
    }
}
