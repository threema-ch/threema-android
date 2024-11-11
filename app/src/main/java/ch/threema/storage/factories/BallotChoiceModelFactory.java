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

import ch.threema.app.utils.TestUtil;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.models.ballot.BallotChoiceModel;

public class BallotChoiceModelFactory extends ModelFactory {
	public BallotChoiceModelFactory(DatabaseServiceNew databaseService) {
		super(databaseService, BallotChoiceModel.TABLE);
	}

	public List<BallotChoiceModel> getAll() {
		return convertList(this.databaseService.getReadableDatabase().query(this.getTableName(),
				null,
				null,
				null,
				null,
				null,
				null));
	}

	public List<BallotChoiceModel> getByBallotId(int ballotId) {
		return convertList(this.databaseService.getReadableDatabase().query(this.getTableName(),
				null,
				BallotChoiceModel.COLUMN_BALLOT_ID + "=?",
				new String[]{
						String.valueOf(ballotId)
				},
				null,
				null,
				"`" + BallotChoiceModel.COLUMN_ORDER + "` ASC"));
	}

	public BallotChoiceModel getByBallotIdAndApiChoiceId(int ballotId, int apiChoiceId) {
		return getFirst(
				BallotChoiceModel.COLUMN_BALLOT_ID + "=? "
						+ "AND " + BallotChoiceModel.COLUMN_API_CHOICE_ID + "=?",
				new String[]{
						String.valueOf(ballotId),
						String.valueOf(apiChoiceId)
				});
	}


	public BallotChoiceModel getById(int id) {
		return getFirst(
				BallotChoiceModel.COLUMN_ID + "=?",
				new String[]{
						String.valueOf(id)
				});
	}

