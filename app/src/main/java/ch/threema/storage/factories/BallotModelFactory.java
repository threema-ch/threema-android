/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2020 Threema GmbH
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

import net.sqlcipher.Cursor;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.DatabaseUtil;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.ballot.BallotVoteModel;
import ch.threema.storage.models.ballot.GroupBallotModel;
import ch.threema.storage.models.ballot.IdentityBallotModel;

public class BallotModelFactory extends ModelFactory {
	public BallotModelFactory(DatabaseServiceNew databaseService) {
		super(databaseService, BallotModel.TABLE);
	}

	public List<BallotModel> getAll() {
		return convertList(this.getReadableDatabase().query(this.getTableName(),
				null,
				null,
				null,
				null,
				null,
				null));
	}

	@Nullable
	public BallotModel getById(int id) {
		return getFirst(
				BallotModel.COLUMN_ID + "=?",
				new String[]{
						String.valueOf(id)
				});
	}

	public List<BallotModel> convert(
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

	protected List<BallotModel> convertList(Cursor c) {

		List<BallotModel> result = new ArrayList<>();
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

	protected BallotModel convert(Cursor cursor) {
		if(cursor != null && cursor.getPosition() >= 0) {
			final BallotModel c = new BallotModel();

			//convert default
			new CursorHelper(cursor, this.getColumnIndexCache()).current(new CursorHelper.Callback() {
				@Override
				public boolean next(CursorHelper cursorHelper) {
					c
							.setId(cursorHelper.getInt(BallotModel.COLUMN_ID))
							.setApiBallotId(cursorHelper.getString(BallotModel.COLUMN_API_BALLOT_ID))
							.setCreatorIdentity(cursorHelper.getString(BallotModel.COLUMN_CREATOR_IDENTITY))
							.setName(cursorHelper.getString(BallotModel.COLUMN_NAME))
							.setCreatedAt(cursorHelper.getDate(BallotModel.COLUMN_CREATED_AT))
							.setModifiedAt(cursorHelper.getDate(BallotModel.COLUMN_MODIFIED_AT))
							.setLastViewedAt(cursorHelper.getDate(BallotModel.COLUMN_LAST_VIEWED_AT));

					String stateString = cursorHelper.getString(BallotModel.COLUMN_STATE);
					if (!TestUtil.empty(stateString)) {
						c.setState(BallotModel.State.valueOf(stateString));
					}
					String assessment = cursorHelper.getString(BallotModel.COLUMN_ASSESSMENT);
					if (!TestUtil.empty(assessment)) {
						c.setAssessment(BallotModel.Assessment.valueOf(assessment));
					}

					String type = cursorHelper.getString(BallotModel.COLUMN_TYPE);
					if (!TestUtil.empty(type)) {
						c.setType(BallotModel.Type.valueOf(type));
					}

					String choiceType = cursorHelper.getString(BallotModel.COLUMN_CHOICE_TYPE);
					if (!TestUtil.empty(choiceType)) {
						c.setChoiceType(BallotModel.ChoiceType.valueOf(choiceType));
					}

					return false;
				}
			});

			return c;
		}

		return null;
	}

	public boolean createOrUpdate(BallotModel ballotModel) {

		boolean insert = true;
		if(ballotModel.getId() > 0) {
			Cursor cursor = this.getReadableDatabase().query(
					this.getTableName(),
					null,
					BallotModel.COLUMN_ID + "=?",
					new String[]{
							String.valueOf(ballotModel.getId())
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
			return create(ballotModel);
		}
		else {
			return update(ballotModel);
		}
	}

	protected ContentValues buildContentValues(BallotModel ballotModel) {
		ContentValues contentValues = new ContentValues();
		contentValues.put(BallotModel.COLUMN_API_BALLOT_ID, ballotModel.getApiBallotId());
		contentValues.put(BallotModel.COLUMN_CREATOR_IDENTITY, ballotModel.getCreatorIdentity());
		contentValues.put(BallotModel.COLUMN_NAME, ballotModel.getName());
		contentValues.put(BallotModel.COLUMN_STATE, ballotModel.getState() != null ? ballotModel.getState().toString() : null);
		contentValues.put(BallotModel.COLUMN_ASSESSMENT, ballotModel.getAssessment() != null ? ballotModel.getAssessment().toString() : null);
		contentValues.put(BallotModel.COLUMN_TYPE, ballotModel.getType() != null ? ballotModel.getType().toString() : null);
		contentValues.put(BallotModel.COLUMN_CHOICE_TYPE, ballotModel.getChoiceType() != null ? ballotModel.getChoiceType().toString() : null);
		contentValues.put(BallotModel.COLUMN_CREATED_AT, ballotModel.getCreatedAt() != null ? ballotModel.getCreatedAt().getTime(): null);
		contentValues.put(BallotModel.COLUMN_MODIFIED_AT, ballotModel.getModifiedAt() != null ? ballotModel.getModifiedAt().getTime(): null);
		contentValues.put(BallotModel.COLUMN_LAST_VIEWED_AT, ballotModel.getLastViewedAt() != null ? ballotModel.getLastViewedAt().getTime(): null);
		return contentValues;
	}

	public boolean create(BallotModel ballotModel) {
		ContentValues contentValues = buildContentValues(ballotModel);
		long newId = this.getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
		if (newId > 0) {
			ballotModel.setId((int) newId);
			return true;
		}
		return false;
	}

	public boolean update(BallotModel ballotModel) {
		ContentValues contentValues = buildContentValues(ballotModel);
		this.getWritableDatabase().update(this.getTableName(),
				contentValues,
				BallotModel.COLUMN_ID + "=?",
				new String[]{
						String.valueOf(ballotModel.getId())
				});
		return true;
	}

	public int delete(BallotModel ballotModel) {
		return this.getWritableDatabase().delete(this.getTableName(),
				BallotModel.COLUMN_ID + "=?",
				new String[]{
						String.valueOf(ballotModel.getId())
				});
	}

	@Nullable
	protected BallotModel getFirst(String selection, String[] selectionArgs) {
		Cursor cursor = this.getReadableDatabase().query (
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

	public long count(BallotService.BallotFilter filter) {
		Cursor resultCursor = this.runBallotFilterQuery(filter, "SELECT COUNT(*)");

		if (resultCursor != null) {
			return DatabaseUtil.count(resultCursor);
		}

		return 0L;
	}


	public List<BallotModel> filter(BallotService.BallotFilter filter) {
		Cursor resultCursor = this.runBallotFilterQuery(filter, "SELECT DISTINCT b.*");

		if (resultCursor != null) {
			return this.convertList(resultCursor);
		}

		return new ArrayList<>();
	}

	public BallotModel getByApiBallotIdAndIdentity(String apiBallotId, String groupCreator) {
		return getFirst(
				BallotModel.COLUMN_API_BALLOT_ID + "=? "
						+ "AND " + BallotModel.COLUMN_CREATOR_IDENTITY + "=?",
				new String[]{
						apiBallotId,
						groupCreator
				});
	}

	@Override
	public String[] getStatements() {
		return new String[] {
				"CREATE TABLE `ballot` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `apiBallotId` VARCHAR NOT NULL , `creatorIdentity` VARCHAR NOT NULL , `name` VARCHAR , `state` VARCHAR NOT NULL , `assessment` VARCHAR NOT NULL , `type` VARCHAR NOT NULL , `choiceType` VARCHAR NOT NULL , `createdAt` BIGINT NOT NULL , `modifiedAt` BIGINT NOT NULL , `lastViewedAt` BIGINT )",

				//indices
				"CREATE UNIQUE INDEX `apiBallotIdAndCreator` ON `ballot` ( `apiBallotId`, `creatorIdentity` )"
		};
	}

	protected Cursor runBallotFilterQuery(BallotService.BallotFilter filter, String select) {
		String query = select + " FROM " + this.getTableName() + " b";
		List<String> args = new ArrayList<>();

		if(filter != null) {

			MessageReceiver receiver = filter.getReceiver();
			if (receiver != null) {
				String linkTable;
				String linkField;
				String linkFieldReceiver;
				String linkValue;
				switch (receiver.getType()) {
					case MessageReceiver.Type_GROUP:
						linkTable = GroupBallotModel.TABLE;
						linkField = GroupBallotModel.COLUMN_BALLOT_ID;
						linkFieldReceiver = GroupBallotModel.COLUMN_GROUP_ID;
						linkValue = String.valueOf(((GroupMessageReceiver) receiver).getGroup().getId());

						break;
					case MessageReceiver.Type_CONTACT:
						linkTable = IdentityBallotModel.TABLE;
						linkField = IdentityBallotModel.COLUMN_BALLOT_ID;
						linkFieldReceiver = IdentityBallotModel.COLUMN_IDENTITY;
						linkValue = ((ContactMessageReceiver) receiver).getContact().getIdentity();

						break;
					default:
						//do not run a ballot query
						return null;
				}

				if(linkTable != null) {
					query += " INNER JOIN " + linkTable + " l"
						+ " ON l." + linkField + " = b." + BallotModel.COLUMN_ID
						+ " AND l." + linkFieldReceiver + " = ?";

					args.add(linkValue);
				}
			}

			// Build where statement
			List<String> where = new ArrayList<>();

			if(filter.getStates() != null && filter.getStates().length > 0) {
				where.add("b." + BallotModel.COLUMN_STATE + " IN (" + DatabaseUtil.makePlaceholders(filter.getStates().length) + ")");
				for(BallotModel.State f: filter.getStates()) {
					args.add(f.toString());
				}
			}

			if (filter.createdOrNotVotedByIdentity() != null) {

				// Created by the identity OR no votes from the identity
				where.add("b." + BallotModel.COLUMN_CREATOR_IDENTITY + " = ? OR NOT EXISTS ("
					+ "SELECT sv." + BallotVoteModel.COLUMN_BALLOT_ID
					+ " FROM " + BallotVoteModel.TABLE + " sv"
					+ " WHERE sv." + BallotVoteModel.COLUMN_VOTING_IDENTITY + " = ? AND sv." + BallotVoteModel.COLUMN_BALLOT_ID  + " = b." + BallotModel.COLUMN_ID
					+ ")");
				args.add(filter.createdOrNotVotedByIdentity());
				args.add(filter.createdOrNotVotedByIdentity());
			}

			if (where.size() > 0) {
				String whereStatement = "";
				for (String s: where) {
					whereStatement += (whereStatement.length() > 0 ? ") AND (" : "");
					whereStatement += s;
				}
				query += " WHERE (" +whereStatement + ")";
			}

			query += " ORDER BY b." + BallotModel.COLUMN_CREATED_AT + " DESC";
		}

		return this.getReadableDatabase().rawQuery(query,
			DatabaseUtil.convertArguments(args));
	}
}
