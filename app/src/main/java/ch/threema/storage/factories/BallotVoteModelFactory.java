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

import net.zetetic.database.DatabaseUtils;

import java.util.ArrayList;
import java.util.List;

import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseService;
import ch.threema.storage.DatabaseUtil;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.models.ballot.BallotVoteModel;

public class BallotVoteModelFactory extends ModelFactory {
    public BallotVoteModelFactory(DatabaseService databaseService) {
        super(databaseService, BallotVoteModel.TABLE);
    }

    public List<BallotVoteModel> getAll() {
        return convertList(getReadableDatabase().query(this.getTableName(),
            null,
            null,
            null,
            null,
            null,
            null));
    }

    public List<BallotVoteModel> getByBallotId(int ballotId) {
        return convertList(getReadableDatabase().query(this.getTableName(),
            null,
            BallotVoteModel.COLUMN_BALLOT_ID + "=?",
            new String[]{
                String.valueOf(ballotId)
            },
            null,
            null,
            null));
    }

    public List<BallotVoteModel> getByBallotIdAndVotingIdentity(Integer ballotId, String fromIdentity) {
        return convertList(getReadableDatabase().query(this.getTableName(),
            null,
            BallotVoteModel.COLUMN_BALLOT_ID + "=? "
                + " AND " + BallotVoteModel.COLUMN_VOTING_IDENTITY + "=?",
            new String[]{
                String.valueOf(ballotId),
                fromIdentity
            },
            null,
            null,
            null));
    }

    public long countByBallotIdAndVotingIdentity(Integer ballotId, String fromIdentity) {
        return DatabaseUtils.longForQuery(getReadableDatabase(),
            "SELECT COUNT(*) FROM " + this.getTableName() + " "
                + "WHERE " + BallotVoteModel.COLUMN_BALLOT_ID + "=?"
                + " AND " + BallotVoteModel.COLUMN_VOTING_IDENTITY + "=?",
            new String[]{
                String.valueOf(ballotId),
                String.valueOf(fromIdentity)
            });
    }

    public List<BallotVoteModel> getByBallotChoiceId(int ballotChoiceId) {
        return convertList(getReadableDatabase().query(
            this.getTableName(),
            null,
            BallotVoteModel.COLUMN_BALLOT_CHOICE_ID + "=?",
            new String[]{
                String.valueOf(ballotChoiceId)
            },
            null, null, null
        ));
    }