	public List<BallotChoiceModel> convert(QueryBuilder queryBuilder,
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

	private List<BallotChoiceModel> convertList(Cursor c) {

		List<BallotChoiceModel> result = new ArrayList<>();
		if(c != null) {
			try {
				while (c.moveToNext()) {
					result.add(convert(c));
				}
			}
			finally {
				c.close();
			}
		}
		return result;
	}

	private BallotChoiceModel convert(Cursor cursor) {
		if(cursor != null && cursor.getPosition() >= 0) {
			final BallotChoiceModel c = new BallotChoiceModel();

			//convert default
			new CursorHelper(cursor, columnIndexCache).current(new CursorHelper.Callback() {
				@Override

				public boolean next(CursorHelper cursorFactory) {
					c
							.setId(cursorFactory.getInt(BallotChoiceModel.COLUMN_ID))
							.setBallotId(cursorFactory.getInt(BallotChoiceModel.COLUMN_BALLOT_ID))
							.setApiBallotChoiceId(cursorFactory.getInt(BallotChoiceModel.COLUMN_API_CHOICE_ID))
							.setName(cursorFactory.getString(BallotChoiceModel.COLUMN_NAME))
							.setVoteCount(cursorFactory.getInt(BallotChoiceModel.COLUMN_VOTE_COUNT))
							.setOrder(cursorFactory.getInt(BallotChoiceModel.COLUMN_ORDER))
							.setCreatedAt(cursorFactory.getDate(BallotChoiceModel.COLUMN_CREATED_AT))
							.setModifiedAt(cursorFactory.getDate(BallotChoiceModel.COLUMN_MODIFIED_AT));

					String type = cursorFactory.getString(BallotChoiceModel.COLUMN_TYPE);
					if(!TestUtil.isEmptyOrNull(type)) {
						c.setType(BallotChoiceModel.Type.valueOf(type));
					}
					return false;
				}
			});

			return c;
		}

		return null;
	}

	public boolean createOrUpdate(BallotChoiceModel ballotChoiceModel) {

		boolean insert = true;
		if(ballotChoiceModel.getId() > 0) {
			Cursor cursor = this.databaseService.getReadableDatabase().query(
					this.getTableName(),
					null,
					BallotChoiceModel.COLUMN_ID + "=?",
					new String[]{
							String.valueOf(ballotChoiceModel.getId())
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


		if(insert) {
			return create(ballotChoiceModel);
		}
		else {
			return update(ballotChoiceModel);
		}
	}

	private ContentValues buildContentValues(BallotChoiceModel ballotChoiceModel) {
		ContentValues contentValues = new ContentValues();

		contentValues.put(BallotChoiceModel.COLUMN_BALLOT_ID, ballotChoiceModel.getBallotId());
		contentValues.put(BallotChoiceModel.COLUMN_API_CHOICE_ID, ballotChoiceModel.getApiBallotChoiceId());
		contentValues.put(BallotChoiceModel.COLUMN_TYPE, ballotChoiceModel.getType() != null ? ballotChoiceModel.getType().toString() : null);
		contentValues.put(BallotChoiceModel.COLUMN_NAME, ballotChoiceModel.getName());
		contentValues.put(BallotChoiceModel.COLUMN_VOTE_COUNT, ballotChoiceModel.getVoteCount());
		contentValues.put("`" + BallotChoiceModel.COLUMN_ORDER + "`", ballotChoiceModel.getOrder());
		contentValues.put(BallotChoiceModel.COLUMN_CREATED_AT, ballotChoiceModel.getCreatedAt() != null ? ballotChoiceModel.getCreatedAt().getTime(): null);
		contentValues.put(BallotChoiceModel.COLUMN_MODIFIED_AT, ballotChoiceModel.getModifiedAt() != null ? ballotChoiceModel.getModifiedAt().getTime() : null);

		return contentValues;
	}

	public boolean create(BallotChoiceModel ballotChoiceModel) {
		ContentValues contentValues = buildContentValues(ballotChoiceModel);
		long newId = this.databaseService.getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
		if (newId > 0) {
			ballotChoiceModel.setId((int) newId);
			return true;
		}
		return false;
	}

	private boolean update(BallotChoiceModel ballotChoiceModel) {
		ContentValues contentValues = buildContentValues(ballotChoiceModel);
		this.databaseService.getWritableDatabase().update(this.getTableName(),
				contentValues,
				BallotChoiceModel.COLUMN_ID + "=?",
				new String[]{
						String.valueOf(ballotChoiceModel.getId())
				});
		return true;
	}


	public int delete(BallotChoiceModel ballotChoiceModel) {
		return this.databaseService.getWritableDatabase().delete(this.getTableName(),
				BallotChoiceModel.COLUMN_ID + "=?",
				new String[]{
						String.valueOf(ballotChoiceModel.getId())
				});
	}

	public int deleteByBallotId(int ballotId) {
		return this.databaseService.getWritableDatabase().delete(
				this.getTableName(),
				BallotChoiceModel.COLUMN_BALLOT_ID + "=?",
				new String[] {
						String.valueOf(ballotId)
				}
		);
	}

	private BallotChoiceModel getFirst(String selection, String[] selectionArgs) {
		Cursor cursor = this.databaseService.getReadableDatabase().query (
				this.getTableName(),
				null,
				selection,
				selectionArgs,
				null,
				null,
				null
		);

		if(cursor != null) {
			try {
				if (cursor.moveToFirst()) {
					return convert(cursor);
				}
			}
			finally {
				cursor.close();
			}
		}

		return null;
	}

	@Override
	public String[] getStatements() {
		return new String[]{
				"CREATE TABLE `ballot_choice` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `ballotId` INTEGER , `apiBallotChoiceId` INTEGER , `type` VARCHAR , `name` VARCHAR , `voteCount` INTEGER , `order` INTEGER NOT NULL , `createdAt` BIGINT , `modifiedAt` BIGINT )",

				//indices
				"CREATE UNIQUE INDEX `apiBallotChoiceId` ON `ballot_choice` ( `ballotId`, `apiBallotChoiceId` )"
		};
	}
}