    public BallotVoteModel getById(int id) {
        return getFirst(
            BallotVoteModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(id)
            });
    }

    public List<BallotVoteModel> convert(QueryBuilder queryBuilder,
                                         String[] args,
                                         String orderBy) {
        queryBuilder.setTables(this.getTableName());
        return convertList(queryBuilder.query(
            getReadableDatabase(),
            null,
            null,
            args,
            null,
            null,
            orderBy));
    }

    private List<BallotVoteModel> convertList(Cursor c) {

        List<BallotVoteModel> result = new ArrayList<>();
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

    private BallotVoteModel convert(Cursor cursor) {
        if (cursor != null && cursor.getPosition() >= 0) {
            final BallotVoteModel c = new BallotVoteModel();

            //convert default
            new CursorHelper(cursor, getColumnIndexCache()).current(new CursorHelper.Callback() {
                @Override

                public boolean next(CursorHelper cursorHelper) {
                    c
                        .setId(cursorHelper.getInt(BallotVoteModel.COLUMN_ID))
                        .setBallotId(cursorHelper.getInt(BallotVoteModel.COLUMN_BALLOT_ID))
                        .setBallotChoiceId(cursorHelper.getInt(BallotVoteModel.COLUMN_BALLOT_CHOICE_ID))
                        .setVotingIdentity(cursorHelper.getString(BallotVoteModel.COLUMN_VOTING_IDENTITY))
                        .setChoice(cursorHelper.getInt(BallotVoteModel.COLUMN_CHOICE))
                        .setCreatedAt(cursorHelper.getDate(BallotVoteModel.COLUMN_CREATED_AT))
                        .setModifiedAt(cursorHelper.getDate(BallotVoteModel.COLUMN_MODIFIED_AT));
                    return false;
                }
            });

            return c;
        }

        return null;
    }

    public boolean createOrUpdate(BallotVoteModel ballotVoteModel) {

        boolean insert = true;
        if (ballotVoteModel.getId() > 0) {
            Cursor cursor = getReadableDatabase().query(
                this.getTableName(),
                null,
                BallotVoteModel.COLUMN_ID + "=?",
                new String[]{
                    String.valueOf(ballotVoteModel.getId())
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
            return create(ballotVoteModel);
        } else {
            return update(ballotVoteModel);
        }
    }

    private ContentValues buildContentValues(BallotVoteModel ballotVoteModel) {
        ContentValues contentValues = new ContentValues();

        contentValues.put(BallotVoteModel.COLUMN_BALLOT_ID, ballotVoteModel.getBallotId());
        contentValues.put(BallotVoteModel.COLUMN_BALLOT_CHOICE_ID, ballotVoteModel.getBallotChoiceId());
        contentValues.put(BallotVoteModel.COLUMN_VOTING_IDENTITY, ballotVoteModel.getVotingIdentity());
        contentValues.put(BallotVoteModel.COLUMN_CHOICE, ballotVoteModel.getChoice());
        contentValues.put(BallotVoteModel.COLUMN_CREATED_AT, ballotVoteModel.getCreatedAt() != null ? ballotVoteModel.getCreatedAt().getTime() : null);
        contentValues.put(BallotVoteModel.COLUMN_MODIFIED_AT, ballotVoteModel.getModifiedAt() != null ? ballotVoteModel.getModifiedAt().getTime() : null);

        return contentValues;
    }

    public boolean create(BallotVoteModel ballotVoteModel) {
        ContentValues contentValues = buildContentValues(ballotVoteModel);
        long newId = getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
        if (newId > 0) {
            ballotVoteModel.setId((int) newId);
            return true;
        }
        return false;
    }

    private boolean update(BallotVoteModel ballotVoteModel) {
        ContentValues contentValues = buildContentValues(ballotVoteModel);
        getWritableDatabase().update(this.getTableName(),
            contentValues,
            BallotVoteModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(ballotVoteModel.getId())
            });
        return true;
    }

    public int delete(BallotVoteModel ballotVoteModel) {
        return getWritableDatabase().delete(this.getTableName(),
            BallotVoteModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(ballotVoteModel.getId())
            });
    }

    public int deleteByIds(int[] ids) {
        String[] params = new String[ids.length];
        for (int n = 0; n < ids.length; n++) {
            params[n] = String.valueOf(ids[n]);
        }
        return getWritableDatabase().delete(this.getTableName(),
            BallotVoteModel.COLUMN_ID + " IN(" + DatabaseUtil.makePlaceholders(params.length) + ")",
            params);
    }

    public int deleteByBallotId(int ballotId) {
        return getWritableDatabase().delete(
            this.getTableName(),
            BallotVoteModel.COLUMN_BALLOT_ID + "=?",
            new String[]{
                String.valueOf(ballotId)
            }
        );
    }

    public int deleteByBallotIdAndVotingIdentity(int ballotId, String identity) {
        return getWritableDatabase().delete(
            this.getTableName(),
            BallotVoteModel.COLUMN_BALLOT_ID + "=? "
                + "AND " + BallotVoteModel.COLUMN_VOTING_IDENTITY + "=?",
            new String[]{
                String.valueOf(ballotId),
                identity
            }
        );
    }

    private BallotVoteModel getFirst(String selection, String[] selectionArgs) {
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

    public long countByBallotChoiceIdAndChoice(int ballotChoiceId, int choice) {
        return DatabaseUtils.longForQuery(getReadableDatabase(),
            "SELECT COUNT(*) FROM " + this.getTableName() + " "
                + "WHERE " + BallotVoteModel.COLUMN_BALLOT_CHOICE_ID + "=? "
                + "AND " + BallotVoteModel.COLUMN_CHOICE + "=?",
            new String[]{
                String.valueOf(ballotChoiceId),
                String.valueOf(choice)
            });
    }

    @Override
    public String[] getStatements() {
        return new String[]{
            "CREATE TABLE `ballot_vote` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `ballotId` INTEGER NOT NULL , `ballotChoiceId` INTEGER NOT NULL , `votingIdentity` VARCHAR NOT NULL , `choice` INTEGER , `createdAt` BIGINT NOT NULL , `modifiedAt` BIGINT NOT NULL );",
            //indices
            "CREATE INDEX `ballotVotingCount` ON `ballot_vote` ( `ballotChoiceId`, `choice` )",
            "CREATE UNIQUE INDEX `ballotVoteIdentity` ON `ballot_vote` ( `ballotId`, `ballotChoiceId`, `votingIdentity` );"
        };
    }
}
